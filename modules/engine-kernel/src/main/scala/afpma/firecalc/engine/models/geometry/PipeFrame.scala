/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3

/**
 * Local frame that rides along a pipe.
 * direction: unit vector in the direction of flow
 * upRef: unit vector perpendicular to direction, derived from gravity convention
 *
 * After every bend, upRef is re-normalized to the gravity convention (same as PipeFrame.initial):
 *   - Vertical Up/Down → upRef = Rear (+Y)
 *   - Otherwise → Gram-Schmidt projection of +Z onto plane perpendicular to direction
 * This ensures roll angles are always interpreted relative to gravity, not to a carried-forward frame.
 *
 * Roll convention (right-hand rule around pipe axis):
 *   Positive roll = counterclockwise when looking INTO the pipe from outside (in direction of flow).
 *   Equivalently: right thumb along flow direction → fingers point in direction of increasing roll.
 *
 *   Vertical pipe (up, dir=+Z, upRef=Rear=+Y):
 *     roll 0°=Rear, 90°=Left, 180°=Front, 270°=Right
 *
 *   Horizontal pipe (rear, dir=+Y, upRef=Up=+Z):
 *     roll 0°=Up, 90°=Right, 180°=Down, 270°=Left
 */
case class PipeFrame(direction: Vec3, upRef: Vec3):

    /**
     * Apply a bend to this frame.
     * @param deflectionDeg deflection angle in degrees (how much the pipe turns)
     * @param rollDeg roll angle in degrees (right-hand rule around pipe axis — see class doc)
     * @return new PipeFrame after the bend
     */
    def applyBend(deflectionDeg: Double, rollDeg: Double): PipeFrame =
        // Step 1: find the bend axis perpendicular to direction, at rollDeg from upRef
        val rightRef = direction.cross(upRef).normalized
        val rollRad  = math.toRadians(rollDeg)
        val bendAxis = (rightRef * math.cos(rollRad) - upRef * math.sin(rollRad)).normalized

        // Step 2: rotate direction around bendAxis by deflectionDeg
        val deflRad = math.toRadians(deflectionDeg)
        val newDir  = PipeFrame.rodriguesRotate(direction, bendAxis, deflRad).normalized.snap

        // Step 3: re-normalize upRef to gravity convention (same as PipeFrame.initial)
        PipeFrame.initial(newDir)

    /** Returns (azimuth, elevation) of direction in absolute coordinates (degrees) */
    def directionAsAbsolute: (Double, Double) = direction.toAzimuthElevation

    /**
     * Compute the roll angle (degrees) such that `applyBend(deflectionDeg, roll)` produces `targetDir`.
     * Returns None if the angle between `direction` and `targetDir` is not approximately `deflectionDeg`
     * (within ~1° tolerance), meaning the target is not reachable at the given deflection.
     *
     * Algorithm:
     *   1. Check that direction.angleTo(targetDir) ≈ deflectionDeg.
     *   2. Find the bend axis that would produce targetDir: the unique axis perpendicular to
     *      direction that rotates it toward targetDir.
     *   3. Decompose the bend axis into (rightRef, upRef) components to recover roll via atan2.
     *
     * Used to compute context-aware preset button labels in the UI.
     */
    def rollAngleForOutputDirection(targetDir: Vec3, deflectionDeg: Double): Option[Double] =
        val tgt       = targetDir.normalized
        val actualDeg = direction.angleTo(tgt)
        // Tolerance: ~1° for reachability check
        if math.abs(actualDeg - deflectionDeg) > 1.0 then None
        else if deflectionDeg < 1e-6 then
            // Near-zero deflection: any roll gives same result; return 0
            Some(0.0)
        else
            // Find the bend axis from direction & targetDir:
            // bendAxis = direction.cross(targetDir).normalized
            // (unique perpendicular axis that rotates direction toward targetDir)
            val crossVec  = direction.cross(tgt)
            val crossNorm = crossVec.norm
            if crossNorm < 1e-12 then
                // direction ≈ targetDir (deflection ≈ 0°) or opposite (≈ 180°)
                Some(0.0)
            else
                val bendAxis = crossVec * (1.0 / crossNorm)
                val rightRef = direction.cross(upRef).normalized
                val cosR     = rightRef.dot(bendAxis)
                val sinR     = -upRef.dot(bendAxis)
                Some(math.toDegrees(math.atan2(sinR, cosR)))

    /** 1-arg overload: compute roll for a 90° bend (original behavior). */
    def rollAngleForOutputDirection(targetDir: Vec3): Option[Double] =
        rollAngleForOutputDirection(targetDir, 90.0)

    /**
     * Check whether `target` is geometrically reachable from this frame at the given deflection angle.
     *
     * A direction is reachable if the angle between the current frame direction and the target
     * is approximately equal to the deflection angle (within ~1° tolerance).
     *
     * This is a pure function with no side effects, suitable for use in both UI and engine code.
     *
     * @param target the target direction as a unit Vec3
     * @param deflectionDeg the deflection angle in degrees
     * @return true if the target is reachable at the given deflection angle
     */
    def isReachable(target: Vec3, deflectionDeg: Double): Boolean =
        rollAngleForOutputDirection(target, deflectionDeg).isDefined

    /**
     * Compute the deflection angle (degrees) required to reach `targetDir` from the current direction.
     * Returns `direction.angleTo(targetDir.normalized)`.
     */

    def computeRequiredDeflection(targetDir: Vec3): Double =
        direction.angleTo(targetDir.normalized)

    /**
     * All cardinal directions reachable via a bend at the given deflection angle from this frame,
     * each paired with the required roll angle (degrees). Sorted by roll angle.
     *
     * Includes the 6 axis-aligned cardinals plus 4 intermediate horizontal directions
     * (RearRight, FrontRight, FrontLeft, RearLeft).
     *
     * Use this to build context-aware roll preset buttons in the UI.
     */
    def reachableCardinals(deflectionDeg: Double = 90.0): List[(Vec3, Double)] =
        val cardinals = List(
            Vec3.Up,
            Vec3.Down,
            Vec3.Rear,
            Vec3.Front,
            Vec3.Right,
            Vec3.Left,
            // Intermediate horizontal directions (diagonal, normalized)
            Vec3(1, 1, 0).normalized, // RearRight
            Vec3(1, -1, 0).normalized, // FrontRight
            Vec3(-1, -1, 0).normalized, // FrontLeft
            Vec3(-1, 1, 0).normalized // RearLeft
        )
        cardinals
            .flatMap(t => rollAngleForOutputDirection(t, deflectionDeg).map(r => (t, r)))
            .sortBy(_._2)

    /**
     * Apply a bend that aims for a specific target direction.
     * Computes the required roll angle internally from the target direction and deflection angle.
     * If the target is not reachable at the given deflection, falls back to roll = 0°.
     */
    def applyBendForFinalDir(deflectionDeg: Double, targetDir: Vec3): PipeFrame =
        val rollDeg = rollAngleForOutputDirection(targetDir, deflectionDeg).getOrElse(0.0)
        applyBend(deflectionDeg, rollDeg)

    /**
     * Viewer-intuitive "right" direction: horizontal unit vector, 90° clockwise from the pipe's
     * horizontal heading when viewed from above.
     *
     * For vertical pipes (Up/Down), returns Right(+X) by convention.
     *
     * This differs from PipeFrame's internal `rightRef` (which equals `direction.cross(upRef)`)
     * because `rightRef` gives Left(-X) for vertical Up pipes due to the cross-product orientation.
     * `localRight` always gives the direction a viewer would intuitively call "right".
     */
    def localRight: Vec3 =
        val hProj = Vec3(direction.x, direction.y, 0)
        if hProj.norm < 1e-6 then Vec3.Right
        else
            val h = hProj.normalized
            Vec3(h.y, -h.x, 0)

    /**
     * Viewer-intuitive "up" direction: unit vector perpendicular to both
     * the pipe direction and localRight, forming a right-handed (localRight, localUp, direction) frame.
     *
     * For horizontal pipes: `localUp = (0, 0, 1)` (world Up).
     * For vertical Up pipe: `localUp = (0, -1, 0)` (Front-ish, by convention).
     */
    def localUp: Vec3 =
        localRight.cross(direction).normalized

    /**
     * Compute the target direction for a relative bend using 4-quadrant parameterization.
     *
     * The bend plane is determined by the quadrant (Right/Up/Left/Down) and theta rotation
     * within that quadrant. No `sideSign` — the direction of the bend is fully encoded
     * in the quadrant's base vectors.
     *
     * @param side which quadrant the bend aims toward
     * @param thetaDeg rotation within the quadrant, in degrees. Range: [0°, 90°].
     * @param deflectionDeg deflection angle of the bend (how much the pipe turns)
     * @return target direction as a unit Vec3 in absolute coordinates
     */
    def relativeTarget(side: PipeFrame.RelativeSide, thetaDeg: Double, deflectionDeg: Double): Vec3 =
        val lr = localRight
        val lu = localUp
        val (base, nextBase) = side.bases(lr, lu)
        val thetaRad  = math.toRadians(thetaDeg)
        val bentRight = (base * math.cos(thetaRad) + nextBase * math.sin(thetaRad)).normalized
        val bendAxis  = direction.cross(bentRight).normalized
        val deflRad   = math.toRadians(deflectionDeg)
        PipeFrame.rodriguesRotate(direction, bendAxis, deflRad).normalized.snap

    /**
     * Recover the relative (side, thetaDeg) from an absolute target direction.
     *
     * Inverse of `relativeTarget`: given a target direction and deflection angle, find which
     * (RelativeSide, theta) pair would produce it.
     *
     * @return (side, thetaDeg) where side is a RelativeSide quadrant, theta ∈ [0°, 90°].
     *         Returns (Right, 0.0) for degenerate cases (target ≈ direction or opposite).
     */
    def recoverRelative(targetDir: Vec3, deflectionDeg: Double): (PipeFrame.RelativeSide, Double) =
        import PipeFrame.RelativeSide
        val tgt      = targetDir.normalized
        val crossVec = direction.cross(tgt)
        if crossVec.norm < 1e-10 then return (RelativeSide.Right, 0.0) // degenerate

        val bendAxis  = crossVec.normalized
        val bentRight = bendAxis.cross(direction).normalized
        val lr        = localRight
        val lu        = localUp

        // Project bentRight onto the local frame basis
        val rightComp = bentRight.dot(lr)
        val upComp    = bentRight.dot(lu)

        // Full angle α ∈ [0°, 360°) measured counterclockwise from localRight
        val rawAlpha = math.toDegrees(math.atan2(upComp, rightComp))
        val alpha    = if rawAlpha < 0.0 then rawAlpha + 360.0 else rawAlpha

        // Determine quadrant and local theta
        if alpha < 90.0 then (RelativeSide.Right, alpha)
        else if alpha < 180.0 then (RelativeSide.Up, alpha - 90.0)
        else if alpha < 270.0 then (RelativeSide.Left, alpha - 180.0)
        else                       (RelativeSide.Down, alpha - 270.0)

