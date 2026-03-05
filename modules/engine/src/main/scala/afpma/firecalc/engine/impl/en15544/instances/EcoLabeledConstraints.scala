/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

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

import io.taig.babel.Locale

/** EN 15544 constraints for [[Ecolabeled]] fireboxes.
 *
 * Extends the default constraint set, overriding m_B bounds (min=6kg,
 * max=40kg), m_B_min (min=6kg), and specific constraint validation.
 */
given ecoLabeledConstraints: FireboxConstraints[Ecolabeled] =
    new FireboxConstraints_Strict[Ecolabeled]:

        /** Ecolabeled: min=6kg, max=40kg. */
        override def m_B_constraints(
            firebox: Ecolabeled,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            Seq(
                Some(TermConstraint.Min[m_B](6.kg)),
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
        )(using Locale): List[FireboxError] =
            import ctx.*
            val buf = new ListBuffer[FireboxError]()
            val mass: Mass = mB

            if (firebox.h83_hauteurEntreLaSoleEtLe1erInjecteur_X < 5.cm)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.height_of_first_row_of_air_injectors_X,
                        firebox.h83_hauteurEntreLaSoleEtLe1erInjecteur_X,
                        5.cm
                    )
                )

            if (mass < 6.kg)
                buf.append(TermValueShouldBeGreaterOrEqThan(I18N.en15544.terms.m_B.name, mass, 6.kg))
            if (mass > 40.kg)
                buf.append(TermValueShouldBeLessOrEqThan(I18N.en15544.terms.m_B.name, mass, 40.kg))

            val (d1, d2) = (
                firebox.h77_epaisseurParoiInterneFoyer_D1,
                firebox.epaisseurParoiExterneFoyer_D2
            )
            val (d1_min, d2_min) =
                if (6.kg <= mass && mass < 12.9.kg) (4.cm, 5.cm)
                else (5.cm, 5.cm)

            if (d1 < d1_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.inner_wall_thickness_D1,
                        d1.to_cm,
                        d1_min.to_cm
                    )
                )
            if (d2 < d2_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.outer_wall_thickness_D2,
                        d2.to_cm,
                        d2_min.to_cm
                    )
                )

            val w     = firebox.h75_hauteurArriveeConduitAir_DessousSoleFoyer_W
            val w_min = 5.cm
            if (w < w_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.air_manifold_height_W,
                        w.to_cm,
                        w_min.to_cm
                    )
                )

            val A     = firebox.h12_largeurDuFoyer
            val B     = firebox.h11_profondeurDuFoyer
            val ratio = (A / B).value
            if (!((0.5 <= ratio) && (ratio <= 2.0)))
                buf.append(
                    TermValueShouldBeBetweenInclusive(
                        I18N.firebox.traditional.width_to_depth_ratio,
                        ratio,
                        minValue = 0.5,
                        maxValue = 2.0
                    )
                )

            val AF     = firebox.h74_hauteur_de_cendrier_AF
            val AF_min = 5.cm
            val AF_max = 12.cm
            if (AF < AF_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.ash_pit_height_AF,
                        AF.to_cm,
                        AF_min.to_cm
                    )
                )
            if (AF > AF_max)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        I18N.firebox.ecolabeled.ash_pit_height_AF,
                        AF.to_cm,
                        AF_max.to_cm
                    )
                )

            val H_min = (25.cm.value + mass.toUnit[Kilogram].value).cm
            val H     = firebox.h13_hauteurDuFoyer
            if (H < H_min)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(I18N.firebox.firebox_height, H.to_cm, H_min.to_cm)
                )

            val c19  = firebox.c19_largeurDesInjecteursLateraux
            val c20  = firebox.c20_largeurDesInjecteursArrieres
            val c14  = firebox.c14_debordDesRenfortsDansLesAngles

            val largeurRenfortMedianLateraux_max = c19 * 0.2
            if (firebox.h79_largeurRenfortMedianLateraux > largeurRenfortMedianLateraux_max)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        I18N.firebox.ecolabeled.width_between_two_air_columns_sides_E,
                        firebox.h79_largeurRenfortMedianLateraux.to_cm,
                        largeurRenfortMedianLateraux_max.to_cm
                    )
                )

            val largeurRenfortMedianArriere_max = c20 * 0.2
            if (firebox.h80_largeurRenfortMedianArriere > largeurRenfortMedianArriere_max)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        I18N.firebox.ecolabeled.width_between_two_air_columns_rear_E,
                        firebox.h80_largeurRenfortMedianArriere.to_cm,
                        largeurRenfortMedianArriere_max.to_cm
                    )
                )

            val DEBORD_MAX = 4.5.cm
            if (c14 > DEBORD_MAX)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        I18N.firebox.ecolabeled.reinforcement_bars_offset_in_corners,
                        c14.to_cm,
                        DEBORD_MAX.to_cm
                    )
                )

            val Z_MIN = 6.mm
            val Z_MAX = 8.mm
            if (firebox.h82_hauteurDesInjecteurs_Z < Z_MIN)
                buf.append(
                    TermValueShouldBeGreaterOrEqThan(
                        I18N.firebox.ecolabeled.injector_height_Z,
                        firebox.h82_hauteurDesInjecteurs_Z.to_mm,
                        Z_MIN.to_mm
                    )
                )
            if (firebox.h82_hauteurDesInjecteurs_Z > Z_MAX)
                buf.append(
                    TermValueShouldBeLessOrEqThan(
                        I18N.firebox.ecolabeled.injector_height_Z,
                        firebox.h82_hauteurDesInjecteurs_Z.to_mm,
                        Z_MAX.to_mm
                    )
                )

            val largeurFenteAirFoyer       = 2 * c19 + c20
            val largeurObstructionAirFoyer =
                2 * firebox.h79_largeurRenfortMedianLateraux + firebox.h80_largeurRenfortMedianArriere
            val ratioSurfaceFenteAir     = (largeurObstructionAirFoyer / largeurFenteAirFoyer)
            val ratioSurfaceFenteAir_max = 0.20
            if (ratioSurfaceFenteAir > ratioSurfaceFenteAir_max)
                buf.append(
                    TermValueCustom(
                        I18N.firebox.ecolabeled.injector_surface_area,
                        ratioSurfaceFenteAir,
                        I18N.firebox.ecolabeled
                            .injector_surface_area_obstructed_max_perc(ratioSurfaceFenteAir.toPercent.showP)
                    )
                )

            buf.toList

end ecoLabeledConstraints
