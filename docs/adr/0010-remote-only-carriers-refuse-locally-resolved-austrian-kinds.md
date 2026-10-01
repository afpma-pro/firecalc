# 0010 — Remote-Only Carriers Refuse a Locally Resolved Austrian Kind

**Date:** 2026-10-01
**Status:** Proposed
**Commit:**

## Context

`Ecolabeled` and `Door15aCatalog` are calculated by an engine that lives only in the private
repository; the engine sources were deleted from the public repository in `f1fdfa03`. On the
public side the two kinds are served by the generic remote-only carriers in
`RemoteOnlyFireboxes_Strict`, registered through `FireboxEngineAny.foldedOnly`, and are normally
computed over the HTTP calculation seam.

`FireboxEngineSource` defaults every kind to `Local`, and nothing validates that default against
the carriers' actual capability. A deployment whose remote-calculation variables are left unset
therefore resolves an Austrian kind to the in-process registry. Until now each carrier's
`constraints` returned `Nil`, so that path produced a *silent degenerate calculation*: no Austrian
outputs, no violation, no error — a plausible-looking EN 15544 result with no constraint checking
behind it. For a tool whose output is a dimensioning report, that is worse than a refusal.

## Decision

Each remote-only carrier's `constraints` reports **exactly one blocking refusal verdict** instead
of `Nil`, built from the existing constraint vocabulary:

```scala
ConstraintVerdict(
    ConstraintTerm.Custom,
    code,
    ConstraintDescriptor.Opaque,
    ConstraintOutcome.Violated(code, message, blocking = true)
)
```

`FoldedVerdicts` already folds a blocking `Custom` verdict into `FireboxErrorCustom`, and a carrier
entry has no per-term algebra (`constraintsAlg` is `None`), so the fold lands in
`validateFireboxSpecificConstraints` → `validateResultsExceptEmissionsValues`. No new plumbing is
required for the refusal to fail the application, refuse the panels, and refuse the JVM report
factory.

**Placement is `constraints`, never the contribution methods — this is load-bearing.** The remote
flow builds the local base application *through these carriers before every fetch*
(`RemoteFireboxPrefetch.runCycle` → strict loader → contributed pipes →
`fireboxContribution` / `combustionAirContribution`). A contribution-level refusal would make that
base build Invalid and silently kill every remote calculation. `FireboxContribution.Without` and
the constraint-free carrier shape (no per-term algebra) stay exactly as they are; they are now a
documented invariant rather than an accident.

**Two surfaces, settled precedence.** The browser refuses *first*, through
`KindComputation.Refused(kind, reason)` (ticket 02), so an operator sees "no calculator
configured" rather than a standards finding. The carrier's blocking verdict is the engine-level
backstop for the JVM report factory (`FireCalcReportFactory_15544_Strict` validation) and for any
future direct engine caller.

**No new shared type.** `ConstraintVerdict`, `ConstraintTerm`, `ConstraintDescriptor` and
`ConstraintOutcome` are defined at or above `firebox_calc_api`, so under [ADR-0004](0004-firebox-calc-api-shared-vocabulary.md)'s
sharing rule they are shared directly and are wire-stable contract: reuse them, do not wrap or
mirror them. The carriers' `constraints` signature is untouched, so the pinned private overlay's
registry keeps compiling against the existing shape.

**The message is a non-standards condition.** Dedicated EN + FR HOCON keys
(`builder_errors.no_calculator_configured` in `modules/i18n`) name the refused kind and state that
no calculator is configured for it. It is never filed under an EN 15544 physics group and must
never be reclassified as constraint physics: it describes a deploy condition.

**No deploy-time validation.** No auto-disable of the Austrian kinds, no build-time check, no
change to `FireboxEngineSource` defaults: a misconfigured build still resolves `Local`, and the
refusal fires where the result would otherwise have been produced.

## Consequences

- **Positive:** a misconfigured deployment becomes reproducible instead of silent — the locally
  resolved Austrian application validates invalid, naming the kind, at every consumer seam that
  already reads validation (panels, report factory, direct engine callers).
- **Positive:** the remote path is untouched. The prefetch, registry, reports and payments suites
  that assert the remote path pass byte-unchanged, which is itself the evidence that the refusal
  sits in the right method.
- **Positive:** no contract change. Nothing on the wire, in `dto`, or in the shared vocabulary
  moved, so no OpenAPI amendment and no pin coupling is implied by this decision.
- **Negative:** a misconfigured deployment shows a hard refusal for the two kinds. That is the
  intent; the supported postures remain configuring the calculation backend or disabling the kinds
  via the firebox-availability flags.
- **Negative:** the startup engine-source log still prints `ecolabeled=local` for such a build. It
  now has to be read as "will refuse", not "can compute".
- **Considered:** refusing in the contribution methods. Rejected — it invalidates the base build
  the remote prefetch depends on (see above).
- **Considered:** raising an error from `constraints`. Rejected —
  `RemoteConstraintReportingSuite` already records that throwing from this fold path crashed both
  the firebox panel and the Typst report; the refusal must travel as a verdict.
- **Considered:** a non-blocking verdict. Rejected — `FoldedVerdicts.violations` folds only
  `Violated(_, _, blocking = true)`, so a non-blocking verdict would leave the silence intact.
- **Considered:** deploy-time validation of the remote-calculation variables. Rejected as out of
  scope: the refusal has to fire where the result is produced, and a startup refusal would not
  protect the report factory or direct engine callers.
