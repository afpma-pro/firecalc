<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Direction Propagation in Pipe Panels

How the direction system works for direction-change elements (`AddDirectionChange` and subtypes).

---

## Concepts

### Two representations of direction

| Representation | Where | Type |
|---|---|---|
| **AbsoluteDirection** (absolute) | DTO, badge | `AzimuthDirection × InclinationDirection` — enum variants + `Custom(angle)` |
| **RelativeDirection** (UI-only) | `RelativeDirectionInput` | `(RelativeSide, theta)` where side ∈ {Right, Up, Left, Down}, θ ∈ [0°, 90°] |

Relative direction maps to a continuous **roll angle** α ∈ [0°, 360°):
```
Right → α = θ        Up   → α = 90 + θ
Left  → α = 180 + θ  Down → α = 270 + θ
```

`PipeFrame.relativeTarget(side, θ, deflDeg)` converts relative → absolute Vec3.
`PipeFrame.recoverRelative(targetDir, deflDeg)` is the inverse.

### PipeFrame

`PipeFrame(direction: Vec3, upRef: Vec3)` rides along the pipe. It tracks orientation so that "Right" and "Up" are always relative to the current pipe flow direction.

`frameBeforeByIdx: Signal[Map[Int, PipeFrame]]` — computed by a left-to-right scan in `PipePanel_*` subclasses: for each `AddDirectionChange` element, `applyBendForFinalDir(deflDeg, targetVec)` advances the frame. Any element property change (including `absDir`) triggers a full recompute via `welems_var.signal`.

---

## Signal Chain

```
SetInitialDirection / upstream absDir
        │
        ▼
frameBeforeByIdx (Signal[Map[Int, PipeFrame]])  ← recomputes on any elem change
        │
        ├─► frameBeforeSig_badge(idx)            for DirectionBadgeComponent
        │
        └─► RelativeDirectionInput
                │  ┌─────────────────────────────────────────────────────────────┐
                │  │ Bidirectional sync                                           │
                │  │                                                              │
                │  │  (sideVar, thetaVar) ──forwardSync──► absDirVar           │
                │  │        ▲                                    │               │
                │  │        └───────reverseSync──────────────────┘               │
                │  │                                                              │
                │  │  frameBefore change + absDir unreachable                  │
                │  │     └──cascadeSync──► absDirVar (auto-correct)            │
                │  └─────────────────────────────────────────────────────────────┘
                │
                ▼
           absDirVar  ──► DirectionBadgeComponent (badge + dropdown)
                        ──► frameBeforeByIdx recomputes for downstream elements
```

---

## Sync Binders (in `RelativeDirectionInput`)

All syncs use `.distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)` to prevent transaction-depth overflow. See [`RelativeDirectionInput.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/components/RelativeDirectionInput.scala).

### `forwardSync` — user edits relative dir → updates `absDirVar`

Gates on `.changes` of `(sideVar, thetaVar)` only. `frameBefore` is sampled via `.withCurrentValueOf`, not subscribed. This ensures upstream frame changes do **not** rewrite downstream `absDir` values.

### `reverseSync` — `absDirVar` or context changes → recovers `(side, theta)`

Fires when `externalStSig` (= `absDirVar + frameBefore + deflectionAngle`) changes. Calls `PipeFrame.recoverRelative` to map the absolute direction back to local coordinates. A 0.5° threshold prevents spurious writes.

### `initialSync` — one-time mount

Uses `signal --> Observer` (fires for initial value) + `.composeChanges(_.take(1))` to handle the case where context signals aren't ready at mount time.

### `cascadeSync` — auto-correct incompatible directions

Fires when `frameBefore`/`deflectionAngle` change **and** the current `absDir` is no longer on the deflection cone (checked via `PipeFrame.rollAngleForOutputDirection` returning `None`). Recomputes `absDir` from the preserved `(side, theta)` — which is always valid by construction.

```
isIncompatibleSig = absDirVar × frameBefore × deflectionAngle → Boolean
cascadedFdSig     = (side, theta) × frameBefore × deflectionAngle → Option[AbsoluteDirection]
cascadeNeededSig  = Some(newFd) if incompatible, None otherwise
cascadeSync       = frameBefore.changes.mapTo(()).withCurrentValueOf(cascadeNeededSig) --> absDirVar
```

Chain propagation: element N cascades → `frameBeforeByIdx` recomputes → element N+1 evaluates its own `cascadeSync` if needed → etc.

---

## Warning Badge (`DirectionBadgeComponent`)

`isCompatibleSig: Signal[Option[Boolean]]` — computed from `absDirVar + frameBefore + deflectionAngle` using the same `rollAngleForOutputDirection` oracle. `None` when context is missing.

When `Some(false)`: badge switches to `badge-warning` style and shows a `triangle-alert` icon. In practice the warning is transient (< debounce ms) since `cascadeSync` fires shortly after and corrects `absDir`. The warning persists only for edge cases (zero-deflection, degenerate geometry).

---

## Geometric Constraint

A direction-change element with fixed deflection angle `D°` can only reach directions exactly `D°` from the incoming pipe direction. `AzimuthDirection.Custom` / `InclinationDirection.Custom` store arbitrary angles but the **relative** representation always respects the constraint.

`PipeFrame.reachableCardinals(deflDeg)` lists which named cardinals are reachable — used to populate the badge dropdown.

---

## Key Files

| File | Role |
|---|---|
| `engine/.../geometry/PipeFrame.scala` | Frame geometry, `relativeTarget`, `recoverRelative`, `reachableCardinals` |
| `ui/.../components/RelativeDirectionInput.scala` | Bidirectional sync, cascade, relative dir UI |
| `ui/.../components/DirectionBadgeComponent.scala` | Badge + dropdown, compatibility warning |
| `ui/.../panels/PipePanel_13384_Thermal.scala` | `frameBeforeByIdx` scan, wiring |
| `ui/.../panels/PipePanel.scala` | `renderElemTyped`, badge + extra wiring |
| `dto/.../v4/AbsoluteDirection.scala` | `AzimuthDirection`, `InclinationDirection`, `Custom` variants |

---

## Pitfalls

- **Never subscribe `frameBefore` in `forwardSync`** — causes downstream `absDir` to be overwritten on upstream changes (the original bug, fixed by the `.mapTo(()).withCurrentValueOf` pattern).
- **`.withCurrentValueOf` type erasure** — chaining more than one `.withCurrentValueOf` on a non-Unit stream produces nested tuples that Scala erases to `Any`. Pattern: pre-derive a combined signal, then use a single `.withCurrentValueOf` after `.mapTo(())`.
- **`recoverRelative` does not validate reachability** — it always returns a `(side, theta)` even for impossible targets. Use `rollAngleForOutputDirection(...).isDefined` to check reachability.
