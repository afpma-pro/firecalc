# Subagent Task: Fix Failing Tests After angleN2 → roll Migration

## Context

This is a Scala 3 / sbt project (`firecalc`). The engine module has just
undergone a refactoring that:

1. Made `roll: Angle` **mandatory** on all direction change DSL methods (was `Option[Angle]`).
2. Removed the `legacyAngleN2` fallback in `ElementFactory_15544_Instances.scala` — the DTO roll field is no longer re-interpreted as `angleN2`.
3. `angleN2` in `FlowOnlyPipeDescr_15544.DirectionChange` is now **exclusively computed** from direction tracking (via `PipeFrame.applyBend` in `updateStateAfterConversionStep`).
4. The user provided proper `SetInitialDirection` + physical `roll` values for all hardcoded pipe definitions (commit `2594e9f`).

### Result: 8 tests fail

Run `sbt --client engine/test` to see current state. Expected: 115 pass, 8 fail.

```
afpma.firecalc.engine.cas_types.en15544.cas_types_15544_C3_Suite        1 failure
afpma.firecalc.engine.impl.en15544.Pipes_15544_IncrementalBuilder        2 failures
afpma.firecalc.engine.ops.en15544.DynamicFrictionCoeffOp_EN15544_Suite   5 failures
```

---

## Failure Group C (easy, fix first): `cas_types_15544_C3_Suite`

**File:** `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en15544/strict/EcolabeledToFireboxInternalPipes_15544_Strict.scala`

**Error:** `java.lang.IllegalStateException: dev error: input air geometry should be defined for V2 eco-labeled fireboxs`

**Root cause:** After the user's refactoring, there is a `val air_intake_equivalent_shape = ...` that calls
`arriveeAirGeometryOpt.getOrElse(throw ...)`. This val is computed **eagerly** in the outer `extension` body,
so it executes for **all** firebox versions — including V1, where `arriveeAirGeometryOpt` is `None`.
The val is only used in `start_01_version_2`. It must be moved inside that `Seq(...)` so it is only
evaluated for V2.

**How to find it:** Read the file and search for `air_intake_equivalent_shape`. It will look like:

```scala
val air_intake_equivalent_shape =
    circle(
        arriveeAirGeometryOpt.map(_.perimeterWetted)
            .getOrElse(throw new IllegalStateException("..."))
    )

val start_01_version_2 = Seq(
    innerShape(air_intake_equivalent_shape),
    ...
)
```

**Fix:** Inline the expression into `start_01_version_2` directly (no separate `val`):

```scala
val start_01_version_2 = Seq(
    innerShape(circle(
        arriveeAirGeometryOpt.map(_.perimeterWetted)
            .getOrElse(throw new IllegalStateException("..."))
    )),
    ...
)
```

After this fix, `cas_types_15544_C3_Suite` should pass.

---

## Failure Group B (medium): `Pipes_15544_IncrementalBuilder`

**File:** `modules/engine/src/test/scala/afpma/firecalc/engine/impl/en15544/incremental.scala`

### B1 — "case 2" (line 124): floating-point near-zero elevation_gain

**Test:** after one 45° horizontal bend (`roll = 90.degrees`), the next `StraightSection` has
`elevation_gain = 4.329780281177466E-17` (≈ 0) but the test expects exactly `0.0.meters`.

This float artifact is inherent to trigonometry in direction tracking (`cos(90°)` computed via
`math.cos(math.Pi / 2)` ≈ 6.12e-17 in IEEE 754). The section is physically horizontal.

**Fix options (pick one):**
- A) Change the test assertion to use a tolerance: `shouldEqual(0.meters +- 1e-10.meters)`.
  BUT the test uses structural equality on the full `PipeFullDescr` — so you'd need to either
  assert field-by-field, or…
- B) Add a snap-to-zero threshold in the elevation_gain computation: if `|elevation_gain| < 1e-9`
  then clamp to 0. Find where `elevation_gain` is computed from the direction vector's z-component
  (search for `elevation_gain` or `finalElevGain` in the engine). This is cleaner.
- C) Update the test to use `shouldEqual` only on the fields that matter (exempt `elevation_gain`
  from the structural equality check). This is acceptable since the test already has separate
  assertions for `isShort` etc.

