# 3D Direction Vectors for Pipe Model

## Implementation Status (as of 2026-03-09)

### Done ✅
- **Phase 1**: `Vec3` + `PipeFrame` geometry types with Rodrigues rotation math, 37 tests (commit `c6205c1`)
  - All required methods: `dot`, `cross`, `normalized`, `angleTo`, `+`, `-`, `*`, `unary_-`, `fromAzimuthElevation`, `toAzimuthElevation`
  - `toDisplayString` with 3-tier logic (cardinal / cardinal+elevation / az+el fallback)
  - `lazy val` constants in companion object (`Up`, `Down`, `Rear`, `Front`, `Right`, `Left`)
  - `PipeFrame.initial`, `applyBend`, `directionAsAbsolute`, `rodriguesRotate`
- **Phase 2**: DTO changes — `roll: Option[Angle]` on all `AddDirectionChange` subtypes, `SetInitialDirection` in all 3 pipe hierarchies, i18n keys, YAML migration transformers (commit `a499677`)
- **Phase 2 UI fixes**: `SetInitialDirection` form/defaultable/PipePanel handleCase + tagTreeMenu entry (commit `daacd58`)
- **Phase 3**: Engine builder direction tracking — `initialFrame`/`currentFrame`/`dirBeforePreviousDC` in all 3 PropsState types, `SetInitialDirection` handled in `ThermalIncrementalBuilder`, `angleN2` auto-computed in both factories (13384 + 15544) (commit `09c8f88`)
- **Phase 4**: EN15544 short section tests verified — all pass unchanged (part of commit `27d5056`)
- **Phase 5**: Direction inheritance wired — builder exposes `finalFrame`, flue→connector→chimney propagation in `FireCalcYAML_Loader` (commit `27d5056`)
- **Phase 6a**: Roll preset buttons `[0°][90°][180°][270°][…]` with custom input in `ThermalHorizontalForm_13384` (commit `0a64b25`)
- **Phase 6b**: `SetInitialDirection` form, defaultable, PipePanel handleCase + tagTreeMenu — done in Phase 2 UI fixes
- **Phase 6c**: `direction: Option[Vec3]` on `Preview` case class; direction badge rendered in `PipePanel` (commit `81f72fb`)
- **Phase 7**: `tasks/guide-direction-3d-fr.md` created/updated; `tasks/prd-direction-3d.md` updated (commit `cdc6079`)
- **Gap A**: `directionBefore`/`directionAfter` added to `PipeSectionResult` trait + all impls; `Preview.direction` now populated from `psr.directionAfter`; direction badge in `PipePanel` now shows live values (commit `ba55f04`)
- **Gap B (engine helpers)**: `PipeFrame.rollAngleForOutputDirection` and `PipeFrame.reachableCardinals` added — engine is ready for context-aware preset labels

### Partial ⚠️
- **Phase 6a (roll presets)**: Buttons implemented as static `[0°][90°][180°][270°]`. Context-aware labels (e.g. "Rear/Right/Front/Left") are blocked: `DaisyUIHorizontalForm` typeclass only receives `Var[Option[QtyD[Degree]]]`, not `Signal[XtraOutputs]`. Engine helpers are ready (`PipeFrame.reachableCardinals`); only UI wiring remains.
- **Phase 3 (FlowOnly builders)**: Direction state fields added, but FlowOnly builders use V2 DTO which doesn't carry `roll`. Direction tracking is a no-op for FlowOnly pipes until a DTO version bump.

### Not implemented ❌
- **Phase 2c (RollPreset enum)**: No longer needed for current static buttons. Only needed if/when context-aware presets are wired through the form system.
- **Phase 6a (context-aware UI wiring)**: Requires threading `Signal[XtraOutputs]` through `DaisyUIHorizontalForm` — form system refactor, deferred.
- **FlowOnly direction tracking**: DTO version bump for FlowOnly 13384 and 15544 — deferred (user confirmed).

### Known deviations
- The roll convention comment in `PipeFrame.scala` says "0°=upRef" but actual math shows roll=0° bends toward `direction × upRef` (rightRef). This is correct in the code but the inline comment is misleading.
- `angleN2` formula in plan says `acos(initial_dir · current_dir)` but correct definition is `acos(dir_H1 · dir_H3)` = angle between direction before DC(n-1) and direction after DC(n). Implementation is correct; plan text was clarified during development.
- Direction inheritance is wired in `FireCalcYAML_Loader.scala` rather than `v0_2024_10.scala` as originally planned — functionally equivalent.

---

## PRD: 3D Direction Tracking

### Context

Users currently define pipe geometry as a sequence of straight sections and direction changes, but the model only tracks **how much** the pipe bends (deflection angle), not **which way**. This makes 3D visualization impossible and forces manual input of `angleN2` for EN15544 short section calculations. We add a single new `roll` field to direction changes and a `SetInitialDirection` element, enabling full 3D direction tracking through the pipe sequence.

