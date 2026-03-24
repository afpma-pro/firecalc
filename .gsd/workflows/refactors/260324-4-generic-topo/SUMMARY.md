# Summary: Generic Post-Firebox Pipe Topology Refactor

## Branch: `gsd/refactor/generic-topo` — 6 commits

## What was done

### Wave 1: Core Typeclass Infrastructure ✅ (`8c59318`)
Created 5 new files in `engine/ops/generic/`:
- **TopologyError** — validation error ADT (4 grammar rules)
- **UpstreamState** — inter-pipe state propagation (temp, density, velocity)
- **PipeSlot** — existential wrapper hiding the description algebra + `noop` factory
- **CanComputePipeResult** — path-dependent typeclass with zero-cast `mkSlot`, 3 factory functions (`forFlowOnly15544`, `forThermal13384`, `forFlowOnly13384`)
- **PostFireboxPipeChain** — validated topology container, region accessors, `computeAll` left-fold
- 13 topology validation tests pass.

### Wave 2: Skipped (content merged into Wave 1)

### Wave 3: EN13384 Application Migration ✅ (`1809451`)
Replaced manual density/velocity threading between connector→chimney in `en13384_common_application.scala` with `PostFireboxPipeChain.computeAll` fold via `CanComputePipeResult.forThermal13384`.

### Waves 4+5: EN15544 Strict + MCE ✅ (`0f421bc`)
Added `postFireboxPipeResults: VNelMcalcErr[Vector[PipeResult]]` to `AtParams` trait and `CommonAtParams` implementation.

### Wave 6a: YAML Loader Extension Point ✅ (`09ac622`)
Marked topology validation extension point in `FireCalcYAML_Loader.scala`.

### Wave 6b: DTO Type + Topology Validation ✅ (`bb9bd1e`)
- New `PostFireboxPipeDescrSlot` enum (FlueSlot/ConnectorSlot/ChimneySlot) in DTO module with circe codecs
- `PipeChain_15544_Strict.toSlots`/`fromSlots` and `PipeChain_13384.toSlots`/`fromSlots` conversions
- `FireCalcYAML_Loader`: actual topology validation via `PostFireboxPipeChain.validated` at load time

### Wave 6c: UI Integration Signals ✅ (`0e008f4`)
- `postFireboxDescrSlots_sig`: derives `Vector[PostFireboxPipeDescrSlot]` from the three individual pipe descriptor vars
- `postFireboxPipeResults_sig`: extracts `Vector[PipeResult]` from EN15544 Strict application

## Full stack compilation: ✅
- `dto` (JVM + JS), `engine`, `ui` all compile cleanly with zero warnings

## Test results: 194/201 pass (7 pre-existing failures, 0 regressions)

## Files Changed

### New files (7)
| File | Purpose |
|------|---------|
| `engine/ops/generic/TopologyError.scala` | Validation error ADT |
| `engine/ops/generic/UpstreamState.scala` | Inter-pipe state propagation |
| `engine/ops/generic/PipeSlot.scala` | Existential pipe slot wrapper |
| `engine/ops/generic/CanComputePipeResult.scala` | Typeclass with 3 factory functions |
| `engine/ops/generic/PostFireboxPipeChain.scala` | Validated topology + computeAll |
| `engine/test/.../PostFireboxPipeChainSuite.scala` | 13 topology validation tests |
| `dto/v4/PostFireboxPipeDescrSlot.scala` | Tagged union enum + circe codecs |

### Modified files (6)
| File | Change |
|------|--------|
| `impl/en13384/en13384_common_application.scala` | connector/chimney via chain fold |
| `alg/en15544/en15544_application_alg.scala` | `postFireboxPipeResults` on `AtParams` |
| `impl/en15544/common/en15544_common_application.scala` | `postFireboxPipeResults` implementation |
| `api/FireCalcYAML_Loader.scala` | Topology validation at load time |
| `models/PipeChain.scala` | `toSlots`/`fromSlots` on PipeChain builders |
| `dto/all.scala` | Export `PostFireboxPipeDescrSlot` |
| `ui/models/Variables.scala` | Generic topology signals |

## Remaining Work (DTO V5 + Dynamic UI Panels)
The engine, DTO, and UI foundations are ready for N-pipe topologies. To unlock dynamic pipe management:
1. **DTO V5**: `FireCalcYAML_V5` with `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` replacing the 3 fixed fields
2. **V4→V5 migration**: Convert existing 3-field layout to slot vector
3. **UI Panels**: Pipe add/remove/reorder with topology validation feedback
4. **MCE support**: Add `ThermalFlueSlot` variant to `PostFireboxPipeDescrSlot` for MCE flue pipes
5. **GraphDataConverter**: Accept variable-length post-firebox pipe vector
6. **LocalStorage migration**: V4→V5 schema for persisted state
