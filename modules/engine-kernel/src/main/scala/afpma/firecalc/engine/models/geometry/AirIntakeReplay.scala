/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.domain.FireboxCoordinateSystem
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.*

/**
 * Air intake pipe position replay.
 *
 * Replays an air intake descriptor sequence from the firebox base origin,
 * then computes the effective start position (FinalAuto mode) or the
 * target connection point on the air distribution box surface.
 *
 * Stays in the `geometry` package alongside `PipePositionComputer` —
 * three methods is not enough to justify a new package, and callers
 * benefit from a single import for the pipe-positioning workflow.
 */
object AirIntakeReplay:
    /** Result of a single auto-mode computation: effective start offset and box connection point. */
    case class AirIntakeAutoResult(offset: Position3D, connectionPoint: Position3D)

    /** Default position returned when the descriptor sequence is empty. */
    private def emptyDescriptorPosition(boxZBottom: Double, boxZHeight: Double): Position3D =
        Position3D(
            FireboxCoordinateSystem.FireboxBaseCenterX.m,
            FireboxCoordinateSystem.FireboxBaseCenterY.m,
            (boxZBottom + boxZHeight).m
        )

    /** Resolve the inner shape to use for this pipe direction. */
    private def resolveInnerShape(
        finalDir     : Vec3,
        innerShapeOpt: Option[PipeShape],
        result       : PipePositionResult
    ): PipeShape =
        if finalDir.z.abs > FireboxCoordinateSystem.NearlyVerticalThreshold then
            innerShapeOpt.getOrElse(PipeShape.InnerShapeFallbackCompute)
        else
            innerShapeOpt
                .orElse(result.segments.lastOption.flatMap(_.innerShape))
                .getOrElse         (PipeShape.InnerShapeFallbackCompute)

    /**
     * Replay the air intake descriptor sequence from Origin and return the raw result.
     * Used by `computeAirIntakeAuto` and external callers (e.g. FireCalcYAML_Loader).
     */
    def replayAirIntakeFromOrigin(
        descr                : Seq[FlowOnlyPipeDescr_13384],
        initialDir           : PipeInitialDirection
    ): PipePositionResult =
        PositionTracker.computeFlowOnly13384(
            elems            = descr,
            initialDirection = initialDir,
            externalFrame    = None,
            startPoint       = Vec3(
                FireboxCoordinateSystem.FireboxBaseCenterX,
                FireboxCoordinateSystem.FireboxBaseCenterY,
                FireboxCoordinateSystem.FireboxBaseCenterZ
            )
        )

    /**
     * Single source of truth: replay once, compute both offset and connection point.
     *
     * Full reverse computation:
     *   1. Replay descriptor sequence from Origin → get raw final position and direction
     *   2. Compute target connection point (air distribution box surface)
     *   3. offset = target - rawFinal
     *   4. Return both offset (effective start) and connectionPoint
     */
    def computeAirIntakeAuto(
        descr        : Seq[FlowOnlyPipeDescr_13384],
        initialDir   : PipeInitialDirection,
        boxXWidth    : Double,
        boxYDepth    : Double,
        boxZBottom   : Double,
        boxZHeight   : Double,
        innerShapeOpt: Option[PipeShape]
    ): AirIntakeAutoResult =
        if descr.isEmpty then
            val empty = emptyDescriptorPosition(boxZBottom, boxZHeight)
            AirIntakeAutoResult(empty, empty)
        else
            val result        = replayAirIntakeFromOrigin(descr, initialDir)
            val rawFinal      = result.finalPoint
            val finalFrame    = result.finalFrame
            val finalDir      = finalFrame.map(_.direction).getOrElse(PipeDirection.directionToVec3(initialDir))
            val innerShapeOpt = result.segments.lastOption.flatMap(_.innerShape)
            val shape         = resolveInnerShape(finalDir, innerShapeOpt, result)

            val connectionPoint = PipePositionComputer.computeAirIntakeConnection(
                direction  = finalDir,
                boxXWidth  = boxXWidth,
                boxYDepth  = boxYDepth,
                boxZBottom = boxZBottom,
                boxZHeight = boxZHeight,
                innerShape = shape
            )

            val targetVec = connectionPoint.toVec3
            val offset    = targetVec - rawFinal
            val offsetPos = Position3D(offset.x.m, offset.y.m, offset.z.m)

            AirIntakeAutoResult(offsetPos, connectionPoint)

    /**
     * Compute the effective start position for an air intake pipe in Auto mode.
     * Thin wrapper around [[computeAirIntakeAuto]] returning the offset.
     */
    def computeAirIntakeFinalAuto(
        descr        : Seq[FlowOnlyPipeDescr_13384],
        initialDir   : PipeInitialDirection,
        boxXWidth    : Double,
        boxYDepth    : Double,
        boxZBottom   : Double,
        boxZHeight   : Double,
        innerShapeOpt: Option[PipeShape]
    ): Position3D =
        computeAirIntakeAuto(descr, initialDir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, innerShapeOpt).offset

    /**
     * Compute the target connection point on the air distribution box surface.
     * Thin wrapper around [[computeAirIntakeAuto]] returning the connection point.
     */
    def computeAirIntakeConnectionPoint(
        descr        : Seq[FlowOnlyPipeDescr_13384],
        initialDir   : PipeInitialDirection,
        boxXWidth    : Double,
        boxYDepth    : Double,
        boxZBottom   : Double,
        boxZHeight   : Double,
        innerShapeOpt: Option[PipeShape]
    ): Position3D =
        computeAirIntakeAuto(
            descr,
            initialDir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            innerShapeOpt
        ).connectionPoint

end AirIntakeReplay
