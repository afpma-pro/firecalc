# Plan: Direction Cascade (Strategy 1) + Incompatibility Warning (Strategy 2)

## Problem

When the user changes the initial direction of a pipe (e.g. Right→Up), downstream direction-change elements may have stored `absDir` values that become geometrically impossible with the new upstream frame.

**Example** (from `EngineState.minimal`):
```
Before:  Right ──[90° bend]──> Down     ✓ (90° apart)
After:   Up    ──[90° bend]──> Down     ✗ (180° apart, impossible with 90° bend)
```

The stored `absDir: Down` is no longer reachable at the 90° deflection angle from the new frame.

## Current Behavior

1. `frameBeforeByIdx` **does** recompute when any element's `absDir` changes (zoom lens propagates through `welems_var`)
2. When `absDir` is unreachable, `applyBendForFinalDir` calls `rollAngleForOutputDirection` which returns `None` (angle mismatch > 1°), then falls back to `getOrElse(0.0)` — producing a **silent wrong frame** at roll=0°
3. Part 1 fix: forward sync in `RelativeDirectionInput` only fires on user `(side, theta)` changes, **not** on `frameBefore` changes — so downstream elements keep their (now-invalid) `absDir`
4. The reverse sync **does** fire when `frameBefore` changes, recovering `(side, theta)` from `absDir + frame` — but `recoverRelative` doesn't validate reachability either

**Net result**: after an upstream direction change, downstream elements show stale/wrong `absDir` values with no visual feedback.

## Strategy 1: Preserve Relative Direction (Auto-Cascade)

### Concept

The relative direction `(side, theta)` is **always valid by construction** — it lives on the deflection cone regardless of frame orientation. When upstream changes make `absDir` geometrically impossible, recompute `absDir` from the preserved `(side, theta)` + new frame:

```
Before:  Right-frame + (Down, θ=0°) + 90°bend → absDir=Down    ✓
After:   Up-frame    + (Down, θ=0°) + 90°bend → absDir=Right   ✓ (auto-recomputed)
```

### Implementation

**Key insight**: Part 1's forward sync intentionally blocks cascade (fires only on user changes). We need a **second forward path** that fires when `frameBefore` changes AND the current `absDir` is no longer reachable.

#### 1. Add reachability check helper (in `RelativeDirectionInput.scala`)

```scala
/** True when the current absDir is geometrically reachable from frameBefore at the given deflection. */
private def isReachable(fd: AbsoluteDirection, frame: PipeFrame, deflDeg: Double): Boolean =
    val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
    val targetVec      = Vec3.fromAzimuthElevation(azDeg, elDeg)
    frame.rollAngleForOutputDirection(targetVec, deflDeg).isDefined
```

#### 2. Add cascade sync (alongside existing forward/reverse syncs)

A new sync binder that fires when `frameBefore` changes and detects that the stored `absDir` is no longer reachable. It recomputes `absDir` from the current `(side, theta)`:

```scala
// Cascade sync: frameBefore change + unreachable absDir → recompute from (side, theta)
val cascadeSync =
    frameBefore
        .combineWith(deflectionAngle)
        .distinct
        .changes
        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
        .withCurrentValueOf(absDirVar.signal, sideVar.signal, thetaVar.signal)
        .collect { case ((frameOpt, deflOpt), curFdOpt, side, theta) =>
            for
                frame  <- frameOpt
                defl   <- deflOpt
                curFd  <- curFdOpt
                if !isReachable(curFd, frame, defl)
            yield computeFinalDir(side, theta, frame, defl)
        }
        .collect { case Some(newFdOpt) => newFdOpt }
        --> absDirVar.writer
```

**Why this is safe**:
- Only fires when `frameBefore` or `deflectionAngle` changes (NOT user side/theta)
- Only writes when current `absDir` is actually unreachable (the `isReachable` guard)
- When `absDir` is still reachable (e.g. normal 90° bend, no upstream conflict), does nothing
- Uses existing `(side, theta)` which were set by the last reverse sync or user input