**Recommendation: Option C** — change the `vRepr.shouldBe(Valid(expected))` on line 124 to
assert the key facts separately: length, geometry, roughness. Then assert `elevation_gain` with
tolerance. This avoids touching engine math.

### B2 — "case 3" (line ~200): second bend now has `angleN2 = Some(45.0)` instead of `None`

**Test:** Two successive 45° leftward bends (`roll = -90.degrees`). The test expects the second
`DirectionChange` to have `angleN2 = None`, but direction tracking now computes `angleN2 = Some(45.0)` for it.

**Actual output (from test failure):**
```
AngleVifDe0A180(45.0, Some(45.0))   ← actual (second bend, index 3)
AngleVifDe0A180(45.0, None)         ← expected
```

**Root cause:** Direction tracking is now active (via `SetInitialDirection` added in commit `2594e9f`),
so `computedAngleN2` is filled using `dirBeforePreviousDC.angleTo(currentFrame.direction)` in
`ElementFactory_15544_Instances`. This is intentional — the test expectation is now stale.

**Geometry dispute — needs investigation:**

The user says `angleN2` should be `Some(90.degrees)`, not `Some(45.degrees)`.

Two successive 45° leftward bends in the same plane: the pipe turns left 45°, then left another 45°,
for a total deflection of 90° from the original direction. The question is which angle `angleN2` captures:

- The angle between `dirBefore(DC_n-1)` and `dirAfter(DC_n)` per the factory formula
  (`dirBeforePreviousDC.angleTo(currentFrame.direction)`)
- `dirBeforePreviousDC` = direction before the **first** bend (i.e. straight ahead, 0°)
- `currentFrame.direction` after the **second** bend = 90° total deflection from origin

So the geometrically correct value is **90°**, not 45°. The engine currently returns 45° — this is a bug
in `computedAngleN2`.

**Investigation needed:**

1. Read `ElementFactory_15544_Instances.scala` to understand exactly which frame state is used
   for `dirBeforePreviousDC` and `currentFrame` when the second bend is processed.
2. Trace through `updateStateAfterConversionStep` to verify that `dirBeforePreviousDC` is set to
   the direction **before the first bend** (not the direction between the two bends).
3. Verify whether the issue is in the factory reading stale state, or in `updateStateAfterConversionStep`
   updating `dirBeforePreviousDC` too early / at the wrong moment.
4. Fix the computation so `angleN2 = Some(90.degrees)` for this case.
5. Update the test expected value to `AngleVifDe0A180(45.degrees, angleN2 = Some(90.degrees))`.

**Also fix float noise** in `straight-1-short` (index 2) and `straight-2` (index 4):
- Actual: `elevation_gain = 4.3e-18` and `3.06e-17` — both effectively 0 for horizontal sections.
- Use tolerance assertions (`+- 1e-10`) rather than exact equality, same pattern as B1 fix.

---

## Failure Group A (hard): `DynamicFrictionCoeffOp_EN15544_Suite` (5 failures)

**File:** `modules/engine/src/test/scala/afpma/firecalc/engine/ops/en15544/FlowOnlyDynamicFrictionCoeff_15544.scala`

These tests verify EN15544 §4.9.5 short-section dynamic friction reduction factors (ζ).

### What the tests check

Short sections between two direction changes have a reduced ζ coefficient depending on the
geometric relationship between the two bends (alternating vs. successive). The `angleN2` field
on a `DirectionChange` encodes this relationship.

The 5 failing tests and their expectations:

| Test name | Pipe | Expected ζ | Actual ζ | Note |
|-----------|------|-----------|---------|------|
| "zeta = (0.44, 0.44)" | C2 alternating 90° | 0.44 | 0.82 | Tests virages 4-5 and 5-6 |
| "cut dynamic friction coeff by 2" | dh/2, alternating 90° | 0.60 (both) | 0.90 (v1), 0.90 (v2) | |
| "cut dynamic friction coeff by 4" | dh/4, alternating 90° | 0.30 | 0.75 | |
| "have dynamic friction coeff of 0.5" | dh/2, successive 45° | 0.50 | 0.30 | |
| "have dynamic friction coeff of 0.35" | dh/2, successive 30° | 0.35 | 0.125 | |

