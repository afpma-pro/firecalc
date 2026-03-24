# Plan: DTO V5 — Generic Post-Firebox Pipe Topology Schema

## Scope
Create FireCalcYAML_V5 with `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` replacing the three separate pipe descriptor fields. Add V4→V5 migration. Wire through the full stack.

## Waves

### Wave 7: FireCalcYAML_V5 + V4→V5 Migration
- New `modules/dto/src/main/scala/afpma/firecalc/dto/v5/FireCalcYAML_V5.scala`
- V5 replaces `flue_pipe_descr + connector_pipe_descr + chimney_pipe_descr` with `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]`
- All other fields identical to V4 (same types)
- V4→V5 migration: build `Vector[PostFireboxPipeDescrSlot]` from the three V4 fields
- Update `FireCalcYAML.scala` type alias to V5
- Update `FireCalcYAMLMigrations.scala` with V4→V5 path
- Add V5Instances if needed (probably just reuse V4Instances since pipe descr types unchanged)

### Wave 8: Engine + YAML Loader Adaptation
- Update `FireCalcYAML_Loader` to read from `post_firebox_pipes` instead of the three named fields
- Update `EngineState` (= `FireCalcYAML`) factory methods to use V5 constructor

### Wave 9: UI Variables Adaptation
- Update `Variables.scala` — derive individual pipe vars from `post_firebox_pipes`
- Keep backward compatibility: individual pipe signals still work for existing panels
- `postFireboxDescrSlots_sig` becomes the primary source (directly from state)

### Wave 10: Full Stack Verification
- Compile: dto (JVM+JS), engine, ui
- Test: engine/test (all 201 tests)
- Verify: DTO round-trip test (V5 encode → decode)
- Verify: V4→V5 migration test
