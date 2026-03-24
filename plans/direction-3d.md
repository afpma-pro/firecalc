# 3D Direction Vectors for Pipe Model

## Implementation Status (as of 2026-03-09)

### Done ✅
- **Phase 1**: `Vec3` + `PipeFrame` geometry types with Rodrigues rotation math, 38 tests (commit `c6205c1`)
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
- **Gap B (engine helpers)**: `PipeFrame.rollAngleForOutputDirection` and `PipeFrame.reachableCardinals` added (commit `e0db61a`)
- **Phase 6a (context-aware roll presets)**: Full implementation:
  - `frameBefore: Option[PipeFrame]` threaded through `NamedPipeElDescrG` → `PipeSectionResult` → UI
  - `RollAngleInput` component (`modules/ui/.../daisyui/RollAngleInput.scala`) renders preset buttons with cardinal labels from `frame.reachableCardinals`
  - Roll field suppressed in magnolia-derived DC forms via `autoDeriveAndOverwriteFieldNames_DC_Subtype`; rendered via `extra` callback in `PipePanel_13384_Thermal`
  - UI-side frame computation (`frameBeforeByIdx` in `PipePanel_13384_Thermal`) scans element list to derive `PipeFrame` per element, independent of engine success — cardinal labels show even when pipe has validation errors (e.g. DC as last element)
  - Falls back to static `[0°][90°][180°][270°]` when no `SetInitialDirection` present

### Partial ⚠️
- **Phase 3 (FlowOnly builders)**: Direction state fields added, but FlowOnly builders use V2 DTO which doesn't carry `roll`. Direction tracking is a no-op for FlowOnly pipes until a DTO version bump.

### Not implemented ❌
- **Phase 2c (RollPreset enum)**: No longer needed — context-aware presets use `PipeFrame.reachableCardinals` directly.
- **FlowOnly direction tracking**: DTO version bump for FlowOnly 13384 and 15544 — deferred (user confirmed).

### Known deviations
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
| Roll convention | Right-hand rule: positive = CCW looking into pipe; 0°=Rear for vertical up, 0°=Up for horizontal rear |
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

Roll convention (right-hand rule around pipe axis):
  Positive roll = counterclockwise when looking INTO the pipe from outside (direction of flow).
  Equivalently: right thumb along flow direction → fingers point in direction of increasing roll.

  Vertical pipe up (dir=+Z, upRef=Rear=+Y):
    rightRef = +Z×+Y = -X = Left
    roll=0°   → bendAxis=Left(-X)  → rotate +Z around -X → Rear  (+Y)
    roll=90°  → bendAxis=Front(-Y) → rotate +Z around -Y → Left  (-X)
    roll=180° → bendAxis=Right(+X) → rotate +Z around +X → Front (-Y)
    roll=270° → bendAxis=Rear(+Y)  → rotate +Z around +Y → Right (+X)

  Horizontal pipe Rear (dir=+Y, upRef=Up=+Z):
    rightRef = +Y×+Z = +X = Right
    roll=0°   → bendAxis=Right(+X) → rotate +Y around +X → Up    (+Z)
    roll=90°  → bendAxis=Down(-Z)  → rotate +Y around -Z → Right (+X)
    roll=180° → bendAxis=Left(-X)  → rotate +Y around -X → Down  (-Z)
    roll=270° → bendAxis=Up(+Z)    → rotate +Y around +Z → Left  (-X)

  Preset → roll angle mapping:
    Vertical pipe:    Rear=0°, Left=90°, Front=180°, Right=270°
    Horizontal Rear:  Up=0°,   Right=90°, Down=180°, Left=270°

Absolute direction display (UI):
  azimuth 0°, elevation 0°  = rear (horizontal, toward back of stove)
  azimuth 0°, elevation 90° = up (vertical)
  azimuth 90°, elevation 0° = right
```

> **Note**: The UI presets are named by their resulting absolute direction, not by roll angle. Context-aware labels use `PipeFrame.reachableCardinals` (which calls `rollAngleForOutputDirection` internally).

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

**Create** `PipeFrameSuite` — 38 tests, all passing

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

### Phase 6: UI — Forms + Roll Presets + Direction Display ✅

**6a. Roll presets** ✅
- `RollAngleInput` component with context-aware cardinal labels from `PipeFrame.reachableCardinals`
- Roll field suppressed in magnolia-derived DC forms; rendered via `extra` callback on `renderElemTyped`
- UI-side frame tracking (`frameBeforeByIdx`) ensures labels work even when engine has validation errors
- Falls back to static `[0°][90°][180°][270°]` when no `SetInitialDirection`

**6b. SetInitialDirection form** ✅
- Form, defaultable, PipePanel handleCase, tagTreeMenu entry

**6c. Direction badge** ✅
- `direction: Option[Vec3]` field on `Preview`; badge rendered in `PipePanel`
- `directionBefore`/`directionAfter` on `PipeSectionResult`; `Preview.direction` populated from `psr.directionAfter`
- `frameBefore: Option[PipeFrame]` on `NamedPipeElDescrG` and `PipeSectionResult` for roll label computation

### Phase 7: Documentation ✅

- `tasks/guide-direction-3d-fr.md` — French user guide created
- `tasks/prd-direction-3d.md` — synced with this plan
- `plans/direction-3d.md` — this file

---

## Remaining Work

### Low priority / future
1. **FlowOnly direction tracking**: DTO version bump for FlowOnly 13384 and 15544 to carry `roll` through their element factories (user confirmed: after Thermal 13384 is fully validated).
2. **3D visualization**: Use tracked `Vec3` directions for rendering.
3. **Position tracking**: Cumulative XYZ coordinates of pipe path.

---

## Verification

1. **Unit tests** (Phase 1): `PipeFrameSuite` — 38 tests passing ✅
2. **Compile** (all phases): `compile-full` — clean ✅
3. **Engine tests** (Phase 3-4): 106 engine tests passing ✅
4. **Backward compat**: existing saved projects load without change ✅
5. **UI direction badge**: live direction display working ✅
6. **Roll presets**: context-aware cardinal labels verified in browser ✅
   - Works with following element (engine results available)
   - Works without following element (UI-side frame computation fallback)
   - Falls back to static angles when no `SetInitialDirection`
