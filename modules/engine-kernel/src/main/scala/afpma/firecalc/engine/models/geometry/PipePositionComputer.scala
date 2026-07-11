/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.domain.FireboxCoordinateSystem

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

    // ── Private helpers (extracted from AutoCalcHelper) ────────────────────

    /** Vertical extent of a pipe cross-section in meters. */
    def innerHeight(shape: PipeShape): Double = shape match
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
        if t == Double.MaxValue then
            (FireboxCoordinateSystem.FireboxBaseCenterX, FireboxCoordinateSystem.FireboxBaseCenterY)
        else (dx * t, dy * t)

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
        val dir  = PipeDirection.directionToVec3(direction)
        val topZ = boxZBottom + boxZHeight

        if math.abs(dir.z) > FireboxCoordinateSystem.NearlyVerticalThreshold then
            // Vertical: center of top face
            Position3D       (
                FireboxCoordinateSystem.FireboxBaseCenterX.m,
                FireboxCoordinateSystem.FireboxBaseCenterY.m,
                topZ.m
            )
        else computeSideStart(dir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, innerShape)

    /**
     * Compute the branch start position when the first post-firebox element is a split.
     *
     * When a split is the first element, branch one starts at the firebox top center
     * displaced by the split's absolute direction projected onto the firebox boundary.
     * Z is top-aligned: topZ - innerHeight/2.
     *
     * This is the same geometry as SplitMergeTwoHelper.branchOneStartPosition:
     *   branchStart = fireboxTopCenter + projectOnBoundary(absDir)
     *
     * @param absDir The absolute direction of branch one from the split.
     * @param boxXWidth Firebox width in meters.
     * @param boxYDepth Firebox depth in meters.
     * @param boxZBottom Firebox bottom Z (usually 0.0).
     * @param boxZHeight Firebox height in meters.
     * @param innerShape Inner shape of branch one pipe.
     * @return The full Position3D for branch one's start.
     */
    def computeBranchStartAfterSplit(
        absDir    : AbsoluteDirection,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double,
        innerShape: PipeShape
    ): Position3D =
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(absDir)
        val dir = Vec3.fromAzimuthElevation(azDeg, elDeg)
        computeSideStart(dir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, innerShape)

    /**
     * Compute the split anchor position for the first post-firebox split.
     *
     * This is the geometric center of the firebox top, used as the symmetry-plane
     * anchor for split/merge validation.
     *
     * Z coordinate:
     *   - Vertical split (Up/Down): firebox top face center (boxZBottom + boxZHeight)
     *   - Horizontal split: top-aligned pipe center (boxZBottom + boxZHeight - innerHeight/2)
     *
     * XY: always the firebox center (FireboxBaseCenterX, FireboxBaseCenterY).
     *
     * @param boxZBottom Firebox bottom Z (usually 0.0).
     * @param boxZHeight Firebox height in meters.
     * @param firstInnerShapeHeightInMeter Vertical extent of the first pipe cross-section.
     * @param absDir Absolute direction of the split (inclination determines vertical vs horizontal).
     * @return Vec3 split anchor position.
     */
    def computeSplitPosition(
        boxZBottom                  : Double,
        boxZHeight                  : Double,
        firstInnerShapeHeightInMeter: Double,
        absDir                      : AbsoluteDirection
    ): Vec3 =
        val topZ = boxZBottom + boxZHeight
        if isVertical(absDir                                                                                      ) then
            Vec3     (FireboxCoordinateSystem.FireboxBaseCenterX, FireboxCoordinateSystem.FireboxBaseCenterY, topZ)
        else
            Vec3     (
                FireboxCoordinateSystem.FireboxBaseCenterX,
                FireboxCoordinateSystem.FireboxBaseCenterY,
                topZ - firstInnerShapeHeightInMeter / 2.0
            )

    private def isVertical(absDir: AbsoluteDirection): Boolean =
        absDir.inclination match
            case InclinationDirection.Up | InclinationDirection.Down => true
            case InclinationDirection.Horizontal                     => false
            case InclinationDirection.Custom(el)                     =>
                math.abs(math.sin(math.toRadians(el.value))) > FireboxCoordinateSystem.NearlyVerticalThreshold

    /**
     * Compute a side-face start position top-aligned with the box.
     *
     * Z = boxZBottom + boxZHeight - innerHeight/2 (top-aligned).
     * X/Y are the direction projected onto the box boundary.
     */
    private def computeSideStart(
        dir       : Vec3,
        boxXWidth : Double,
        boxYDepth : Double,
        boxZBottom: Double,
        boxZHeight: Double,
        innerShape: PipeShape
    ): Position3D =
        val topZ      = boxZBottom + boxZHeight
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
        computeAirIntakeConnection(
            PipeDirection.directionToVec3(direction),
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            innerShape
        )

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

        if direction.z > FireboxCoordinateSystem.NearlyVerticalThreshold then
            // Vertical Up: enters from below → center of bottom face
            Position3D(
                FireboxCoordinateSystem.FireboxBaseCenterX.m,
                FireboxCoordinateSystem.FireboxBaseCenterY.m,
                boxZBottom.m
            )
        else if direction.z < -FireboxCoordinateSystem.NearlyVerticalThreshold then
            // Vertical Down: enters from above → center of top face
            Position3D(
                FireboxCoordinateSystem.FireboxBaseCenterX.m,
                FireboxCoordinateSystem.FireboxBaseCenterY.m,
                topZ.m
            )
        else
            val ih        = innerHeight(innerShape)
            val z         = boxZBottom + ih / 2.0
            val halfWidth = boxXWidth / 2.0
            val halfDepth = boxYDepth / 2.0
            val (x, y) = rayIntersectBoxSurface(direction, halfWidth, halfDepth)
            Position3D(x.m, y.m, z.m)

end PipePositionComputer
