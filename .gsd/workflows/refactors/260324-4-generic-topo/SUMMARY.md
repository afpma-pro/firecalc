# Summary: Generic Post-Firebox Pipe Topology Refactor (Final)

## Branch: `gsd/refactor/generic-topo` — 10 commits

## Commits
```
79602ab refactor(dto,engine,ui): wave 8 — MCE ThermalFlueSlot + GraphDataConverter generalization
499f3d4 docs: final refactor summary — all waves complete
1a37bd1 refactor(dto,engine,ui): wave 7 — DTO V5 schema with post_firebox_pipes
31b7b70 docs: update refactor summary and plan for waves 6b-6d
0e008f4 refactor(ui): wave 6c — add generic post-firebox topology signals
bb9bd1e refactor(dto,engine): wave 6b — PostFireboxPipeDescrSlot tagged union + topology validation
09ac622 refactor(engine): wave 6a — mark topology validation extension point in YAML loader
0f421bc refactor(engine): waves 4+5 — add postFireboxPipeResults vector accessor
1809451 refactor(engine): wave 3 — EN13384 application uses generic pipe chain internally
8c59318 refactor(engine): wave 1 — core typeclass infrastructure for generic pipe topology
```

## Architecture

### Engine Layer
- **PostFireboxPipeChain** — validated topology container enforcing grammar rules
- **CanComputePipeResult** — path-dependent typeclass with zero-cast `mkSlot`, 3 factories (forFlowOnly15544, forThermal13384, forFlowOnly13384)
- **PipeSlot** — existential wrapper hiding description algebra
- **UpstreamState** — inter-pipe state propagation (temp, density, velocity)
- **TopologyError** — validation error ADT (4 grammar rules)
- EN13384 `postFireboxChainResults` replaces manual density/velocity threading
- EN15544 `postFireboxPipeResults` vector accessor on AtParams

### DTO Layer
- **PostFireboxPipeDescrSlot** — tagged union enum (FlueSlot, ThermalFlueSlot, ConnectorSlot, ChimneySlot)
- **FireCalcYAML_V5** — `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` replaces 3 fixed fields
- V4→V5 migration, full V1→V5 chain tested
- Backward-compat accessors for flue_pipe_descr/connector_pipe_descr/chimney_pipe_descr

### UI Layer
- `postFireboxDescrSlots_sig` / `postFireboxPipeResults_sig` reactive signals
- Zoom lenses write through `post_firebox_pipes`
- AppStateSchema_V5 for localStorage
- `GraphDataConverter.convertGeneric` for variable-length post-firebox pipes

## Test Results
- **DTO**: 70/70 pass
- **Engine**: 194/201 pass (7 pre-existing, 0 regressions)
- **Full stack**: dto (JVM+JS), engine, ui compile cleanly

## Remaining (Separate Feature Milestone)
Dynamic UI pipe panels (add/remove/reorder pipe slots in accordion). Requires:
- New container component managing Vector[PostFireboxPipeDescrSlot]
- Per-slot collapsible accordion with correct PipePanel subtype
- Frame propagation re-wiring between dynamic slots
- Position tracking for 3D viz
- Full UX design for add/remove/reorder interactions
