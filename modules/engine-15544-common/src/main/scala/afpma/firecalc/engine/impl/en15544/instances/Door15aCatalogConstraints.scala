/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog.SB
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.AirIntakePipeShapeMismatch
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.standard.InvalidFireboxConstraint

import cats.syntax.all.*

/**
 * EN 15544 constraints for [[Door15aFirebox_Catalog]] fireboxes.
 *
 * Extends the default constraint set.
 */
given door15aCatalogConstraints: FireboxConstraints[Door15aFirebox_Catalog] =
    new FireboxConstraints_Strict[Door15aFirebox_Catalog]:

        /** Door15aFirebox_Catalog: 5cm <= AF <= 12cm */
        override def height_of_lowest_opening_constraints(
            firebox : Door15aFirebox_Catalog,
            ctx     : ConstraintContext
        ): Seq[Option[TermConstraint[height_of_lowest_opening]]] =
            Seq(
                Some(TermConstraint.Min(5.cm) ),
                Some(TermConstraint.Max(12.cm))
            )

        override def glassArea_constraints(
            firebox: Door15aFirebox_Catalog,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[GlassArea]]] =
            Seq(Some(glassArea_constraint_oneFifth_maxRatio(firebox, ctx)))

        private val DEFAULT_MB_MIN: Mass = 10.kg
        private val DEFAULT_MB_MAX: Mass = 25.kg

        override def m_B_constraints(
            firebox: Door15aFirebox_Catalog,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[m_B]]] =
            Seq(
                Some(TermConstraint.Min(firebox.mb_min.getOrElse(DEFAULT_MB_MIN))),
                Some(TermConstraint.Max(firebox.mb_max.getOrElse(DEFAULT_MB_MAX)))
            )

        private val DEFAULT_SB_MIN: SB = 1.0.cm.to_cm
        private val DEFAULT_SB_MAX: SB = 4.0.cm.to_cm

        private def s_B_constraints(firebox: Door15aFirebox_Catalog): AllTermConstraints[SB] =
            AllTermConstraints:
                Seq(
                    Some(TermConstraint.Min(firebox.sb_min.getOrElse(DEFAULT_SB_MIN))),
                    Some(TermConstraint.Max(firebox.sb_max.getOrElse(DEFAULT_SB_MAX)))
                )

        /** Check: declared shape is among manufacturer expected shapes (tolerance-aware). */
        private def checkCatalogShapeMatch(actual: PipeShape, expected: List[PipeShape]): Option[FireboxError] =
            if expected.exists(_.equalsTolerance(actual)) then None
            else
                Some(
                    AirIntakePipeShapeMismatch(
                        expected = expected.map(_.show).mkString(", "),
                        actual   = actual.show
                    )
                )

        /** Check: declared shape matches topology-computed end shape. */
        private def checkTopologyShapeMatch(actual: PipeShape, computed: PipeShape): Option[FireboxError] =
            if actual.equalsTolerance(computed) then None
            else
                Some(
                    AirIntakePipeShapeTopologyMismatch(
                        declared = actual.show,
                        computed = computed.show
                    )
                )

        /** Validate air intake pipe shape against catalog and topology. */
        private def airIntakePipeShapeConstraint(
            firebox: Door15aFirebox_Catalog,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            val actual   = firebox.actualAirIntakePipeShape
            val catalog  = checkCatalogShapeMatch(actual, firebox.expectedAirIntakePipeShapes)
            val topology = ctx.airIntakePipeShape.flatMap(checkTopologyShapeMatch(actual, _))
            catalog.toList ++ topology.toList

        override def firebox_custom_constraints(
            firebox: Door15aFirebox_Catalog,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            val sbErrors        =
                s_B_constraints(firebox).checkAllAndCombine(firebox.sb).foldToErrDeep(InvalidFireboxConstraint.apply)
            val pipeShapeErrors = airIntakePipeShapeConstraint(firebox, ctx)
            sbErrors ::: pipeShapeErrors
