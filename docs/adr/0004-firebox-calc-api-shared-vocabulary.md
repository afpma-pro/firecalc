# `firebox_calc_api` is the shared vocabulary module for firebox-calc

## Decision

`firebox_calc_api` is declared the shared vocabulary module for the firebox-calc feature.
Option (a) — "embrace" — was chosen by the module owner over option (b) — "separate"
(splitting verdict types into the engine layer and dropping the engine→api edge).

Option (b) was rejected as more migration work with wire-visible risk, for a boundary the
build graph already accepts: `firebox_calc_api` is leaf-light, with no engine/ui/payments
import anywhere in its main tree.

## Sharing Rule

**Types defined in `engine-kernel` (below the api module) get wire mirrors + total
conversions in `WireConversions`; everything defined at or above `firebox_calc_api` is
shared directly and is wire-stable contract.**

The existing mirrors (`DraftCondition`/`LoadQty` wire twins in `WireDraftCondition`/
`WireLoadQty`, and `WirePipeType` mirroring `PipeType`) are correct under this rule and
stay: those engine-kernel types live below the api module in the dependency graph, so they
keep their wire representations with total, catch-all-free conversions.

Below-api types outside `engine-kernel` follow the same axis: `dto` types (e.g.
`Firebox_V4`) are already wire-shaped DTOs and are embedded directly; `domain` types
(e.g. `PipeShape`) are engine-facing and keep wire mirrors (`WirePipeShape`), like
kernel types.

## Sanctioned Import: `FoldsConstraints` → `WireConversions`

`FoldsConstraints` (in `engine-15544-common`) imports `WireConversions` to emit
`WireQty`/`WirePipeShape` inside constraint descriptors. This is sanctioned under option
(a): the verdict type (`ConstraintVerdict`) IS the wire type, so the folding code that
produces verdicts must emit wire values. The ADR states this explicitly so the import
stops reading as a layering accident.

## Boundary Audit

Two invariants underpin this decision. Both were verified and must be preserved by future
changes.

### Invariant 1: `firebox_calc_api` is leaf-light (no engine/ui/payments import)

`grep -rn "^import " modules/firebox_calc_api/src/main/` shows exactly four import
statements, all in `Codecs.scala`:

- `import afpma.firecalc.dto.instances.V5Instances.given`
- `import io.circe.*`
- `import io.circe.generic.semiauto.*`
- `import io.circe.syntax.*`

No import of `afpma.firecalc.engine`, `afpma.firecalc.ui`, `afpma.firecalc.payments`,
`afpma.firecalc.austrian`, or any engine/ui/payments module exists in the main source
tree of `firebox_calc_api`. The build definition (`build.sbt:377`) confirms:
`.dependsOn(utils, units, domain, dto)` — four leaf modules only.

### Invariant 2: No engine→Austrian edge from vocabulary placement

`firebox_calc_api` carries no reference to `firebox_austrian`. The build definition
(`build.sbt:377`) lists no `firebox_austrian` dependency. The module's types include
`WireFireboxKind.Ecolabeled` and `WireFireboxKind.Door15aCatalog` as wire enum cases
(kind labels, not engine-kind imports) and `WireEcolabeledOutputs` as a wire DTO — neither
is an import from `firebox_austrian`. The dependency graph is:

```
firebox_calc_api → utils, units, domain, dto
engine_15544_common → firebox_calc_api, engine, engine_13384_*
firebox_austrian → engine_15544_common
```

Placing shared vocabulary in `firebox_calc_api` does not introduce any engine→Austrian
edge. The `firebox_austrian` module depends on `engine_15544_common`, not the other way
around (`build.sbt:621`).

**Status update (round 5, ticket 20):** the public `firebox_austrian` module has
been removed from the public repository and build graph; the graph fragment
above is the pre-extraction state. The audit conclusion is unchanged —
`firebox_calc_api` remains leaf-light with no Austrian edge.

## Contract Marking

Extended by [ADR-0011](0011-strict-only-deployment-refuses-non-strict-standards.md) —
the additive `unsupported_standard` discriminator it records follows the same-commit rule below.

The shared contract types in `firebox_calc_api` — `ConstraintVerdict` and its component
enums/ADTs (`ConstraintTerm`, `ConstraintDescriptor`, `ConstraintOutcome`,
`NotEvaluatedReason`), `WireQty`, and the request/response records — carry scaladoc
declaring them wire-stable: any shape change requires a matching OpenAPI document change
in the same commit. This is documentation and review discipline; the mechanical drift guard
(YAML enum/discriminator sets vs Scala `values.map(_.wire)`) is Tier-2 codec consolidation
work.

## Consequences

- A type crossing the wire is shared directly iff it is defined in `firebox_calc_api`
  (or above it in the dependency graph). Types defined below it (`engine-kernel`) keep
  hand-mirrored wire twins with total conversions in `WireConversions`.
- Refactoring a shared type (rename, field change, case addition) is an API break
  requiring an OpenAPI edit — before a client discovers it.
- The `label`/`descriptor` fields of `ConstraintVerdict` are now wire-contract surface;
  their deletion or redefinition (T2.1) is a documented API change with a known process.
- The Slice-6 classpath cut must not re-introduce an engine-side edge to Austrian types;
  vocabulary placement in `firebox_calc_api` is safe on this axis (invariant 2).
