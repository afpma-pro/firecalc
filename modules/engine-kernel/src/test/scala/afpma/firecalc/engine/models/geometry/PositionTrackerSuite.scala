/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.PipeShape

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

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

    // ── Test 12: SplitMerge90 without offset (computed at call sites) ─────

    it should "not advance position by offset for SplitMerge90 (offset computed at call sites)" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        // The offset field was removed from the DTO. The branch start position
        // is now computed at call sites (Variables.scala, FireCalcYAML_Loader.scala)
        // using PipePositionComputer.computeBranchStartAfterSplit.
        // PositionTracker no longer advances by offset for SplitMerge90.
        val initialDir    = PipeInitialDirection.default
        val splitPos      = Vec3(0, 0, 0.62)
        val elems         = Seq(
            SplitSingleFlowIntoTwoFlowsWith90DegTurn                (
                name                 = "split",
                absDir               = Some(AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)),
                newInnerShape        = PipeShape.Circle(18.cm),
                symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
            ),
            AddSectionSlopped                                       ("sortie de foyer", 0.3.meters)
        )
        val externalFrame = Some(PipeFrame.initial(Vec3.Up))
        val result        = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            externalFrame,
            splitPos
        )

        // PositionTracker does not advance by offset anymore.
        // The segment starts at the split position and goes along the branch direction.
        result.segments.size shouldBe 1
        assertVec3Approx(result.segments.head.startPoint, splitPos)

        // After the 90° bend (Up → Front), the horizontal section goes along Front direction.
        // The frame direction after the bend is Front (horizontal).
        // AddSectionSlopped follows the frame, so it goes 0.3m in the Front direction.
        val expectedEnd = splitPos + Vec3.Front * 0.3
        assertVec3Approx(result.segments.head.endPoint, expectedEnd)
        assertVec3Approx(result.finalPoint, expectedEnd            )
    }

    // ── Test 13: Missing symmetryPlaneAzimuth with vertical incoming produces error ─────

    it should "produce error in PipePositionResult.errors when symmetryPlaneAzimuth is missing with vertical incoming" in {
        import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.*
        // symmetryPlaneAzimuth = None with vertical incoming should produce
        // SplitMergeTwoHelperError.SymmetryPlaneAzimuthRequired
        val initialDir    = PipeInitialDirection.default
        val splitPos      = Vec3(0, 0, 0.62)
        val elems         = Seq(
            SplitSingleFlowIntoTwoFlowsWith90DegTurn                (
                name                 = "test-split-missing-azimuth",
                absDir               = Some(AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)),
                newInnerShape        = PipeShape.Circle(18.cm),
                symmetryPlaneAzimuth = None
            ),
            AddSectionSlopped                                       ("sortie de foyer", 0.3.meters)
        )
        val externalFrame = Some(PipeFrame.initial(Vec3.Up))
        val result        = PositionTracker.computeFlowOnly15544(
            elems,
            initialDir,
            externalFrame,
            splitPos
        )

        // The split should have been skipped (no splitMergePosition added)
        result.splitMergePositions shouldBe empty

        // The error should contain the element name
        result.errors should contain("test-split-missing-azimuth")
        result.errors.size shouldBe 1
    }

    // ── Test 14: Thermal SetPropertiesInBatch extracts inner shape ─────

    "PositionTracker.computeThermal13384" should "extract SetInnerShape from SetPropertiesInBatch props" in {
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.*
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val elems      = Seq[ThermalPipeDescr_13384](
            SetPropertiesInBatch(
                batch_name = "batch",
                props      = Seq(
                    SetInnerShape(PipeShape.Circle(18.cm))
                )
            ),
            AddSectionVertical  ("v", 1.0.meters)
        )
        val result     = PositionTracker.computeThermal13384(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        result.segments.size `shouldBe` 1
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 1))
        // Inner shape from SetPropertiesInBatch should propagate into the segment
        result.segments.head.innerShape shouldBe Some(PipeShape.Circle(18.cm))
    }

    // ── Test 15: Thermal LinedFlue extracts inner shape from liner ──────────

    it should "extract SetInnerShape from LinedFlue liner props" in {
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.*
        import afpma.firecalc.dto.v4.AirSpaceDetailed_V2.WithoutAirSpace_V2
        val initialDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
        val liner      = SetPropertiesInBatch(
            batch_name = "liner",
            props      = Seq(SetInnerShape(PipeShape.Circle(20.cm)))
        )
        val casing     = SetPropertiesInBatch(batch_name = "casing", props = Seq.empty)
        val elems      = Seq[ThermalPipeDescr_13384](
            LinedFlue         ("lined", liner, WithoutAirSpace_V2, casing),
            AddSectionVertical("v", 2.0.meters                           )
        )
        val result     = PositionTracker.computeThermal13384(
            elems,
            initialDir,
            None,
            Vec3(0, 0, 0)
        )
        result.segments.size `shouldBe` 1
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 2))
        // Inner shape from LinedFlue liner should propagate into the segment
        result.segments.head.innerShape shouldBe Some(PipeShape.Circle(20.cm))
    }
end PositionTrackerSuite
