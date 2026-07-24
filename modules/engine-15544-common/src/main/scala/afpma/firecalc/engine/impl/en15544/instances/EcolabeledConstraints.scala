/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

import cats.Show
import cats.syntax.all.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

import scala.collection.mutable.ListBuffer

/**
 * EN 15544 constraints for [[Ecolabeled]] fireboxes.
 *
 * Extends the default constraint set, overriding m_B bounds (min=6kg,
 * max=40kg), m_B_min (min=6kg), and specific constraint validation.
 */
given ecolabeledConstraints: FireboxConstraints[Ecolabeled] =
    new FireboxConstraints_Strict[Ecolabeled]:

        /** Ecolabeled: min=6kg, max=40kg. */
        override def m_B_constraints(
            firebox : Ecolabeled,
            ctx     : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            Seq(
                Some(TermConstraint.Min[m_B](6.kg) ),
                Some(TermConstraint.Max[m_B](40.kg))
            )

        /** Ecolabeled: min=6kg. */
        override def m_B_min_constraints(
            firebox: Ecolabeled,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B_min]]] =
            Seq(Some(TermConstraint.Min[m_B_min](6.kg)))

        /** Ecolabeled-specific structural and dimensional constraints. */
        override def firebox_custom_constraints(
            firebox: Ecolabeled,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            import ctx.*
            val buf = new ListBuffer[FireboxError]()
            val mass: Mass = mB

            if (firebox.height_of_first_row_of_air_injectors_X < 5.cm)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.height_of_first_row_of_air_injectors_X),
                        firebox.height_of_first_row_of_air_injectors_X,
                        5.cm
                    )
                )

            if (mass < 6.kg)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(LocalizedString.from(I18N.en15544.terms.m_B.name), mass, 6.kg)
                )
            if (mass > 40.kg)
                buf.append(
                    TermValueShouldBeLessOrEqThan(LocalizedString.from(I18N.en15544.terms.m_B.name), mass, 40.kg)
                )

            val (d1, d2)         = (
                firebox.inner_wall_thickness_D1,
                firebox.outer_wall_thickness_D2
            )
            val (d1_min, d2_min) =
                if (6.kg <= mass && mass < 12.9.kg) (4.cm, 5.cm)
                else (5.cm, 5.cm)

            if (d1 < d1_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.inner_wall_thickness_D1),
                        d1.to_cm,
                        d1_min.to_cm
                    )
                )
            if (d2 < d2_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.outer_wall_thickness_D2),
                        d2.to_cm,
                        d2_min.to_cm
                    )
                )

            val w     = firebox.air_manifold_height_W
            val w_min = 5.cm
            if (w < w_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.air_manifold_height_W),
                        w.to_cm,
                        w_min.to_cm
                    )
                )

            val A     = firebox.firebox_width_A
            val B     = firebox.firebox_depth_B
            val ratio = (A / B).value
            if (!((0.5 <= ratio) && (ratio <= 2.0)))
                buf.append(
                    TermValueShouldBeBetweenInclusive(
                        LocalizedString.from(I18N.firebox.traditional.width_to_depth_ratio),
                        ratio,
                        minValue = 0.5,
                        maxValue = 2.0
                    )
                )

            val AF     = firebox.ash_pit_height_AF
            val AF_min = 5.cm
            val AF_max = 12.cm
            if (AF < AF_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.ash_pit_height_AF),
                        AF.to_cm,
                        AF_min.to_cm
                    )
                )
            if (AF > AF_max)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.ash_pit_height_AF),
                        AF.to_cm,
                        AF_max.to_cm
                    )
                )

            val H_min = (25.cm.value + mass.toUnit[Kilogram].value).cm
            val H     = firebox.firebox_height_H
            if (H < H_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.firebox_height_H),
                        H.to_cm,
                        H_min.to_cm
                    )
                )

            val Ls = firebox.injector_width_side_wall_Ls
            val Lr = firebox.injector_width_rear_wall_Lr

            val max_side_reinforcement_width = Ls * 0.2
            if (firebox.width_between_two_air_columns_sides_E > max_side_reinforcement_width)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.width_between_two_air_columns_sides_E),
                        firebox.width_between_two_air_columns_sides_E.to_cm,
                        max_side_reinforcement_width.to_cm
                    )
                )

            val max_rear_reinforcement_width = Lr * 0.2
            if (firebox.width_between_two_air_columns_rear_E > max_rear_reinforcement_width)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.width_between_two_air_columns_rear_E),
                        firebox.width_between_two_air_columns_rear_E.to_cm,
                        max_rear_reinforcement_width.to_cm
                    )
                )

            // TOCHECK
            val max_corner_offset = 4.5.cm
            for (rxName, rx) <- Seq(
                    (
                        LocalizedString.from(I18N.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R1),
                        firebox.reinforcement_bars_offset_in_corners_R1
                    ),
                    (
                        LocalizedString.from(I18N.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R2),
                        firebox.reinforcement_bars_offset_in_corners_R2
                    ),
                    (
                        LocalizedString.from(I18N.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R3),
                        firebox.reinforcement_bars_offset_in_corners_R3
                    )
                )
            yield
                if (rx > max_corner_offset)
                    buf.append(
                        TermValueShouldBeLessOrEqThan(
                            rxName,
                            rx.to_cm,
                            max_corner_offset.to_cm
                        )
                    )

            val Z_MIN = 6.mm
            val Z_MAX = 8.mm
            if (firebox.injector_height_Z < Z_MIN)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.injector_height_Z),
                        firebox.injector_height_Z.to_mm,
                        Z_MIN.to_mm
                    )
                )
            if (firebox.injector_height_Z > Z_MAX)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        LocalizedString.from(I18N.firebox.ecolabeled.injector_height_Z),
                        firebox.injector_height_Z.to_mm,
                        Z_MAX.to_mm
                    )
                )

            val injector_width_sum                    = 2 * Ls + Lr
            val total_reinforcement_obstruction_width =
                2 * firebox.width_between_two_air_columns_sides_E + firebox.width_between_two_air_columns_rear_E
            val obstruction_ratio                     = (total_reinforcement_obstruction_width / injector_width_sum)
            val obstruction_ratio_max                 = 0.20
            if (obstruction_ratio > obstruction_ratio_max)
                buf.append(
                    TermValueCustom(
                        LocalizedString.from(I18N.firebox.ecolabeled.injector_surface_area),
                        obstruction_ratio,
                        LocalizedString.from(
                            I18N.firebox.ecolabeled
                                .injector_surface_area_obstructed_max_perc(obstruction_ratio.toPercent.showP)
                        )
                    )
                )

            buf.toList

end ecolabeledConstraints