object PipeFrame:

    /**
     * Side of a relative bend in the local frame perpendicular to the pipe.
     * The 4 quadrants span the full 360° circle around the pipe axis:
     *   Right [0°, 90°), Up [90°, 180°), Left [180°, 270°), Down [270°, 360°).
     */
    enum RelativeSide:
        case Right, Up, Left, Down

        /** Base and next-base vectors for this quadrant in the local frame. */
        def bases(localRight: Vec3, localUp: Vec3): (Vec3, Vec3) = this match
            case Right => (localRight, localUp              )
            case Up    => (localUp, localRight * -1.0       )
            case Left  => (localRight * -1.0, localUp * -1.0)
            case Down  => (localUp * -1.0, localRight       )

    /**
     * Create initial frame from a direction vector.
     * upRef is computed from direction + gravity convention:
     *   - Vertical up (+Z): upRef = Rear (+Y)
     *   - Vertical down (-Z): upRef = Rear (+Y)
     *   - Otherwise: project gravity direction onto plane perpendicular to direction (Gram-Schmidt)
     */
    def initial(direction: Vec3): PipeFrame =
        val dir     = direction.normalized
        val gravity = Vec3(0, 0, -1)
        // If pipe is vertical (parallel to gravity), use Rear as upRef
        val absDot  = math.abs(dir.dot(gravity))
        val upRef   =
            if absDot > 0.9999 then Vec3.Rear
            else
                // Gram-Schmidt: remove component of gravity along dir, then negate (so "up" projects to "up")
                val gravComp = dir * dir.dot(Vec3(0, 0, 1))
                val up       = (Vec3(0, 0, 1) - gravComp).normalized
                up
        PipeFrame(dir, upRef)

    /** Rodrigues' rotation formula: rotate v around axis k by theta radians */
    def rodriguesRotate(v: Vec3, k: Vec3, theta: Double): Vec3 =
        val kn   = k.normalized
        val cosT = math.cos(theta)
        val sinT = math.sin(theta)
        v * cosT + kn.cross(v) * sinT + kn * (kn.dot(v) * (1 - cosT))
