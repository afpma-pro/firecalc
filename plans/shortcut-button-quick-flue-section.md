# Shortcut Button: Quick Flue Section (SetInnerShape + Straight + 90° Sharp)

## Context

Building a flue pipe in FireCalc today requires repeatedly clicking through the `TagTreeMenuComponent` menu to add, in order:

1. A `SetInnerShape` (to set/change the pipe's inner cross-section).
2. A straight section (`AddSectionSlopped`).
3. A direction change (typically `AddSharpeAngle_0_to_180` at 90°).

This triplet is the **dominant pattern** when drafting a flue channel. The request is to collapse it into a single "lightning" button in the existing menu so the three elements are inserted atomically at the chosen position (end of list OR any mid-sequence "+" separator).

The button must:

- Appear **only** in flue panels (`FluePipeT` / `FlueSlot_V3`). Air intake, connectors, chimney, all 13384 pipes are out of scope.
- Prefill `SetInnerShape.shape` by reading the inner geometry that is in effect *at the insert position*, using the **canonical** `PipeIncrDescr → PipeFullDescr` conversion — never a parallel recompute.
- Rely on the fact that `AddSectionSlopped` in V3 has no `elevation_gain` field; the engine already computes `|length| × frame.direction.z` in `ElementFactory_15544_Instances.scala:73-106`, so the shortcut just emits `AddSectionSlopped(name, 1.meter)` and correctness follows.
- Use `lucide.zap(stroke_width = 2.0)` and the `bg-base-200 hover:bg-secondary` styling already used by `TagTreeMenu.Shortcut` (`TagTreeMenuComponent.scala:102`).

## Design Decisions (from interview)

| Question | Answer |
|---|---|
| Scope | Flue only (`DynamicFlowOnlyPipeSlotPanel`, the one concrete class with `sectionType = FluePipeT`) |
| Straight variant | `AddSectionSlopped(name, 1.meter)` — elevation_gain auto-computed from the frame |
| Direction change | `AddSharpeAngle_0_to_180(name, 90.degrees, roll = None)` |
| Geometry element | `SetFlowOnlyPipeProp_15544_V3.SetInnerShape(shape)` always inserted (uniform shape of output) |
| Previous-shape source | Canonical fold via a new prefix-state helper; NO parallel recompute |
| Fallback when no previous | `pipeShapeInner.default.shape` (covers index-0/empty-list case) |
| Visual | `lucide.zap`, same chip styling as the existing `Shortcut` variant |

## Implementation Plan

### Step 1 — Engine: prefix-state query helper

**File:** `modules/engine-kernel/src/main/scala/afpma/firecalc/engine/alg/IncrementalBuilderAlg.scala`

Add a method on the builder (next to `buildFrom` / `buildIncrDescr`, ~line 115) that folds only the first `n` `Id_IncrDescr` and returns the accumulated `PropsState`:

```scala
def propsStateAtPrefix(piDescr: PipeIncrDescr, n: Int): ValidatedNel[..., PropsState]
```

Implementation reuses `buildIncrDescr` on `piDescr.listIncrDescr().take(n)` and returns only the `PropsState` component of the accumulator (discarding the partial `PipeFullDescr` and id mappings). This guarantees we inherit all the canonical state-update rules (`SetInnerShape` → `geometry`, direction changes → `currentFrame`, etc.) instead of mirroring them.

No new types. Minimal public surface.

### Step 2 — TagTreeMenu ADT: dynamic shortcut variant

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/components/TagTreeMenuComponent.scala`

Around line 293–305, extend `sealed trait Elems[+A]` with:

```scala
case class ShortcutFn[+A](
    txt    : String,
    compute: Int => Seq[A]   // argument: insert index
) extends Elems[A]
```

The existing `Shortcut` (line 298) stays as-is — it's for static tuples. `ShortcutFn` is strictly additive.

### Step 3 — TagTreeMenuComponent: thread insert-index signal, render `ShortcutFn`

**File:** same as Step 2, the component constructor (around line 23).

- Add parameter `insertIdxSig: Signal[Int]` (default: `elems_size_v.signal` so existing call sites keep working).
- In the render match for `Elems` (around lines 100–141), add a `ShortcutFn` case that, on click, reads `insertIdxSig.now()`, calls `compute(idx)`, and emits one `CollectionCommand.Append((size + i, elem))` per element — identical loop to the existing `Shortcut` branch (lines 132–141).
- Visual: reuse the existing shortcut chip style — `lucide.zap(stroke_width = 2.0)` + `bg-base-200 hover:bg-secondary`.

### Step 4 — PipePanel: pass the right insert-index signal

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel.scala`

- End-of-list menu (line 562): pass `insertIdxSig = elems_size_v.signal`.
- Insert dialog (`InsertElementDialog.innerMenu`, line 489): pass `insertIdxSig = insertIdxVar.signal.map(_.getOrElse(0))`.

This is the only plumbing needed for the shortcut's `compute(idx)` to receive the correct index in both contexts.

### Step 5 — Register shortcut on the flue panel only

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/panels/DynamicPipeSlotPanel.scala` (class `DynamicFlowOnlyPipeSlotPanel`, `tagTreeMenu` val at line 859)

Prepend a `ShortcutFn` entry to this specific `tagTreeMenu`:

```scala
TagTreeMenu.ShortcutFn[FlowOnlyPipeDescr_15544](
  txt     = I18N_UI.shortcuts.quick_flue_section,
  compute = (insertIdx: Int) => {
    val prevShape: PipeShape =
      propsStateAtPrefix(currentPipeIncrDescr, insertIdx)
        .toOption
        .flatMap(_.geometry)
        .getOrElse(pipeShapeInner.default.shape)
    Seq(
      SetInnerShape(prevShape),
      AddSectionSlopped         (I18N_UI.default_element_names.straight_element, 1.meters),
      AddSharpeAngle_0_to_180   (I18N.add_element.AddSharpeAngle_0_to_180, qty_d.angle.ninety.default, None)
    )
  }
)
```

`currentPipeIncrDescr` is already reachable inside this panel (the panel owns `elems_v` and the incr-descr projection used by `pipeMappings_vnel_signal`).

### Step 6 — i18n

**Files:**
- `modules/i18n/src/main/resources/i18n/en.conf`
- `modules/i18n/src/main/resources/i18n/fr.conf`
- `modules/i18n/src/main/scala/afpma/firecalc/i18n/I18nData.scala` (manual — not generated)

Add a `shortcuts.quick_flue_section` key (or similar) with short labels, e.g. EN `"Quick section"`, FR `"Tronçon rapide"`. Add the matching case-class field on `Shortcuts` in `I18nData.scala`.

## Files Modified (Summary)

| File | Purpose |
|---|---|
| `engine-kernel/.../IncrementalBuilderAlg.scala` | Add `propsStateAtPrefix` |
| `ui/.../components/TagTreeMenuComponent.scala` | Add `ShortcutFn` variant + render case + `insertIdxSig` param |
| `ui/.../panels/PipePanel.scala` | Pass correct `insertIdxSig` at both call sites |
| `ui/.../panels/DynamicPipeSlotPanel.scala` | Register the shortcut on the flue panel only |
| `i18n/en.conf`, `i18n/fr.conf`, `i18n/I18nData.scala` | New label |

## Reused Utilities

- `CollectionCommand.Append` / `Insert` and the existing `insertObserver` (PipePanel.scala:476) — no new command types.
- `TagTreeMenuComponent.Shortcut` visual/click pattern (lines 102, 132–141) — mirrored for `ShortcutFn`.
- `FlowOnlyPropsState_15544.geometry` (engine-15544-common, `PropsStateOps_FlowOnly_15544_Instance.scala:28-35`) — already holds the canonical inner shape.
- `ElementFactory_15544_Instances.scala:73-106` — already auto-computes `elevation_gain`, so no UI-side math needed.
- `Defaultable` instances in `instances/defaultable_15544.scala` — reuse `pipeShapeInner.default`, default names, and `qty_d.angle.ninety.default`.

## Verification

1. **Compile:** `sbt --client "ui/fastLinkJS"` (or `firecalc-metals compile-module` on the affected modules, starting with engine-kernel then ui).
2. **Engine tests unaffected:** `sbt --client "engine/test"` should pass — we're adding a helper, not changing existing behavior.
3. **Dev UI smoke tests** (`make dev-web-ui-compile` + `make dev-web-ui-run` + `make dev-electron-app-run-vite`):
   - **A — empty flue pipe + end-of-list click:** lightning button inserts `SetInnerShape(default)` + straight 1 m + 90° sharp. Engine computes elevation_gain without error.
   - **B — flue pipe with existing geometry + end-of-list click:** `SetInnerShape` prefills to the shape active at the end of the list (confirm by clicking into the new element and checking the shape field).
   - **C — flue pipe + click "+" separator mid-sequence:** `SetInnerShape` prefills to the shape in effect *at that position* (may differ from end-of-list if there were prior geometry changes).
   - **D — scope:** confirm the lightning button does **NOT** appear in: combustion-air intake panel, any 13384 panel (thermal or flow-only), connector slot, chimney slot.
4. **Chrome DevTools MCP:** take a snapshot of the flue panel showing the 3 inserted rows; verify the new elements render with the expected badges (direction-change badge on the 3rd row).

## Non-Goals / Explicitly Out of Scope

- No change to connector, chimney, or 13384 panels.
- No change to the engine's geometry / elevation_gain computation (already correct).
- No new `CollectionCommand` variant (existing `Append` + `insertObserver` handles batch insertion).
- No migration of existing `TagTreeMenu.Shortcut` call sites to `ShortcutFn` — they stay static.
