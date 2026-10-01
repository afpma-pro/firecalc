# 0011 — A Strict-Only Deployment Refuses the Standards It Does Not Serve

**Date:** 2026-10-01
**Status:** Proposed

*The contract half of this decision — the `UnsupportedStandard` variant and the OpenAPI
update — lands in this repository. The refusal itself lands in the private calculation
backend, which compiles it only after the SHA stamp is published and pinned (ADR-0007).
The flip to Accepted carries both commit refs.*

## Context

`FireboxCalcRequest` carries a `standard` field typed as the two-value wire enum
`WireStandardVariant`: `strict` / `mce`. The calculation backend wires exactly one engine —
the strict registry — and the generic MCE artifact is deliberately absent from the server
classpath. Today a request for `standard: "mce"` is therefore answered with HTTP 200 and
*strict* data: the client receives a different standard than it asked for, silently. That is
the same failure class as the carrier refusal in
[ADR-0010](0010-remote-only-carriers-refuse-locally-resolved-austrian-kinds.md) — a plausible-
looking result for work the deployment cannot do — and for an API whose output feeds an
EN 15544 dimensioning report, it is worse than a refusal.

Nothing in the public product sends `mce` (the browser and payments construct strict
requests), so the affected callers are third parties exercising the contract's `mce` value —
who are currently being served wrong-standard data.

## Decision

**Refuse at the calculation-core checkpoint.** The private core gains a vocabulary checkpoint
on `standard`, ordered **apiVersion → standard → locale → kind** next to the existing
api-version and locale checks. It refuses every request whose standard is not `strict`, for
**all kinds**: strict-only is a deployment posture, not a per-kind capability, and placing
the check ahead of kind dispatch means it cannot mask a malformed request behind a standard
error.

**The shared contract gains an `UnsupportedStandard` variant, rendered as HTTP 400
`unsupported_standard`.** The sealed `FireboxCalcServerError` taxonomy in `firebox_calc_api`
grows the variant — `(requested, supported)`, mirroring `UnsupportedLocale` and
`UnsupportedApiVersion` — and the routes layer renders it as 400 with an `ErrorResponseEnvelope`
whose discriminator is `unsupported_standard` and whose English wire text names the requested
and the served standards. The server error type has no wire codec of its own (the envelopes
are the wire shape), so this is a taxonomy addition, not a wire-shape change. English wire
text only: the wire error family is not localized.

**The request enum stays `[strict, mce]`.** The value now means "accepted by the schema,
refused by this deployment". Narrowing or dropping `standard` was rejected as a breaking
change to a contract frozen at `1.0.0`, and it would foreclose an MCE-serving deployment
becoming an additive change.

**Discovery is not widened.** `DiscoveryResponse` (`apiVersion`, `kinds`) and
`FireboxCalcResponse` are unchanged: a deployment cannot advertise served standards. The
strict-only posture is carried by the OpenAPI prose and by the refusal itself.

**The OpenAPI document is updated additively under `info.version: 1.0.0`, in the same commit
as the Scala variant** — the same-commit rule from [ADR-0004](0004-firebox-calc-api-shared-vocabulary.md)'s
Contract Marking section. The 400 discriminator list gains an `unsupported_standard` clause
and example; the `WireStandardVariant` description gains the accepted-by-schema /
refused-by-deployment clarification.

## Consequences

- **Positive:** an `mce` request can no longer be answered with wrong-standard data. The
  refusal names the requested standard and the served ones, so a client can decide whether
  to retry with `strict`.
- **Positive:** a `strict` request is byte-identical to today's response — every existing
  suite and consumer constructs strict requests, so the refusal cannot break a live caller.
- **Positive:** already-shipped clients degrade safely. `Http4sCalcClient` maps any non-2xx
  with a decodable envelope to a typed `CalculationFailure.ProtocolError` carrying the
  discriminator string, and never matches on that string (the `ErrorResponseEnvelope`
  fallback path is covered by `Http4sCalcClientSuite`), so a stale bundle sees a typed
  failure, not a crash. The public tree compiles with the variant present but unused.
- **Positive:** the routes match arms every variant with no default arm — the invariant
  against catch-all dispatch — so adding the variant forces the new arm at compile time.
- **Negative (accepted knowingly): the publish/pin coupling.** The variant lives in the
  pinned `firecalc-firebox_calc_api` artifact (ADR-0007), so the private tree cannot compile
  the refusal until the SHA-stamped artifact is published and `engine_version_pin` is
  bumped. The variant, the routes arm and the pin bump therefore share one private commit —
  a build can never be missing either half.
- **Negative:** clients must treat request-schema acceptance as necessary but not sufficient;
  a deployment can refuse a schema-legal `standard` at 400. This is documented in the
  contract's own vocabulary rather than hidden in deployment configuration.
- **Considered:** serving MCE from the backend. Rejected — the generic MCE artifact is
  deliberately off the server classpath; including it collides with the assembly merge rules
  (which would need `preferProject` shadow-priority changes), and no MCE equivalence coverage
  exists that would make what ships provable.
- **Considered:** 404 `kind_not_served`. Rejected — the contract's 404 tells the client to
  consult `GET /kinds` first, but discovery cannot advertise standards, so 404 would point a
  refused client at an answer that does not exist. The vocabulary refusals in this taxonomy
  are 400 (`unsupported_locale`, `unsupported_api_version`), and this joins them.
- **Considered:** narrowing the enum to `[strict]`, or dropping the `standard` field.
  Rejected as a breaking contract change that forecloses the additive MCE-serving future.
- **Considered:** a private-only error type. Rejected — the taxonomy is contract surface:
  clients need the discriminator documented in the normative OpenAPI document, which only
  the shared module's variant can carry alongside its same-commit documentation update.
