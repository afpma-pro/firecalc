# Progress: Relative Direction Input

**Branch**: `feat-replace-roll-with-finaldir`
**Date**: 2026-03-14

## Phase 1: Left/Right + Theta (commit `a308d5c`)

- [x] Step 1: Engine — `localRight`, `relativeTarget`, `recoverRelative` on PipeFrame
- [x] Step 2: Engine — Tests for new methods (87 tests, all pass)
- [x] Step 3: I18n — Add labels (EN/FR + data model)
- [x] Step 4: UI — New `RelativeDirectionInput` component
- [x] Step 5: UI — Wire into panel subclasses (3 panels, 22 DC element call sites)
- [x] Step 6: Verification — compile + test

## Phase 2: 4 Quadrants + Inline Layout (plan: `relative-direction-4-quadrants.md`)

- [x] Step 1: Engine — `RelativeSide` enum (Right/Up/Left/Down), `localUp`, updated `relativeTarget`/`recoverRelative` for 4-quadrant math
- [x] Step 2: Engine — Tests expanded to 109 tests (localUp, 4-quadrant relativeTarget, recoverRelative round-trip, boundary, degenerate)
- [x] Step 3: I18n — Added `relative_up`, `relative_down`, `relative_dir_label`, `abs_dir_label` (EN/FR)
- [x] Step 4: UI — `RelativeDirectionInput` updated: `Var[RelativeSide]`, theta ∈ [0°, 90°], `<details>` dropdown selector
- [x] Step 5: UI — `DirectionBadgeComponent` label changed to "Final dir."
- [x] Step 6: UI — Inline layout in `PipePanel.renderElemTyped`: form + relative dir + badge on same flex row
- [x] Step 7: Verification — 109 PipeFrame tests pass, full compile clean

## Verification

| Check | Result |
|-------|--------|
| `engine/test` PipeFrameSuite (109 tests) | **PASS** |
| `compile-full` (all modules) | **PASS** (no warnings) |
| `compile-module ui` | **PASS** |

## Files Modified (cumulative)

| File | Change |
|------|--------|
| `modules/engine/.../geometry/PipeFrame.scala` | `RelativeSide` enum, `localRight`, `localUp`, `relativeTarget(RelativeSide, theta, defl)`, `recoverRelative → (RelativeSide, Double)` |
| `modules/engine/src/test/.../PipeFrameSuite.scala` | 109 tests covering localRight, localUp, 4-quadrant relativeTarget/recoverRelative, boundary, degenerate |
| `modules/ui-i18n/.../I18nData_UI.scala` | +7 fields on `DirectionBadge`: `relative_left`, `relative_right`, `relative_theta`, `relative_up`, `relative_down`, `relative_dir_label`, `abs_dir_label` |
| `modules/ui-i18n/.../i18n/en.conf` | +7 keys in `direction_badge` block |
| `modules/ui-i18n/.../i18n/fr.conf` | +7 keys in `direction_badge` block |
| `modules/ui/.../components/RelativeDirectionInput.scala` | 4-quadrant dropdown selector + theta input, bidirectional sync with `absDirVar` |
| `modules/ui/.../components/DirectionBadgeComponent.scala` | Label changed to "Final dir." (`abs_dir_label`) |
| `modules/ui/.../panels/PipePanel.scala` | Inline layout: form + extraNode + badge on same flex row |
| `modules/ui/.../panels/PipePanel_13384_FlowOnly.scala` | +`relativeDirectionExtra` helper, `extra` param on 10 DC elements |
| `modules/ui/.../panels/PipePanel_13384_Thermal.scala` | +`relativeDirectionExtra` helper, `extra` param on 10 DC elements |
| `modules/ui/.../panels/FluePipePanel.scala` | +`relativeDirectionExtra` helper, `extra` param on 2 DC elements |

---

## Lessons Learned

### 1. recoverRelative sign derivation

Initial test expected theta=+90° for reaching Up from a Rear pipe with side=Right. Correct answer is theta=-90°, because `rodriguesRotate(Right, Rear, -90°) = Up` (right-hand rule: rotating Right counterclockwise around Rear by -90° gives Up). The forward/reverse math is self-consistent; the error was purely in the test expectation.

### 2. Airstream bidirectional sync — never use mutable guards

The initial `RelativeDirectionInput` used a mutable `var syncing = false` to prevent feedback loops. This caused `Transaction depth exceeded maxDepth = 1000` at runtime because Airstream transactions are synchronous and nested — a mutable flag set/cleared within the same synchronous chain cannot break the recursion.

**Correct pattern** (from `LaminarFormFactory.mkFromUnderlyingWithLinkedVar`):
```scala
varA.signal
    .distinct
    .changes
    .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)  // 1000ms — breaks sync chain
    .withCurrentValueOf(varB.signal)
    .collect { case (newVal, curVal) if newVal != curVal => newVal }
    --> varB.writer
```

Key elements:
- `.distinct` — skip if value hasn't changed
- `.changes` — only emit on actual changes (not initial value)
- `.debounce(ms)` — async break, prevents synchronous transaction nesting
- `.withCurrentValueOf(other).collect { != }` — skip if already in sync

### 3. Airstream `.withCurrentValueOf` erases types in pattern matches

`.withCurrentValueOf(sig1, sig2)` returns tuples where Metals/Scala 3 can lose type information in `case` patterns, leading to `Any` types and compilation errors. Fix: extract derived signals first, then use `.withCurrentValueOf` with individual signals.

### 4. Do not use `ctx.owner` / `.observe(ctx.owner)` in `onMountCallback`

Bad practice in Laminar. The reverse sync binder handles initialization naturally — when the component mounts and `absDirVar` already has a value, the signal chain emits and (after debounce) populates the local state.

## Architecture Notes

- **Local frame convention**: `localRight` = horizontal unit vector, 90° CW from pipe heading when viewed from above. For vertical pipes, `Right(+X)`. This differs from PipeFrame's internal `rightRef` which gives `Left(-X)` for vertical Up due to cross-product orientation.
- **No DTO change**: Relative (side, theta) is ephemeral UI state. `RelativeDirectionInput` converts to/from absolute `AbsoluteDirection` via `relativeTarget` (forward) and `recoverRelative` (reverse).
- **Bidirectional sync**: Uses `.distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)` pattern on both directions. Initialization happens through the reverse sync binder after mount + debounce.
