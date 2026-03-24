# Summary: Generic Post-Firebox Pipe Topology Refactor (Complete)

## Branch: `gsd/refactor/generic-topo` — 8 commits

## What was done

### Wave 1: Core Typeclass Infrastructure ✅ (`8c59318`)
5 new files in `engine/ops/generic/`: TopologyError, UpstreamState, PipeSlot, CanComputePipeResult, PostFireboxPipeChain. 13 topology validation tests.

### Wave 3: EN13384 Application Migration ✅ (`1809451`)
`postFireboxChainResults` replaces manual density/velocity threading between connector→chimney.

### Waves 4+5: EN15544 Strict + MCE ✅ (`0f421bc`)
`postFireboxPipeResults: VNelMcalcErr[Vector[PipeResult]]` on `AtParams` trait.

### Wave 6a-c: YAML Loader + DTO Type + UI Signals ✅ (`09ac622`, `bb9bd1e`, `0e008f4`)
- `PostFireboxPipeDescrSlot` enum (FlueSlot/ConnectorSlot/ChimneySlot) with circe codecs
- `PipeChain_15544_Strict.toSlots`/`fromSlots` conversions
- `FireCalcYAML_Loader`: topology validation at load time
- UI signals: `postFireboxDescrSlots_sig`, `postFireboxPipeResults_sig`

### Wave 7: DTO V5 Schema ✅ (`1a37bd1`)
- `FireCalcYAML_V5` with `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]`
- Backward-compat accessors (`flue_pipe_descr`, `connector_pipe_descr`, `chimney_pipe_descr`)
- V4→V5 migration chain (V1→V2→V3→V4→V5 all tested)
- AppStateSchema_V5 for UI localStorage persistence
- EngineState factories use V5
- Variables.scala zoom lenses write through `post_firebox_pipes`

## Test Results
- **Engine**: 194/201 pass (7 pre-existing, 0 regressions)
- **DTO**: 70/70 pass (including V1-V4 format stability + migration round-trips)
- **Full stack compiles**: dto (JVM+JS), engine, ui — zero warnings

## Files Changed

### New files (9)
| File | Purpose |
|------|---------|
| `engine/ops/generic/TopologyError.scala` | Validation error ADT |
| `engine/ops/generic/UpstreamState.scala` | Inter-pipe state propagation |
| `engine/ops/generic/PipeSlot.scala` | Existential pipe slot wrapper |
| `engine/ops/generic/CanComputePipeResult.scala` | Typeclass + 3 factory functions |
| `engine/ops/generic/PostFireboxPipeChain.scala` | Validated topology + computeAll |
| `engine/test/.../PostFireboxPipeChainSuite.scala` | 13 topology validation tests |
| `dto/v4/PostFireboxPipeDescrSlot.scala` | Tagged union enum + circe codecs |
| `dto/v5/FireCalcYAML_V5.scala` | V5 schema with post_firebox_pipes |
| `ui/models/schema/v5/AppStateSchema_V5.scala` | UI localStorage V5 container |

### Modified files (9)
| File | Change |
|------|--------|
| `impl/en13384/en13384_common_application.scala` | connector/chimney via chain fold |
| `alg/en15544/en15544_application_alg.scala` | `postFireboxPipeResults` on AtParams |
| `impl/en15544/common/en15544_common_application.scala` | `postFireboxPipeResults` impl |
| `api/FireCalcYAML_Loader.scala` | Topology validation at load time |
| `models/PipeChain.scala` | toSlots/fromSlots conversions |
| `dto/all.scala` | Export PostFireboxPipeDescrSlot |
| `dto/FireCalcYAML.scala` | Type alias V4→V5 |
| `dto/FireCalcYAMLMigrations.scala` | V4→V5 migration + decode path |
| `ui/models/Variables.scala` | Generic topology signals + V5 zoom lenses |
| `ui/models/EngineState.scala` | V5 constructors |
| `ui/models/UndoManager.scala` | V5 import |
| `ui/models/schema/AppStateSchema.scala` | V5 alias |
| `ui/models/schema/AppStateSchemaMigrations.scala` | V4→V5 migration |
| `ui/models/AppStateSchemaHelper.scala` | V5 reference |
| `engine/test/.../SingleTested_Integration_Suite.scala` | V5 constructor |

## What Remains (Future Milestone)
The infrastructure for N-pipe topologies is complete. Future work:
1. **Dynamic UI pipe panels** — add/remove/reorder pipe slots in the accordion
2. **MCE ThermalFlueSlot** — extend PostFireboxPipeDescrSlot for MCE flue pipes
3. **GraphDataConverter** — iterate over variable-length post-firebox pipes
4. **V4 cleanup** — remove deprecated V4 compat accessors when all callers migrated
