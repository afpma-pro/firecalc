/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.RemovedFireboxSizingConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog.SB
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.standard.InvalidFireboxConstraint

import io.taig.babel.Locale

/** EN 15544 constraints for [[Door15aFirebox_Catalog]] fireboxes.
 *
 * Extends the default constraint set.
 */
given door15aCatalogConstraints: FireboxConstraints[Door15aFirebox_Catalog] =
    new FireboxConstraints_Strict[Door15aFirebox_Catalog] with RemovedFireboxSizingConstraints[Door15aFirebox_Catalog]:

        /** Door15aFirebox_Catalog: 5cm <= AF <= 12cm */
        override def height_of_lowest_opening_constraints(
            firebox: Door15aFirebox_Catalog, 
            ctx: ConstraintContext
        ): Seq[Option[TermConstraint[height_of_lowest_opening]]] = 
            Seq(
                Some(TermConstraint.Min(5.cm)),
                Some(TermConstraint.Max(12.cm))
            )

        override def glassArea_constraints(
            firebox: Door15aFirebox_Catalog,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[GlassArea]]] =
            Seq(Some(glassArea_constraint_oneFifth_maxRatio(firebox, ctx)))

        override def m_B_constraints(
            firebox: Door15aFirebox_Catalog,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            Seq(
                Some(TermConstraint.Min(10.kg)), 
                Some(TermConstraint.Max(25.kg))
            )

        val s_B_min: SB = 1.0.cm.to_cm
        val s_B_max: SB = 4.0.cm.to_cm

        def s_B_constraints(firebox: Door15aFirebox_Catalog): AllTermConstraints[SB] =
            AllTermConstraints:
                Seq(
                    Some(TermConstraint.Min(s_B_min)),
                    Some(TermConstraint.Max(s_B_max))
                )

        override def firebox_custom_constraints(
            firebox  : Door15aFirebox_Catalog,
            mB       : m_B,
            flow_rate: Option[VolumeFlow]
        )(using Locale): List[FireboxError] =
            s_B_constraints(firebox).checkAllAndCombine(firebox.sb).foldToErrDeep(InvalidFireboxConstraint.apply)
