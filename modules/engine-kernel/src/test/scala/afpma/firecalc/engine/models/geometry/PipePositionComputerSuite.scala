/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.common.PipeShape.Circle
import afpma.firecalc.dto.common.PipeShape.Rectangle
import afpma.firecalc.dto.common.PipeShape.Square
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.domain.AirDistributionBox
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

class PipePositionComputerSuite extends AnyFlatSpec with Matchers:

    val eps = 1e-10

    def assertApprox(a: Double, b: Double, msg: String = ""): Unit =
        val diff = math.abs(a - b)
        withClue(s"$msg (got $a, expected $b, diff=$diff)") {
            diff should be < eps
        }

    def assertVec3Approx(a: Vec3, b: Vec3): Unit =
        assertApprox(a.x, b.x, "x component")
        assertApprox(a.y, b.y, "y component")
        assertApprox(a.z, b.z, "z component")

    def assertPosition3DApprox(pos: Position3D, expectedX: Double, expectedY: Double, expectedZ: Double): Unit =
        assertApprox(pos.x.value, expectedX, "x")
        assertApprox(pos.y.value, expectedY, "y")
        assertApprox(pos.z.value, expectedZ, "z")

    // Standard firebox dimensions (meters): width=0.66, depth=0.66, height=0.52
    val boxXWidth  = 0.66
    val boxYDepth  = 0.66
    val boxZBottom = 0.0
    val boxZHeight = 0.52
    val topZ       = 0.52

    // Standard air distribution box dimensions
    val adBoxZBottom = AirDistributionBox.CenterZ  // -0.20
    val adBoxZHeight = AirDistributionBox.Z_HEIGHT // 0.20

    // Standard pipe shape: circle 20cm diameter
    val standardShape = Circle(0.2.meters)

    // ── computePostFireboxStart: cardinal directions ──────────────────────────

    "computePostFireboxStart" should "place exit at right face center for Right direction" in {
        val dir = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        // Right face: x = width/2 = 0.33, y = 0, z = topZ - innerHeight/2 = 0.52 - 0.1 = 0.42
        assertApprox(pos.x.value, boxXWidth / 2.0, "x should be at right face")
        assertApprox(pos.y.value, 0.0, "y should be centered"                 )
        assertApprox(pos.z.value, topZ - 0.2 / 2.0, "z should be top-aligned" )
    }

    it should "place exit at left face center for Left direction" in {
        val dir = PipeInitialDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, -boxXWidth / 2.0, "x should be at left face")
        assertApprox(pos.y.value, 0.0, "y should be centered"                 )
        assertApprox(pos.z.value, topZ - 0.2 / 2.0, "z should be top-aligned" )
    }

    it should "place exit at rear face center for Rear direction" in {
        val dir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"                )
        assertApprox(pos.y.value, boxYDepth / 2.0, "y should be at rear face")
        assertApprox(pos.z.value, topZ - 0.2 / 2.0, "z should be top-aligned")
    }

    it should "place exit at front face center for Front direction" in {
        val dir = PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"                  )
        assertApprox(pos.y.value, -boxYDepth / 2.0, "y should be at front face")
        assertApprox(pos.z.value, topZ - 0.2 / 2.0, "z should be top-aligned"  )
    }

    it should "place exit at center of top face for Up direction" in {
        val dir = PipeInitialDirection(InclinationDirection.Up)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"    )
        assertApprox(pos.y.value, 0.0, "y should be centered"    )
        assertApprox(pos.z.value, topZ, "z should be at top face")
    }

    it should "place exit at center of top face for Down direction" in {
        val dir = PipeInitialDirection(InclinationDirection.Down)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"    )
        assertApprox(pos.y.value, 0.0, "y should be centered"    )
        assertApprox(pos.z.value, topZ, "z should be at top face")
    }

    // ── computePostFireboxStart: diagonal directions ──────────────────────────

    it should "place exit at corner for diagonal direction (RearRight)" in {
        // RearRight = 45° → Vec3(0.707, 0.707, 0)
        // projectOnBoundary normalizes by max(0.707, 0.707) = 0.707
        // → x = (0.707/0.707) * halfWidth = halfWidth, y = (0.707/0.707) * halfDepth = halfDepth
        val dir = PipeInitialDirection(AzimuthDirection.RearRight, InclinationDirection.Horizontal)
        val pos = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, boxXWidth / 2.0, "x should be at right face"        )
        assertApprox(pos.y.value, boxYDepth / 2.0, "y should be at rear face (corner)")
    }

    // ── computePostFireboxStart: different box dimensions ─────────────────────

    it should "move boundary correctly for wide box" in {
        val wideWidth = 1.0
        val dir       = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos       = PipePositionComputer.computePostFireboxStart(
            dir,
            wideWidth,
            boxYDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, wideWidth / 2.0, "x should be at right face of wide box")
    }

    it should "move boundary correctly for deep box" in {
        val deepDepth = 1.0
        val dir       = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val pos       = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            deepDepth,
            boxZBottom,
            boxZHeight,
            standardShape
        )
        assertApprox(pos.y.value, deepDepth / 2.0, "y should be at rear face of deep box")
    }

    it should "move boundary correctly for tall box" in {
        val tallHeight = 1.0
        val dir        = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos        = PipePositionComputer.computePostFireboxStart(
            dir,
            boxXWidth,
            boxYDepth,
            boxZBottom,
            tallHeight,
            standardShape
        )
        assertApprox(pos.z.value, tallHeight - 0.2 / 2.0, "z should be top-aligned with tall box")
    }

    // ── computePostFireboxStart: different inner shapes ──────────────────────

    it should "use circle diameter for innerHeight" in {
        val shape = Circle(0.15.meters) // 15cm diameter
        val dir   = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos   = PipePositionComputer.computePostFireboxStart(dir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, shape)
        assertApprox(pos.z.value, topZ - 0.15 / 2.0, "z should use circle diameter")
    }

    it should "use square side for innerHeight" in {
        val shape = Square(0.18.meters) // 18cm side
        val dir   = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos   = PipePositionComputer.computePostFireboxStart(dir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, shape)
        assertApprox(pos.z.value, topZ - 0.18 / 2.0, "z should use square side")
    }

    it should "use rectangle height (b) for innerHeight" in {
        val shape = Rectangle(0.25.meters, 0.15.meters) // 25cm x 15cm
        val dir   = PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
        val pos   = PipePositionComputer.computePostFireboxStart(dir, boxXWidth, boxYDepth, boxZBottom, boxZHeight, shape)
        assertApprox(pos.z.value, topZ - 0.15 / 2.0, "z should use rectangle height (b)")
    }

    // ── computeAirIntakeConnection: cardinal directions ───────────────────────

    "computeAirIntakeConnection" should "place entry at bottom center for Up direction" in {
        val pos = PipePositionComputer.computeAirIntakeConnection(
            Vec3.Up,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"               )
        assertApprox(pos.y.value, 0.0, "y should be centered"               )
        assertApprox(pos.z.value, adBoxZBottom, "z should be at bottom face")
    }

    it should "place entry at top center for Down direction" in {
        val pos = PipePositionComputer.computeAirIntakeConnection(
            Vec3.Down,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, 0.0, "x should be centered"                           )
        assertApprox(pos.y.value, 0.0, "y should be centered"                           )
        assertApprox(pos.z.value, adBoxZBottom + adBoxZHeight, "z should be at top face")
    }

    it should "place entry at left face for Right direction (pipe enters from left, flows right toward center)" in {
        val pos = PipePositionComputer.computeAirIntakeConnection(
            Vec3.Right,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            standardShape
        )
        // Ray from center in -Right = Left direction hits left face
        assertApprox(pos.x.value, -boxXWidth / 2.0, "x should be at left face"          )
        assertApprox(pos.y.value, 0.0, "y should be centered"                           )
        assertApprox(pos.z.value, adBoxZBottom + 0.2 / 2.0, "z should be bottom-aligned")
    }

    it should "place entry at front face for Rear direction (pipe enters from front, flows rear toward center)" in {
        val pos = PipePositionComputer.computeAirIntakeConnection(
            Vec3.Rear,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            standardShape
        )
        // Ray from center in -Rear = Front direction hits front face
        assertApprox(pos.x.value, 0.0, "x should be centered"                           )
        assertApprox(pos.y.value, -boxYDepth / 2.0, "y should be at front face"         )
        assertApprox(pos.z.value, adBoxZBottom + 0.2 / 2.0, "z should be bottom-aligned")
    }

    // ── computeAirIntakeConnection: diagonal directions ────────────────────

    it should "place entry at corner for diagonal direction (RearRight)" in {
        // Vec3(1, 1, 0).normalized = (0.707, 0.707, 0)
        // Ray from center in -dir = (-0.707, -0.707, 0) hits FrontLeft corner
        // tx = halfWidth/0.707, ty = halfDepth/0.707 → equal for square box → corner
        val dir = Vec3(1, 1, 0).normalized
        val pos = PipePositionComputer.computeAirIntakeConnection(
            dir,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            standardShape
        )
        assertApprox(pos.x.value, -boxXWidth / 2.0, "x should be at left face (corner)" )
        assertApprox(pos.y.value, -boxYDepth / 2.0, "y should be at front face (corner)")
    }

    // ── computeAirIntakeConnection: Z within box bounds ──────────────────────

    it should "keep Z within air distribution box bounds for non-vertical directions" in {
        val directions = Seq(Vec3.Right, Vec3.Left, Vec3.Rear, Vec3.Front, Vec3(1, 1, 0.5).normalized)
        directions.foreach { dir =>
            val pos = PipePositionComputer.computeAirIntakeConnection(
                dir,
                boxXWidth,
                boxYDepth,
                adBoxZBottom,
                adBoxZHeight,
                standardShape
            )
            withClue(s"Z should be within box bounds for direction $dir") {
                pos.z.value should be >= adBoxZBottom
                pos.z.value should be <= adBoxZBottom + adBoxZHeight
            }
        }
    }

    // ── computeAirIntakeFinalAuto: single-section ────────────────────────────

    "computeAirIntakeFinalAuto" should "compute correct offset for single horizontal section" in {
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4.*

        // Single 2m horizontal section going Rear
        val descr      = Seq(
            SetInnerShape(Circle(0.2.meters)),
            AddSectionHorizontal("h", 2.0.meters)
        )
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)

        val pos = AirIntakeReplay.computeAirIntakeFinalAuto(
            descr,
            initialDir,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            innerShapeOpt = Some(Circle(0.2.meters))
        )

        // After replay from Origin: final point = (0, 2, 0), final direction = Rear
        // Target connection (Rear direction): ray from center in -Rear=Front hits front face
        // x=0, y=-boxYDepth/2, z=adBoxZBottom+0.2/2
        // Offset = target - rawFinal = (0, -boxYDepth/2 - 2, adBoxZBottom + 0.1)
        assertApprox(pos.x.value, 0.0, "x offset should be 0"                                        )
        assertApprox(pos.y.value, -boxYDepth / 2.0 - 2.0, "y offset should compensate for 2m section")
        assertApprox(pos.z.value, adBoxZBottom + 0.1, "z offset should be bottom-aligned"            )
    }

    // ── computeAirIntakeFinalAuto: multi-section ─────────────────────────────

    it should "compute correct offset for multi-section sequence" in {
        import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4.*

        // Multi-section: horizontal Rear 2m, then vertical Up 1m
        val descr      = Seq(
            SetInnerShape(Circle(0.2.meters)),
            AddSectionHorizontal  ("h1", 2.0.meters                                                          ),
            AddSharpeAngle_0_to_90("bend", 90.degrees, Some(AbsoluteDirection(None, InclinationDirection.Up))),
            AddSectionVertical    ("v1", 1.0.meters                                                          )
        )
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)

        val pos = AirIntakeReplay.computeAirIntakeFinalAuto(
            descr,
            initialDir,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            innerShapeOpt = Some(Circle(0.2.meters))
        )

        // After replay: final point = (0, 2, 1), final direction = Up
        // Target connection (Up direction): x=0, y=0, z=adBoxZBottom
        // Offset = target - rawFinal = (0, -2, adBoxZBottom - 1)
        assertApprox(pos.x.value, 0.0, "x offset should be 0"                                     )
        assertApprox(pos.y.value, -2.0, "y offset should compensate for 2m horizontal"            )
        assertApprox(pos.z.value, adBoxZBottom - 1.0, "z offset should compensate for 1m vertical")
    }

    // ── computeAirIntakeFinalAuto: empty descriptor sequence ──────────────────

    it should "return box surface position for empty descriptor sequence" in {
        val initialDir = PipeInitialDirection.default
        val pos        = AirIntakeReplay.computeAirIntakeFinalAuto(
            Seq.empty,
            initialDir,
            boxXWidth,
            boxYDepth,
            adBoxZBottom,
            adBoxZHeight,
            innerShapeOpt = None
        )
        assertApprox(pos.x.value, 0.0, "x should be 0")
        assertApprox(pos.y.value, 0.0, "y should be 0"                                    )
        assertApprox(pos.z.value, adBoxZBottom + adBoxZHeight, "z should be at top of box")
    }

end PipePositionComputerSuite
