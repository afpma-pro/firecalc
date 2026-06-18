/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.common.{PipeInitialDirection, Position3D}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.units.Vec3

class PositionTrackerSuite extends AnyFlatSpec with Matchers:

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

    // Shared wrapper-level initial direction/position for tests
    val defaultInitialDir = PipeInitialDirection.default // Right + Horizontal

    // ── Test 1: Single vertical section ────────────────────────────────────────

    "PositionTracker.computeFlowOnly15544" should "place end point at (0,0,1) for single vertical section" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val elems      = Seq(
            AddSectionVertical("v", 1.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 1))
        result.segments.size `shouldBe` 1
    }

    // ── Test 2: Single horizontal section with Rear initial direction ───────────

    it should "place end point at (0,2,0) for horizontal section with Rear direction" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val elems      = Seq(
            AddSectionHorizontal("h", 2.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertVec3Approx(result.finalPoint, Vec3(0, 2, 0))
    }

    // ── Test 3: Slopped section ─────────────────────────────────────────────────

    it should "place end point at (0,4,3) for slopped section (5m length, 3m elevation gain)" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val elems      = Seq(
            AddSectionSloppedForceManualElevationGain("s", 5.0.meters, 3.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        // horizontal distance = sqrt(25 - 9) = 4, elevation = 3 → (0, 4, 3)
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 4.0, "y")
        assertApprox(result.finalPoint.z, 3.0, "z")
    }

    // ── Test 4: Direction change + horizontal section ────────────────────────────

    it should "place end point at (2,0,0) after 90° bend (roll=90°) from Rear + horizontal section" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val elems      = Seq(
            AddSharpeAngle_0_to_180(
                "dc",
                90.0.degrees,
                Some(AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal))
            ),
            AddSectionHorizontal   ("h", 2.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        // After 90° bend with roll=90° from Rear frame, new direction is Right (+X)
        assertApprox(result.finalPoint.x, 2.0, "x")
        assertApprox(result.finalPoint.y, 0.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
    }

    // ── Test 5: Multiple sections — both follow frame (straight-section semantics) ─

    it should "accumulate straight sections: both Vertical and Horizontal follow frame" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        // Both AddSectionVertical and AddSectionHorizontal are legacy names treated as
        // straight sections that follow the current frame. With Rear horizontal frame:
        //   vertical(1m) goes Rear 1m → (0,1,0)
        //   horizontal(2m) goes Rear another 2m → (0,3,0)
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val elems      = Seq(
            AddSectionVertical  ("v", 1.0.meters),
            AddSectionHorizontal("h", 2.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 3.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
        result.segments.size `shouldBe` 2
    }

    // ── Test 6: Direction change mid-sequence via AddSharpeAngle ─────────────────

    it should "change direction mid-sequence via AddSharpeAngle: end at (1,1,0)" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        val elems      = Seq(
            AddSectionHorizontal   ("h1", 1.0.meters), // goes Rear to (0,1,0)
            AddSharpeAngle_0_to_180(
                "dc",
                90.0.degrees,
                Some(AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal))
            ),
            AddSectionHorizontal   ("h2", 1.0.meters)  // goes Right to (1,1,0)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertApprox(result.finalPoint.x, 1.0, "x")
        assertApprox(result.finalPoint.y, 1.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
    }

    // ── Test 7: AddSectionHorizontal under Up frame follows frame (goes up) ───────

    it should "follow Up frame for AddSectionHorizontal (straight-section semantics)" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        // AddSectionHorizontal is a legacy name treated as a straight section that follows
        // the current frame. With a vertical (Up) frame, the section goes up too.
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val elems      = Seq(
            AddSectionHorizontal("h", 1.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 0.0, "y")
        assertApprox(result.finalPoint.z, 1.0, "z")
    }

    // ── Test 8: Empty pipe ───────────────────────────────────────────────────────

    it should "return empty result for empty Seq" in {
        val result = PositionTracker.computeFlowOnly15544(
            Seq.empty,
            defaultInitialDir,
            None,
            Vec3(0, 0, 0)
        )
        result.segments.isEmpty `shouldBe` true
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 0))
        // frame is initialized from initialDirection, not None
        result.finalFrame.isDefined `shouldBe` true
    }

    // ── Test 9: Negative elevation_gain ─────────────────────────────────────────

    it should "place end at (0,0,-2) for downward vertical section with length=2" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val elems      = Seq(
            AddSectionVertical("down", -2.0.meters)
        )
        val result     = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 0.0, "y"             )
        assertApprox(result.finalPoint.z, -2.0, "z"            )
        result.segments.size `shouldBe` 1
        assertApprox(result.segments.head.length, 2.0, "length")
    }

    // ── Test 10: AddSectionVertical follows post-bend frame direction ───────────

    it should "follow post-bend frame for AddSectionVertical (treated as straight section)" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        // Repro: vertical Up + 30° bend toward Rear/60° elevation + vertical (eg=3m).
        // Old behavior snapped the second vertical to +Z. New (straight-section) behavior:
        //   dir = (0, cos60°, sin60°) = (0, 0.5, 0.866)     — Rear-tilted-up
        //   length = 3m (interpreted as 3D length, not vertical projection)
        //   disp = dir * 3 = (0, 1.5, 2.598); starts from (0,0,6)
        val initialDir    = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val elems         = Seq(
            AddSectionVertical     ("up1", 6.0.meters),
            AddSharpeAngle_0_to_180(
                "bend",
                30.0.degrees,
                Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.fromDegrees(60.0)))
            ),
            AddSectionVertical     ("up2", 3.0.meters)
        )
        val externalFrame = Some(PipeFrame.initial(Vec3.Up)) // ChimneySlot starts vertical
        val result        = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            externalFrame,
            Vec3(0, 0, 0)
        )
        // Two visible segments (bend produces no segment, just frame update)
        result.segments.size `shouldBe` 2
        // Second segment should follow the bent direction with vertical projection = 3m
        val seg2          = result.segments(1)
        assertApprox(seg2.direction.x, 0.0, "seg2.dir.x"                                           )
        assertApprox(seg2.direction.y, math.cos           (math.toRadians(60.0)), "seg2.dir.y"     ) // 0.5
        assertApprox(seg2.direction.z, math.sin           (math.toRadians(60.0)), "seg2.dir.z"     ) // 0.866
        assertApprox(seg2.length, 3.0, "seg2.length"                                               ) // parameter is 3D length
        // Endpoint gains Rear (y) and Up (z) proportionally to the bent direction
        assertApprox(seg2.endPoint.y, 3.0 * math.cos      (math.toRadians(60.0)), "seg2.endPoint.y") // 1.5
        assertApprox(seg2.endPoint.z, 6.0 + 3.0 * math.sin(math.toRadians(60.0)), "seg2.endPoint.z") // 8.598
    }

    // ── Test 11: Chained computation ─────────────────────────────────────────────

    it should "chain flue and connector computations correctly" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        val flueDir    = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val connDir    = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
        // Flue pipe: 1m vertical from origin
        val flueElems  = Seq(AddSectionVertical("v", 1.0.meters))
        val flueResult = PositionTracker.computeFlowOnly15544(
            flueElems,
            flueDir,
            None,
            Vec3(0, 0, 0)
        )
        assertVec3Approx(flueResult.finalPoint, Vec3(0, 0, 1))

        // Connector: start from flue's final point, 1 horizontal section (Rear direction)
        val connElems  = Seq(AddSectionHorizontal("h", 1.0.meters))
        val connResult = PositionTracker.computeFlowOnly15544(
            connElems,
            connDir,
            None,
            flueResult.finalPoint
        )
        assertApprox(connResult.finalPoint.x, 0.0, "x")
        assertApprox(connResult.finalPoint.y, 1.0, "y")
        assertApprox(connResult.finalPoint.z, 1.0, "z")
    }

end PositionTrackerSuite
