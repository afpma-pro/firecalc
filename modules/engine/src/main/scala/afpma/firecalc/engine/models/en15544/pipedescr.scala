/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import cats.Show
import cats.derived.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange.AngleVifDe0A180
import afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange.CircularArc60
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given

object pipedescr extends afpma.firecalc.engine.models.PipeDescrAlg:
    ctx =>

    given showPipeElDescr: Show[PipeElDescr] = cats.derived.semiauto.show
    override given hasLength: HasLength[PipeElDescr] = new HasLength[PipeElDescr] {
        extension (a: PipeElDescr) def length: Length = 
            val ZERO = 0.0.meters
            a match
                case StraightSection(length, _, _, _) => length
                case AngleVifDe0A180(_, _)            => ZERO
                case CircularArc60                    => ZERO
                case SectionGeometryChange(_, _)      => ZERO
                case SingularFlowResistance(_, _)     => ZERO
                case PressureDiff(_)                  => ZERO
    }

    override given hasVerticalElev: HasVerticalElev[PipeElDescr]: 
        extension (p: PipeElDescr) def verticalElev = 
            val ZERO = 0.0.meters
            p match
                case x: StraightSection        => x.elevation_gain
                case _: AngleVifDe0A180        => ZERO
                case _: CircularArc60          => ZERO
                case _: SectionGeometryChange  => ZERO
                case _: SingularFlowResistance => ZERO
                case _: PressureDiff           => ZERO

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr]:
        extension (el: PipeElDescr)
            def innerShape(oPrevGeom: Option[PipeShape]): Option[PositionOp[PipeShape]] = 
                def makeQtyAtPositionForGeometryTransition(from: PipeShape, to: PipeShape): Option[PositionOp[PipeShape]] = 
                    QtyDAtPosition.from(
                        start = from, 
                        middle = (from, to) match
                            case (Circle(from), Circle(to))                       => Circle((from + to) / 2.0)
                            case (Square(from), Square(to))                       => Square((from + to) / 2.0)
                            case (Rectangle(fromA, fromB), Rectangle(toA, toB))   => Rectangle((fromA + toA) / 2.0, (fromB + toB) / 2.0)
                            case (Square(from), Rectangle(toA, toB))              => Rectangle((from + toA) / 2.0 , (from + toB) / 2.0)
                            case (Rectangle(fromA, fromB), Square(to))            => Rectangle((fromA + to) / 2.0 , (fromB + to) / 2.0)
                            case (from, to) => 
                                Circle((from.dh + to.dh) / 2.0),
                                // throw new IllegalStateException(s"pipe shape transition not implemented : '${from} -> ${to}"),
                        end = to
                    ).some.map(_.atPos)
                el match
                    case s: StraightSection => 
                        QtyDAtPosition.constant(s.geometry).some.map(_.atPos)
                    case s: SectionGeometryChange => 
                        makeQtyAtPositionForGeometryTransition(s.from, s.to)
                    case SingularFlowResistance(_, Some(crossSection)) => 
                        val equivCircle = Circle.fromArea(crossSection)
                        QtyDAtPosition.constant(equivCircle).some.map(_.atPos)
                    case _: ( SingularFlowResistance | PressureDiff | DirectionChange ) => 
                        oPrevGeom.map(prevGeom => QtyDAtPosition.constant(prevGeom).atPos)

    type NotStraightSection = DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff
    type NotPressureDiff    = StraightSection | DirectionChange | SectionGeometryChange | SingularFlowResistance

    sealed trait PipeElDescr extends Matchable

    case class StraightSection(
        length: QtyD[Meter],
        geometry: PipeShape,
        roughness: Roughness,
        elevation_gain: QtyD[Meter],
    ) extends PipeElDescr derives Show {
        def approx_elevation_gain_at(l: Length): Length = 
            // interpolate as a (reasonable ?) approximation
            // should be true for straight section
            elevation_gain * l / length
    }

    sealed abstract class DirectionChange(
        val angleN1: QtyD[Degree],
        val angleN2: Option[QtyD[Degree]]
    ) extends PipeElDescr derives Show

    object DirectionChange:
        val angleVifZero = AngleVifDe0A180(0.0.degrees, None)
        case class AngleVifDe0A180(α: Angle, override val angleN2: Option[Angle] = None)
            extends DirectionChange(α, angleN2)
            derives Show

        case object CircularArc60 extends DirectionChange(60.degrees, None)
        type CircularArc60 = CircularArc60.type

    case class SectionGeometryChange(
        from: PipeShape,
        to: PipeShape
    ) extends PipeElDescr derives Show

    case class SingularFlowResistance(zeta: ζ, crossSectionO: Option[Area]) extends PipeElDescr derives Show
    case class PressureDiff(pa: QtyD[Pascal]) extends PipeElDescr derives Show