### Problem
1. No 3D direction info — cannot render pipes in 3D
2. `angleN2` (EN15544) must be manually provided — error-prone and redundant
3. `angle_to_original_direction` field exists in DTO but is unused (marked `TOFIX`)

### Solution
Add a **roll angle** to each `AddDirectionChange`: rotation around the current pipe axis defining which way the bend goes (industry-standard LRA system). The engine tracks a local frame (direction + up reference) through the pipe, auto-computing `angleN2`.

### Key Design Decisions

| Decision | Choice |
|----------|--------|
| New field on `AddDirectionChange` | `roll: Option[Angle] = None` (single angle) |
| Roll convention | 0° = toward `dir × upRef` (rightRef) |
| Roll UX | Preset buttons (context-aware labels) + custom angle input |
| Initial direction | `SetInitialDirection(azimuth, inclination)` — new SetProp |
| Direction inheritance | Connector ← flue; chimney ← connector (or flue) |
| Internal representation | `Vec3(x, y, z)` unit vector + `Vec3` up-reference in `PipeFrame` |
| `angleN2` on DCn | Auto-computed: angle between dir BEFORE DC(n-1) and dir AFTER DCn |
| `angleN1` | Kept as-is (= deflection angle of the bend) — used for ζ computation |
| Old `angle_to_original_direction` | Removed from DTO |
| Backward compat | `roll = None` → no direction tracking (existing behavior) |
| UI direction display | 3-tier: cardinal name / cardinal+elevation / az+el fallback |

### angleN2 Definition

For a pipe sequence `H1 → DC1 → H2 → DC2 → H3`:
- `angleN1` of DC1 = deflection angle of DC1 (how much the pipe bends)
- `angleN1` of DC2 = deflection angle of DC2
- **`angleN2` of DC2 = angle between H1's direction vector and H3's direction vector**

`angleN2 = acos(dir_H1 · dir_H3)` — computed automatically from tracked direction vectors.

The engine keeps `angleN1` and `angleN2` as derived values because they feed into ζ via `ζ1_modified_calc` and `ζ2_modified_calc` (EN15544 §4.9.5).

### Coordinate System
```
Facing the stove:
  +X = right
  +Y = rear (away from you)
  +Z = up (gravity)

Roll convention: 0° = toward (dir × upRef) perpendicular, which gives:
  Vertical pipe up (dir=+Z, upRef=Rear=+Y):
    roll=0°   → bendAxis=Rear(+Y) → rotate +Z around +Y → Right (+X)
    roll=90°  → bendAxis=Left(-X)  → rotate +Z around -X → Rear  (+Y)
    roll=180° → bendAxis=Front(-Y) → rotate +Z around -Y → Left  (-X)
    roll=270° → bendAxis=Right(+X) → rotate +Z around +X → Front (-Y)

  Horizontal pipe Rear (dir=+Y, upRef=Up=+Z):
    roll=0°   → bendAxis=Up(+Z)    → rotate +Y around +Z → Left  (-X)
    roll=90°  → bendAxis=Right(+X) → rotate +Y around +X → Up    (+Z)
    roll=180° → bendAxis=Down(-Z)  → rotate +Y around -Z → Right (+X)
    roll=270° → bendAxis=Left(-X)  → rotate +Y around -X → Down  (-Z)

  Preset → roll angle mapping:
    Vertical pipe:    Right=0°, Rear=90°, Left=180°, Front=270°
    Horizontal Rear:  Left=0°,  Up=90°,   Right=180°, Down=270°

Absolute direction display (UI):
  azimuth 0°, elevation 0°  = rear (horizontal, toward back of stove)
  azimuth 0°, elevation 90° = up (vertical)
  azimuth 90°, elevation 0° = right
```

> **Note**: The UI presets are named by their resulting absolute direction, not by roll angle. Context-aware labels require `PipeFrame.rollAngleForOutputDirection` (not yet implemented).

### Out of Scope
- 3D rendering / visualization (future, but this enables it)
- Position tracking (cumulative XYZ coordinates) — future

### FlowOnly Pipes Limitation

Direction tracking is currently only active for **Thermal 13384** pipes. FlowOnly pipes have `roll` in their DTOs but the V2 DTO format does not carry `roll` through the builder's element factory. Direction tracking will activate for FlowOnly pipes when a DTO version bump wires `roll` through the respective element factories.

---

## Implementation Plan

### Phase 1: Vec3 + PipeFrame + Rotation Math (engine) ✅

