# 0006 — Flat Firebox Wire Encoding

**Date:** 2026-08-01
**Status:** Accepted
**Commit:** d5f97b5c

## Context

The `FireboxCalcRequest` type on the HTTP wire carries a `firebox` field whose shape depends on the `kind` discriminator. The `dto` module defines `Firebox_V4` as a sealed trait with circe codecs derived by `V5Instances` using default circe sealed-trait encoding: a single-key wrapper object keyed by the Scala constructor name (e.g., `{"Ecolabeled": {"fuel": "wood", ...}}`).

This wrapper encoding introduced two problems identified as BLOCKER 1 in the final isolation gate:

1. **Second vocabulary:** The wrapper key uses Scala constructor names (`Ecolabeled`, `AFPMA_PRSE`, `Door15aFirebox_Catalog`), which diverge from the `WireFireboxKind` strings (`ecolabeled`, `afpmaPrse`, `door15aCatalog`) used by the discovery endpoint (`GET /v1/kinds`). An independent implementer reading the OpenAPI spec would see `WireFireboxKind` enum values in the `kind` field but constructor names as wrapper keys in the `firebox` field — two different naming schemes for the same concept.

2. **OpenAPI incompleteness:** The OpenAPI `firebox` field was `type: object` with no properties, no discriminator, and no schema expansion. It delegated entirely to the AGPL `dto` module's circe codec, making the OpenAPI document insufficient for an independent implementer.

The custom codec in `Codecs.scala` (`firebox_calc_api`) already diverged from `V5Instances` for `WireFireboxKind` (flat string encoding via `wireCodec`), but the `firebox` field still used the default wrapper encoding from `V5Instances` derivation.

## Decision

Diverge from default circe wrapper encoding on the HTTP wire while preserving it for YAML persistence and localStorage. The HTTP wire uses **flat encoding**: the `firebox` object contains the subtype fields directly, discriminated by the sibling `kind` field (a `WireFireboxKind` string).

The custom encoder in `Codecs.scala` extracts the inner fields from the V5Instances wrapper and flattens them into the `firebox` object:

```scala
// Flat encoding — kind discriminator + subtype fields, no wrapper key
Json.obj(
  ("apiVersion", req.apiVersion.asJson),
  ("kind", req.kind.asJson),           // "ecolabeled", "traditional", etc.
  ("firebox", Json.obj(fireboxFields*)), // fields directly, no {"Ecolabeled": {...}}
  ("params", req.params.asJson),
  ...
)
```

The custom decoder reconstructs the wrapper shape before delegating to the V5Instances decoder, so the `dto` module's codec contract is preserved internally.

The OpenAPI spec was expanded (ticket 31) to define the `firebox` schema for all five `Firebox_V4` subtypes (Traditional, Ecolabeled, AFPMA_PRSE, SingleTested, Door15aFirebox_Catalog) with a discriminator referencing the `kind` field. `WireFireboxKind` has six values: `CustomForLab` is excluded from the `Firebox_V4` hierarchy and has no corresponding constructor.

## Boundary Audit

The divergence is contained to `Codecs.scala` in `firebox_calc_api`. Evidence:

- `V5Instances` (`modules/dto/src/main/scala/afpma/firecalc/dto/instances/V5Instances.scala:24-25`) derives default `Decoder[Firebox_V4]` and `Encoder[Firebox_V4]` via circe semiauto — unchanged. YAML persistence and localStorage continue to use wrapper encoding.
- `Firebox_V4` sealed hierarchy (`modules/dto/src/main/scala/afpma/firecalc/dto/v5/Firebox_V4.scala`) is unchanged. Constructor names remain the source of truth for YAML/localStorage encoding.
- `WireFireboxKind` (`modules/firebox_calc_api/src/main/scala/afpma/firecalc/firebox_calc_api/WireFireboxKind.scala:26-32`) maps wire strings to constructor names via `constructorName`. The mapping is exhaustive by construction (enum match).
- The custom `Encoder[FireboxCalcRequest]` (`Codecs.scala:103`) and `Decoder[FireboxCalcRequest]` (`Codecs.scala:139`) in `modules/firebox_calc_api/src/main/scala/afpma/firecalc/firebox_calc_api/Codecs.scala` are the only code that performs the flat/wrapper translation.

Build dependency (`build.sbt`): `firebox_calc_api` depends on `utils, units, domain, dto`. No module depends on `firebox_calc_api` except `firebox_calc_server` (private) and `ui` (for the client). The divergence does not affect the engine layer, payments, or reports.

## Consequences

### Positive

- **One wire vocabulary:** The `kind` field uses `WireFireboxKind` strings (`ecolabeled`, `traditional`, etc.) consistently across discovery (`GET /v1/kinds`) and calculation (`POST /v1/calculate`). No second vocabulary (constructor names) on the wire.
- **Stability under class renames:** Wire values are decoupled from Scala constructor names. Renaming a case class (e.g., `Ecolabeled` → `EcoLabeled`) does not change the wire format, as long as the `WireFireboxKind.wire` value is preserved.
- **Simpler OpenAPI:** The expanded OpenAPI schema uses a single discriminator (`kind`) with clear subtype schemas. An independent implementer can construct valid `firebox` payloads from the OpenAPI document alone.
- **Smaller payloads:** Flat encoding eliminates the wrapper key overhead per request.

### Negative

- **Custom codec divergence:** `Codecs.scala` contains a hand-written encoder/decoder for `FireboxCalcRequest` that diverges from the `V5Instances` derivation. This is a maintenance surface: if `Firebox_V4` subtypes gain or lose fields, the custom codec must be updated alongside the `V5Instances` derivation.
- **Two encodings for the same type:** Developers must understand that `Firebox_V4` is encoded differently on the HTTP wire (flat) versus YAML persistence and localStorage (wrapper). The glossary in `CONTEXT.md` documents the distinction.

## Considered Alternatives

- **Keep wrapper encoding on the wire.** Rejected: it requires the OpenAPI spec to document a second vocabulary (constructor names) alongside `WireFireboxKind` strings, and it couples the wire format to Scala constructor naming.
- **Change V5Instances to flat encoding.** Rejected: YAML persistence and localStorage would break; the change would be repo-wide rather than contained to the HTTP boundary.
- **Use a circe discriminator adapter.** Rejected: circe's built-in discriminator support still produces wrapper-like JSON; the custom codec gives precise control over the flat shape.
