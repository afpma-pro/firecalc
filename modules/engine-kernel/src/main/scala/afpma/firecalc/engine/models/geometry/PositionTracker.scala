/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import coulomb.*
import coulomb.policy.standard.given

object PositionTracker:

    /** Common commands used to unify different pipe descriptor types. */
    private sealed trait PipeCommand
    private case class CmdSetInnerShape(shape: PipeShape)                                  extends PipeCommand
    private case class CmdDirectionChange(absDir: Option[AbsoluteDirection], angle: Angle) extends PipeCommand
    private case class CmdSplitMerge90(
        absDir             : Option[AbsoluteDirection],
        isSplit            : Boolean,
        name               : String,
        symmetryPlaneAbsDir: Option[AbsoluteDirection] = None
    ) extends PipeCommand
    private case class CmdSectionVertical(elevGain: Length)                                extends PipeCommand
    private case class CmdSectionHorizontal(horizLen: Length)                              extends PipeCommand
    private case class CmdSectionSlopped(length: Length)                                   extends PipeCommand
    private case class CmdSectionSloppedForceManual(length: Length, elevGain: Length)      extends PipeCommand
    private case object CmdNoOp                                                            extends PipeCommand

    private def mapFlowOnly13384(elem: FlowOnlyPipeDescr_13384): Seq[PipeCommand] =
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4.*
        elem match
            case SetInnerShape(shape) => Seq(CmdSetInnerShape(shape))
            case dc: AddDirectionChange                       => Seq(CmdDirectionChange(dc.absDir, dc.angle))
            case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
                Seq(CmdSplitMerge90(sm.absDir, true, sm.name, sm.symmetryPlaneAbsDir))
            case sm: MergeTwoFlowsIntoSingleWith90DegTurn     =>
                Seq(CmdSplitMerge90(sm.absDir, false, sm.name, sm.symmetryPlaneAbsDir))
            case AddSectionVertical(_, elevGain) => Seq(CmdSectionVertical(elevGain))
            case AddSectionHorizontal(_, horizLen)                              => Seq(CmdSectionHorizontal(horizLen))
            case AddSectionSlopped(_, length)                                   => Seq(CmdSectionSlopped(length)     )
            case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                Seq(CmdSectionSloppedForceManual(length, elevGain))
            case _                                                              => Seq(CmdNoOp)

    private def mapFlowOnly15544(elem: FlowOnlyPipeDescr_15544): Seq[PipeCommand] =
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        elem match
            case SetInnerShape(shape) => Seq(CmdSetInnerShape(shape))
            case dc: AddDirectionChange                       => Seq(CmdDirectionChange(dc.absDir, dc.angle))
            case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
                Seq(CmdSplitMerge90(sm.absDir, true, sm.name, sm.symmetryPlaneAbsDir))
            case sm: MergeTwoFlowsIntoSingleWith90DegTurn     =>
                Seq(CmdSplitMerge90(sm.absDir, false, sm.name, sm.symmetryPlaneAbsDir))
            case AddSectionVertical(_, elevGain) => Seq(CmdSectionVertical(elevGain))
            case AddSectionHorizontal(_, horizLen)                              => Seq(CmdSectionHorizontal(horizLen))
            case AddSectionSlopped(_, length)                                   => Seq(CmdSectionSlopped(length)     )
            case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                Seq(CmdSectionSloppedForceManual(length, elevGain))
            case _                                                              => Seq(CmdNoOp)

    private def mapThermal13384(elem: ThermalPipeDescr_13384): Seq[PipeCommand] =
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.*
        elem match
            case SetInnerShape(shape)              => Seq(CmdSetInnerShape(shape))
            case SetPropertiesInBatch(_, props, _) =>
                props.collect { case SetInnerShape(shape) => CmdSetInnerShape(shape) }.toSeq
            case LinedFlue(_, liner, _, _)         =>
                liner.props.collect { case SetInnerShape(shape) => CmdSetInnerShape(shape) }.toSeq
            case dc: AddDirectionChange => Seq(CmdDirectionChange(dc.absDir, dc.angle))
            case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
                Seq(CmdSplitMerge90(sm.absDir, true, sm.name, sm.symmetryPlaneAbsDir))
            case sm: MergeTwoFlowsIntoSingleWith90DegTurn     =>
                Seq(CmdSplitMerge90(sm.absDir, false, sm.name, sm.symmetryPlaneAbsDir))
            case AddSectionVertical(_, elevGain) => Seq(CmdSectionVertical(elevGain))
            case AddSectionHorizontal(_, horizLen)                              => Seq(CmdSectionHorizontal(horizLen))
            case AddSectionSlopped(_, length)                                   => Seq(CmdSectionSlopped(length)     )
            case AddSectionSloppedForceManualElevationGain(_, length, elevGain) =>
                Seq(CmdSectionSloppedForceManual(length, elevGain))
            case _                                                              => Seq(CmdNoOp)

    private def computeGenericPipePositions[T](
        elems                   : Seq[T],
        map                     : T => Seq[PipeCommand],
        initialDirection        : PipeInitialDirection,
        externalFrame           : Option[PipeFrame],
        startPoint              : Vec3,
        currentInnerShapeInitial: Option[PipeShape],
        splitPosition           : Option[Vec3]
    ): PipePositionResult =

        var frame             : Option[PipeFrame] = externalFrame.orElse(
            Some(
                PipeFrame.initial(
                    Vec3.fromAzimuthElevation(
                        initialDirection.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0                         ),
                        InclinationDirection.toDegrees                                    (initialDirection.inclination)
                    )
                )
            )
        )
        var currentPosition   : Vec3              = startPoint
        var currentInnerShape : Option[PipeShape] = currentInnerShapeInitial
        var firstSplitConsumed: Boolean           = false
        val segments = Seq.newBuilder[PipeSegmentPosition]
        val splitMergePositions = Seq.newBuilder[SplitMergePosition]
        val positionErrors = Vector.newBuilder[String]

        for (elem, idx) <- elems.zipWithIndex do
            for (cmd) <- map(elem) do
                cmd match
                    case CmdSetInnerShape(shape)                                     =>
                        currentInnerShape = Some(shape)
                    case CmdDirectionChange(absDir, angle)                           =>
                        for
                            f  <- frame
                            fd <- absDir
                        do
                            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                            val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                            frame = Some(f.applyBendForFinalDir(angle.toUnit[Degree].value, targetVec))
                    case CmdSplitMerge90(absDir, isSplit, name, symmetryPlaneAbsDir) =>
                        frame.foreach: f =>
                            val frameAfter =
                                absDir match
                                    case Some(fd) =>
                                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                        f.applyBendForFinalDir(90.0, targetVec)
                                    case None     => f
                            SymmetryPlaneConfig.fromIncomingWithAbsDir(f.direction, symmetryPlaneAbsDir) match
                                case Right(config) =>
                                    val pos =
                                        if isSplit && !firstSplitConsumed && splitPosition.isDefined then
                                            firstSplitConsumed = true
                                            splitPosition.get
                                        else currentPosition
                                    splitMergePositions += SplitMergePosition(
                                        idx,
                                        pos,
                                        f,
                                        frameAfter.direction,
                                        isSplit,
                                        config
                                    )
                                    frame = Some(frameAfter)
                                case Left(_)       =>
                                    positionErrors += name
                    case c: (CmdSectionVertical | CmdSectionHorizontal) =>
                        val l          = c match
                            case CmdSectionVertical(eg)   => eg.toUnit[Meter].value
                            case CmdSectionHorizontal(hl) => hl.toUnit[Meter].value
                        val defaultDir = c match
                            case CmdSectionVertical(_)   => Vec3.Up
                            case CmdSectionHorizontal(_) => Vec3.Rear
                        val base       = frame.map(_.direction).getOrElse(defaultDir)
                        val dir        = if l < 0 then base * -1.0 else base
                        val dist       = math.abs(l)
                        val disp       = dir * dist
                        val endPt      = currentPosition + disp
                        segments += PipeSegmentPosition(
                            elementIndex = idx,
                            startPoint   = currentPosition,
                            endPoint     = endPt,
                            direction    = dir,
                            length       = dist,
                            innerShape   = currentInnerShape,
                            frame        = frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                        )
                        currentPosition = endPt
                    case c: (CmdSectionSlopped | CmdSectionSloppedForceManual) =>
                        val l     = c match
                            case CmdSectionSlopped(len)               => len.toUnit[Meter].value
                            case CmdSectionSloppedForceManual(len, _) => len.toUnit[Meter].value
                        val eg    = c match
                            case CmdSectionSlopped(_)                    => frame.map(f => l * f.direction.z).getOrElse(0.0)
                            case CmdSectionSloppedForceManual(_, egGain) => egGain.toUnit[Meter].value
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
                    case CmdNoOp                                                     => ()

        PipePositionResult(
            segments.result           (),
            currentPosition,
            frame,
            splitMergePositions.result(),
            positionErrors.result     ()
        )

    private def runPipeline[T](
        elems           : Seq[T],
        initialDirection: PipeInitialDirection,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3],
        splitPosition   : Option[Vec3],
        map             : T => Seq[PipeCommand]
    ): PipePositionResult =
        val result = computeGenericPipePositions(
            elems,
            map,
            initialDirection,
            externalFrame,
            startPoint,
            currentInnerShapeInitial = None,
            splitPosition            = splitPosition
        )
        applyFinalTranslate(result, finalPoint)

    /** Apply the final-position translate post-processing if needed. */
    private def applyFinalTranslate(result: PipePositionResult, effectiveFinal: Option[Vec3]): PipePositionResult =
        effectiveFinal match
            case Some(target) =>
                val offset = target - result.finalPoint
                result.translate(offset)
            case None         => result

    def computeFlowOnly13384(
        elems           : Seq[FlowOnlyPipeDescr_13384],
        initialDirection: PipeInitialDirection,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None,
        splitPosition   : Option[Vec3] = None
    ): PipePositionResult =

        runPipeline(
            elems,
            initialDirection,
            externalFrame,
            startPoint,
            finalPoint,
            splitPosition,
            mapFlowOnly13384
        )

    def computeFlowOnly15544(
        elems           : Seq[FlowOnlyPipeDescr_15544],
        initialDirection: PipeInitialDirection,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None,
        splitPosition   : Option[Vec3] = None
    ): PipePositionResult =

        runPipeline(
            elems,
            initialDirection,
            externalFrame,
            startPoint,
            finalPoint,
            splitPosition,
            mapFlowOnly15544
        )

    def computeThermal13384(
        elems           : Seq[ThermalPipeDescr_13384],
        initialDirection: PipeInitialDirection,
        externalFrame   : Option[PipeFrame],
        startPoint      : Vec3,
        finalPoint      : Option[Vec3] = None,
        splitPosition   : Option[Vec3] = None
    ): PipePositionResult =

        runPipeline(
            elems,
            initialDirection,
            externalFrame,
            startPoint,
            finalPoint,
            splitPosition,
            mapThermal13384
        )

    private def horizontalDirection(frame: Option[PipeFrame]): Vec3 =
        frame match
            case Some(f) =>
                val d     = f.direction
                val horiz = Vec3(d.x, d.y, 0.0)
                if horiz.norm < 1e-9 then Vec3.Rear
                else horiz.normalized
            case None    => Vec3.Rear
