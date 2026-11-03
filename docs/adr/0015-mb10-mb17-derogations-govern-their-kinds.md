# 0015 — A Merkblatt derogation governs its kind; EN 15544 governs the rest

**Date:** 2026-10-27
**Status:** Proposed — the rule is recorded before any code follows it. Nothing numeric changes: every
conflict found in the 2026-10 air-supply round had already been implemented in the Merkblatt's
favour, so this record makes an existing practice accountable rather than moving a value.

## Context

The two Austrian kinds are certification constructs, not standard models: the ecolabeled firebox comes
from MB 10 ("Fiche 10 Foyer Eco-labellisé"), the 15a door from MB 17. Both are nevertheless validated
through the EN 15544:2023 constraint machinery, because that is the standard whose terms the engine
owns. Where the two sources state different values for the same quantity, the engine has had to pick.

MB 10 states its own relationship to the standard, English printed p. 7 §3 (French printed p. 9):

> The combustion chamber geometry is designed in accordance with ÖNORM EN 15544. By way of
> derogation, a width-to-depth ratio of the combustion chamber from 0.5 to 2 is permissible.

That sentence does two things. It makes EN 15544 the baseline the Merkblatt is read against, and it
declares a **derogation** — a deliberate, author-owned override of one of the standard's numbers
(EN 15544:2023 §4.3.1.3, printed pp. 11–12, gives the depth-to-width ratio as 1.0–2.0).

The same pattern recurs. MB 10 fixes the ash drop at "a minimum of 5 cm and a maximum of 12 cm"
(English printed p. 7 §3, French printed p. 9) where §4.3.1.1 (printed p. 11) carries only the 5 cm
floor; MB 10 2022 indexes the wall-thickness table by charge mass with a 13 kg boundary and adds
`D1 + D2 ≥ 9 cm` (English printed p. 6, French printed p. 8), neither of which the standard states;
MB 17 scopes its charge band to 10–25 kg where the standard's scope clause is wider.

The engine's answers are already split along exactly this line, by kind rather than by rule:

- the kind accepts a width-to-depth ratio of 0.5–2 (`EcolabeledConstraints.width_to_depth_ratio`,
  still flagged `// TOCHECK`) and an ash drop of 5–12 cm (`EcolabeledConstraints.ash_pit_height_band`);
- the strict path enforces the standard: `FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = 1.0` /
  `FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = 2.0` (`std.scala:74-75`, compared at
  `FireboxConstraints_Strict.scala:151-178`) and a 5 cm floor with no ceiling
  (`FireboxConstraints_Strict.scala:69-79`).

What was missing was the rule that makes those splits intentional, and the boundary of that rule: the
Merkblätter are also **silent** on large parts of the standard (`O_BR = 900·m_B`, `A_BR`, the `± 5 %`
height tolerance, the dew-point check, the pipe-calculus clauses), and silence has to mean something
other than permission to ignore the standard.

## Decision

1. **A declared derogation wins, for its kind.** Where MB 10 or MB 17 states a value for a quantity
   that EN 15544:2023 also states, and the two differ, the ecolabeled and 15a kinds implement the
   Merkblatt value. This includes values the Merkblatt presents as derogations from the standard and
   values it states where the standard states only half a bound (a floor without a ceiling).
2. **Silence is not a derogation.** Where the Merkblätter say nothing about a requirement, EN
   15544:2023 applies unchanged — MB 10's own sentence puts the geometry in the standard's hands
   except where it explicitly deviates. The `± 5 %` height band of §4.3.1.4 is the test case: it is
   enforced and graded ([ADR-0014](0014-surface-identity-enforced-two-sided.md)), and the workbook
   note « Abweichung ±5 % erlaubt (lt. Norm keine) » is simply wrong about the 2023 edition.
3. **The strict path keeps the standard's numbers.** A geometry can be legal for an Austrian kind and
   refused by the strict engine — 0.6 width-to-depth is one — and that is the design, not drift.
   Kind/standard separation is already carried by the refusal rules
   ([ADR-0010](0010-remote-only-carriers-refuse-locally-resolved-austrian-kinds.md),
   [ADR-0011](0011-strict-only-deployment-refuses-non-strict-standards.md)).
4. **An override names its paper at the code site.** Clause or section, printed page, and the edition
   it was read in; a bare `// TOCHECK` where this rule already decides the question is replaced by
   that citation. The same attribution is repeated in the module's air-supply notes, which are
   kept outside this repository.
5. **This narrows the programme's standing "escalate every conflict" answer.** An MB 10/MB 17 versus
   EN 15544 conflict is settled here and is not put to KOV. Still escalated: conflicts between the
   Merkblätter or between their editions and translations (the 2018 PDF's French column is the 2015
   French text; MB 17's 15 mm centre-web maximum versus MB 10's `E < 6 cm`), anything where a
   worksheet and a paper disagree (the UZ37 sheets' `O_BR/6` against both papers' 1/5), and every
   constant of our own that no document supports.

## Consequences

- The aspect-ratio question leaves the KOV letter: the point stays as an informational statement of
  the conflict, because the answer is already decided by the paper that owns the kind.
- `EcolabeledConstraints.width_to_depth_ratio`'s `// TOCHECK` becomes a citation to MB 10 §3, printed
  p. 7 (FR p. 9). No value changes; the marker's removal belongs with the code pass this record
  accepts.
- Reviewers and agents get a decidable test for a divergence: is the quantity stated by both sources
  (derogation → follow the Merkblatt), stated by one (follow it, cite it), or stated by neither
  (the standard governs). Anything that fails that test cleanly is a wording problem, and wording
  problems go to the owner or to KOV — not silently to whichever source is closer to the code.
- A new Austrian-family kind has to say which source it follows per term, and the citation at the
  code site (decision 4) is where that answer is recorded.
- Tests keep both sides honest: strict fixtures stay at 1.0–2.0, the kind's own band is tested at
  0.5–2, and neither set is adjusted to make the other pass.

## Evidence

- MB 10, 2022 printing: English printed p. 7 §3 — the "designed in accordance with ÖNORM EN 15544.
  By way of derogation … 0.5 to 2" sentence, the 5–12 cm ash-drop band, `W ≥ 5 cm`; English printed
  p. 6 / French printed p. 8 — the mass-indexed wall table (13 kg boundary) and `D1 + D2 ≥ 9 cm`.
- EN 15544:2023: §4.3.1.1, printed p. 11 (5 cm floor on the lowest opening); §4.3.1.3, printed
  pp. 11–12 (base dimensions, depth/width 1.0–2.0); §4.3.1.4, printed p. 12 (height floor,
  `± 5.0 %` tolerance).
- Code as it stands: `EcolabeledConstraints.width_to_depth_ratio`,
  `EcolabeledConstraints.ash_pit_height_band` (the kind, 0.5–2 and 5–12 cm); `std.scala:74-75` and
  `FireboxConstraints_Strict.scala:151-178` (the standard, 1.0–2.0 inclusive),
  `FireboxConstraints_Strict.scala:69-79` (the 5 cm floor alone).
