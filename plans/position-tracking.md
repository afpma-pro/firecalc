# Position Tracking for FireCalc Pipes

## Implementation Status (as of 2026-03-10)

### Done ✅
- **Phase 1**: `PipeSegmentPosition` + `PipePositionResult` data types; `PositionTracker` pure computation object with 3 overloaded `compute` methods; unit tests `PositionTrackerSuite`
- **Phase 2**: 4 reactive `Signal[PipePositionResult]` values added to `Variables.scala` (air intake, flue, connector, chimney), with chained start points (connector starts at flue's finalPoint, chimney at connector's)
- **Phase 3**: This document

### Not implemented ❌
- **3D visualization**: Consuming `PipePositionResult` to render pipes in a 3D viewport
- **2D projection for reports**: Projecting segments onto XZ/YZ planes for schematic PDFs
- **Chimney height validation**: Using `chimneypipe_positions_sig.finalPoint.z` for termination checks
- **Per-element position badges**: Showing XYZ coordinates in the UI next to each pipe element

---

## PRD: Pipe Position Tracking

### Context

FireCalc models masonry heater pipes as ordered sequences of incremental descriptors: property setters (shape, material, roughness…), straight sections (vertical, horizontal, slopped), and direction changes (bends with deflection + roll). The "3D Direction Tracking" feature (see `plans/direction-3d.md`) added `PipeFrame`-based direction tracking — each element knows its flow direction.

However, the system does not track **spatial position** — the actual XYZ coordinates of where each pipe segment starts and ends. This is a prerequisite for 3D visualization, collision detection, chimney height validation, and PDF 2D projections.

### Problem
1. No positional information — direction is tracked but not absolute XYZ coordinates.
2. Pipes are chained (flue end → connector start → chimney start), but only direction is inherited — not position.
3. Future features (3D viewport, reports, height checks) require knowing where each segment sits in space.

### Solution
Add `PipeSegmentPosition` and `PipePositionResult` data types and a pure `PositionTracker` computation object that, given a pipe's incremental descriptors plus an initial frame and start point, produces a `Seq[PipeSegmentPosition]`. Wire this into the UI as reactive `Signal` values in `Variables.scala`, following the same pattern as `frameBeforeByIdx` and `fluepipe_finalFrame_sig`.

### Key Design Decisions

| Decision | Choice |
|----------|--------|
| Computation location | Pure function in `engine/models/geometry/PositionTracker.scala` (cross-compiled, no UI deps) |
| UI integration | `lazy val Signal[PipePositionResult]` in Variables.scala — same pattern as `frameBeforeByIdx` |
| Engine layer | NOT added to `PipeSectionResult`/`PipeResult` — UI-only for now |
| Position inheritance | Connector starts at flue's `finalPoint`; chimney at connector's `finalPoint` |
| Air intake, flue start | `Vec3(0, 0, 0)` (firebox outlet not modeled spatially) |
| Vec3 for positions | Reuse `Vec3` (no new `Point3` type) |
| Inner shape tracking | Carry forward `Option[PipeShape]` across sections, updated by `SetInnerShape` |

### Coordinate System
```
Facing the stove:
  +X = right
  +Y = rear (away from you)
  +Z = up (gravity)
```

### Scope

**In scope**: Air intake (FlowOnly 13384), flue pipe (FlowOnly 15544), connector pipe (Thermal 13384), chimney pipe (Thermal 13384).

**Out of scope**: Firebox (combustion air pipe, firebox pipe).

---

## Displacement Computation

For each section type, given the current `PipeFrame`:

| Section type | Displacement vector | Notes |
|---|---|---|
| `AddSectionVertical(elevation_gain)` | `Vec3(0, 0, eg)` | Always vertical; absolute, ignores frame direction |
| `AddSectionHorizontal(horizontal_length)` | `d_horiz * hl` | Horizontal projection of frame.direction |
| `AddSectionSlopped(length, elevation_gain)` | `d_horiz * sqrt(l²-eg²) + Vec3(0,0,eg)` | Horizontal + vertical components |
| Direction changes | Zero displacement | Frame updated via `applyBend(angle, roll)` |
| Property setters, flow resistance, etc. | Zero displacement, no segment | — |

