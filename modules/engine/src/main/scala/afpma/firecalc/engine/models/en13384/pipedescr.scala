/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import cats.Show
import cats.derived.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.en13384.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

object pipedescr extends afpma.firecalc.engine.models.PipeDescrAlg:
    ctx =>

    override given hasLength: HasLength[PipeElDescr]: 
        extension (p: PipeElDescr) def length = p match
            case s: StraightSection => s.length
            case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => 0.meters

    override given hasVerticalElev: HasVerticalElev[PipeElDescr]: 
            extension (p: PipeElDescr) def verticalElev = p match
                case s: StraightSection => s.elevation_gain
                case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => 0.meters

    given HasUnheatedHeightInsideAndOutside[PipeElDescr]:
        extension (el: PipeElDescr) 
            def unheatedHeightInsideAndOutside: UnheatedHeightInsideAndOutside =
                import PipeLocation.*
                el match
                    case sec: StraightSection => 
                        if (sec.pipeLoc.areaHeatingStatus == AreaHeatingStatus.NotHeated)
                            val elev_gain = sec.elevation_gain
                            // remove require because we can have air intake pipe with negative vertical elevation
                            // require(elev_gain >= 0.meters, s"unexpected negative vertical elevation found in pipe : $el (dev error)")
                            val absValue_in_m = math.abs(elev_gain.toUnit[Meter].value)
                            absValue_in_m.meters
                        else
                            0.0.meters
                    case _ => 
                        0.0.meters

    given HasOutsideSurfaceInLocation[PipeElDescr]:
        extension (el: PipeElDescr) 
            def outsideSurfaceIn[PLoc <: PipeLocation](
                inLoc: PLoc
            ): QtyD[(Meter ^ 2)] =
                el match
                    case sec: StraightSection =>
                        if (sec.pipeLoc == inLoc) 
                            sec.outer_shape.area
                        else 0.m2
                    case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => 
                        0.m2

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
                        QtyDAtPosition.constant(s.innerShape).some.map(_.atPos)
                    case s: SectionDecrease => 
                        makeQtyAtPositionForGeometryTransition(s.from, s.to)
                    case s: SectionIncrease => 
                        makeQtyAtPositionForGeometryTransition(s.from, s.to)
                    case _ @ SingularFlowResistance(_, Some(crossSection)) => 
                        val equivCircle = Circle.fromArea(crossSection)
                        QtyDAtPosition.constant(equivCircle).some.map(_.atPos)
                    case _: ( SingularFlowResistance | PressureDiff | DirectionChange ) => 
                        oPrevGeom.map(prevGeom => QtyDAtPosition.constant(prevGeom).atPos)
    
    enum Error:
        case OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow
    object Error:
        type OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow = 
            Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow.type

    extension (el: PipeElDescr)
        def toPipeWithGasFlowWithLength(
            gas: GasProps,
            gas_massflow: QtyD[Kilogram / Second],
            exterior_air: ExteriorAir,
            length: Length,
        ): Either[Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow.type, PipeWithGasFlow] = 
            el match
                case el: StraightSection => 
                    PipeWithGasFlow(
                        el.innerShape,
                        el.outer_shape,
                        length, // instead of el.length,
                        el.layers,
                        el.roughness,
                        el.airSpaceDetailed,
                        el.pipeLoc,
                        gas,
                        gas_massflow,
                        exterior_air,
                    ).asRight
                case el =>
                    Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow.asLeft
        def toPipeWithGasFlow(
            gas: GasProps,
            gas_massflow: QtyD[Kilogram / Second],
            exterior_air: ExteriorAir
        ): Either[Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow.type, PipeWithGasFlow] = 
            toPipeWithGasFlowWithLength(gas, gas_massflow, exterior_air, el.length)
            
    
    sealed trait PipeElDescr extends Matchable

    case class StraightSection(
        length: Length,
        innerShape: PipeShape,
        outer_shape: PipeShape,
        roughness: Roughness,
        layers: List[AppendLayerDescr],
        elevation_gain: Length,
        airSpaceDetailed: AirSpaceDetailed,
        pipeLoc: PipeLocation,
        ductType: DuctType
    ) extends PipeElDescr {
        def approx_elevation_gain_at(l: Length): Length = 
            // interpolate as a (reasonable ?) approximation
            // should be true for straight section
            elevation_gain * l / length
    }

    // just some case of SingularFlowResistance ?
    // should extends SingularFlowResistance ?
    // 
    sealed abstract class DirectionChange(
        val angleN1: QtyD[Degree],
        val angleN2: Option[QtyD[Degree]] = None // TOFIX ?
    ) extends PipeElDescr derives Show

    object DirectionChange:
        given Show[AngleVifDe0A90_Unsafe | AngleVifDe0A90 | CoudeCourbe90 | CoudeCourbe90_Unsafe | CoudeCourbe60 | CoudeCourbe60_Unsafe] =
            Show.show:
                case x: AngleVifDe0A90          => Show[AngleVifDe0A90].show(x)
                case x: AngleVifDe0A90_Unsafe   => Show[AngleVifDe0A90_Unsafe].show(x)
                case x: CoudeCourbe90           => Show[CoudeCourbe90].show(x)
                case x: CoudeCourbe90_Unsafe    => Show[CoudeCourbe90_Unsafe].show(x)
                case x: CoudeCourbe60           => Show[CoudeCourbe60].show(x)
                case x: CoudeCourbe60_Unsafe    => Show[CoudeCourbe60_Unsafe].show(x)

    case class AngleSpecifique(ɣ: QtyD[Degree], zeta: ζ)
        extends DirectionChange(ɣ) derives Show
    
    case class AngleVifDe0A90(ɣ: QtyD[Degree], Ld: QtyD[Meter], Dh: QtyD[Meter])
        extends DirectionChange(ɣ) derives Show

    // __INTERPRETATION__
    case class AngleVifDe0A90_Unsafe(ɣ: QtyD[Degree], Ld: QtyD[Meter], Dh: QtyD[Meter])
        extends DirectionChange(ɣ) derives Show
    
    case class CoudeCourbe90(R: QtyD[Meter], Dh: QtyD[Meter], Ld: QtyD[Meter])
        extends DirectionChange(90.degrees) derives Show

    // __INTERPRETATION__
    case class CoudeCourbe90_Unsafe(R: QtyD[Meter], Dh: QtyD[Meter], Ld: QtyD[Meter]) // __INTERPRETATION__
        extends DirectionChange(90.degrees) derives Show
    
    case class CoudeCourbe60(R: QtyD[Meter], Dh: QtyD[Meter], Ld: QtyD[Meter])
        extends DirectionChange(60.degrees) derives Show
    
    // __INTERPRETATION__
    case class CoudeCourbe60_Unsafe(R: QtyD[Meter], Dh: QtyD[Meter], Ld: QtyD[Meter]) // __INTERPRETATION__
        extends DirectionChange(60.degrees) derives Show
    sealed abstract class CoudeASegment90(
        val nSeg: 2 | 3 | 4,
        val R: QtyD[Meter],
        val Dh: QtyD[Meter]
    ) extends DirectionChange(90.degrees) derives Show
    case class CoudeASegment90Avec2A45(
        override val R: QtyD[Meter],
        override val Dh: QtyD[Meter]
    ) extends CoudeASegment90(2, R, Dh) derives Show
    case class CoudeASegment90Avec3A30(
        override val R: QtyD[Meter],
        override val Dh: QtyD[Meter]
    ) extends CoudeASegment90(3, R, Dh) derives Show
    case class CoudeASegment90Avec4A22p5(
        override val R: QtyD[Meter],
        override val Dh: QtyD[Meter]
    ) extends CoudeASegment90(4, R, Dh) derives Show

    sealed abstract class SectionGeometryChange(
        val from: PipeShape.Circle,
        val to: PipeShape.Circle
    ) extends PipeElDescr derives Show {
        def fromA1: QtyD[(Meter ^ 2)] = from.area
        def toA2: QtyD[(Meter ^ 2)]   = to.area
    }

    object SectionGeometryChange:
        def make(from: Area, to: Area): SectionGeometryChange =
            if (to >= from) SectionIncrease.mkFromAreas(from, to)
            else SectionDecrease.mkFromAreas(from,to)

    // Tableau B.8 - Line 6
    case class SectionDecrease(override val from: PipeShape.Circle, override val to: PipeShape.Circle) 
        extends SectionGeometryChange(from, to)
        derives Show

    object SectionDecrease:
        def mkFromDiameters(fromD1: Length, toD2: Length): SectionDecrease = SectionDecrease(
            from    = PipeShape.Circle(fromD1),
            to      = PipeShape.Circle(toD2)
        )
        def mkFromAreas(fromA1: Area, toA2: Area): SectionDecrease = SectionDecrease(
            from    = PipeShape.Circle.fromArea(fromA1),
            to      = PipeShape.Circle.fromArea(toA2)
        )

    // Tableau B.8 - Line 7
    case class SectionIncrease(override val from: PipeShape.Circle, override val to: PipeShape.Circle) 
        extends SectionGeometryChange(from, to)
        derives Show

    object SectionIncrease:
        def mkFromDiameters(fromD1: Length, toD2: Length): SectionIncrease = SectionIncrease(
            from    = PipeShape.Circle(fromD1),
            to      = PipeShape.Circle(toD2)
        )
        def mkFromAreas(fromA1: Area, toA2: Area): SectionIncrease = SectionIncrease(
            from    = PipeShape.Circle.fromArea(fromA1),
            to      = PipeShape.Circle.fromArea(toA2)
        )
    
    // Tableau B.8 - Line 8
    // case class SectionDecreaseProgressive(fromD1: QtyD[Meter], toD2: QtyD[Meter], ɣ: QtyD[Degree])
    //     extends SectionGeometryChange(
    //         from    = PipeShape.Circle(fromD1),
    //         to      = PipeShape.Circle(toD2)
    //     ) 
    //     derives Show 
    // {
    //     val length: Length = 
    //         val ɣ_radian = ɣ.toUnit[Radian].value
    //         (fromD1 - toD2) / (2.0 * math.tan(ɣ_radian / 2))

    //     /**
    //      * x is the lengthwise distance starting at A1
    //      */
    //     def diamAt(x: Length): QtyD[Meter] = 
    //         val ɣ_radian = ɣ.toUnit[Radian].value
    //         val d = (length - x) * math.tan( ɣ_radian / 2 )
    //         toD2 + 2.0 * d

    //     def geometryAt(x: Length) = PipeShape.Circle(diamAt(x))

    //     def areaAt(x: Length) = 
    //         geometryAt(x).area
    // }
    // object SectionDecreaseProgressive:
    //     def fromLength(fromD1: QtyD[Meter], toD2: QtyD[Meter], length: Length): SectionDecreaseProgressive = 
    //         val ɣ_radian = 
    //             ( 
    //                 2.0 * math.atan( ((fromD1 - toD2) / (2.0 * length)).value ) 
    //             ).withUnit[Radian]
    //         SectionDecreaseProgressive(fromD1, toD2, ɣ_radian.toUnit[Degree])

    case class SingularFlowResistance(zeta: ζ, crossSectionO: Option[Area]) extends PipeElDescr derives Show
    case class PressureDiff(pa: QtyD[Pascal]) extends PipeElDescr derives Show
