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

  "angleN2 simulation" should "produce 180° between H1 and H3 via H1→DC1→H2→DC2→H3" in {
    // H1: horizontal Rear (dir=+Y, upRef=Up=+Z)
    //   rightRef = +Y×+Z = +X = Right
    //   roll=0° → bendAxis = rightRef = Right(+X); rotate +Y around +X by 90° → +Z = Up
    //   DC1 direction = Up(+Z), upRef after bend = rotate +Z around +X by 90° → -Y = Front
    // DC2: Up (dir=+Z, upRef=Front(-Y)):
    //   rightRef = +Z×(-Y) = -(+Z×+Y) = -(-X) = +X = Right
    //   roll=0° → bendAxis = rightRef = Right(+X); rotate +Z around +X by 90° → -Y = Front
    //   DC2 direction = Front(-Y) which is antiparallel to H1 direction Rear(+Y) → angle = 180°
    val h1 = PipeFrame.initial(Vec3.Rear)
    val dc1 = h1.applyBend(90.0, 0.0)
    val dc2 = dc1.applyBend(90.0, 0.0)
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
    // Simulate floating-point residuals after accumulated rotations
    val nearUp = Vec3(1e-10, 1e-10, 1.0).normalized
    nearUp.toDisplayString shouldBe "Up"
  }

  it should "show 'Down' for near-vertical-down vector with tiny x,y residuals" in {
    val nearDown = Vec3(-1e-10, 1e-10, -1.0).normalized
    nearDown.toDisplayString shouldBe "Down"
  }

  // ── reachableCardinals with tracked frame vs initial ──────────────────────

  "PipeFrame.reachableCardinals" should "differ between tracked frame and PipeFrame.initial after a bend" in {
    // Start vertical up → bend at roll=0° → now horizontal Rear with upRef=Down
    val frame0  = PipeFrame.initial(Vec3.Up)
    val tracked = frame0.applyBend(90.0, 0.0)
    // tracked: direction=Rear, upRef=Down
    // initial: direction=Rear, upRef=Up (gravity convention)
    val fromInitial = PipeFrame.initial(Vec3.Rear)

    assertVec3Approx(tracked.direction, fromInitial.direction)
    // upRef should differ (tracked=Down, initial=Up — antiparallel)
    val trackedUpRef = tracked.upRef
    val initialUpRef = fromInitial.upRef
    assert(trackedUpRef.dot(initialUpRef) < -0.99,
      s"upRef should be antiparallel: tracked=$trackedUpRef vs initial=$initialUpRef")

    // Roll angle for "Up" should differ: tracked frame gives 180°, initial gives 0°
    val trackedRollForUp = tracked.rollAngleForOutputDirection(Vec3.Up).get
    val initialRollForUp = fromInitial.rollAngleForOutputDirection(Vec3.Up).get
    assert(math.abs(trackedRollForUp - initialRollForUp) > 1.0,
      s"roll for Up should differ: tracked=$trackedRollForUp vs initial=$initialRollForUp")
  }

end PipeFrameSuite
