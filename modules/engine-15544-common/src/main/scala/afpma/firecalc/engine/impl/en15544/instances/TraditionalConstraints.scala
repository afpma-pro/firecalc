/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

/**
 * EN 15544 constraints for [[TraditionalFirebox]] fireboxes.
 *
 * Extends the default constraint set, overriding only m_B bounds (min=10kg,
 * max=40kg) and specific constraint validation (injector air velocity check).
 */
given traditionalConstraints: FireboxConstraints[TraditionalFirebox] =
    new FireboxConstraints_Strict[TraditionalFirebox]:

        /** Traditional: min=10kg, max=40kg. */
        override def m_B_constraints(
            firebox: TraditionalFirebox,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            Seq(
                Some(TermConstraint.Min[m_B](10.kg)),
                Some(TermConstraint.Max[m_B](40.kg))
            )

        /** Validates that the injector air velocity is within [2, 4] m/s. */
        override def firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            import ctx.*
            flow_rate match
                case None            => MissingFlowRate :: Nil
                case Some(flow_rate) =>
                    val injection_velocity_rate    =
                        flow_rate / firebox.h67_sectionCumuleeEntreeAirPorte
                    val injector_velocity_rate_min = 2.m_per_s
                    val injector_velocity_rate_max = 4.m_per_s
                    if      (injection_velocity_rate < injector_velocity_rate_min)
                        InjectorVelocityBelowMinimum(
                            injection_velocity_rate.showP,
                            injector_velocity_rate_min.showP
                        ) :: Nil
                    else if (injection_velocity_rate > injector_velocity_rate_max)
                        InjectorVelocityAboveMaximum(
                            injection_velocity_rate.showP,
                            injector_velocity_rate_max.showP
                        ) :: Nil
                    else
                        Nil
