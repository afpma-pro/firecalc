# Fix: Viz signal spam & wrong pipe directions on project load

## Status: Partially fixed

## Problem

Clicking "Load example project" or "New blank project" causes the 3D viz to render with wrong pipe directions. Multiple clicks needed before it stabilizes.

## Root cause analysis

### Finding 1: Massive signal re-emission (60+ times per click)

A single `engineStateVar.set(EngineState.init)` triggers 60+ re-emissions of `fluepipe_incrdescr_var.signal` and downstream signals. This is caused by **form field bidirectional write-backs**: each form field observes the new state, re-renders, and writes back, which updates the parent var and triggers all signals again.

### Finding 2: Form write-backs mutate pipe descriptor data (example project)

For the example project, the `flueEndPt` actually **changes** during the cascade:
- First emissions: `Vec3(-0.246, 0.974, 0.795)`
- Later emissions: `Vec3(-0.246, 1.789, 1.61)` (different position!)
- Then: `Vec3(-0.24600000000000044, 1.789, 1.61)` (floating-point drift)

This means form fields are writing back **modified** values (rounding, unit conversion, default insertion), which changes pipe geometry. This is the deeper bug.

### Finding 3: Blank project roundtrips cleanly

"New blank project" (`EngineState.minimal`) doesn't suffer from this — the form field write-backs produce identical values, so `.distinct` filters them out.

## Fixes applied

### 1. Debounce on `allPositionsSig` (Viz3DPanel.scala:56)

```scala
.composeChanges(_.debounce(LAMINAR_VIZ_DEBOUNCE_MS))
```

Constant: `LAMINAR_VIZ_DEBOUNCE_MS = 1000` in `constants.scala:12`.

### 2. `.distinct` on position and frame signals (Variables.scala:213-274)

Added `.distinct` to all 6 signals to suppress duplicate emissions:
- `fluepipe_finalFrame_sig`
- `connectorpipe_finalFrame_sig`
- `fluepipe_positions_sig`
- `connectorpipe_positions_sig`
- `chimneypipe_positions_sig`
- `airintake_positions_sig`

## What's still broken

The example project (`ExampleProject_15544`) still needs multiple clicks because form write-backs **actually change** the pipe data. `.distinct` correctly lets these through since the values differ.

## Remaining work

1. **Investigate form field write-back cascade**: identify which form fields write back modified values on project load. Likely candidates:
   - Direction inputs (azimuth/inclination) with rounding
   - Unit conversion in dimension fields
   - Default value insertion for empty optional fields (`LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS = 3000`)

2. **Fix the write-back loop**: ensure form fields don't write back values that differ from what they received. Options:
   - Add `.distinct` at the form field binding level
   - Fix the rounding/conversion that introduces differences
   - Break the bidirectional sync loop on project load

3. **Performance**: even after `.distinct`, the frame signal `.map` still computes 60+ times (computation runs before `.distinct` filters). Could add `.distinct` to the input `*_incrdescr_var.signal` in the frame signals to cut this waste.

## Debug logs in place

The following files have `[VIZ-DEBUG]` println statements (remove after investigation):

- `modules/ui/src/main/scala/afpma/firecalc/ui/viz/Viz3DPanel.scala` — render callback
- `modules/ui/src/main/scala/afpma/firecalc/ui/models/Variables.scala` — frame + position signals
- `modules/ui/src/main/scala/afpma/firecalc/ui/components/FireCalcProjectComponent.scala` — click handlers
- `modules/engine/src/main/scala/afpma/firecalc/engine/cas_types/en15544/ExampleProject_15544.scala` — descriptor evaluation
