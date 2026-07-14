/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.{
    AbsoluteDirection,
    AzimuthDirection,
    HasDirectionChangeData,
    HasInnerShapeValue,
    HasSectionElevation,
    HasSectionLength,
    HasSplitMergeData,
    InclinationDirection
}
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.{LinedFlue, SetPropertiesInBatch}

import coulomb.*
import coulomb.policy.standard.given

object PositionTracker:

    /** Common commands used to unify different pipe descriptor types. */
    private sealed trait PipeCommand
    private case class CmdSetInnerShape(shape: PipeShape)                                  extends PipeCommand
    private case class CmdDirectionChange(absDir: Option[AbsoluteDirection], angle: Angle) extends PipeCommand
    private case class CmdSplitMerge90(
        absDir              : Option[AbsoluteDirection],
        isSplit             : Boolean,
        name                : String,
        symmetryPlaneAzimuth: Option[AzimuthDirection] = None
    ) extends PipeCommand
    private case class CmdSection(length: Length)                                          extends PipeCommand
    private case class CmdSectionSloppedForceManual(length: Length, elevGain: Length)      extends PipeCommand
    private case object CmdNoOp                                                            extends PipeCommand

    /** Immutable accumulator for pipe position tracking. */
    private case class TrackerState(
        frame             : PipeFrame,
        currentPosition   : Vec3,
        currentInnerShape : Option[PipeShape],
        firstSplitConsumed: Boolean
    )

    /** Collected outputs from processing commands. */
    private case class TrackingOutputs(
        segments     : Vector[PipeSegmentPosition],
        splitMergePos: Vector[SplitMergePosition],
        errors       : Vector[String]
    )

    private object TrackingOutputs:
        val empty: TrackingOutputs = TrackingOutputs(Vector.empty, Vector.empty, Vector.empty)

        extension (t: TrackingOutputs)
            def combineWith(other: TrackingOutputs): TrackingOutputs =
                TrackingOutputs(
                    t.segments ++ other.segments,
                    t.splitMergePos ++ other.splitMergePos,
                    t.errors ++ other.errors
                )
            def isEmpty                            : Boolean         = t.segments.isEmpty && t.splitMergePos.isEmpty && t.errors.isEmpty

    /** Pure function: process one command, return new state + outputs. */
    private def processCommand(
        cmd          : PipeCommand,
        state        : TrackerState,
        idx          : Int,
        splitPosition: Option[Vec3]
    ): (TrackerState, TrackingOutputs) =
        cmd match
            case CmdSetInnerShape(shape) =>
                (state.copy(currentInnerShape = Some(shape)), TrackingOutputs.empty)

            case CmdDirectionChange(absDir, angle) =>
                val newFrame = absDir.map: fd =>
                    val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                    val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                    state.frame.applyBendForFinalDir(angle.toUnit[Degree].value, targetVec)
                (state.copy(frame = newFrame.getOrElse(state.frame)), TrackingOutputs.empty)

            case CmdSplitMerge90(absDir, isSplit, name, symmetryPlaneAzimuth) =>
                val f                    = state.frame
                val frameAfter           =
                    absDir match
                        case Some(fd) =>
                            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                            val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                            if f.isReachable      (targetVec, 90.0) then f.applyBendForFinalDir(90.0, targetVec)
                            else PipeFrame.initial(targetVec      )
                        case None     =>
                            val defaultBranch = f.direction.cross(Vec3.Up)
                            if defaultBranch.norm > 1e-9 then f.applyBendForFinalDir(90.0, defaultBranch.normalized)
                            else f.applyBendForFinalDir                             (90.0, Vec3.Rear               )
                val (smPos, frameResult) =
                    SymmetryPlaneConfig.fromIncomingWithRotation(f.direction, symmetryPlaneAzimuth) match
                        case Right(config) =>
                            val pos =
                                if isSplit && !state.firstSplitConsumed && splitPosition.isDefined then
                                    splitPosition.get
                                else state.currentPosition
                            (
                                SplitMergePosition(
                                    idx,
                                    pos,
                                    f,
                                    frameAfter.direction,
                                    isSplit,
                                    config
                                ),
                                frameAfter
                            )
                        case Left(_)       =>
                            (null, f)
                (smPos, frameResult) match
                    case (null, f2       ) =>
                        (state.copy(frame = f2), TrackingOutputs(Vector.empty, Vector.empty, Vector(name)))
                    case (smPos, newFrame) =>
                        val newState =
                            if isSplit && !state.firstSplitConsumed && splitPosition.isDefined then
                                state.copy (frame = newFrame, firstSplitConsumed = true)
                            else state.copy(frame = newFrame                           )
                        (newState, TrackingOutputs(Vector.empty, Vector(smPos), Vector.empty))

            case CmdSection(length)                             =>
                val l       = length.toUnit[Meter].value
                val base    = state.frame.direction
                val dir     = if l < 0 then base * -1.0 else base
                val dist    = math.abs(l)
                val disp    = dir * dist
                val endPt   = state.currentPosition + disp
                val segment = PipeSegmentPosition(
                    elementIndex = idx,
                    startPoint   = state.currentPosition,
                    endPoint     = endPt,
                    direction    = dir,
                    length       = dist,
                    innerShape   = state.currentInnerShape,
                    frame        = state.frame
                )
                (state.copy(currentPosition = endPt), TrackingOutputs(Vector(segment), Vector.empty, Vector.empty))
            case CmdSectionSloppedForceManual(length, elevGain) =>
                val l       = length.toUnit[Meter].value
                val eg      = elevGain.toUnit[Meter].value
                val hDist   = math.sqrt(math.max(0.0, l * l - eg * eg))
                val disp    = horizontalDirection(state.frame) * hDist + Vec3(0, 0, eg)
                val dir     = disp.normalized
                val endPt   = state.currentPosition + disp
                val segment = PipeSegmentPosition(
                    elementIndex = idx,
                    startPoint   = state.currentPosition,
                    endPoint     = endPt,
                    direction    = dir,
                    length       = l,
                    innerShape   = state.currentInnerShape,
                    frame        = state.frame
                )
                (state.copy(currentPosition = endPt), TrackingOutputs(Vector(segment), Vector.empty, Vector.empty))

            case CmdNoOp =>
                (state, TrackingOutputs.empty)

    /**
     * Maps DTO elements to pipe commands using typed accessor traits.
     *
     * All three v7 DTO families (FlowOnlyPipeDescr_13384_V4, FlowOnlyPipeDescr_15544_V4,
     * ThermalPipeDescr_13384_V4) mix in the same accessor traits from `domain`, so a
     * single pattern-match covers all families. No reflection, no casts, no string matching.
     */
    private def mapCommon(elem: Any): Seq[PipeCommand] = elem match
        case e: HasSplitMergeData =>
            Seq(
                CmdSplitMerge90(
                    e.absDir,
                    isSplit = e.n_flows == 2,
                    name    = e.name,
                    e.symmetryPlaneAzimuth
                )
            )

        case e: HasDirectionChangeData =>
            Seq(CmdDirectionChange(e.absDir, e.angle))

        case e: HasInnerShapeValue =>
            Seq(CmdSetInnerShape(e.innerShapeValue))

        case e: HasSectionElevation =>
            Seq(CmdSectionSloppedForceManual(e.sectionLength, e.sectionElevationGain))

        case e: HasSectionLength =>
            Seq(CmdSection(e.sectionLength))

        case _ => Seq(CmdNoOp)

    private def mapThermal13384(elem: ThermalPipeDescr_13384): Seq[PipeCommand] =
        elem match
            case SetPropertiesInBatch(_, props, _) =>
                props.collect { case e: HasInnerShapeValue => CmdSetInnerShape(e.innerShapeValue) }.toSeq
            case LinedFlue(_, liner, _, _)         =>
                liner.props.collect { case e: HasInnerShapeValue => CmdSetInnerShape(e.innerShapeValue) }.toSeq
            case _                                 => mapCommon(elem)

    private def computeGenericPipePositions[T](
        elems                   : Seq[T],
        map                     : T => Seq[PipeCommand],
        initialDirection        : PipeInitialDirection,
        externalFrame           : Option[PipeFrame],
        startPoint              : Vec3,
        currentInnerShapeInitial: Option[PipeShape],
        splitPosition           : Option[Vec3]
    ): PipePositionResult =
        val initialState = TrackerState(
            frame              = externalFrame.getOrElse(
                PipeFrame.initial(
                    Vec3.fromAzimuthElevation(
                        initialDirection.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0                         ),
                        InclinationDirection.toDegrees                                    (initialDirection.inclination)
                    )
                )
            ),
            currentPosition    = startPoint,
            currentInnerShape  = currentInnerShapeInitial,
            firstSplitConsumed = false
        )

        val commandsWithIdx = elems.zipWithIndex.flatMap: (elem, idx) =>
            map(elem).map(cmd => (idx, cmd))

        val (finalState, allOutputs) = commandsWithIdx.foldLeft((initialState, TrackingOutputs.empty)): (acc, pair) =>
            val (state, accOutputs) = acc
            val (idx, cmd         ) = pair
            val (newState, outputs) = processCommand(cmd, state, idx, splitPosition)
            (newState, accOutputs.combineWith(outputs))

        PipePositionResult(
            allOutputs.segments,
            finalState.currentPosition,
            Some(finalState.frame),
            allOutputs.splitMergePos,
            allOutputs.errors
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
            (elem: FlowOnlyPipeDescr_13384) => mapCommon(elem)
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
            (elem: FlowOnlyPipeDescr_15544) => mapCommon(elem)
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

    private def horizontalDirection(frame: PipeFrame): Vec3 =
        val d     = frame.direction
        val horiz = Vec3(d.x, d.y, 0.0)
        if horiz.norm < 1e-9 then Vec3.Rear
        else horiz.normalized
