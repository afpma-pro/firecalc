# 0014 — The `H_BR` band is enforced, two-sided and blocking

**Date:** 2026-10-27
**Status:** Accepted — by the owner on 2026-10-27, ahead of implementation. The work is ticket 22 of
the maintainer-local KOV tracker (`kov-2026-10-air-supply/issues/22-h-band-and-surface-identity.md`).
**Commit:** d056011936c4b4121d0e44428c18c8e5703ee676 (public: both terms graded blocking from the
shared strict owner) + d39e5e5936c3f345d93665856497ea5de4411d40 (Austrian backend: the duplicate
floor deleted, the zero-geometry fixtures re-charged), published as stamp `engine-0.3.1-b27+3f7ea4e`.

## Context

EN 15544:2023 states the firebox surface as a requirement on the charge, §4.3.1.2 formula (3), printed
p. 11:

> `O_BR = 900 · m_B`

and derives from it the height the charge implies, §4.3.1.4, printed p. 12: a floor
`H_min = 25 cm + m_B`, a calculated height `H_BR = (900·m_B − 2·A_BR)/U_BR`, and permission for the
specified chamber height to deviate by **± 5.0 %** from that calculated value provided the surface
formula holds. The same floor appears in MB 10 (2022 printing, English printed p. 7 §3; French printed
p. 9: « La hauteur minimale de la chambre de combustion en cm est de 25 + charge max. de bois »).

Nothing in this codebase compares a modelled chamber's surface against `900·m_B`. The identity exists
in exactly one evaluated form — the height band: `H_BR_calc` at
`FireboxFormulas_Strict.scala:108-114`, the `± 5 %` band around it at
`FireboxConstraints_Strict.scala:214-235`. Both halves of §4.3.1.4 are therefore already computed and
reported:

- the floor is emitted as a bound the caller can read, `TermConstraint.Min(ctx.H_BR_min)` at
  `FireboxConstraints_Strict.scala:211-212`;
- the band is emitted as a range, and it is *evaluated* — but unmarked, so a chamber outside it is
  reported `Satisfied` (`FoldsConstraints.scala:165-181`: only a constraint marked through
  `TermConstraint.Evaluatable.withEvaluated` can produce a violation).

So on the calculation-server path the surface identity is currently described and never enforced, while
on the desktop path the same two constraints are graded regardless of any marker
(`termdef.scala:313-319`, `en15544_common_constraints.scala:115-116`) — two paths, two answers for one
chamber. The Austrian kind adds a third answer: it enforces the height floor again as its own check,
`EcolabeledConstraints.scala:153-174`, with the same arithmetic as the shared term.

## Decision

1. **The band is enforced, two-sided and blocking.** `H ∈ H_BR_calc ± 5 %` stays exactly as it is
   computed, and is marked `Blocking` in the shared strict owner
   (`FireboxConstraints_Strict.scala:207-238`), so every kind that carries the H_BR terms is graded.
   A kind that must not carry them drops the terms entirely, as it already does
   (`FireboxConstraints.scala:265-268`); there is no per-kind softening of the grading.
2. **The floor is enforced through the same owner, marked `Blocking`.** The bound-only form cannot be
   graded — its embedded value is its own bound, so evaluating it would be a self-check
   (`termdef.scala:325-339`) — so the slot becomes value-carrying: it embeds the specified height,
   compares it with `ctx.H_BR_min`, and reports through the existing
   `TermValueShouldBeGreaterOrEqThan` message (`EN15544_Error.scala:228-231`, `errors.term_should_be_greater_or_eq_than`).
   The `ConstraintDescriptor` keeps the minimum, read from the context in the fold
   (`FoldsConstraints.scala:388-400`).
3. **One owner per rule.** The Austrian kind's own copy of the floor is deleted; the shared term is the
   only place `H ≥ 25 cm + m_B` is enforced.
4. **The identity is not implemented as a separate surface comparison.** The band is its evaluated
   form; a second check on `O_BR` would grade the same requirement twice and could disagree with the
   band on rounding alone.
5. **The `± 5 cm` variant is not implemented.** The tolerance is the standard's percentage, not a
   fixed length.

## Consequences

- The calculation server starts rejecting chambers it accepted. A height below the band says the
  chamber is too short for its charge; a height above it says the chamber is larger than its charge
  justifies. Both directions come out of the same two-sided comparison, which is why the question of
  whether the identity is a charge ceiling or a charge floor does not have to be settled in order to
  enforce it.
- The desktop grades are unchanged: that path already runs these constraints and grades them
  unconditionally, so this record removes a divergence rather than creating one. The message text
  does move: the below-floor finding renders through the `≥` template instead of the `Min` template
  (same information — term, entered value, minimum), and the English band message's third
  placeholder now says "entered value" instead of "computed value".
- A chamber can now receive two findings at once — the floor and the band, when a height is below both.
  They are different requirements (`25 cm + m_B` is not derived from the surface formula) and each needs
  its own fix.
- A request that does not carry both the charge and the required efficiency builds no constraint
  context, and the H_BR terms then report `NotEvaluated` with reason `missingMB`
  (`FireboxCalcCore.scala:169-188`, `FoldsConstraints.scala:280-289`). Neither term needs the
  efficiency — the context is built as one unit (`FireboxConstraints.scala:31-48`) — so a request that
  omits only the efficiency stops enforcing an uncontested requirement. No shipped sender omits it, and
  splitting the context is not part of this record.
- Fixtures whose specified height sits far from the height their charge implies become invalid and must
  be re-charged. That is the requirement working as intended, but it is a visible change to expected
  verdict lists and to any baseline indexed on those fixtures.
- Grading is compiled in, not configured: relaxing the band for one kind later means an override of
  `h_br_constraints` for that kind, and a rule that must not be graded at all cannot be marked.
- The published engine stamp and the pin that consumes it both move; the marker is in shared code.

## Evidence

- EN 15544:2023: §4.3.1.2 formula (3), printed p. 11 (`O_BR = 900 · m_B`); §4.3.1.4, printed p. 12
  (the floor, the calculated height, and the `± 5.0 %` tolerance on the specified height).
- MB 10, 2022 printing: English printed p. 7 §3, French printed p. 9 — the same height floor. MB 17
  carries no such floor.
- Code as it stands: `FireboxConstraints_Strict.scala:207-238` (both terms, unmarked);
  `FireboxFormulas_Strict.scala:105-114` (floor and calculated height);
  `FireboxSizing_15544_Common.scala:58-68` (threading them into the context);
  `FoldsConstraints.scala:165-181,385-416` (a marker is the only route to a violation; the two H_BR
  slots and their descriptors); `FoldsConstraints.scala:280-289` (the no-context branch);
  `termdef.scala:97-113,254-275,325-345` (`MinError` exists, and bound-only terms cannot carry a
  marker); `en15544_common_constraints.scala:115-116` and `termdef.scala:313-319` (the desktop path
  grades regardless of the marker); `EcolabeledConstraints.scala:92,153-174` (the duplicate this record
  removes).
