/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.TypeOfAppliance

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.RemovedFireboxSizingConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.SingleTested
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ
import afpma.firecalc.engine.standard.*

/**
 * EN 15544 constraints for [[SingleTested]] fireboxes.
 *
 * Clause 4.3.1 does not apply to tested fireboxes, so all §4.3.1.x sizing
 * constraints are removed via [[RemovedFireboxSizingConstraints]].
 * Only m_B bounds (from type-test data) and λ bounds are retained.
 */
given singleTestedConstraints: (FireboxConstraints[SingleTested] & RemovedFireboxSizingConstraints[SingleTested]) =
    new FireboxConstraints_Strict[SingleTested] with RemovedFireboxSizingConstraints[SingleTested]:

        // ── 4.2.1 – m_B: from type-test data ─────────────────────────────────
        override def m_B_constraints(
            firebox: SingleTested,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            val minConstraint: Option[TermConstraint[m_B]] =
                firebox.minimumFuelMass.map(TermConstraint.Min.apply)
            val maxConstraint: Option[TermConstraint[m_B]] =
                Some(TermConstraint.Max(firebox.maximumFuelMass))

            val maxShouldEqualStoveParamsNominal: Option[TermConstraint[m_B]] =
                Some(
                    TermConstraint.GenericTyped(
                        firebox.maximumFuelMass,
                        fbMax =>
                            Either.cond(
                                fbMax == ctx.m_B,
                                fbMax,
                                InconsistentMaxLoadAccrossInputs(
                                    stoveParamsValue = ctx.m_B,
                                    fireboxValue     = fbMax,
                                    loc => firebox.firebox_type(using loc)
                                )
                            )
                    )
                )

            Seq(minConstraint, maxConstraint, maxShouldEqualStoveParamsNominal)

        // ── 4.2.2 – m_B_min: not constrained for SingleTested ────────────────
        override def m_B_min_constraints(
            firebox: SingleTested,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B_min]]] = Seq.empty

        // ── 4.5 – λ: SingleTested-specific bounds ────────────────────────────
        override def lambda_constraints(
            firebox: SingleTested,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[λ]]] =
            Seq(
                Some(TermConstraint.Min(1.95.unitless)),
                Some(TermConstraint.Max(3.95.unitless))
            )

        def pellets_load_burn_duration_constraints(
            firebox: SingleTested
        ): Seq[Option[TermConstraint[t_BU]]] =
            if (firebox.type_of_appliance == TypeOfAppliance.Pellets)
                Seq(
                    Some(TermConstraint.Min(58.minutes)),
                    Some(TermConstraint.Max(98.minutes))
                )
            else
                Seq.empty

        // ── 4.10.3 – η: same as default (empty, application layer handles it)
        override def eta_constraints(
            firebox: SingleTested,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[η]]] = Seq.empty

        // ── Glass surface ratio: use firebox-level field ──────────────────────
        override def firebox_glass_surface_ratio_constraint(
            firebox: SingleTested
        ): Option[TermConstraint[Unit]] =
            import afpma.firecalc.engine.models.en15544.typedefs.given_TermDefDetails_Unit
            Some(
                TermConstraint.GenericTyped[Unit, GlassSurfaceRatioNotConfirmed]  (
                    value   = (),
                    isValid = _ =>
                        if firebox.is_glass_surface_ratio_below_one_fifth
                        then Right(())
                        else Left(GlassSurfaceRatioNotConfirmed())
                )
            )

        // ── No firebox-specific constraints ───────────────────────────────────
        override def firebox_custom_constraints(
            firebox: SingleTested,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            AllTermConstraints(pellets_load_burn_duration_constraints(firebox))
                .checkAllAndCombineWhenDefined(firebox.pellets_load_burn_duration)
                .map(_.foldToErrDeep(InvalidFireboxConstraint.apply)) match
                case Some(xs) => xs
                case None     => Nil
