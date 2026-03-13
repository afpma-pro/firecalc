/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.v4.{FinalDirection, AzimuthDirection, InclinationDirection}

import coulomb.*
import coulomb.policy.standard.given

object PositionTracker:

    def computeFlowOnly13384(
        elems        : Seq[FlowOnlyPipeDescr_13384],
        externalFrame: Option[PipeFrame],
        startPoint   : Vec3
    ): PipePositionResult =
        import SetFlowOnlyPipeProp_13384_V3.*
        import AddFlowOnlyPipeElement_13384_V3.*

        var frame             : Option[PipeFrame] = externalFrame
        var currentPosition   : Vec3              = startPoint
        var currentInnerShape : Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInitialDirection(az, incl) =>
                    frame = Some(PipeFrame.initial(
                        Vec3.fromAzimuthElevation(AzimuthDirection.toDegrees(az), InclinationDirection.toDegrees(incl))
                    ))
                case SetInnerShape(shape) =>
                    currentInnerShape = Some(shape)
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.finalDir
                    do
                        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain) =>
                    val eg   = elevGain.toUnit[Meter].value
                    val dir  = if eg >= 0 then Vec3.Up else Vec3.Down
                    val disp = Vec3(0, 0, eg)
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(eg),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen) =>
                    val hl   = horizLen.toUnit[Meter].value
                    val dir  = horizontalDirection(frame)
                    val disp = dir * hl
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(hl),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length, elevGain) =>
                    val l   = length.toUnit[Meter].value
                    val eg  = elevGain.toUnit[Meter].value
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
                case _ => ()

        PipePositionResult(segments.result(), currentPosition, frame)

    def computeFlowOnly15544(
        elems        : Seq[FlowOnlyPipeDescr_15544],
        externalFrame: Option[PipeFrame],
        startPoint   : Vec3
    ): PipePositionResult =
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*

        var frame             : Option[PipeFrame] = externalFrame
        var currentPosition   : Vec3              = startPoint
        var currentInnerShape : Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInitialDirection(az, incl) =>
                    frame = Some(PipeFrame.initial(
                        Vec3.fromAzimuthElevation(AzimuthDirection.toDegrees(az), InclinationDirection.toDegrees(incl))
                    ))
                case SetInnerShape(shape) =>
                    currentInnerShape = Some(shape)
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.finalDir
                    do
                        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain) =>
                    val eg   = elevGain.toUnit[Meter].value
                    val dir  = if eg >= 0 then Vec3.Up else Vec3.Down
                    val disp = Vec3(0, 0, eg)
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(eg),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen) =>
                    val hl   = horizLen.toUnit[Meter].value
                    val dir  = horizontalDirection(frame)
                    val disp = dir * hl
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(hl),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length, elevGain) =>
                    val l   = length.toUnit[Meter].value
                    val eg  = elevGain.toUnit[Meter].value
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
                case _ => ()

        PipePositionResult(segments.result(), currentPosition, frame)

    def computeThermal13384(
        elems        : Seq[ThermalPipeDescr_13384],
        externalFrame: Option[PipeFrame],
        startPoint   : Vec3
    ): PipePositionResult =
        import SetThermalPipeProp_13384_V3.*
        import AddThermalPipeElement_13384_V3.*

        var frame             : Option[PipeFrame] = externalFrame
        var currentPosition   : Vec3              = startPoint
        var currentInnerShape : Option[PipeShape] = None
        val segments = Seq.newBuilder[PipeSegmentPosition]

        for (elem, idx) <- elems.zipWithIndex do
            elem match
                case SetInitialDirection(az, incl) =>
                    frame = Some(PipeFrame.initial(
                        Vec3.fromAzimuthElevation(AzimuthDirection.toDegrees(az), InclinationDirection.toDegrees(incl))
                    ))
                case SetInnerShape(shape) =>
                    currentInnerShape = Some(shape)
                case SetPropertiesInBatch(_, props) =>
                    props.collectFirst { case SetInnerShape(shape) => shape }
                        .foreach(shape => currentInnerShape = Some(shape))
                case LinedFlue(_, liner, _, _) =>
                    liner.props.collectFirst { case SetInnerShape(shape) => shape }
                        .foreach(shape => currentInnerShape = Some(shape))
                case dc: AddDirectionChange =>
                    for
                        f  <- frame
                        fd <- dc.finalDir
                    do
                        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                case AddSectionVertical(_, elevGain) =>
                    val eg   = elevGain.toUnit[Meter].value
                    val dir  = if eg >= 0 then Vec3.Up else Vec3.Down
                    val disp = Vec3(0, 0, eg)
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(eg),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionHorizontal(_, horizLen) =>
                    val hl   = horizLen.toUnit[Meter].value
                    val dir  = horizontalDirection(frame)
                    val disp = dir * hl
                    val endPt = currentPosition + disp
                    segments += PipeSegmentPosition(
                        elementIndex = idx,
                        startPoint   = currentPosition,
                        endPoint     = endPt,
                        direction    = dir,
                        length       = math.abs(hl),
                        innerShape   = currentInnerShape,
                        frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                    )
                    currentPosition = endPt
                case AddSectionSlopped(_, length, elevGain) =>
                    val l   = length.toUnit[Meter].value
                    val eg  = elevGain.toUnit[Meter].value
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
                case _ => ()

        PipePositionResult(segments.result(), currentPosition, frame)

    private def horizontalDirection(frame: Option[PipeFrame]): Vec3 =
        frame match
            case Some(f) =>
                val d     = f.direction
                val horiz = Vec3(d.x, d.y, 0.0)
                if horiz.norm < 1e-9 then Vec3.Rear
                else horiz.normalized
            case None => Vec3.Rear
