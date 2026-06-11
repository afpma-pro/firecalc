/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.dto.v7.PostFireboxInitialDirection
import afpma.firecalc.dto.v7.PostFireboxInitialPosition

import coulomb.*
import coulomb.policy.standard.given

object PositionTracker:

    private def toVec3(x: Length, y: Length, z: Length): Vec3 =
        Vec3(x.toUnit[Meter].value, y.toUnit[Meter].value, z.toUnit[Meter].value)

    /**
     * Scan a descriptor sequence for SetFinalPosition (the last one wins).
     * Returns the effective finalPoint to use.
     */
    private def resolveFinalPosition[A](
        elems     : Seq[A],
        finalPoint: Option[Vec3]
    )(
        extract: PartialFunction[(A, Int), (Vec3, Int)]
    ): Option[Vec3] =
        elems.zipWithIndex.collect(extract).maxByOption(_._2).map(_._1).orElse(finalPoint)

    /** Apply the final-position translate post-processing if needed. */
    private def applyFinalTranslate(result: PipePositionResult, effectiveFinal: Option[Vec3]): PipePositionResult =
        effectiveFinal match
            case Some(target) =>
                val offset = target - result.finalPoint
                result.translate(offset)
            case None         => result

    def computeFlowOnly13384(
        elems           : Seq[FlowOnlyPipeDescr_13384],
        initialDirection: PostFireboxInitialDirection,
        initialPosition : PostFireboxInitialPosition,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None
    ): PipePositionResult =
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4.*
        import afpma.firecalc.dto.v7.FlowOnlyPipeTrackingOp_13384_V4.*

        val effectiveFinal = resolveFinalPosition(elems, finalPoint) { case (SetFinalPosition(x, y, z), idx) =>
            (toVec3(x, y, z), idx)
        }

        var frame            : Option[PipeFrame] = externalFrame.orElse(
            Some(
                PipeFrame.initial(
                    Vec3.fromAzimuthElevation(
                        AzimuthDirection.toDegrees    (initialDirection.azimuth    ),
                        InclinationDirection.toDegrees(initialDirection.inclination)
                    )
                )
            )
        )
        var currentPosition  : Vec3              = startPoint
        var currentInnerShape: Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInnerShape(shape)                                           =>
                    currentInnerShape = Some(shape)
                case SetInitialDirection(az, incl)                                  =>
                    val azDeg = AzimuthDirection.toDegrees(az)
                    val elDeg = InclinationDirection.toDegrees(incl)
                    frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.absDir
                    do
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain)                                =>
                    val eg    = elevGain.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Up)
                    val dir   = if eg < 0 then base * -1.0 else base
                    val l     = math.abs(eg)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen)                              =>
                    val hl    = horizLen.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Rear)
                    val dir   = if hl < 0 then base * -1.0 else base
                    val l     = math.abs(hl)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length)                                   =>
                    val l     = length.toUnit[Meter].value
                    val eg    = frame.map(f => l * f.direction.z).getOrElse(0.0)
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                    val l     = length.toUnit[Meter].value
                    val eg    = elevGain.toUnit[Meter].value
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case _: FlowOnlyPipeDescr_13384 => ()

        val result = PipePositionResult(segments.result(), currentPosition, frame)
        applyFinalTranslate(result, effectiveFinal)

    def computeFlowOnly15544(
        elems           : Seq[FlowOnlyPipeDescr_15544],
        initialDirection: PostFireboxInitialDirection,
        initialPosition : PostFireboxInitialPosition,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None
    ): PipePositionResult =
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        import afpma.firecalc.dto.v7.FlowOnlyPipeTrackingOp_15544_V4.*

        val effectiveFinal = resolveFinalPosition(elems, finalPoint) { case (SetFinalPosition(x, y, z), idx) =>
            (toVec3(x, y, z), idx)
        }

        var frame            : Option[PipeFrame] = externalFrame.orElse(
            Some(
                PipeFrame.initial(
                    Vec3.fromAzimuthElevation(
                        AzimuthDirection.toDegrees    (initialDirection.azimuth    ),
                        InclinationDirection.toDegrees(initialDirection.inclination)
                    )
                )
            )
        )
        var currentPosition  : Vec3              = startPoint
        var currentInnerShape: Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInnerShape(shape)                                           =>
                    currentInnerShape = Some(shape)
                case SetInitialDirection(az, incl)                                  =>
                    val azDeg = AzimuthDirection.toDegrees(az)
                    val elDeg = InclinationDirection.toDegrees(incl)
                    frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.absDir
                    do
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain)                                =>
                    val eg    = elevGain.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Up)
                    val dir   = if eg < 0 then base * -1.0 else base
                    val l     = math.abs(eg)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen)                              =>
                    val hl    = horizLen.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Rear)
                    val dir   = if hl < 0 then base * -1.0 else base
                    val l     = math.abs(hl)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length)                                   =>
                    val l     = length.toUnit[Meter].value
                    val eg    = frame.map(f => l * f.direction.z).getOrElse(0.0)
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                    val l     = length.toUnit[Meter].value
                    val eg    = elevGain.toUnit[Meter].value
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case _: FlowOnlyPipeDescr_15544 => ()

        val result = PipePositionResult(segments.result(), currentPosition, frame)
        applyFinalTranslate(result, effectiveFinal)

    def computeThermal13384(
        elems           : Seq[ThermalPipeDescr_13384],
        initialDirection: PostFireboxInitialDirection,
        initialPosition : PostFireboxInitialPosition,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None
    ): PipePositionResult =
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.*
        import afpma.firecalc.dto.v7.ThermalPipeTrackingOp_13384_V4.*

        val effectiveFinal = resolveFinalPosition(elems, finalPoint) { case (SetFinalPosition(x, y, z), idx) =>
            (toVec3(x, y, z), idx)
        }

        var frame            : Option[PipeFrame] = externalFrame.orElse(
            Some(
                PipeFrame.initial(
                    Vec3.fromAzimuthElevation(
                        AzimuthDirection.toDegrees    (initialDirection.azimuth    ),
                        InclinationDirection.toDegrees(initialDirection.inclination)
                    )
                )
            )
        )
        var currentPosition  : Vec3              = startPoint
        var currentInnerShape: Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInnerShape(shape)                                           =>
                    currentInnerShape = Some(shape)
                case SetPropertiesInBatch(_, props, _)                              =>
                    props
                        .collectFirst { case SetInnerShape(shape) => shape }
                        .foreach(shape => currentInnerShape = Some(shape))
                case LinedFlue(_, liner, _, _)                                      =>
                    liner.props
                        .collectFirst { case SetInnerShape(shape) => shape }
                        .foreach(shape => currentInnerShape = Some(shape))
                case SetInitialDirection(az, incl)                                  =>
                    val azDeg = AzimuthDirection.toDegrees(az)
                    val elDeg = InclinationDirection.toDegrees(incl)
                    frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.absDir
                    do
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain)                                =>
                    val eg    = elevGain.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Up)
                    val dir   = if eg < 0 then base * -1.0 else base
                    val l     = math.abs(eg)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen)                              =>
                    val hl    = horizLen.toUnit[Meter].value
                    val base  = frame.map(_.direction).getOrElse(Vec3.Rear)
                    val dir   = if hl < 0 then base * -1.0 else base
                    val l     = math.abs(hl)
                    val disp  = dir * l
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length)                                   =>
                    val l     = length.toUnit[Meter].value
                    val eg    = frame.map(f => l * f.direction.z).getOrElse(0.0)
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                    val l     = length.toUnit[Meter].value
                    val eg    = elevGain.toUnit[Meter].value
                    val hDist = math.sqrt(math.max(0.0, l * l - eg * eg))
                    val disp  = horizontalDirection(frame) * hDist + Vec3(0, 0, eg)
                    val dir   = disp.normalized
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = l,
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case _: ThermalPipeDescr_13384 => ()

        val result = PipePositionResult(segments.result(), currentPosition, frame)
        applyFinalTranslate(result, effectiveFinal)

    private def horizontalDirection(frame: Option[PipeFrame]): Vec3 =
        frame match
            case Some(f) =>
                val d     = f.direction
                val horiz = Vec3(d.x, d.y, 0.0)
                if horiz.norm < 1e-9 then Vec3.Rear
                else horiz.normalized
            case None    => Vec3.Rear
