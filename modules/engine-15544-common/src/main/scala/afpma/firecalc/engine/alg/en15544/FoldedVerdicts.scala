/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.standard.FireboxErrorCustom

import afpma.firecalc.firebox_calc_api.ConstraintOutcome
import afpma.firecalc.firebox_calc_api.ConstraintTerm
import afpma.firecalc.firebox_calc_api.ConstraintVerdict

/**
 * Reads already-folded [[ConstraintVerdict]]s back as `FireboxError`s.
 *
 * Needed on the one path where the per-term [[FireboxConstraints]] algebra is
 * absent — [[FireboxEngineFor.constraintsAlg]] is `None`, i.e. a
 * [[RemoteBackedFireboxEngine]]. The per-term detail did not cross the wire,
 * but the *verdicts* did, and they are authoritative. Validation therefore
 * reports them instead of reporting nothing: a violated remote constraint
 * still fails, and a satisfied one is never invented.
 *
 * The split mirrors how [[FoldsConstraints.constraints]] produced the verdicts
 * in the first place: terms 1–9 come from the cited §4.3 per-term methods,
 * terms 10–11 from `firebox_custom_constraints` /
 * `internal_firebox_custom_constraints`. Reading them back along the same seam
 * keeps each validator reporting exactly what it would have reported locally.
 */
object FoldedVerdicts:

    /** Terms produced by the firebox-specific custom-constraint methods (10, 11). */
    private def isFireboxSpecific(term: ConstraintTerm): Boolean =
        term match
            case ConstraintTerm.AirIntakePipeShape    => true
            case ConstraintTerm.NominalLoadInRange    => true
            case ConstraintTerm.Custom                => true
            case ConstraintTerm.MB                    => false
            case ConstraintTerm.MBMin                 => false
            case ConstraintTerm.GlassArea             => false
            case ConstraintTerm.FireboxBase           => false
            case ConstraintTerm.H_BR                  => false
            case ConstraintTerm.Lambda                => false
            case ConstraintTerm.HeightOfLowestOpening => false
            case ConstraintTerm.GlassSurfaceRatio     => false

    /**
     * The blocking violations among `verdicts`, as `FireboxError`s.
     *
     * `Satisfied` and `NotApplicable` yield nothing — the constraint was
     * evaluated and passed or did not apply. `NotEvaluated` also yields
     * nothing, matching the local path, where a term whose value is absent
     * contributes no error (`CheckableConstraint.vresultOption` is `None`).
     * Non-blocking violations are warnings, surfaced by the verdict list the
     * UI renders, not by validation.
     *
     * `message` was localised by the side that folded the verdict, so it is
     * passed through rather than re-translated.
     */
    private def violations(verdicts: List[ConstraintVerdict]): List[FireboxError] =
        verdicts.collect:
            case ConstraintVerdict(_, _, _, ConstraintOutcome.Violated(_, message, true)) =>
                FireboxErrorCustom(message)

    /** Blocking violations of the cited §4.3 terms (verdict terms 1–9). */
    def citedErrors(verdicts: List[ConstraintVerdict]): List[FireboxError] =
        violations(verdicts.filterNot(v => isFireboxSpecific(v.term)))

    /** Blocking violations of the firebox-specific terms (verdict terms 10–11). */
    def fireboxSpecificErrors(verdicts: List[ConstraintVerdict]): List[FireboxError] =
        violations(verdicts.filter(v => isFireboxSpecific(v.term)))

end FoldedVerdicts
