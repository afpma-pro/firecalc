# Viz Click → UI Focus — Implementation Progress

Branch: `feat-viz-ui-sync`
Date: 2026-03-18

---

## Summary

Feature adds bidirectional visual linking between the 3D viz panel and UI form fieldsets:
- **Hover** a pipe in the viz → fieldset border + legend turn brown (`#3B2416`)
- **Click** a pipe → panel opens and scrolls to the fieldset; click again to deselect
- **Click empty space** → deselects
- **15s idle** → auto-deselect
- Panel open/close states and camera state now persist across page reloads via `UIState` in localStorage

MVP scope: **flue pipes only** (stubs in place for Connector, Chimney, AirIntake).

---

## Commits (7 feature commits, all on branch)

| Hash | Description |
|------|-------------|
| `2ba2715` | feat(viz): add onPipeHover callback and empty-space click deselect to filaire-viz.ts |
| `2524101` | feat(viz): plumb onPipeHover and onShapeHover callbacks through Scala facade |
| `925cc5f` | feat(ui): add VizElementId, UIState, uiStateVar, panelOpenedVar, and UI_STATE key |
| `954772f` | feat(ui): add .viz-highlighted CSS class for viz-to-fieldset hover/click feedback |
| `fe50312` | feat(ui): add viz-fieldset DOM IDs, highlight signals, scroll-on-select, and persistent panel state |
| `351ed19` | feat(ui): wire viz click/hover callbacks, auto-deselect timer, and migrate camera state to uiStateVar |
| `7d4f992` | chore(ui): add TODO comments for future VizElementId extension (Connector, Chimney, AirIntake) |

---

## What Is Left (Compilation Fix — Not Yet Committed)

Two files have **uncommitted changes** with a **compile error** blocking the build.

### Root cause

`FilaireLinesViz.render` originally had:
```scala
onShapeClick: Option[FireCalcFilaireLine => Unit]   // line directly — no None for empty-space
onShapeHover: Option[Option[FireCalcFilaireLine] => Unit]
```

The Viz3DPanel wiring (commit `351ed19`) wrote an `onShapeClick` callback matching on
`Option[FireCalcFilaireLine]`, but the type was still the non-Option variant →
Scala 3 overload resolution failed → "Missing parameter type" / "Some[Any]" errors.

### Fixes already applied (not yet committed)

**`modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireLinesViz.scala`**
- Changed `onShapeClick` type in both `render` overloads to
  `Option[Option[FireCalcFilaireLine] => Unit]` (mirrors `onShapeHover`)
- Updated internal `clickCallback` to call `cb(None)` when `idx < 0`
  (empty-space click now propagates deselect through the callback)

**`modules/ui/src/main/scala/afpma/firecalc/ui/viz/Viz3DPanel.scala`**
- Updated `onShapeClick` lambda to pattern match on `Option[FireCalcFilaireLine]`
  (`case Some(line) => ...`, `case None => vizSelectedElement.set(None)`)
- Added explicit type annotation on `onShapeHover` lambda parameter:
  `(lineOpt: Option[FireCalcFilaireLine]) => ...`

### Compile errors — RESOLVED

**Commit `14d2c2e`** — fix(viz): fix onShapeClick/Hover type and import:
- Added `import afpma.firecalc.filaire.FilaireTypes.*` in `Viz3DPanel.scala`
- Ascribed `Some[Option[FireCalcFilaireLine] => Unit]` on both callback args (Scala 3 overload resolution)
- Changed `onShapeClick` type to `Option[Option[FireCalcFilaireLine] => Unit]` in both `FilaireLinesViz.render` overloads
- `clickCallback` now calls `cb(None)` for empty-space clicks (idx < 0)

### Persistence bug — RESOLVED

**Commit `aea2611`** — fix(ui): persist UIState to localStorage and save camera on beforeunload:
- Added `writeUIStateSubscription` in `Frontend.scala` (same debounced sync pattern as `appStateSchemaVar` and `catalogStateVar`)
- Added `beforeunload` event handler in `Viz3DPanel` that flushes camera state to localStorage immediately before page reload

---

## Manual Verification Checklist — ALL PASSED

- [x] **Hover flue pipe** → fieldset border and legend text turn brown (`#3B2416`)
- [x] **Hover leaves** → brown highlight removed
- [x] **Click flue pipe** → flue panel opens; page scrolls smoothly to the fieldset; highlight stays
- [x] **Click same pipe again** → deselects; highlight removed
- [x] **Click empty space** in the 3D canvas → deselects
- [x] **Wait 15 seconds** after selecting a pipe → auto-deselects
- [x] **Non-flue pipes** (connector, chimney) → clicking does nothing (no highlight, no scroll)
- [x] **Reload page** → panel open/close states are restored (flue panel stays open if it was open)
- [x] **Reload page** → camera position/rotation is restored
- [x] **Old `viz_camera_state` key** is absent from localStorage after first load (migrated to `ui_state`)
- [x] **No console errors** during normal use

---

## Files Modified (full list)

| File | Change |
|------|--------|
| `modules/viz/src/ts/filaire-viz.ts` | `onPipeHover` param, empty-space `onPipeClick(-1)`, label pointerenter/leave |
| `modules/viz/src/main/scala/.../ThreeVizFacade.scala` | `onPipeHover` facade param |
| `modules/viz/src/main/scala/.../FilaireLinesViz.scala` | `onShapeHover` + `onShapeClick` type change + click/hover callback builders |
| `modules/ui/src/main/scala/.../models/UIState.scala` | **New**: `CameraState` + `UIState` case classes with circe codecs |
| `modules/ui/src/main/scala/.../models/Variables.scala` | `VizElementId`, `vizHoveredElement`, `vizSelectedElement`, `uiStateVar`, `panelOpenedVar` |
| `modules/ui/src/main/scala/.../schema/LocalStorageKeys.scala` | `UI_STATE` key |
| `modules/ui/main.css` | `.viz-highlighted` CSS class |
| `modules/ui/src/main/scala/.../panels/PipePanel.scala` | DOM IDs, `vizHighlightSignal`, scroll binder, abstract viz members, persistent `panelOpened` |
| `modules/ui/src/main/scala/.../panels/FluePipePanel.scala` | Override `vizFieldsetIdPrefix`, `ownsVizElement`, `vizElementIndex` |
| `modules/ui/src/main/scala/.../panels/ConnectingPipePanel.scala` | Stub overrides (TODO) |
| `modules/ui/src/main/scala/.../panels/ChimneyPipePanel.scala` | Stub overrides (TODO) |
| `modules/ui/src/main/scala/.../panels/FlowOnlyAirIntakePipePanel.scala` | Stub overrides (TODO) |
| `modules/ui/src/main/scala/.../daisyui/DaisyUIVerticalAccordionAndJoin.scala` | `panelOpenedVar` for all 4 non-pipe accordion panels |
| `modules/ui/src/main/scala/.../viz/Viz3DPanel.scala` | Wire callbacks, 15s auto-deselect, camera → `uiStateVar` |
