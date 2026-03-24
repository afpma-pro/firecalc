# Summary: Generic Post-Firebox Pipe Topology Refactor

## What was done

### Wave 1: Core Typeclass Infrastructure ✅
Created 5 new files in `engine/ops/generic/`:
- **TopologyError** — validation error ADT (4 grammar rules)
- **UpstreamState** — inter-pipe state propagation (temp, density, velocity)
- **PipeSlot** — existential wrapper hiding the description algebra + `noop` factory
- **CanComputePipeResult** — path-dependent typeclass with zero-cast `mkSlot`, 3 factory functions (`forFlowOnly15544`, `forThermal13384`, `forFlowOnly13384`)
- **PostFireboxPipeChain** — validated topology container, region accessors, `computeAll` left-fold

13 topology validation tests pass.

### Wave 2: Skipped (content merged into Wave 1)
`PostFireboxPipeChain` already provides region accessors and `computeAll`.

### Wave 3: EN13384 Application Migration ✅
Replaced manual density/velocity threading between connector→chimney in `en13384_common_application.scala` with `PostFireboxPipeChain.computeAll` fold via `CanComputePipeResult.forThermal13384`. All EN13384 integration tests pass (C2, C16).

### Waves 4+5: EN15544 Strict + MCE ✅
Added `postFireboxPipeResults: VNelMcalcErr[Vector[PipeResult]]` to `AtParams` trait and `CommonAtParams` implementation. EN15544 connector/chimney already benefit from Wave 3's chain through the inner EN13384 application.

### Wave 6a: YAML Loader Extension Point ✅
Marked topology validation extension point in `FireCalcYAML_Loader.scala`.

### Wave 6b: DTO V5 — Deferred
Requires new schema (`post_firebox_pipes: Seq[PostFireboxPipeDescr_V5]`), V4→V5 migration, circe codecs. This is a separate milestone-sized effort.

### Wave 6c: UI Dynamic Pipe Panels — Deferred
Requires DTO V5, new reactive state management, pipe panel add/remove/reorder UI, schema migration for localStorage. Separate milestone.

## Files Changed (8 files, 6 new)

### New files
| File | Lines |
|------|-------|
| `engine/ops/generic/TopologyError.scala` | 28 |
| `engine/ops/generic/UpstreamState.scala` | 52 |
| `engine/ops/generic/PipeSlot.scala` | 42 |
| `engine/ops/generic/CanComputePipeResult.scala` | 116 |
| `engine/ops/generic/PostFireboxPipeChain.scala` | 141 |
| `engine/test/.../PostFireboxPipeChainSuite.scala` | 105 |

### Modified files
| File | Change |
|------|--------|
| `impl/en13384/en13384_common_application.scala` | connector/chimney via chain fold |
| `alg/en15544/en15544_application_alg.scala` | `postFireboxPipeResults` on `AtParams` |
| `impl/en15544/common/en15544_common_application.scala` | `postFireboxPipeResults` implementation |
| `api/FireCalcYAML_Loader.scala` | Extension point comment |

## Commits
1. `refactor(engine): wave 1 — core typeclass infrastructure for generic pipe topology`
2. `refactor(engine): wave 3 — EN13384 application uses generic pipe chain internally`
3. `refactor(engine): waves 4+5 — add postFireboxPipeResults vector accessor`
4. `refactor(engine): wave 6a — mark topology validation extension point in YAML loader`

## Remaining Work (DTO V5 + UI)
The engine core is ready for N-pipe topologies. To unlock the UI:
1. **DTO V5**: New `PostFireboxPipeDescr_V5` tagged union, `FireCalcYAML_V5` with `post_firebox_pipes: Seq[...]`, V4→V5 migration
2. **UI State**: `Signal[Vector[PipeSlotUI]]` replacing individual pipe vars in `Variables.scala`
3. **UI Panels**: Dynamic pipe add/remove/reorder panels with topology validation
4. **GraphDataConverter**: Iterate over generic pipe list
5. **Schema migration**: LocalStorage V4→V5