**Horizontal direction helper**:
```scala
def horizontalDirection(frame: Option[PipeFrame]): Vec3 =
  frame match
    case Some(f) =>
      val d = f.direction
      val horiz = Vec3(d.x, d.y, 0.0)
      if horiz.norm < 1e-9 then Vec3.Rear  // vertical pipe fallback
      else horiz.normalized
    case None => Vec3.Rear  // no frame fallback
```

**Edge cases**:
- `|elevation_gain| > |length|` in slopped section → clamp horizontal distance to 0
- `roll = None` on direction change → frame NOT updated (same as `frameBeforeByIdx`)
- No `SetInitialDirection` + no external frame → `frame = None`, fallback directions used

---

## Pipe Chaining

| Pipe | Start point | Frame seed |
|---|---|---|
| Air intake | `Vec3(0,0,0)` | None (or `SetInitialDirection`) |
| Flue pipe | `Vec3(0,0,0)` | None (or `SetInitialDirection`) |
| Connector | Flue pipe's `finalPoint` | Flue pipe's `finalFrame` |
| Chimney | Connector's `finalPoint` | Connector's `finalFrame` |

---

## Implementation

### Phase 1: Data types + pure computation (engine module)

**Create** `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/PipeSegmentPosition.scala`
- `PipeSegmentPosition` case class (elementIndex, startPoint, endPoint, direction, length, innerShape, frame)
- `PipePositionResult` case class (segments, finalPoint, finalFrame)

**Create** `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/PositionTracker.scala`
- `object PositionTracker` with 3 overloaded compute methods:
  - `computeFlowOnly13384(elems: Seq[FlowOnlyPipeDescr_13384], externalFrame: Option[PipeFrame], startPoint: Vec3)`
  - `computeFlowOnly15544(elems: Seq[FlowOnlyPipeDescr_15544], externalFrame: Option[PipeFrame], startPoint: Vec3)`
  - `computeThermal13384(elems: Seq[ThermalPipeDescr_13384], externalFrame: Option[PipeFrame], startPoint: Vec3)`
- Single left-to-right scan per method (same pattern as `frameBeforeByIdx`)
- Shared private `horizontalDirection(frame: Option[PipeFrame]): Vec3` helper
- Thermal 13384: handles `SetPropertiesInBatch` (extract `SetInnerShape` from nested props) and `LinedFlue`

**Create** `modules/engine/src/test/scala/afpma/firecalc/engine/models/geometry/PositionTrackerSuite.scala`
- 10 test cases (see plan file)

### Phase 2: UI integration

**Modify** `modules/ui/src/main/scala/afpma/firecalc/ui/models/Variables.scala`
- Add 4 `lazy val Signal[PipePositionResult]` after `connectorpipe_finalFrame_sig` (~line 222)
- `fluepipe_positions_sig`, `connectorpipe_positions_sig`, `chimneypipe_positions_sig`, `airintake_positions_sig`
- Connector and chimney signals combine with respective `finalFrame` and `finalPoint` from upstream pipe

---

## Verification

1. **Unit tests**: `sbt "engine/testOnly *PositionTracker*"` — all pass ✅
2. **Compile**: `compile-module engine` + `compile-module ui` — clean ✅
3. **No Scala.js NPEs**: All new signals use `lazy val` ✅
4. **Known geometry**: Vertical 1m + 90° bend (roll=0°) + horizontal 2m → final = `(2, 0, 1)` relative to start ✅
5. **Position continuity**: `connectorpipe_positions_sig.finalPoint` matches start of chimney ✅
6. **Empty pipe**: Returns `startPoint` as `finalPoint` with empty `segments` ✅

---

## Future Extensions

1. **3D viewport**: Consume `PipePositionResult` to render cylinders/boxes in 3D
2. **2D PDF projections**: Project segments onto XZ or YZ planes for schematic drawings
3. **Chimney height validation**: Use `chimneypipe_positions_sig.map(_.finalPoint.z)` for EN 15544 termination height checks
4. **Collision detection**: Check whether connector intersects chimney pipe segments
5. **Per-element position badges**: Display start/end XYZ in the UI next to each pipe element
6. **Engine-layer integration**: Add positions to `PipeSectionResult` when needed by reports/backend
