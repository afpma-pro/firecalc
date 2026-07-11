/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.IsDirectionChange
import afpma.firecalc.domain.IsPressureDiff
import afpma.firecalc.domain.IsSectionGeometryChange
import afpma.firecalc.domain.IsSingularFlowResistance
import afpma.firecalc.domain.IsSplitMergeTurn
import afpma.firecalc.domain.IsZeroLengthPipeElement
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange.AngleVifDe0A180
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange.CircularArc60
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*

import cats.Show
import cats.derived.*
import cats.syntax.all.*

import coulomb.policy.standard.given

object FlowOnlyPipeDescr_15544 extends afpma.firecalc.engine.models.PipeDescrAlg:
    ctx =>

    given showPipeElDescr   : Show[PipeElDescr]      = cats.derived.semiauto.show
    override given hasLength: HasLength[PipeElDescr] = new HasLength[PipeElDescr] {
        extension (a: PipeElDescr)
            def length: Length =
                val ZERO = 0.0.meters
                a match
                    case StraightSection(length, _, _, _) => length
                    case _: IsZeroLengthPipeElement => ZERO
    }

    override given hasVerticalElev: HasVerticalElev[PipeElDescr]:
        extension (p: PipeElDescr)
            def verticalElev =
                val ZERO = 0.0.meters
                p match
                    case x: StraightSection         => x.elevation_gain
                    case _: IsZeroLengthPipeElement => ZERO

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr]:
        extension (el: PipeElDescr)
            def innerShape(oPrevGeom: Option[PipeShape]): Option[PositionOp[PipeShape]] =

                def makeQtyAtPositionForGeometryTransition(
                    from: PipeShape,
                    to  : PipeShape
                ): Option[PositionOp[PipeShape]] =
                    QtyDAtPosition
                        .from (
                            start  = from,
                            middle = {
                                (from, to) match
                                    case (Circle(from), Circle(to)                    ) => Circle((from + to) / 2.0)
                                    case (Square(from), Square(to)                    ) => Square((from + to) / 2.0)
                                    case (Rectangle(fromA, fromB), Rectangle(toA, toB)) =>
                                        Rectangle((fromA + toA) / 2.0, (fromB + toB) / 2.0)
                                    case (Square(from), Rectangle(toA, toB)           ) =>
                                        Rectangle((from + toA) / 2.0, (from + toB) / 2.0)
                                    case (Rectangle(fromA, fromB), Square(to)         ) =>
                                        Rectangle((fromA + to) / 2.0, (fromB + to) / 2.0)
                                    case (from, to                                    ) =>
                                        Circle((from.dh + to.dh) / 2.0)
                            },
                            // throw new IllegalStateException(s"pipe shape transition not implemented : '${from} -> ${to}"),
                            end    = to
                        )
                        .some
                        .map(_.atPos)

                el match
                    case s : StraightSection         =>
                        QtyDAtPosition.constant(s.geometry).some.map(_.atPos)
                    case s : SectionGeometryChange   =>
                        makeQtyAtPositionForGeometryTransition(s.from, s.to)
                    case dc: DirectionChange         =>
                        QtyDAtPosition.constant(dc.effectiveShape).some.map(_.atPos)
                    case SingularFlowResistance(_, Some(crossSection)) =>
                        val equivCircle = Circle.fromArea(crossSection)
                        QtyDAtPosition.constant(equivCircle).some.map(_.atPos)
                    case PressureDiff(_, Some(crossSection)) =>
                        val equivCircle = Circle.fromArea(crossSection)
                        QtyDAtPosition.constant(equivCircle).some.map(_.atPos)
                    case _ : IsZeroLengthPipeElement =>
                        oPrevGeom.map(prevGeom => QtyDAtPosition.constant(prevGeom).atPos)

    type NotPressureDiff = StraightSection | DirectionChange | SectionGeometryChange | SingularFlowResistance

    sealed trait PipeElDescr extends Matchable

    case class StraightSection(
        length        : QtyD[Meter],
        geometry      : PipeShape,
        roughness     : Roughness,
        elevation_gain: QtyD[Meter]
    ) extends PipeElDescr derives Show {
        def approx_elevation_gain_at(l: Length): Length =
            // interpolate as a (reasonable ?) approximation
            // should be true for straight section
            elevation_gain * l / length
    }

    sealed abstract class DirectionChange(
        val angleN1       : QtyD[Degree],
        val angleN2       : Option[QtyD[Degree]],
        val effectiveShape: PipeShape
    ) extends PipeElDescr
        with IsDirectionChange derives Show

    object DirectionChange:
        def angleVifZero(effectiveShape: PipeShape): AngleVifDe0A180 =
            AngleVifDe0A180(0.0.degrees, None, effectiveShape)

        case class AngleVifDe0A180(
            α                          : Angle,
            override val angleN2       : Option[Angle] = None,
            override val effectiveShape: PipeShape
        ) extends DirectionChange(α, angleN2, effectiveShape) derives Show

        case class CircularArc60(
            override val effectiveShape: PipeShape
        ) extends DirectionChange(60.degrees, None, effectiveShape) derives Show

    /**
     * Direction change that splits a single flow into two flows (or merges two flows into one)
     * with a 90-degree turn.
     *
     * @param nFlows number of flows after the split/merge
     * @param zeta   legacy field; retained for compatibility but no longer used as fallback.
     *               The actual zeta is resolved by position in the pipe chain:
     *               - Start (first element after firebox): 0.0
     *               - Mid-chain: FWindow neighbor-dependent computation (~1.4)
     *               - End (FWindow fails): 1.4 (CoefficientOfFlowResistance.splitMerge90Zeta)
     * @param offset offset from the junction point
     * @param angleN2 optional second angle
     * @param effectiveShape pipe shape at the direction change
     */
    case class SplitMerge90(
        nFlows                     : NbOfFlows,
        offset                     : Length               = 0.meters,
        override val angleN2       : Option[QtyD[Degree]] = None,
        override val effectiveShape: PipeShape
    ) extends DirectionChange(90.degrees, angleN2, effectiveShape)
        with IsSingularFlowResistance
        with IsSplitMergeTurn derives Show

    case class SectionGeometryChange(
        from: PipeShape,
        to  : PipeShape
    ) extends PipeElDescr
        with IsSectionGeometryChange derives Show

    case class SingularFlowResistance(zeta: ζ, crossSectionO: Option[Area])
        extends PipeElDescr
        with IsSingularFlowResistance derives Show
    case class PressureDiff(pa: QtyD[Pascal], crossSectionO: Option[Area]) extends PipeElDescr with IsPressureDiff
        derives Show