**Create** `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/Vec3.scala`
- `case class Vec3(x: Double, y: Double, z: Double)` with `dot`, `cross`, `normalized`, `angleTo`, `+`, `-`, `*`, `unary_-`
- Constants (`lazy val`): `Up`, `Down`, `Rear`, `Front`, `Right`, `Left`
- `Vec3.fromAzimuthElevation(azimuth, elevation)` — azimuth 0°=rear(+Y), elevation 0°=horizontal, 90°=up(+Z)
- `toAzimuthElevation: (Double, Double)` — inverse
- `toDisplayString: String` — 3-tier: cardinal / cardinal+elevation / az+el fallback

**Create** `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/PipeFrame.scala`
- `case class PipeFrame(direction: Vec3, upRef: Vec3)`
- `PipeFrame.initial(direction: Vec3): PipeFrame` — Gram-Schmidt upRef from gravity
- `def applyBend(deflectionDeg: Double, rollDeg: Double): PipeFrame` — Rodrigues rotation
- `def directionAsAbsolute: (Double, Double)` — (azimuth, elevation) in degrees
- `PipeFrame.rodriguesRotate(v, k, theta): Vec3`

**Create** `PipeFrameSuite` — 37 tests, all passing

### Phase 2: DTO Changes ✅

- `roll: Option[Angle] = None` on all `AddDirectionChange` subtypes (3 files)
- `angle_to_original_direction` removed
- `SetInitialDirection(azimuth, inclination)` in all 3 SetProp hierarchies
- i18n: `terms.roll`, `terms.azimuth`, `terms.inclination`, `set_prop.SetInitialDirection`
- YAML migration transformers updated
- UI: form, defaultable, PipePanel handleCase + tagTreeMenu

### Phase 3: Engine Builder Integration ✅

- `initialFrame`, `currentFrame`, `dirBeforePreviousDC` in all 3 PropsState types
- `SetInitialDirection` handled in `ThermalIncrementalBuilder_13384`
- `dirBeforePreviousDC` saved before each bend; `currentFrame` updated after
- `angleN2` auto-computed in `ElementFactory_13384_Instances` and `ElementFactory_15544_Instances`
- ⚠️ FlowOnly 13384/15544 direction tracking is no-op until DTO V2→next version bump

### Phase 4: EN15544 Short Section ✅

`shortsection.scala` — no structural changes. All tests pass.

### Phase 5: Direction Inheritance Between Pipes ✅

- `IncrementalBuilderAlg` extended: `toFullDescrWithFinalFrame()`, `toFullDescrWithExternalInitialFrame(frame)`, `currentFrameFromPropsState`, `applyExternalFrame` hooks
- All 3 concrete builders override the hooks
- `FireCalcYAML_Loader` threads flue → connector → chimney final frames

### Phase 6: UI — Forms + Roll Presets + Direction Display

**6a. Roll presets** ⚠️ (partial)
- ✅ Static `[0°][90°][180°][270°][…]` buttons implemented in `ThermalHorizontalForm_13384`
- ❌ Context-aware labels (Rear/Right/Front/Left vs Up/Right/Down/Left) — requires `PipeFrame.rollAngleForOutputDirection` helper + reactive frame signal in form

**6b. SetInitialDirection form** ✅
- Form, defaultable, PipePanel handleCase, tagTreeMenu entry

**6c. Direction badge** ⚠️ (partial)
- ✅ `direction: Option[Vec3]` field on `Preview`; badge rendered in `PipePanel` when `Some`
- ❌ `Preview.direction` not yet populated (always `None`) — need to thread `PipeFrame` from `PipeSectionResult` into `Preview`

### Phase 7: Documentation ✅

- `tasks/guide-direction-3d-fr.md` — French user guide created
- `tasks/prd-direction-3d.md` — synced with this plan
- `plans/direction-3d.md` — this file

---

## Remaining Work

### Medium priority
1. **Context-aware roll preset labels**: Requires threading `Signal[XtraOutputs]` through the `DaisyUIHorizontalForm` typeclass or adding per-element custom rendering in `PipePanel_13384_Thermal`. Engine helpers (`PipeFrame.reachableCardinals`, `rollAngleForOutputDirection`) are ready.

### Low priority / future
2. **FlowOnly direction tracking**: DTO version bump for FlowOnly 13384 and 15544 to carry `roll` through their element factories (user confirmed: after Thermal 13384 is fully validated).
3. **3D visualization**: Use tracked `Vec3` directions for rendering.
4. **Position tracking**: Cumulative XYZ coordinates of pipe path.

---

## Verification

1. **Unit tests** (Phase 1): `PipeFrameSuite` — 37 tests passing ✅
2. **Compile** (all phases): `compile-full` — clean ✅
3. **Engine tests** (Phase 3-4): existing EN13384 and EN15544 suites pass ✅
4. **Backward compat**: existing saved projects load without change ✅
5. **UI direction badge**: browser test pending (Preview.direction wiring needed)
6. **Roll presets**: context-aware labels pending
