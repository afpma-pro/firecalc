# Revised Plan: Waves 6b–6d (DTO + Loader Integration)

## Status after Waves 1–6a
- Engine core complete: `PostFireboxPipeChain`, `CanComputePipeResult`, `PipeSlot`
- EN13384 application uses generic chain internally
- EN15544 has `postFireboxPipeResults` vector accessor
- YAML Loader has extension point comment

## Remaining Waves

### Wave 6b: PostFireboxPipeDescrSlot (DTO module, cross-compiled)
**Goal**: Add a tagged union type in the DTO module that pairs a PipeType with a pipe descriptor sequence. This is the building block the UI and loader need to express N-pipe topologies.

**Files (new)**:
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/PostFireboxPipeDescrSlot.scala`

**Design**:
```scala
enum PostFireboxPipeDescrSlot:
  case FlueSlot(descr: Seq[FlowOnlyPipeDescr_15544_V3])
  case ConnectorSlot(descr: Seq[ThermalPipeDescr_13384_V3])
  case ChimneySlot(descr: Seq[ThermalPipeDescr_13384_V3])
```

With circe codecs (discriminator-based). Add to `all.scala` exports.

**Note**: No V5 schema yet — this type is usable TODAY alongside V4 for internal processing. V5 will use it as a field.

### Wave 6c: PostFireboxPipeChain builder from DTO slots
**Goal**: Bridge from DTO descriptors to engine chain. Takes a `Vector[PostFireboxPipeDescrSlot]` and produces a validated `PostFireboxPipeChain` (for topology validation) plus the materialized pipe models.

**Files (new)**:
- `modules/engine/src/main/scala/afpma/firecalc/engine/api/PostFireboxChainBuilder.scala`

**Design**:
```scala
object PostFireboxChainBuilder:
  case class PipeSlotWithDescr(
    slot: PipeSlot,
    pipe: ValidatedNel[IncrementalValidation_Error, ???],
    mappings: ValidatedNel[IncrementalValidation_Error, ???]
  )
  
  def fromV4(
    flue: Seq[FlowOnlyPipeDescr_15544_V3],
    connector: Seq[ThermalPipeDescr_13384_V3],
    chimney: Seq[ThermalPipeDescr_13384_V3]
  ): (PostFireboxPipeChain, ...) = ...
  
  def fromSlots(
    slots: Vector[PostFireboxPipeDescrSlot]
  ): ValidatedNel[TopologyError, (PostFireboxPipeChain, ...)] = ...
```

### Wave 6d: Wire FireCalcYAML_Loader
**Goal**: Use PostFireboxChainBuilder in the YAML loader. Replace current PipeChain_15544_Strict with the generic builder.

### Wave 6e (future): DTO V5 + UI
Deferred to separate milestone. Requires full UI panel rewrite.

## Verification
- `sbt compile` (JVM + JS — dto is cross-compiled)
- `sbt 'engine/test'` — full engine test suite