#### 3. Expose reachability signal for Strategy 2

Add a signal that Strategy 2's warning badge can consume:

```scala
/** Signal: is the current absDir geometrically compatible with frameBefore + deflection? */
val isCompatibleSig: Signal[Option[Boolean]] =
    absDirVar.signal
        .combineWith(frameBefore, deflectionAngle)
        .map { case (fdOpt, frameOpt, deflOpt) =>
            for fd <- fdOpt; frame <- frameOpt; defl <- deflOpt
            yield isReachable(fd, frame, defl)
        }
```

**Note**: This signal is `Option[Boolean]` — `None` when context is missing (no frame/deflection yet), `Some(true)` when compatible, `Some(false)` when incompatible.

---

## Strategy 2: Warning Badge for Incompatible Directions

### Concept

When a direction-change element's `absDir` is geometrically impossible (before the cascade sync fires, or in edge cases where cascade produces a different direction than expected), show a visual warning on the badge.

### UI Design

```
Normal:        ┌───────────┐
               │ Down    ▼ │    ← badge-ghost (normal)
               └───────────┘

Incompatible:  ┌───────────┐
               │ ⚠ Down  ▼ │    ← badge-warning + tooltip
               └───────────┘
```

The warning appears **transiently** — it flashes during the brief window between `frameBefore` changing and `cascadeSync` recomputing the `absDir`. In most cases this is < 100ms (debounce delay). The user sees a quick flash, then the badge updates to the new valid direction.

For **degenerate cases** where even cascade can't fix (e.g. zero-deflection bend), the warning persists with an explanatory tooltip.

### Implementation

#### 1. Pass `isCompatibleSig` to `DirectionBadgeComponent`

Add a new parameter:

```scala
case class DirectionBadgeComponent(
    ...
    isCompatible: Signal[Option[Boolean]] = Signal.fromValue(None)  // default: no check
)(using Locale) extends Component:
```

#### 2. Adjust badge styling based on compatibility

In `readOnlyBadge` and `editableBadge`, conditionally apply warning style:

```scala
private def badgeCls(isCompat: Option[Boolean]): String = isCompat match
    case Some(false) => "badge badge-warning badge-sm font-mono"
    case _           => "badge badge-ghost badge-sm font-mono"
```

#### 3. Add warning icon + tooltip

When `isCompatible` is `Some(false)`, prepend a `lucide.triangleAlert` icon (already used in `OrderPDFReportModalComponent`) and wrap in a `DaisyUITooltip` with `tooltip-warning`:

```scala
// Inside badge rendering, conditionally add warning
child <-- isCompatible.map:
    case Some(false) => lucide.triangleAlert(w = 12, h = 12)
    case _           => emptyNode
```

Tooltip text (i18n):
- EN: `"Direction incompatible with bend angle — will auto-correct"`
- FR: `"Direction incompatible avec l'angle de coude — correction automatique"`

#### 4. Wire `isCompatibleSig` from `RelativeDirectionInput` to `DirectionBadgeComponent`

In `PipePanel.scala`'s `renderElemTyped`, pass the signal through. This requires `RelativeDirectionInput` to expose `isCompatibleSig` as a public member.

---

## Files to Modify

### 1. `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/PipeFrame.scala`

No changes needed — `rollAngleForOutputDirection` already returns `None` for unreachable targets with 1° tolerance. This is the reachability oracle.

### 2. `modules/ui/src/main/scala/afpma/firecalc/ui/components/RelativeDirectionInput.scala`

- [ ] Add `isReachable` helper method
- [ ] Add `cascadeSync` binder (fires on `frameBefore` changes when `absDir` is unreachable)
- [ ] Add `isCompatibleSig: Signal[Option[Boolean]]` as public val (for badge wiring)
- [ ] Add `cascadeSync` to the list of binders in `node`

