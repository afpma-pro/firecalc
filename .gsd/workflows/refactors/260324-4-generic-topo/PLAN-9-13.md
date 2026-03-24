# PLAN: Dynamic Post-Firebox Pipe Panels

## Goal
Replace the 3 hardcoded pipe panels (FluePipePanel, ConnectorPipePanel, ChimneyPipePanel) with a dynamically-rendered panel list driven by `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]`. Users can add, remove, and reorder pipe slots. Topology validation shows inline warnings. Frame chaining is generic (slot N's final frame → slot N+1's initial frame).

## Decisions
- **Full dynamic**: add/remove/reorder at runtime, not just rendering from vector
- **Parameterized panels**: single panel class per pipe category (FlowOnly15544, Thermal13384) taking Var/Signal wiring as constructor params — replaces FluePipePanel/ConnectorPipePanel/ChimneyPipePanel
- **Generic frame chain**: Vector[Signal[Option[PipeFrame]]] where each entry depends on previous
- **Full indexed engine generalization**: EngineState/Loader produce per-slot indexed results + mappings
- **Permissive topology**: any slot combination allowed, TopologyError shown as inline warnings
- **Toolbar UX**: buttons [+ Flue] [+ Connector] [+ Chimney] above/below panels, with reorder arrows

## Waves

### Wave 9: Generic PipeChain — slot-indexed engine results
**Scope**: Engine + FireCalcYAML_Loader
**Goal**: FireCalcYAML_Loader produces `Vector[SlotResult]` where each slot carries pipe model, IdsMapping, and PipeResult — indexed by slot position.

Tasks:
- [ ] Define `SlotResult` case class in engine (pipe, mappings, pipeResult, finalFrame per slot)
- [ ] Generalize `PipeChain_15544_Strict.build` to accept `Seq[PostFireboxPipeDescrSlot]` and produce `Vector[SlotResult]`
- [ ] Frame chaining: each slot's build receives the previous slot's final frame
- [ ] Update `FireCalcYAML_Loader` to use `post_firebox_pipes` directly (drop backward-compat field access)
- [ ] Expose `slotResults: Vector[SlotResult]` on Loader alongside existing named fields (backward compat)
- [ ] Verify: engine tests pass, full stack compiles

### Wave 10: Variables.scala — generic slot signals
**Scope**: UI models (Variables.scala)
**Goal**: Replace per-pipe-type Vars/Signals with slot-indexed reactive state.

Tasks:
- [ ] `postFireboxSlots_var: Var[Seq[PostFireboxPipeDescrSlot]]` zoomed from engineStateVar
- [ ] Per-slot zoom: `slotDescrVar(idx): Var[Seq[IncrDescr]]` — writes back to slot at index
- [ ] Per-slot results signal from engineStateHelperVar → `slotResults(idx)`
- [ ] Per-slot mappings signal from engineStateHelperVar → `slotMappings(idx)`
- [ ] Generic frame chain: `slotFinalFrames: Signal[Vector[Option[PipeFrame]]]` computed by folding slots
- [ ] Per-slot position tracking: `slotPositions: Signal[Vector[PipePositionResult]]`
- [ ] Keep backward-compat val aliases for existing code that reads individual signals
- [ ] Verify: ui compiles

### Wave 11: Parameterized PipePanel
**Scope**: UI panels
**Goal**: Single `DynamicPipePanel` class (or two: one for FlowOnly15544, one for Thermal13384) that takes slot config as constructor params instead of hardcoding signal references.

Tasks:
- [ ] Create `DynamicPipeSlotPanel` taking: slotIndex, slotType, elemsVar, pipeResultSig, mappingsSig, externalFrameSig, vizPrefix
- [ ] Extend PipePanel (FlowOnly variant) or PipePanel_13384_Thermal based on slot type
- [ ] Wire statusIcon/vnel_signal/quadrionSubtotal from generic slot results
- [ ] tagTreeMenu selection based on slot type
- [ ] VizElementId: new `PostFireboxElement(slotIdx, elemIdx)` variant (or reuse existing by slot type)
- [ ] Verify: ui compiles, panels render correctly

### Wave 12: Dynamic pipe panel container + toolbar
**Scope**: UI accordion (DaisyUIVerticalAccordionAndJoin)
**Goal**: Replace hardcoded FluePipePanel()/ConnectorPipePanel()/ChimneyPipePanel() with `PostFireboxPipePanels` container that renders N panels from the slot vector.

Tasks:
- [ ] `PostFireboxPipePanels` component: `children <-- postFireboxSlots_var.signal.map(renderSlots)`
- [ ] Toolbar with [+ Flue] [+ Connector] [+ Chimney] buttons
- [ ] Remove/reorder controls per slot (trash icon, up/down arrows)
- [ ] Topology validation: `PostFireboxPipeChain.validated` → show TopologyError inline
- [ ] Default initial content for new slots (same as existing shortcut_start_new_pipe)
- [ ] Wire into accordion replacing the 3 hardcoded panels
- [ ] Verify: ui compiles, full stack test

### Wave 13: Integration + cleanup
**Scope**: All
**Goal**: Full verification, remove dead code, update docs.

Tasks:
- [ ] Remove FluePipePanel, ConnectorPipePanel, ChimneyPipePanel (dead code)
- [ ] Remove backward-compat aliases in Variables.scala if no longer referenced
- [ ] Update GraphPanel to use `convertGeneric` with slot-indexed results
- [ ] Run all tests: engine + dto
- [ ] Visual verification: dev server + browser
- [ ] Update SUMMARY.md
