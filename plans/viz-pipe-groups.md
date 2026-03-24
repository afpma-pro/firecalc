# Plan: Add Pipe Group Support to Viz Module

## Context

The viz module currently accepts a flat `PipeData[]` array. In `filaire-viz.ts:888-892`, consecutive pipes in this flat array get miter joints between them. When multiple disconnected pipe systems (flue, connector, chimney, air-intake) are concatenated, the last pipe of one system incorrectly gets a miter joint with the first pipe of the next — they're spatially disconnected and should have flat ends at group boundaries.

The goal is to introduce a `PipeGroup` concept so that miter joints only form between consecutive pipes **within** the same group.

## Files to Modify (all in `modules/viz/`)

### 1. `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireTypes.scala`

Add after `FireCalcFilaireLines` (line ~211):

```scala
case class FireCalcFilaireGroup(
    lines: FireCalcFilaireLines,
    name: Option[String] = None
)

type FireCalcFilaireGroups = List[FireCalcFilaireGroup]
```

### 2. `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireVizJS.scala`

Add new `PipeGroupJS` facade after `PipeDataJS` (line ~103):

```scala
trait PipeGroupJS extends js.Object:
  var pipes: js.Array[PipeDataJS]
  var name: js.UndefOr[String]

object PipeGroupJS:
  def apply(
    pipes: js.Array[PipeDataJS],
    name: js.UndefOr[String] = js.undefined
  ): PipeGroupJS =
    js.Dynamic.literal(pipes = pipes, name = name).asInstanceOf[PipeGroupJS]
```

### 3. `modules/viz/src/main/scala/afpma/firecalc/filaire/ThreeVizFacade.scala`

Change `pipes: js.Array[PipeDataJS]` → `pipeGroups: js.Array[PipeGroupJS]` in the `apply` signature.

### 4. `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireLinesViz.scala`

- **New primary `render` overload** accepting `FireCalcFilaireGroups`
- **Keep old `render` overload** accepting `FireCalcFilaireLines` as backward-compatible convenience (delegates to the groups version by wrapping in a single group)
- Replace `linesToJs` with `groupsToJs` that builds `js.Array[PipeGroupJS]` with a global running `lineIndex` counter across all groups (for click callback lookup)

### 5. `modules/viz/src/ts/filaire-viz.ts`

**New interface** (after `PipeData`, ~line 40):
```typescript
export interface PipeGroup {
  pipes: PipeData[]
  name?: string
}
```

**Signature change** (`initFilaireViz`):
- `pipes: PipeData[]` → `pipeGroups: PipeGroup[]`

**Rendering loop changes** — 3 locations where `for (... of pipes)` or `for (let i = 0; i < pipes.length; ...)` becomes a nested loop over groups then pipes within each group:

1. **FullShape loop** (lines 888-912): `prevPipe`/`nextPipe` scoped within each group — this is the core fix
2. **CenterLine loop** (lines 922-932): Nest iteration
3. **Name labels loop** (lines 1171-1222): Nest iteration

**View mode toggle** (line 1076): Update variable name `pipes` → `pipeGroups`.

## Key Design Details

- **`lineIndex` stays global**: Each `PipeDataJS` gets a unique `lineIndex` across all groups (0, 1, 2, ..., N-1). The click callback and raycasting continue to work via this flat index.
- **Backward-compatible Scala API**: The old `render(fcalcLines: FireCalcFilaireLines, ...)` signature is preserved as a convenience that wraps lines into one group. Existing callers (like `Viz3DPanel`) keep compiling without changes.
- **No changes** to `FilaireVizConfig.scala` — config is unaffected.

## Verification

1. Compile the viz module: `sbt "viz/compile"` (or Metals MCP `compile-module` with module `ui` since viz is a dependency)
2. Verify the TypeScript compiles (part of the Scala.js link step)
3. Existing callers using the old `render(fcalcLines, ...)` overload should compile unchanged
4. Visual test: run the app and verify that pipe groups no longer show miter joints at group boundaries (flat ends at first/last pipe of each group)
