# Final Summary: Generic Post-Firebox Pipe Topology Refactor

## Branch: `gsd/refactor/generic-topo` — 19 commits

## Status: UI loads, dynamic panels functional. Needs visual verification of add/remove/reorder.

## What Was Built

A complete generic N-pipe topology system replacing the hardcoded 3-pipe (flue → connector → chimney) post-firebox layout. Full stack: engine, DTO, UI.

### Engine Layer (Waves 1, 3, 4-5, 6a, 8, 9)
- **CanComputePipeResult** typeclass — path-dependent types with zero-cast `mkSlot`
- **PostFireboxPipeChain** — validated topology container with grammar rules
- **PipeSlot/UpstreamState/TopologyError** — generic state propagation and validation
- **PipeChainGeneric** — builds `Vector[SlotBuildResult]` from any `Seq[PostFireboxPipeDescrSlot]` with automatic frame chaining
- **SlotBuildResult** — type-erased container (pipe model + IdsMapping as `Int→Option[Int]` + final PipeFrame)
- EN13384/EN15544 applications use the generic chain internally

### DTO Layer (Waves 6b, 7, 8)
- **PostFireboxPipeDescrSlot** — tagged union: FlueSlot, ThermalFlueSlot, ConnectorSlot, ChimneySlot
- **FireCalcYAML_V5** — `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` replacing 3 fixed fields
- Full V1→V2→V3→V4→V5 migration chain

### UI Layer (Waves 6c, 7, 10, 11, 12, 13, fix)
- **postFireboxSlots_var** — writable Var driving add/remove/reorder
- **Slot-indexed signals** — build results, frame chain, position tracking, per-slot accessors
- **DynamicFlowOnlyPipeSlotPanel** / **DynamicThermalPipeSlotPanel** — parameterized panels replacing FluePipePanel/ConnectorPipePanel/ChimneyPipePanel
- **PostFireboxPipePanels** — container component with [+ Flue] [+ Connector] [+ Chimney] toolbar, per-slot reorder/remove controls, inline topology validation
- GraphPanel uses `convertGeneric` with slot-indexed results
- Viz3DPanel uses `slotPositions_sig`
- **Blank page fix**: panels built from `.now()` snapshots via `structureVersion` counter, not inside `Signal.map` (Laminar anti-pattern)

### Removed (Wave 13)
- FluePipePanel.scala, ConnectingPipePanel.scala, ChimneyPipePanel.scala
- 17 named per-pipe signals from Variables.scala

## Test Results
- **DTO**: 70/70 pass
- **Engine**: 202/209 pass (7 pre-existing, 0 regressions)
- **Full stack compiles**: dto (JVM+JS), engine, ui — zero warnings

## Known Gaps
1. **I18n** — "Post-firebox pipes" toolbar label hardcoded English
2. **Double pipe build** — FireCalcYAML_Loader builds both PipeChain_15544_Strict and PipeChainGeneric (redundant)
3. **GraphDataConverter** — `convertGeneric` delegates to old 3-pipe `convert` by position; >3 pipes won't chart correctly
4. **Viz3DPanel** — `VizConverter.allPipesToGroups` expects exactly 3 named positions; extra pipes won't render in 3D
5. **Visual verification needed** — add/remove/reorder, localStorage round-trip, undo/redo, topology warnings

## Commit Log
```
a987a3a fix(ui): fix blank page — avoid creating panels inside Signal.map
eb4f5c4 docs: final summary — all 13 waves complete
e22694d wave 13 — cleanup: remove dead panels + wire GraphPanel/Viz3DPanel
862b699 wave 12 — dynamic pipe panel container + toolbar
c6f902e wave 11 — parameterized DynamicPipeSlotPanel
22d1934 wave 10 — generic slot-indexed reactive state in Variables.scala
ee529c1 wave 9 — generic PipeChainGeneric with slot-indexed results
fd10fa3 docs: plan waves 9-13 — dynamic pipe panels
d4d5587 docs: final summary after wave 8 completion
79602ab wave 8 — MCE ThermalFlueSlot + GraphDataConverter generalization
499f3d4 docs: final refactor summary — all waves complete
1a37bd1 wave 7 — DTO V5 schema with post_firebox_pipes
31b7b70 docs: update refactor summary and plan for waves 6b-6d
0e008f4 wave 6c — add generic post-firebox topology signals
bb9bd1e wave 6b — PostFireboxPipeDescrSlot tagged union + topology validation
09ac622 wave 6a — mark topology validation extension point in YAML loader
0f421bc waves 4+5 — add postFireboxPipeResults vector accessor
1809451 wave 3 — EN13384 application uses generic pipe chain internally
8c59318 wave 1 — core typeclass infrastructure for generic pipe topology
```
