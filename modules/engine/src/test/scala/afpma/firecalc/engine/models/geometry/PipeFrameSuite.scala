/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

class PipeFrameSuite extends AnyFlatSpec with Matchers:

  val eps = 1e-10

  def approx(a: Double, b: Double): Boolean = math.abs(a - b) < eps

  def assertApprox(a: Double, b: Double, msg: String = ""): Unit =
    val diff = math.abs(a - b)
    withClue(s"$msg (got $a, expected $b, diff=$diff)") {
      diff should be < eps
    }

  def assertVec3Approx(a: Vec3, b: Vec3): Unit =
    assertApprox(a.x, b.x, s"x component")
    assertApprox(a.y, b.y, s"y component")
    assertApprox(a.z, b.z, s"z component")

  /** Looser tolerance for direction comparisons after trigonometric round-trips */
  def assertVec3Close(a: Vec3, b: Vec3, tolerance: Double = 1e-6): Unit =
    withClue(s"expected $b but got $a") {
      math.abs(a.x - b.x) should be < tolerance
      math.abs(a.y - b.y) should be < tolerance
      math.abs(a.z - b.z) should be < tolerance
    }

  // ── Vec3 basic operations ──────────────────────────────────────────────────

  "Vec3.dot" should "return 0 for perpendicular vectors" in {
    val a = Vec3(1, 0, 0)
    val b = Vec3(0, 1, 0)
    assertApprox(a.dot(b), 0.0)
  }

  it should "compute dot product correctly" in {
    assertApprox(Vec3(1, 2, 3).dot(Vec3(4, 5, 6)), 32.0)
  }

  "Vec3.cross" should "produce +Z from +X cross +Y" in {
    assertVec3Approx(Vec3(1, 0, 0).cross(Vec3(0, 1, 0)), Vec3(0, 0, 1))
  }

  it should "produce -Z from +Y cross +X" in {
    assertVec3Approx(Vec3(0, 1, 0).cross(Vec3(1, 0, 0)), Vec3(0, 0, -1))
  }

  "Vec3.norm" should "compute Euclidean length" in {
    assertApprox(Vec3(3, 4, 0).norm, 5.0)
    assertApprox(Vec3(0, 0, 1).norm, 1.0)
  }

  "Vec3.normalized" should "produce a unit vector" in {
    val v = Vec3(3, 4, 0).normalized
    assertApprox(v.norm, 1.0)
  }

  it should "return (0,0,1) for zero vector" in {
    assertVec3Approx(Vec3(0, 0, 0).normalized, Vec3(0, 0, 1))
  }

  "Vec3.angleTo" should "return 90° for perpendicular vectors" in {
    assertApprox(Vec3(1, 0, 0).angleTo(Vec3(0, 1, 0)), 90.0)
  }

  it should "return 0° for parallel vectors" in {
    val a = Vec3(1, 0, 0)
    assertApprox(a.angleTo(a), 0.0)
  }

  it should "return 180° for opposite vectors" in {
    assertApprox(Vec3(1, 0, 0).angleTo(Vec3(-1, 0, 0)), 180.0)
  }

  // ── Vec3 azimuth/elevation round-trips ────────────────────────────────────

  "Vec3.fromAzimuthElevation / toAzimuthElevation" should "round-trip Rear (az=0, el=0)" in {
    val v = Vec3.fromAzimuthElevation(0.0, 0.0)
    val (az, el) = v.toAzimuthElevation
    assertApprox(az, 0.0)
    assertApprox(el, 0.0)
  }

  it should "round-trip Right (az=90, el=0)" in {
    val v = Vec3.fromAzimuthElevation(90.0, 0.0)
    val (az, el) = v.toAzimuthElevation
    assertApprox(az, 90.0)
    assertApprox(el, 0.0)
  }

  it should "round-trip Up (el=90)" in {
    val v = Vec3.fromAzimuthElevation(45.0, 90.0)
    val (_, el) = v.toAzimuthElevation
    assertApprox(el, 90.0)
  }

  it should "round-trip arbitrary az=135, el=30" in {
    val v = Vec3.fromAzimuthElevation(135.0, 30.0)
    val (az, el) = v.toAzimuthElevation
    assertApprox(az, 135.0)
    assertApprox(el, 30.0)
  }

  it should "round-trip az=270, el=-45" in {
    val v = Vec3.fromAzimuthElevation(270.0, -45.0)
    val (az, el) = v.toAzimuthElevation
    assertApprox(az, 270.0)
    assertApprox(el, -45.0)
  }

  it should "return elevation=90 for Vec3.Up regardless of azimuth" in {
    val (_, el) = Vec3.Up.toAzimuthElevation
    assertApprox(el, 90.0)
  }

  it should "return elevation=-90 for Vec3.Down regardless of azimuth" in {
    val (_, el) = Vec3.Down.toAzimuthElevation
    assertApprox(el, -90.0)
  }

  // ── Vec3.toDisplayString ───────────────────────────────────────────────────

  "Vec3.toDisplayString" should "return 'Up' for Vec3.Up" in {
    Vec3.Up.toDisplayString shouldBe "Up"
  }

  it should "return 'Down' for Vec3.Down" in {
    Vec3.Down.toDisplayString shouldBe "Down"
  }

  it should "return 'Rear' for Vec3.Rear" in {
    Vec3.Rear.toDisplayString shouldBe "Rear"
  }

  it should "return 'Front' for Vec3.Front" in {
    Vec3.Front.toDisplayString shouldBe "Front"
  }

  it should "return 'Right' for Vec3.Right" in {
    Vec3.Right.toDisplayString shouldBe "Right"
  }

  it should "return 'Left' for Vec3.Left" in {
    Vec3.Left.toDisplayString shouldBe "Left"
  }

  it should "show cardinal + elevation for Rear tilted 45° up" in {
    // normalize(0, 1, 1) → Rear direction tilted 45° up
    val v = Vec3(0, 1, 1).normalized
    val s = v.toDisplayString
    s should startWith("Rear ↑")
    s should include("45.0°")
  }

  it should "show cardinal + elevation for Right tilted down 30°" in {
    val angle = math.toRadians(30.0)
    val v = Vec3(math.cos(angle), 0, -math.sin(angle)).normalized
    val s = v.toDisplayString
    s should startWith("Right ↓")
  }

  it should "use az/el fallback for diagonal direction" in {
    val v = Vec3(1, 1, 0).normalized
    v.toDisplayString should startWith("az:")
  }

  // ── PipeFrame.initial ──────────────────────────────────────────────────────

  "PipeFrame.initial" should "set upRef = Rear for vertical up pipe" in {
    val frame = PipeFrame.initial(Vec3.Up)
    assertVec3Approx(frame.direction, Vec3.Up)
    assertVec3Approx(frame.upRef, Vec3.Rear)
  }

  it should "set upRef = Rear for vertical down pipe" in {
    val frame = PipeFrame.initial(Vec3.Down)
    assertVec3Approx(frame.direction, Vec3.Down)
    assertVec3Approx(frame.upRef, Vec3.Rear)
  }

  it should "set upRef = Up for horizontal Rear pipe" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    assertVec3Approx(frame.direction, Vec3.Rear)
    assertVec3Approx(frame.upRef, Vec3.Up)
  }

  it should "set upRef = Up for horizontal Right pipe" in {
    val frame = PipeFrame.initial(Vec3.Right)
    assertVec3Approx(frame.direction, Vec3.Right)
    assertVec3Approx(frame.upRef, Vec3.Up)
  }

  it should "always produce upRef perpendicular to direction" in {
    val dirs = List(Vec3.Rear, Vec3.Right, Vec3.Front, Vec3.Left, Vec3.Up, Vec3.Down,
                   Vec3(1, 1, 0).normalized, Vec3(0, 1, 1).normalized)
    dirs.foreach { d =>
      val frame = PipeFrame.initial(d)
      withClue(s"direction=$d") {
        assertApprox(frame.direction.dot(frame.upRef), 0.0)
      }
    }
  }

  // ── PipeFrame.applyBend ────────────────────────────────────────────────────

  // For vertical up (dir=+Z, upRef=+Y=Rear), right-hand rule convention:
  //   rightRef = dir.cross(upRef) = +Z×+Y = -X = Left
  //   roll=0°  → bendAxis = rightRef*cos(0) - upRef*sin(0) = Left(-X)
  //             rotate +Z around -X by 90° → +Y = Rear
  //   roll=90° → bendAxis = rightRef*cos(90) - upRef*sin(90) = -Rear(-Y) = Front
  //             rotate +Z around -Y by 90° → -X = Left
  "PipeFrame.applyBend" should "produce horizontal Rear from vertical up + 90° roll=0°" in {
    val frame0 = PipeFrame.initial(Vec3.Up)
    val frame1 = frame0.applyBend(deflectionDeg = 90.0, rollDeg = 0.0)
    assertVec3Approx(frame1.direction, Vec3.Rear)
  }

  it should "produce horizontal Left from vertical up + 90° roll=90°" in {
    val frame0 = PipeFrame.initial(Vec3.Up)
    val frame1 = frame0.applyBend(deflectionDeg = 90.0, rollDeg = 90.0)
    assertVec3Approx(frame1.direction, Vec3.Left)
  }

  // For horizontal Rear (dir=+Y, upRef=+Z=Up), right-hand rule convention:
  //   rightRef = dir.cross(upRef) = +Y×+Z = +X = Right
  //   roll=0°  → bendAxis = rightRef*cos(0) - upRef*sin(0) = Right(+X)
  //             rotate +Y around +X by 90° → +Z = Up
  it should "produce vertical Up from horizontal Rear + 90° roll=0°" in {
    val frame0 = PipeFrame.initial(Vec3.Rear)
    val frame1 = frame0.applyBend(deflectionDeg = 90.0, rollDeg = 0.0)
    assertVec3Approx(frame1.direction, Vec3.Up)
  }

  it should "return to original direction and upRef after 4 x 90° same roll" in {
    val frame0 = PipeFrame.initial(Vec3.Up)
    val frame4 = (1 to 4).foldLeft(frame0)((f, _) => f.applyBend(90.0, 0.0))
    assertVec3Approx(frame4.direction, frame0.direction)
    assertVec3Approx(frame4.upRef, frame0.upRef)
  }

  it should "leave frame unchanged with 0° deflection" in {
    val frame0 = PipeFrame.initial(Vec3.Rear)
    val frame1 = frame0.applyBend(0.0, 0.0)
    assertVec3Approx(frame1.direction, frame0.direction)
    assertVec3Approx(frame1.upRef, frame0.upRef)
  }

  // ── angleN2 simulation ─────────────────────────────────────────────────────

  "angleN2 simulation" should "produce 0° between H1 and H3 via H1→DC1(roll=0)→H2→DC2(roll=0)→H3" in {
    // With gravity-convention upRef re-normalization:
    // H1: Rear (dir=+Y, upRef=Up=+Z)
    //   DC1: roll=0° → bend Up → dir=Up, upRef re-normalized to Rear
    // DC2: Up (dir=+Z, upRef=Rear=+Y):
    //   roll=0° → bendAxis = Left(-X); rotate Up around Left → Rear(+Y)
    //   DC2 direction = Rear(+Y) = same as H1 → angle = 0°
    val h1 = PipeFrame.initial(Vec3.Rear)
    val dc1 = h1.applyBend(90.0, 0.0)
    val dc2 = dc1.applyBend(90.0, 0.0)
    val angle = h1.direction.angleTo(dc2.direction)
    assertApprox(angle, 0.0)
  }

  it should "produce 180° via H1→DC1(roll=0)→H2→DC2(roll=180)→H3" in {
    // To get antiparallel: second bend uses roll=180° to reverse direction
    val h1 = PipeFrame.initial(Vec3.Rear)
    val dc1 = h1.applyBend(90.0, 0.0)
    val dc2 = dc1.applyBend(90.0, 180.0)
    val angle = h1.direction.angleTo(dc2.direction)
    assertApprox(angle, 180.0)
  }

  // ── Rodrigues rotation ─────────────────────────────────────────────────────

  "PipeFrame.rodriguesRotate" should "rotate +X 90° around +Z to give +Y" in {
    val rotated = PipeFrame.rodriguesRotate(Vec3(1, 0, 0), Vec3(0, 0, 1), math.Pi / 2)
    assertVec3Approx(rotated, Vec3(0, 1, 0))
  }

  it should "return original vector after 360° rotation" in {
    val v = Vec3(1, 2, 3).normalized
    val k = Vec3(0, 1, 0)
    val rotated = PipeFrame.rodriguesRotate(v, k, 2 * math.Pi)
    assertVec3Approx(rotated, v)
  }

  // ── Direction display after 90° bend to vertical ──────────────────────────

  "90° bend from diagonal to Up" should "produce exact Vec3.Up and display 'Up'" in {
    // Start: pipe at az=45°, el=0° (diagonal horizontal, between Rear and Right)
    val startDir = Vec3.fromAzimuthElevation(45.0, 0.0)
    val frame0   = PipeFrame.initial(startDir)
    // For horizontal pipe: upRef = Up, rightRef = dir × Up
    // roll=0° bends toward Up → new direction = Up
    val frame1   = frame0.applyBend(deflectionDeg = 90.0, rollDeg = 0.0)
    assertVec3Approx(frame1.direction, Vec3.Up)
    frame1.direction.toDisplayString shouldBe "Up"
  }

  it should "suppress azimuth for vertical Up in toAzimuthElevation (elevation ≈ 90°)" in {
    val startDir = Vec3.fromAzimuthElevation(45.0, 0.0)
    val frame0   = PipeFrame.initial(startDir)
    val frame1   = frame0.applyBend(deflectionDeg = 90.0, rollDeg = 0.0)
    val (_, el)  = frame1.direction.toAzimuthElevation
    assertApprox(el, 90.0, "elevation should be 90° for Up")
  }

  it should "show 'Up' for near-vertical vector with tiny x,y residuals (snap robustness)" in {
    // Simulate IEEE 754 residuals after trig — snapped by Vec3.snap at 1e-12
    val nearUp = Vec3(1e-16, 1e-16, 1.0).normalized.snap
    nearUp.toDisplayString shouldBe "Up"
  }

  it should "show 'Down' for near-vertical-down vector with tiny x,y residuals" in {
    val nearDown = Vec3(-1e-16, 1e-16, -1.0).normalized.snap
    nearDown.toDisplayString shouldBe "Down"
  }

  // ── reachableCardinals with tracked frame vs initial ──────────────────────

  "PipeFrame.reachableCardinals" should "match between tracked frame and PipeFrame.initial after a bend" in {
    // With gravity-convention re-normalization, tracked frame = PipeFrame.initial(newDir)
    val frame0  = PipeFrame.initial(Vec3.Up)
    val tracked = frame0.applyBend(90.0, 0.0) // → Rear
    val fromInitial = PipeFrame.initial(Vec3.Rear)

    assertVec3Approx(tracked.direction, fromInitial.direction)
    // upRef should now be identical (both follow gravity convention)
    assertVec3Approx(tracked.upRef, fromInitial.upRef)

    // Roll angles should be identical too
    val trackedRollForUp = tracked.rollAngleForOutputDirection(Vec3.Up).get
    val initialRollForUp = fromInitial.rollAngleForOutputDirection(Vec3.Up).get
    assertApprox(trackedRollForUp, initialRollForUp, "roll for Up")
  }

  // ── upRef gravity convention after bends ──────────────────────────────────

  "PipeFrame.applyBend upRef convention" should "set upRef = Rear after bending to Up" in {
    // Rear → 90° roll=0° → Up; gravity convention: vertical Up → upRef = Rear
    val frame0 = PipeFrame.initial(Vec3.Rear)
    val frame1 = frame0.applyBend(90.0, 0.0)
    assertVec3Approx(frame1.direction, Vec3.Up)
    assertVec3Approx(frame1.upRef, Vec3.Rear)
  }

  it should "set upRef = Up (Gram-Schmidt) after bending to non-vertical direction" in {
    // Up → 45° roll=0° → Rear ↑45°; gravity convention: non-vertical → upRef from Gram-Schmidt ≈ Up
    val frame0 = PipeFrame.initial(Vec3.Up)
    val frame1 = frame0.applyBend(45.0, 0.0)
    // direction should be Rear ↑45°
    val expectedDir = Vec3(0, 1, 1).normalized
    assertVec3Approx(frame1.direction, expectedDir)
    // upRef should be Gram-Schmidt of +Z onto plane perp to direction (projects to roughly Up)
    val expectedFrame = PipeFrame.initial(expectedDir)
    assertVec3Approx(frame1.upRef, expectedFrame.upRef)
  }

  it should "set upRef = Rear after bending to Down" in {
    // Rear → 90° roll=180° → Down; gravity convention: vertical Down → upRef = Rear
    val frame0 = PipeFrame.initial(Vec3.Rear)
    val frame1 = frame0.applyBend(90.0, 180.0)
    assertVec3Approx(frame1.direction, Vec3.Down)
    assertVec3Approx(frame1.upRef, Vec3.Rear)
  }

  // ── rollAngleForOutputDirection with non-90° deflection ───────────────────

  "rollAngleForOutputDirection(target, 45°)" should "find roll for 45° bend from vertical Up toward Rear↑45°" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    // A 45° deflection from Up should reach Rear↑45° = normalized(0,1,1)
    val target = Vec3(0, 1, 1).normalized
    val result = frame.rollAngleForOutputDirection(target, 45.0)
    result shouldBe defined
    // Verify by applying the bend
    val after = frame.applyBend(45.0, result.get)
    assertVec3Close(after.direction, target)
  }

  it should "find roll for 45° bend from vertical Up toward Left↑45°" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    val target = Vec3(-1, 0, 1).normalized
    val result = frame.rollAngleForOutputDirection(target, 45.0)
    result shouldBe defined
    val after = frame.applyBend(45.0, result.get)
    assertVec3Close(after.direction, target)
  }

  it should "return None when target is not reachable at 45°" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    // Vec3.Down is 180° from Up, not reachable at 45°
    val result = frame.rollAngleForOutputDirection(Vec3.Down, 45.0)
    result shouldBe None
  }

  "rollAngleForOutputDirection(target, 60°)" should "find roll for 60° bend from horizontal Rear" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    // 60° from Rear toward Up+Rear → target at elevation 60° in Rear direction
    val target = Vec3.fromAzimuthElevation(0.0, 60.0)
    val result = frame.rollAngleForOutputDirection(target, 60.0)
    result shouldBe defined
    val after = frame.applyBend(60.0, result.get)
    assertVec3Close(after.direction, target)
  }

  it should "find roll for 60° bend from horizontal Rear toward Right↑some°" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    // First compute a target that's 60° from Rear: az=90°(Right), el adjusted
    // direction.angleTo(target) must be ≈ 60°
    // Rear = (0,1,0). Right = (1,0,0). angleTo = 90° — not 60°.
    // Use applyBend to create a known-good target at 60° deflection
    val knownFrame = frame.applyBend(60.0, 90.0) // 60° bend with roll=90°
    val target     = knownFrame.direction
    val result     = frame.rollAngleForOutputDirection(target, 60.0)
    result shouldBe defined
    val after = frame.applyBend(60.0, result.get)
    assertVec3Close(after.direction, target)
  }

  // ── computeRequiredDeflection ─────────────────────────────────────────────

  "computeRequiredDeflection" should "return 90° for perpendicular target" in {
    val frame = PipeFrame.initial(Vec3.Up)
    assertApprox(frame.computeRequiredDeflection(Vec3.Rear), 90.0)
  }

  it should "return 0° for same direction" in {
    val frame = PipeFrame.initial(Vec3.Up)
    assertApprox(frame.computeRequiredDeflection(Vec3.Up), 0.0)
  }

  it should "return 180° for opposite direction" in {
    val frame = PipeFrame.initial(Vec3.Up)
    assertApprox(frame.computeRequiredDeflection(Vec3.Down), 180.0)
  }

  it should "return 45° for diagonal from Up" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    val target = Vec3(0, 1, 1).normalized // Rear↑45°
    assertApprox(frame.computeRequiredDeflection(target), 45.0)
  }

  // ── applyBendForFinalDir round-trip ───────────────────────────────────────

  "applyBendForFinalDir" should "reach target direction (90° bend, cardinal targets)" in {
    val frame = PipeFrame.initial(Vec3.Up)
    val targets = List(Vec3.Rear, Vec3.Front, Vec3.Right, Vec3.Left)
    targets.foreach { target =>
      val after = frame.applyBendForFinalDir(90.0, target)
      withClue(s"target=$target") {
        assertVec3Close(after.direction, target)
      }
    }
  }

  it should "reach target direction (45° bend from vertical Up)" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    val target = Vec3(0, 1, 1).normalized // Rear↑45°
    val after  = frame.applyBendForFinalDir(45.0, target)
    assertVec3Close(after.direction, target)
  }

  it should "reach target direction (60° bend from horizontal Rear)" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    // Create a known-good target at 60° from Rear using applyBend
    val target = frame.applyBend(60.0, 45.0).direction
    val after  = frame.applyBendForFinalDir(60.0, target)
    assertVec3Close(after.direction, target)
  }

  it should "round-trip: applyBendForFinalDir(defl, target).direction ≈ target for various angles" in {
    val deflections = List(30.0, 45.0, 60.0, 90.0, 120.0)
    val startDirs   = List(Vec3.Up, Vec3.Rear, Vec3.Right, Vec3(1, 1, 0).normalized)

    for
      startDir   <- startDirs
      deflection <- deflections
    do
      val frame = PipeFrame.initial(startDir)
      // Generate a valid target by applying a bend with known roll
      val rollAngles = List(0.0, 45.0, 90.0, 135.0, 180.0, -90.0)
      rollAngles.foreach { roll =>
        val target = frame.applyBend(deflection, roll).direction
        val after  = frame.applyBendForFinalDir(deflection, target)
        withClue(s"startDir=$startDir, defl=$deflection, roll=$roll") {
          assertVec3Close(after.direction, target)
        }
      }
  }

  // ── reachableCardinals with non-90° deflection ────────────────────────────

  "reachableCardinals(45°)" should "return empty from vertical Up (no cardinal at 45° from Up)" in {
    val frame   = PipeFrame.initial(Vec3.Up)
    val results = frame.reachableCardinals(45.0)
    // No axis-aligned or horizontal-diagonal cardinal is at 45° from vertical Up
    // (they're all at 90° or 0°/180°)
    results shouldBe empty
  }

  it should "find Up from Rear↑45° at 45° deflection" in {
    // Rear↑45° is 45° from both Up and Rear
    val startDir = Vec3(0, 1, 1).normalized
    val frame    = PipeFrame.initial(startDir)
    val results  = frame.reachableCardinals(45.0)
    results should not be empty
    // Up and Rear should both be reachable at 45° from Rear↑45°
    val dirs = results.map(_._1)
    dirs should contain(Vec3.Up)
    dirs should contain(Vec3.Rear)
    // Each result should be a valid 45° deflection
    results.foreach { (vec, roll) =>
      val angle = frame.direction.angleTo(vec)
      withClue(s"vec=$vec, roll=$roll, angle=$angle") {
        math.abs(angle - 45.0) should be < 1.0
      }
    }
  }

  "reachableCardinals(90°)" should "include intermediate diagonal directions" in {
    val frame   = PipeFrame.initial(Vec3.Up)
    val results = frame.reachableCardinals(90.0)
    // Should include the 4 axis-aligned horizontal cardinals + 4 diagonals (all at 90° from Up)
    // All horizontal directions are 90° from vertical Up, so we expect 8 results
    results.length shouldBe 8
  }

  it should "be backward-compatible with no-arg reachableCardinals" in {
    val frame     = PipeFrame.initial(Vec3.Rear)
    val withArg   = frame.reachableCardinals(90.0)
    val withoutArg = frame.reachableCardinals()
    withArg.length shouldBe withoutArg.length
    withArg.zip(withoutArg).foreach { case ((v1, r1), (v2, r2)) =>
      assertVec3Approx(v1, v2)
      assertApprox(r1, r2, s"roll for $v1")
    }
  }

  // ── localRight ───────────────────────────────────────────────────────────

  "localRight" should "return Right(+X) for Rear-facing pipe" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    assertVec3Approx(frame.localRight, Vec3.Right)
  }

  it should "return Front(-Y) for Right-facing pipe" in {
    val frame = PipeFrame.initial(Vec3.Right)
    assertVec3Approx(frame.localRight, Vec3.Front)
  }

  it should "return Left(-X) for Front-facing pipe" in {
    val frame = PipeFrame.initial(Vec3.Front)
    assertVec3Approx(frame.localRight, Vec3.Left)
  }

  it should "return Rear(+Y) for Left-facing pipe" in {
    val frame = PipeFrame.initial(Vec3.Left)
    assertVec3Approx(frame.localRight, Vec3.Rear)
  }

  it should "return Right(+X) for vertical Up pipe" in {
    val frame = PipeFrame.initial(Vec3.Up)
    assertVec3Approx(frame.localRight, Vec3.Right)
  }

  it should "return Right(+X) for vertical Down pipe" in {
    val frame = PipeFrame.initial(Vec3.Down)
    assertVec3Approx(frame.localRight, Vec3.Right)
  }

  it should "be horizontal and perpendicular to direction for diagonal pipe" in {
    val dir   = Vec3(1, 1, 1).normalized
    val frame = PipeFrame.initial(dir)
    val lr    = frame.localRight
    // localRight should be horizontal (z=0)
    assertApprox(lr.z, 0.0, "localRight.z should be 0")
    // localRight should be perpendicular to horizontal projection of direction
    val hProj = Vec3(dir.x, dir.y, 0).normalized
    assertApprox(hProj.dot(lr), 0.0, "localRight perpendicular to horizontal heading")
    // localRight should be unit vector
    assertApprox(lr.norm, 1.0, "localRight norm")
  }

  it should "point 90° CW from heading for az=45° pipe" in {
    // az=45° heading → localRight should be at az=135° (FrontRight direction)
    val dir   = Vec3.fromAzimuthElevation(45.0, 0.0)
    val frame = PipeFrame.initial(dir)
    val lr    = frame.localRight
    val (az, el) = lr.toAzimuthElevation
    assertApprox(el, 0.0, "elevation")
    assertApprox(az, 135.0, "azimuth should be heading+90°")
  }

  // ── relativeTarget ──────────────────────────────────────────────────────

  "relativeTarget" should "bend toward localRight for side=+1, theta=0°" in {
    val frame  = PipeFrame.initial(Vec3.Rear)
    // From Rear, localRight=Right. 90° bend right → should reach Right
    val target = frame.relativeTarget(1.0, 0.0, 90.0)
    assertVec3Close(target, Vec3.Right)
  }

  it should "bend toward -localRight for side=-1, theta=0°" in {
    val frame  = PipeFrame.initial(Vec3.Rear)
    // From Rear, localRight=Right. 90° bend left → should reach Left
    val target = frame.relativeTarget(-1.0, 0.0, 90.0)
    assertVec3Close(target, Vec3.Left)
  }

  it should "produce horizontal result for horizontal pipe with theta=0°" in {
    val frame  = PipeFrame.initial(Vec3.Right)
    // From Right, localRight=Front. 65° bend right → az≈155° el=0°
    val target = frame.relativeTarget(1.0, 0.0, 65.0)
    val (_, el) = target.toAzimuthElevation
    assertApprox(el, 0.0, "elevation should be 0° for horizontal theta=0°")
  }

  it should "produce correct az for 65° right from Right Horizontal" in {
    val frame  = PipeFrame.initial(Vec3.Right)
    val target = frame.relativeTarget(1.0, 0.0, 65.0)
    val (az, _) = target.toAzimuthElevation
    // From Right(az=90°), localRight=Front(az=180°). Bend 65° toward Front:
    // target = cos(65°)*Right + sin(65°)*Front → az≈155°
    assertApprox(az, 155.0, "azimuth for 65° right from Right")
  }

  it should "produce correct az for 65° left from Right Horizontal" in {
    val frame  = PipeFrame.initial(Vec3.Right)
    val target = frame.relativeTarget(-1.0, 0.0, 65.0)
    val (az, _) = target.toAzimuthElevation
    // Bend 65° toward -Front = Rear → az≈25°
    assertApprox(az, 25.0, "azimuth for 65° left from Right")
  }

  it should "produce Right for 45° right from vertical Up with theta=0°" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    // From Up, localRight=Right. 45° bend right → Right↑45°
    val target = frame.relativeTarget(1.0, 0.0, 45.0)
    val (az, el) = target.toAzimuthElevation
    assertApprox(az, 90.0, "azimuth should be 90° (Right)")
    assertApprox(el, 45.0, "elevation should be 45°")
  }

  it should "produce Left for 45° left from vertical Up with theta=0°" in {
    val frame  = PipeFrame.initial(Vec3.Up)
    val target = frame.relativeTarget(-1.0, 0.0, 45.0)
    val (az, el) = target.toAzimuthElevation
    assertApprox(az, 270.0, "azimuth should be 270° (Left)")
    assertApprox(el, 45.0, "elevation should be 45°")
  }

  it should "produce non-trivial direction with theta=30°" in {
    val frame  = PipeFrame.initial(Vec3.Rear)
    // theta=30° rotates the bend plane, so result should not be horizontal
    val target = frame.relativeTarget(1.0, 30.0, 90.0)
    val (_, el) = target.toAzimuthElevation
    // With theta=30°, the result should have non-zero elevation
    math.abs(el) should be > 1.0
  }

  it should "preserve deflection angle" in {
    val frame = PipeFrame.initial(Vec3.Right)
    val sides = List(1.0, -1.0)
    val thetas = List(0.0, 15.0, -30.0, 45.0, -60.0, 90.0)
    val defls  = List(30.0, 45.0, 65.0, 90.0, 120.0)
    for
      side  <- sides
      theta <- thetas
      defl  <- defls
    do
      val target = frame.relativeTarget(side, theta, defl)
      val angle  = frame.direction.angleTo(target)
      withClue(s"side=$side, theta=$theta, defl=$defl") {
        math.abs(angle - defl) should be < 0.01
      }
  }

  // ── recoverRelative ─────────────────────────────────────────────────────

  "recoverRelative" should "round-trip with relativeTarget for various parameters" in {
    val startDirs = List(Vec3.Rear, Vec3.Right, Vec3.Up, Vec3.Down, Vec3(1, 1, 0).normalized)
    val sides     = List(1.0, -1.0)
    val thetas    = List(0.0, 15.0, -30.0, 45.0, -60.0, 80.0, -80.0)
    val defls     = List(30.0, 45.0, 65.0, 90.0)

    for
      startDir <- startDirs
      side     <- sides
      theta    <- thetas
      defl     <- defls
    do
      val frame  = PipeFrame.initial(startDir)
      val target = frame.relativeTarget(side, theta, defl)
      val (recSide, recTheta) = frame.recoverRelative(target, defl)
      withClue(s"startDir=$startDir, side=$side, theta=$theta, defl=$defl → target=$target") {
        recSide shouldBe side
        math.abs(recTheta - theta) should be < 0.1
      }
  }

  it should "return (1.0, 0.0) for degenerate case (target ≈ direction)" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    val (side, theta) = frame.recoverRelative(Vec3.Rear, 90.0)
    side shouldBe 1.0
    theta shouldBe 0.0
  }

  it should "recover side=Right for cardinal Right from Rear pipe" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    val (side, theta) = frame.recoverRelative(Vec3.Right, 90.0)
    side shouldBe 1.0
    assertApprox(theta, 0.0, "theta should be 0° for pure right from Rear")
  }

  it should "recover side=Left for cardinal Left from Rear pipe" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    val (side, theta) = frame.recoverRelative(Vec3.Left, 90.0)
    side shouldBe -1.0
    assertApprox(theta, 0.0, "theta should be 0° for pure left from Rear")
  }

  it should "recover side=Right, theta=-90° for cardinal Up from Rear pipe" in {
    val frame = PipeFrame.initial(Vec3.Rear)
    // From Rear: localRight=Right. rodriguesRotate(Right, Rear, -90°)=Up, then bendAxis=Right,
    // rotate Rear around Right by +90° → Up. So reaching Up requires side=+1, theta=-90°.
    val (side, theta) = frame.recoverRelative(Vec3.Up, 90.0)
    side shouldBe 1.0
    assertApprox(theta, -90.0, "theta should be -90° for Up from Rear")
  }

end PipeFrameSuite