### How angleN2 is computed

`angleN2` on bend N = the angle between the **direction of approach before bend N-1** and the
**direction of travel after bend N**. This is computed in `ElementFactory_15544_Instances` as:

```scala
val computedAngleN2: Option[Angle] =
    ctx.dirBeforePreviousDC.map { dirBefore =>
        val angleRad = dirBefore.angleTo(ctx.currentFrame.get.direction)
        angleRad.toDegrees.degrees
    }
```

Where `ctx.dirBeforePreviousDC` = direction **before** the previous bend, and
`ctx.currentFrame.direction` = direction **after** the current bend (already updated by
`updateStateAfterConversionStep`).

### The formula

Read `modules/engine/src/main/scala/afpma/firecalc/engine/models/en15544/shortsection/ShortSection.scala`
(or similar file — search for `MissingAlpha3Angle`, `alpha3`, `ζ1`, `ζ2`) to understand how
`angleN2` maps to the ζ reduction factor.

The standard EN15544 §4.9.5 defines:
- `alpha3 = angleN2`
- For `alpha3 = 0°` (successive same-direction): maximum reduction
- For `alpha3 = 180°` (alternating opposite): minimum effect

### Investigation strategy

1. **Understand the formula**: read `shortsection.scala` to see the exact `ζ(alpha3, l/dh)` function.
2. **Run the failing tests verbosely**: `sbt --client 'engine/testOnly afpma.firecalc.engine.ops.en15544.DynamicFrictionCoeffOp_EN15544_Suite'` and observe the debug `println` lines that show element-by-element pipe output including `angleN2` values.
3. **Map expected ζ → required angleN2**: from the formula, compute what `angleN2` value would produce the expected ζ for each test set (l/dh ratio and bend angle are known).
4. **Map required angleN2 → required roll**: given horizontal pipes with `azimuth = 0.degrees, inclination = 0.degrees`, determine what `roll` angle at each bend produces the required `angleN2` via `PipeFrame.applyBend`.

### Key file to read first

`modules/engine/src/main/scala/afpma/firecalc/engine/models/en15544/shortsection/`
(look for short section formula, alpha3, ζ calculation)

`modules/engine/src/main/scala/afpma/firecalc/engine/geometry/PipeFrame.scala`
(or wherever `PipeFrame.applyBend` is defined — check `modules/engine` for `applyBend`)

### Possible outcomes

**Option A — Fix roll angles in the tests** so that direction tracking produces the old `angleN2` values.
This is correct if the expected ζ values are physically right per the standard.

**Option B — Accept new ζ values** if direction tracking computes physically correct `angleN2` values
and the old test expectations were wrong. In that case, update the expected ζ in the tests.
Leave a comment explaining the physical meaning of each roll configuration.

**Option C — Hybrid**: some tests were testing configurations where the expected ζ came from
a specific previous computation that happens to still be correct. Fix the roll angles to recover
the expected ζ. Other tests may need new expectations.

---

## Compilation strategy

Use the firecalc-metals MCP tools for compilation:
```
compile-module(module = "engine")   # fast incremental
compile-full()                      # full check
```

Fallback if MCP fails:
```bash
sbt --client 'engine/compile'
sbt --client 'engine/test'
```

To run a single test class:
```bash
sbt --client 'engine/testOnly afpma.firecalc.engine.cas_types.en15544.cas_types_15544_C3_Suite'
sbt --client 'engine/testOnly afpma.firecalc.engine.impl.en15544.Pipes_15544_IncrementalBuilder'
sbt --client 'engine/testOnly afpma.firecalc.engine.ops.en15544.DynamicFrictionCoeffOp_EN15544_Suite'
```

## Success criteria

All 123 engine tests pass: `sbt --client 'engine/test'` → `Tests: succeeded 123, failed 0`.

## Do NOT

- Reintroduce `legacyAngleN2` fallback in the factory.
- Make `roll` optional again anywhere in the DSL chain.
- Change the physical `roll` angles in `cas_types` files — those were set by the user based on real geometry.
- Add `SetInitialDirection` to `DynamicFrictionCoeff` tests differently from what is already there.
