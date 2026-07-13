/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.{AbsoluteDirection, AzimuthDirection, InclinationDirection, IsSplitMergeTurn}

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

    /** Immutable accumulator for pipe position tracking. */
    private case class TrackerState(
        frame             : Option[PipeFrame],
        currentPosition   : Vec3,
        currentInnerShape : Option[PipeShape],
        firstSplitConsumed: Boolean
    )

    /** Collected outputs from processing commands. */
    private case class TrackingOutputs(
        segments     : Seq[PipeSegmentPosition],
        splitMergePos: Seq[SplitMergePosition],
        errors       : Vector[String]
    )

    private object TrackingOutputs:
        val empty: TrackingOutputs = TrackingOutputs(Nil, Nil, Vector.empty)

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
                val newFrame =
                    for
                        f  <- state.frame
                        fd <- absDir
                    yield
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        f.applyBendForFinalDir(angle.toUnit[Degree].value, targetVec)
                (state.copy(frame = newFrame.orElse(state.frame)), TrackingOutputs.empty)

            case CmdSplitMerge90(absDir, isSplit, name, symmetryPlaneAbsDir) =>
                val result = state.frame.map: f =>
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
                        SymmetryPlaneConfig.fromIncomingWithAbsDir(f.direction, symmetryPlaneAbsDir) match
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
                        case (null, _        ) =>
                            (state.copy(frame = Some(f)), TrackingOutputs(Nil, Nil, Vector(name)))
                        case (smPos, newFrame) =>
                            val newState =
                                if isSplit && !state.firstSplitConsumed && splitPosition.isDefined then
                                    state.copy (frame = Some(newFrame), firstSplitConsumed = true)
                                else state.copy(frame = Some(newFrame)                           )
                            (newState, TrackingOutputs(Nil, Seq(smPos), Vector.empty))

                val (newState, outputs) = result.getOrElse((state, TrackingOutputs.empty))
                (newState, outputs)

            case c: (CmdSectionVertical | CmdSectionHorizontal) =>
                val l          = c match
                    case CmdSectionVertical(eg)   => eg.toUnit[Meter].value
                    case CmdSectionHorizontal(hl) => hl.toUnit[Meter].value
                val defaultDir = c match
                    case CmdSectionVertical(_)   => Vec3.Up
                    case CmdSectionHorizontal(_) => Vec3.Rear
                val base       = state.frame.map(_.direction).getOrElse(defaultDir)
                val dir        = if l < 0 then base * -1.0 else base
                val dist       = math.abs(l)
                val disp       = dir * dist
                val endPt      = state.currentPosition + disp
                val segment    = PipeSegmentPosition(
                    elementIndex = idx,
                    startPoint   = state.currentPosition,
                    endPoint     = endPt,
                    direction    = dir,
                    length       = dist,
                    innerShape   = state.currentInnerShape,
                    frame        = state.frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                )
                (state.copy(currentPosition = endPt), TrackingOutputs(Seq(segment), Nil, Vector.empty))

            case c: (CmdSectionSlopped | CmdSectionSloppedForceManual) =>
                val l       = c match
                    case CmdSectionSlopped(len)               => len.toUnit[Meter].value
                    case CmdSectionSloppedForceManual(len, _) => len.toUnit[Meter].value
                val eg      = c match
                    case CmdSectionSlopped(_)                    => state.frame.map(f => l * f.direction.z).getOrElse(0.0)
                    case CmdSectionSloppedForceManual(_, egGain) => egGain.toUnit[Meter].value
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
                    frame        = state.frame.getOrElse(PipeFrame.initial(Vec3.Rear))
                )
                (state.copy(currentPosition = endPt), TrackingOutputs(Seq(segment), Nil, Vector.empty))

            case CmdNoOp =>
                (state, TrackingOutputs.empty)

    /* Maps the 8 common cases shared by all three descriptor families.
     *
     * The DTO case classes have the same names but live in different packages
     * (13384_V4 vs 15544_V4 vs Thermal_13384_V4), so we can't share a single
     * pattern-match. Instead we:
     * - Match on domain marker trait IsSplitMergeTurn (shared across packages),
     *   using productElement for field access since the trait is a marker.
     * - Use runtime class-name matching + productElement for structurally
     *   identical case classes (AddDirectionChange subclasses, SetInnerShape,
     *   AddSection*) whose field order is guaranteed identical across packages.
     *   AddDirectionChange subclasses are detected via superclass chain check.
     */
    private def mapCommon(elem: Any): Seq[PipeCommand] = elem match
        // IsSplitMergeTurn: shared marker trait; fields via productElement
        //   (product indices: 0=name, 1=absDir, 2=newInnerShape, 3=symmetryPlaneAbsDir)
        case _: IsSplitMergeTurn =>
            val p       = elem.asInstanceOf[Product]
            val absDir  = p.productElement(1).asInstanceOf[Option[AbsoluteDirection]]
            val symDir  = p.productElement(3).asInstanceOf[Option[AbsoluteDirection]]
            val isSplit = elem.getClass.getSimpleName.startsWith("Split")
            Seq(CmdSplitMerge90(absDir, isSplit, p.productElement(0).asInstanceOf[String], symDir))

        // AddDirectionChange subclasses: check superclass chain for "AddDirectionChange"
        //   (product indices: 0=name, 1=angle, 2=absDir)
        case e if isAddDirectionChange(e) =>
            val p = e.asInstanceOf[Product]
            Seq(
                CmdDirectionChange(
                    p.productElement(2).asInstanceOf[Option[AbsoluteDirection]],
                    p.productElement(1).asInstanceOf[Angle]
                )
            )

        // SetInnerShape + AddSection*: same simple name across packages
        case _ if elem.getClass.getSimpleName == "SetInnerShape"                             =>
            Seq(CmdSetInnerShape(elem.asInstanceOf[Product].productElement(0).asInstanceOf[PipeShape]))
        case _ if elem.getClass.getSimpleName == "AddSectionVertical"                        =>
            Seq(CmdSectionVertical(elem.asInstanceOf[Product].productElement(1).asInstanceOf[Length]))
        case _ if elem.getClass.getSimpleName == "AddSectionHorizontal"                      =>
            Seq(CmdSectionHorizontal(elem.asInstanceOf[Product].productElement(1).asInstanceOf[Length]))
        case _ if elem.getClass.getSimpleName == "AddSectionSlopped"                         =>
            Seq(CmdSectionSlopped(elem.asInstanceOf[Product].productElement(1).asInstanceOf[Length]))
        case _ if elem.getClass.getSimpleName == "AddSectionSloppedForceManualElevationGain" =>
            val p = elem.asInstanceOf[Product]
            Seq(
                CmdSectionSloppedForceManual(
                    p.productElement(1).asInstanceOf[Length],
                    p.productElement(2).asInstanceOf[Length]
                )
            )
        case _                                                                               => Seq(CmdNoOp)

    // Check if elem extends AddDirectionChange by walking the superclass chain.
    // AddDirectionChange is a sealed abstract class in each DTO package;
    // its subclasses (AddAngleAdjustable, AddSharpeAngle_*, AddSmoothCurve_*, etc.)
    // have different simple names but share the same superclass.
    private def isAddDirectionChange(e: Any): Boolean =
        var c: Class[?] = e.getClass
        while c != null && c != classOf[Object] do
            if c.getSimpleName == "AddDirectionChange" then return true
            c = c.getSuperclass
        false

    private def mapFlowOnly13384(elem: FlowOnlyPipeDescr_13384): Seq[PipeCommand] =
        mapCommon(elem)

    private def mapFlowOnly15544(elem: FlowOnlyPipeDescr_15544): Seq[PipeCommand] =
        mapCommon(elem)

    private def mapThermal13384(elem: ThermalPipeDescr_13384): Seq[PipeCommand] =
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        elem match
            case SetPropertiesInBatch(_, props, _) =>
                props.collect { case SetInnerShape(shape) => CmdSetInnerShape(shape) }.toSeq
            case LinedFlue(_, liner, _, _)         =>
                liner.props.collect { case SetInnerShape(shape) => CmdSetInnerShape(shape) }.toSeq
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
            frame              = externalFrame.orElse(
                Some(
                    PipeFrame.initial(
                        Vec3.fromAzimuthElevation(
                            initialDirection.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0                         ),
                            InclinationDirection.toDegrees                                    (initialDirection.inclination)
                        )
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
            finalState.frame,
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
