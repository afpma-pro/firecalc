/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

/**
 * Pure geometry computation for pipe start positions.
 *
 * Single source of truth for position geometry — called by both the engine loader
 * (initial load) and the UI (reactive updates). No UI dependencies, no Airstream,
 * no Laminar.
 *
 * Coordinate system: +X = right, +Y = rear, +Z = up. Box centered at origin (0, 0).
 *
 * Parameter naming convention: `boxXWidth`, `boxYDepth`, `boxZBottom`, `boxZHeight`
 * — generic box semantics. The caller determines which box (firebox or air distribution
 * box) and passes the appropriate dimensions.
 */
object PipePositionComputer:

    /**
     * Threshold for treating a direction as "nearly vertical".
     * cos(8°) ≈ 0.99 — pipes within ~8° of vertical are treated as vertical.
     */
    private val NearlyVerticalThreshold = 0.99

    // ── Private helpers (extracted from AutoCalcHelper) ────────────────────

    /** Vertical extent of a pipe cross-section in meters. */
    private def innerHeight(shape: PipeShape): Double = shape match
        case Circle(d)       => d.value
        case Square(s)       => s.value
        case Rectangle(_, b) => b.value // b = height

    /**
     * Per-axis normalization: project direction onto a box side boundary.
     *
     * Each horizontal component is divided by max(|dir.x|, |dir.y|) then scaled
     * to its box half-dimension. This gives:
     *   - Cardinal directions → face center
     *   - Diagonal directions → corner
     *   - Custom angles → smooth interpolation between face and corner
     */
    private def projectOnBoundary(dir: Vec3, halfWidth: Double, halfDepth: Double): (Double, Double) =
        val scale = math.max(math.abs(dir.x), math.abs(dir.y))
        val x     = if scale > 1e-9 then (dir.x / scale) * halfWidth else 0.0
        val y     = if scale > 1e-9 then (dir.y / scale) * halfDepth else 0.0
        (x, y)

    /**
     * Ray-box intersection: find where a ray from the box center in direction −dir
     * exits the box surface. This gives the entry point where a pipe flowing in
     * direction `dir` would connect to the box while pointing at the center.
     */
    private def rayIntersectBoxSurface(dir: Vec3, halfWidth: Double, halfDepth: Double): (Double, Double) =
        val dx = -dir.x
        val dy = -dir.y
        val tx = if math.abs(dx) > 1e-9 then halfWidth / math.abs(dx) else Double.MaxValue
        val ty = if math.abs(dy) > 1e-9 then halfDepth / math.abs(dy) else Double.MaxValue
        val t  = math.min(tx, ty)
        if t == Double.MaxValue then (0.0, 0.0      )
        else                         (dx * t, dy * t)

    /** Convert a PipeInitialDirection to a Vec3. */
    private def directionToVec3(dir: PipeInitialDirection): Vec3 =
        Vec3.fromAzimuthElevation(
            dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
            InclinationDirection.toDegrees                       (dir.inclination)
        )

    // ── Shape extraction ───────────────────────────────────────────────────

    /**
     * Extract the first inner shape from the first slot in a post-firebox chain.
     *
     * Matches both FlueSlot (flow-only) and ThermalFlueSlot / ConnectorSlot /
     * ChimneySlot (thermal) descriptors. Returns None for NoFlueSlot or when
     * no SetInnerShape is present in the first slot's descriptors.
     *
     * Single source of truth — called by the engine loader (initial load) and
     * the UI (reactive updates).
     */
    def firstInnerShapeIn(slots: Seq[PostFireboxPipeDescrSlot_V7]): Option[PipeShape] =
        import PostFireboxPipeDescrSlot_V7.*
        import SetFlowOnlyPipeProp_15544.SetInnerShape as FlowOnlySetInnerShape
        import SetThermalPipeProp_13384.SetInnerShape as ThermalSetInnerShape
        slots.headOption.flatMap {
            case FlueSlot(descr)        => descr.collectFirst { case FlowOnlySetInnerShape(shape) => shape }
            case ThermalFlueSlot(descr) => descr.collectFirst { case ThermalSetInnerShape(shape) => shape }
            case ConnectorSlot(descr)   => descr.collectFirst { case ThermalSetInnerShape(shape) => shape }
            case ChimneySlot(descr)     => descr.collectFirst { case ThermalSetInnerShape(shape) => shape }
            case NoFlueSlot             => None
        }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Default pipe shape used when no inner shape is found in the descriptor sequence.
     *  20cm circle is the most common air intake / post-firebox pipe size.
     */
    val DefaultPipeShape: PipeShape = PipeShape.circle(0.2.m)

    /**
     * Compute the start position for a post-firebox pipe exiting from the box.
     *
     * Top-aligned: the top of the pipe opening aligns with the top of the box.
     *   - Vertical (|dir.z| > NearlyVerticalThreshold): center of top face → (0, 0, boxZBottom + boxZHeight)
     *   - Non-vertical: side face at `boxZBottom + boxZHeight - innerHeight/2`
     */
    def computePostFireboxStart(
        direction : PipeInitialDirection,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double,
        innerShape: PipeShape
    ): Position3D =
        val dir  = directionToVec3(direction)
        val topZ = boxZBottom + boxZHeight

        if math.abs(dir.z) > NearlyVerticalThreshold then
            // Vertical: center of top face
            Position3D(0.0.m, 0.0.m, topZ.m)
        else
            val ih        = innerHeight(innerShape)
            val z         = topZ - ih / 2.0
            val halfWidth = boxXWidth / 2.0
            val halfDepth = boxYDepth / 2.0
            val (x, y) = projectOnBoundary(dir, halfWidth, halfDepth)
            Position3D(x.m, y.m, z.m)

    /**
     * Compute the connection point for an air intake pipe entering the box.
     *
     * Bottom-aligned: the bottom of the pipe opening aligns with the bottom of the box.
     * Uses ray-box intersection so the pipe's flow direction vector points toward
     * the box center from the entry point.
     *
     *   - Vertical Up (dir.z > NearlyVerticalThreshold): enters from below → center of bottom face
     *   - Vertical Down (dir.z < −NearlyVerticalThreshold): enters from above → center of top face
     *   - Non-vertical: ray from center in −dir hits box surface at entry point
     */
    def computeAirIntakeConnection(
        direction : PipeInitialDirection,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double,
        innerShape: PipeShape
    ): Position3D =
        computeAirIntakeConnection(directionToVec3(direction), boxXWidth, boxYDepth, boxZBottom, boxZHeight, innerShape)

    /** Compute the connection point for an air intake pipe (Vec3 overload for internal use). */
    def computeAirIntakeConnection(
        direction : Vec3,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double,
        innerShape: PipeShape
    ): Position3D =
        val topZ = boxZBottom + boxZHeight

        if direction.z > NearlyVerticalThreshold then
            // Vertical Up: enters from below → center of bottom face
            Position3D(0.0.m, 0.0.m, boxZBottom.m)
        else if direction.z < -NearlyVerticalThreshold then
            // Vertical Down: enters from above → center of top face
            Position3D(0.0.m, 0.0.m, topZ.m)
        else
            val ih        = innerHeight(innerShape)
            val z         = boxZBottom + ih / 2.0
            val halfWidth = boxXWidth / 2.0
            val halfDepth = boxYDepth / 2.0
            val (x, y) = rayIntersectBoxSurface(direction, halfWidth, halfDepth)
            Position3D(x.m, y.m, z.m)

    /**
     * Replay the air intake descriptor sequence from Origin and return the raw result.
     * Used by both `computeAirIntakeFinalAuto` and `computeAirIntakeConnectionPoint`.
     */
    private def replayAirIntakeFromOrigin(
        descr                : Seq[FlowOnlyPipeDescr_13384],
        initialDir           : PipeInitialDirection
    ): PipePositionResult =
        PositionTracker.computeFlowOnly13384(
            elems            = descr,
            initialDirection = initialDir,
            externalFrame    = None,
            startPoint       = Vec3(0, 0, 0)
        )

    /**
     * Compute the target connection point on the air distribution box surface.
     * This is the end position displayed in FinalAuto mode — the point where the
     * pipe connects to the box.
     */
    def computeAirIntakeConnectionPoint(
        descr     : Seq[FlowOnlyPipeDescr_13384],
        initialDir: PipeInitialDirection,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double
    ): Position3D =
        if descr.isEmpty then Position3D(0.0.m, 0.0.m, (boxZBottom + boxZHeight).m)
        else
            val result   = replayAirIntakeFromOrigin(descr, initialDir)
            val finalDir = result.finalFrame.map(_.direction).getOrElse(directionToVec3(initialDir))
            computeAirIntakeConnection (
                direction  = finalDir,
                boxXWidth  = boxXWidth,
                boxYDepth  = boxYDepth,
                boxZBottom = boxZBottom,
                boxZHeight = boxZHeight,
                innerShape =
                    result.segments.lastOption.flatMap(_.innerShape).getOrElse(PipePositionComputer.DefaultPipeShape)
            )

    /**
     * Compute the effective start position for an air intake pipe in FinalAuto mode.
     *
     * Full reverse computation:
     *   1. Replay descriptor sequence from Origin → get raw final position and direction
     *   2. Compute target connection point (air distribution box surface)
     *   3. offset = target - rawFinal
     *   4. Return offset as effective start position
     *
     * When no inner shape is found in the descriptor sequence, falls back to
     * `DefaultPipeShape` (20 cm circle) for the Z-offset calculation in step 2.
     */
    def computeAirIntakeFinalAuto(
        descr     : Seq[FlowOnlyPipeDescr_13384],
        initialDir: PipeInitialDirection,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double
    ): Position3D =
        if descr.isEmpty then
            // Edge case: empty descriptor sequence — return box surface position directly
            Position3D(0.0.m, 0.0.m, (boxZBottom + boxZHeight).m)
        else
            // Step 1: Replay descriptor sequence from Origin to get raw final position and direction
            val result     = replayAirIntakeFromOrigin(descr, initialDir)
            val rawFinal   = result.finalPoint
            val finalFrame = result.finalFrame

            // Step 2: Compute target connection point (air distribution box surface)
            // Use the final direction from the replay to determine entry point
            val finalDir = finalFrame.map(_.direction).getOrElse(directionToVec3(initialDir))
            val target   = computeAirIntakeConnection(
                direction  = finalDir,
                boxXWidth  = boxXWidth,
                boxYDepth  = boxYDepth,
                boxZBottom = boxZBottom,
                boxZHeight = boxZHeight,
                innerShape =
                    result.segments.lastOption.flatMap(_.innerShape).getOrElse(PipePositionComputer.DefaultPipeShape)
            )

            // Step 3: offset = target - rawFinal
            val targetVec = target.toVec3
            val offset    = targetVec - rawFinal

            // Step 4: Return offset as effective start position
            Position3D(offset.x.m, offset.y.m, offset.z.m)

end PipePositionComputer
