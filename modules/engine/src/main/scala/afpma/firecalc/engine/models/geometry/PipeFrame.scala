/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

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

  /** Display string for UI using 3-tier cardinal logic */
  def directionDisplayString: String = direction.toDisplayString

  /**
   * Compute the roll angle (degrees) such that `applyBend(90, roll)` produces `targetDir`.
   * Returns None if `targetDir` is not perpendicular to `direction` (not reachable at 90°).
   *
   * Used to compute context-aware preset button labels in the UI.
   */
  def rollAngleForOutputDirection(targetDir: Vec3): Option[Double] =
    val dot = direction.dot(targetDir.normalized)
    if math.abs(dot) > 1e-6 then None
    else
      // For 90° bend: newDir = bendAxis × direction → bendAxis = direction × targetDir
      val bendAxis = direction.cross(targetDir).normalized
      val rightRef = direction.cross(upRef).normalized
      val cosR     = rightRef.dot(bendAxis)
      val sinR     = -upRef.dot(bendAxis)
      Some(math.toDegrees(math.atan2(sinR, cosR)))

  /**
   * All cardinal directions reachable via a 90° bend from this frame,
   * each paired with the required roll angle (degrees). Sorted by roll angle.
   *
   * Use this to build context-aware roll preset buttons in the UI.
   */
  def reachableCardinals: List[(Vec3, Double)] =
    List(Vec3.Up, Vec3.Down, Vec3.Rear, Vec3.Front, Vec3.Right, Vec3.Left)
      .flatMap(t => rollAngleForOutputDirection(t).map(r => (t, r)))
      .sortBy(_._2)

object PipeFrame:

  /**
   * Create initial frame from a direction vector.
   * upRef is computed from direction + gravity convention:
   *   - Vertical up (+Z): upRef = Rear (+Y)
   *   - Vertical down (-Z): upRef = Rear (+Y)
   *   - Otherwise: project gravity direction onto plane perpendicular to direction (Gram-Schmidt)
   */
  def initial(direction: Vec3): PipeFrame =
    val dir = direction.normalized
    val gravity = Vec3(0, 0, -1)
    // If pipe is vertical (parallel to gravity), use Rear as upRef
    val absDot = math.abs(dir.dot(gravity))
    val upRef =
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