### 3. `modules/ui/src/main/scala/afpma/firecalc/ui/components/DirectionBadgeComponent.scala`

- [ ] Add `isCompatible: Signal[Option[Boolean]]` parameter (default `Signal.fromValue(None)`)
- [ ] Add `badgeCls` helper for conditional warning styling
- [ ] Add warning icon (`lucide.triangleAlert`, 12×12) when incompatible
- [ ] Add warning tooltip (`DaisyUITooltip` with `tooltip-warning`) when incompatible

### 4. `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel.scala`

- [ ] In `renderElemTyped`, wire `RelativeDirectionInput.isCompatibleSig` to `DirectionBadgeComponent.isCompatible`
- [ ] This may require storing the `RelativeDirectionInput` instance before calling `.node`

### 5. `modules/ui/src/main/scala/afpma/firecalc/ui/icons/lucide.scala`

- [ ] Ensure `lucide.triangleAlert` has `w`/`h` parameters (check if already parameterized)

### 6. `modules/ui-i18n/src/main/resources/i18n/en.conf`

- [ ] Add `direction_incompatible_warning` key in `direction_badge` block

### 7. `modules/ui-i18n/src/main/resources/i18n/fr.conf`

- [ ] Add `direction_incompatible_warning` key in `direction_badge` block

---

## Signal Flow After Implementation

```
frameBefore changes (upstream edit)
    │
    ├─→ reverseSync: recovers (side, theta) from old absDir + new frame
    │       └─→ updates sideVar, thetaVar (visual update in RelativeDirectionInput)
    │
    ├─→ cascadeSync: checks isReachable(absDir, newFrame, defl)
    │       ├─ reachable → no-op (absDir is still valid)
    │       └─ NOT reachable → recompute absDir from (side, theta) + new frame
    │               └─→ writes new absDir → badge updates
    │
    └─→ isCompatibleSig: emits Some(false) immediately
            └─→ DirectionBadgeComponent shows ⚠ warning badge
                    └─→ once cascadeSync writes new absDir → isCompatibleSig becomes Some(true)
                            └─→ warning disappears
```

**Timing**: The warning flash lasts ~`LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS` (typically 50-100ms) — long enough to be technically correct but too brief for the user to notice in practice. For persistent incompatibility (degenerate geometry), the warning stays visible.

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Upstream changes, downstream `absDir` still reachable | No cascade, no warning. Everything stays as-is. |
| Upstream changes, downstream `absDir` unreachable | Warning flashes → cascade recomputes → warning clears |
| Zero-deflection bend + any absDir | `isReachable` returns `true` (within 1° tolerance for tiny defl). No warning. |
| Direction ≈ target (degenerate cross product) | `recoverRelative` returns `(Right, 0.0)` default. `isReachable` may be `true` for small deflections. |
| User manually sets impossible `absDir` via code/DTO | Warning persists. Cascade doesn't fire (no `frameBefore` change). User sees ⚠ and can fix via dropdown. |
| Multiple downstream elements affected | Each element independently evaluates + cascades. Chain propagation: element N cascades → new `absDir` → `frameBeforeByIdx` recomputes → element N+1 evaluates → cascade if needed → etc. |

## Verification

- [ ] Compile `ui_i18nJS` then `ui`
- [ ] Test: EngineState.minimal — change initial direction Right→Up, verify 2nd element's absDir auto-updates (was Down, becomes Right or valid direction)
- [ ] Test: EngineState.minimal — change initial direction Right→Front, verify cascade propagates through all downstream direction changes
- [ ] Test: Normal case — change a direction via badge dropdown, verify downstream elements are NOT cascaded (their absDirs are still reachable)
- [ ] Test: Warning badge — temporarily add a `Thread.sleep` or increase debounce to see the ⚠ flash
- [ ] Test: Multiple direction changes — create pipe with 3+ direction changes, change initial dir, verify all cascade correctly in sequence
