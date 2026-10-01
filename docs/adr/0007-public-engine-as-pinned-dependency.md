# 0007 — Public engine modules as pinned binary dependency

**Date:** 2026-09-08
**Status:** Implemented (gate: `.scratch/round-6-engine-as-dependency/GATE-REPORT-ROUND-6.md`, 2026-09-10)
**Supersedes (partially):** RELOCATION.md §3 (private relicensed copies of the generic foundation)

## Context

Ticket 14 (round 5) relocated the Austrian calculation engine to the private
repository `firecalc-firebox-austrian-api` by **copying** 17 sbt modules — the
proprietary Austrian IP *plus* the entire generic EN 15544 / EN 13384
foundation (`engine_kernel`, `engine`, `engine_13384_*`, `engine_15544_common`,
`units`, `domain`, `utils`, `i18n`, `i18n_utils`, `dto`, `firebox_calc_api`, and
the generic halves of `engine_15544_strict` / `engine_15544_mce`) — and
relicensing them `LicenseRef-AFPMA-Proprietary`. The copy is byte-identical at
`6cceaa7`, but the private repo has since **edited the copied generic modules**
(zero-geometry fail-fast in `engine_kernel`, air-intake shape checks duplicated
on both sides of the fork, `firebox_calc_api` codec fixes). Two forks of the
EN-cross-validated core now drift with no sync mechanism, and the golden-test
guarantee (engine validated byte-for-byte against independent EN 13384/15544
implementations) is only meaningful for one of them.

A competing licensing theory — "if the private repo merely *uses* the public
AGPL engine modules without editing them, the private server escapes AGPL" —
was examined and rejected: AGPL v3 obligations attach to conveying and to
remote network use (§13) of a work that links AGPL code, not to whether the
AGPL code was edited. The lawful basis for the private build is AFPMA holding
sole copyright over everything and granting itself a proprietary license
(provenance scan: zero non-AFPMA copyright holders), not the no-edit theory.

## Decision

The private repository stops carrying copies of the generic foundation and
instead **depends on the public repository's modules as external binary
artifacts**, pinned by version. The no-edit rule is retained — not as a legal
theory, but as the **anti-drift invariant**: the private repo never edits an
imported module; Austrian behaviour is expressed only in private overlay
modules that sit above them.

- **Imported (public AGPL artifacts, 14 modules):** engine-kernel, engine,
  engine-13384-common, engine-13384-strict, engine-15544-common,
  engine-15544-strict (generic), engine-15544-mce (generic), units, domain,
  utils, i18n, i18n-utils, dto, firebox_calc_api.
- **Private (5 modules):** `firebox-austrian` (models/constraints/formulas),
  `firebox-austrian-strict` (new: FireboxCalcCore, Austrian givens, 6-kind
  registry, model conversion, C3 cas-types, NPipe fixture — the current
  `src/main-jvm` overlay), `firebox-austrian-mce` (new: the two Austrian MCE
  givens + registry lifted out of the mixed MCE files), `firebox_calc_server`,
  `firebox_calc_testkit`.
- **Pinning:** `sbt publishLocal` from a checked-out public tree is the only
  artifact path (no registry, no CI publishing; private builds are
  developer-local by choice). Daily dev uses `-SNAPSHOT`; anything committed or
  shipped pins a SHA-stamped version (`0.3.0-b25+<sha7>`, mirroring the
  existing `+engine-` metadata idiom). Bumps are manual, on demand.
- **Source of truth:** the public repository owns the generic engine and its
  golden EN-validation tests. The private correctness claims track whatever pin
  is checked out. Divergences introduced privately after the copy are
  reconciled into public **before** the cutover (public air-intake is
  canonical; private-only generic changes — the kernel fail-fast and codec
  fixes — are upstreamed).
- **Tests:** Austrian-evidence suites live in the private overlays; generic
  cas-type/fixture suites duplicated in the private tree are dropped — public
  owns that regression net.
- **Enforcement:** a private-repo check fails the build if any imported module
  name reappears as a private source directory. The licensing basis is
  documented as an internal dual-license grant
  (`docs/licensing/afpma-dual-license-grant.md`).

## Considered Options

- **Keep relicensed private copies (status quo).** Legal (sole copyright), but
  permanently forks the EN-validated core; the dual-landing of the air-intake
  feature is the first realized drift bug.
- **Pure dependency without the grant ("no edit → no AGPL").** Rejected: wrong
  on the law (§13 links, not edits), and it hides the actual dual-license
  basis rather than documenting it.
- **Git source dependency (clone public at tag/SHA in the private build).**
  Rejected for now: slower builds and AGPL sources inside the private tree;
  publishLocal gives the same dev loop without either. May revisit when CI
  reproducibility matters.
- **Git subtree of engine modules.** Rejected: still a copy; recreates the
  drift the rule exists to prevent.

## Consequences

- Public engine changes must land publicly (AGPL) before the private server can
  use them; the private repo's build graph gains an external boundary that sbt
  cannot resolve without a prior `publishLocal` of the pinned version.
- Two `LicenseRef-AFPMA-Proprietary`→import flips (`dto`, `firebox_calc_api`)
  mean the private server links AGPL-licensed binaries; this is safe only under
  the internal grant and requires AFPMA's sole-copyright position to stay true
  (no external contributor patches without a CLA).
- If the private overlay ever needs a hook the generic engine lacks, the fix
  goes into public `engine_*` and comes back as a pin bump — never into a
  private fork. The CI guard makes a violation loud instead of silent.
