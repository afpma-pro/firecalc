/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{FinalDirection, AzimuthDirection, InclinationDirection}

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

    // ── Test 1: Single vertical section ────────────────────────────────────────

    "PositionTracker.computeFlowOnly15544" should "place end point at (0,0,1) for single vertical section" in {
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            AddSectionVertical("v", 1.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 1))
        result.segments.size shouldBe 1
    }

    // ── Test 2: Single horizontal section with Rear initial direction ───────────

    it should "place end point at (0,2,0) for horizontal section with Rear direction" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),
            AddSectionHorizontal("h", 2.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        assertVec3Approx(result.finalPoint, Vec3(0, 2, 0))
    }

    // ── Test 3: Slopped section ─────────────────────────────────────────────────

    it should "place end point at (0,4,3) for slopped section (5m length, 3m elevation gain)" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),
            AddSectionSlopped("s", 5.0.meters, 3.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        // horizontal distance = sqrt(25 - 9) = 4, elevation = 3 → (0, 4, 3)
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 4.0, "y")
        assertApprox(result.finalPoint.z, 3.0, "z")
    }

    // ── Test 4: Direction change + horizontal section ────────────────────────────

    it should "place end point at (2,0,0) after 90° bend (roll=90°) from Rear + horizontal section" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),  // Rear direction
            AddSharpeAngle_0_to_180("dc", 90.0.degrees, Some(FinalDirection(AzimuthDirection.Right, InclinationDirection.Horizontal))),
            AddSectionHorizontal("h", 2.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        // After 90° bend with roll=90° from Rear frame, new direction is Right (+X)
        assertApprox(result.finalPoint.x, 2.0, "x")
        assertApprox(result.finalPoint.y, 0.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
    }

    // ── Test 5: Multiple sections — cumulative position ──────────────────────────

    it should "accumulate positions: vertical (0,0,1) then horizontal (0,2,1)" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),
            AddSectionVertical("v", 1.0.meters),
            AddSectionHorizontal("h", 2.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 2.0, "y")
        assertApprox(result.finalPoint.z, 1.0, "z")
        result.segments.size shouldBe 2
    }

    // ── Test 6: SetInitialDirection changes direction between sections ───────────

    it should "respect second SetInitialDirection: end at (1,1,0)" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal),   // az=0=Rear
            AddSectionHorizontal("h1", 1.0.meters),          // goes to (0,1,0)
            SetInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),  // az=90=Right
            AddSectionHorizontal("h2", 1.0.meters)           // goes to (1,1,0)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        assertApprox(result.finalPoint.x, 1.0, "x")
        assertApprox(result.finalPoint.y, 1.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
    }

    // ── Test 7: Vertical direction + horizontal section falls back to Rear ────────

    it should "fall back to Rear for horizontal section when frame direction is Up" in {
        import SetFlowOnlyPipeProp_15544_V3.*
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),  // Up direction (el=90°)
            AddSectionHorizontal("h", 1.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        // Frame direction is Up (+Z), horizontal projection is zero → falls back to Rear
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 1.0, "y")
        assertApprox(result.finalPoint.z, 0.0, "z")
    }

    // ── Test 8: Empty pipe ───────────────────────────────────────────────────────

    it should "return empty result for empty Seq" in {
        val result = PositionTracker.computeFlowOnly15544(Seq.empty, None, Vec3(0, 0, 0))
        result.segments.isEmpty shouldBe true
        assertVec3Approx(result.finalPoint, Vec3(0, 0, 0))
        result.finalFrame shouldBe None
    }

    // ── Test 9: Negative elevation_gain ─────────────────────────────────────────

    it should "place end at (0,0,-2) for downward vertical section with length=2" in {
        import AddFlowOnlyPipeElement_15544_V3.*
        val elems = Seq(
            AddSectionVertical("down", -2.0.meters)
        )
        val result = PositionTracker.computeFlowOnly15544(elems, None, Vec3(0, 0, 0))
        assertApprox(result.finalPoint.x, 0.0, "x")
        assertApprox(result.finalPoint.y, 0.0, "y")
        assertApprox(result.finalPoint.z, -2.0, "z")
        result.segments.size shouldBe 1
        assertApprox(result.segments.head.length, 2.0, "length")
    }

    // ── Test 10: Chained computation ─────────────────────────────────────────────

    it should "chain flue and connector computations correctly" in {
        import AddFlowOnlyPipeElement_15544_V3.*
        // Flue pipe: 1m vertical from origin
        val flueElems  = Seq(AddSectionVertical("v", 1.0.meters))
        val flueResult = PositionTracker.computeFlowOnly15544(flueElems, None, Vec3(0, 0, 0))
        assertVec3Approx(flueResult.finalPoint, Vec3(0, 0, 1))

        // Connector: start from flue's final point, 1 horizontal section (no initial direction → Rear)
        val connElems  = Seq(AddSectionHorizontal("h", 1.0.meters))
        val connResult = PositionTracker.computeFlowOnly15544(connElems, None, flueResult.finalPoint)
        assertApprox(connResult.finalPoint.x, 0.0, "x")
        assertApprox(connResult.finalPoint.y, 1.0, "y")
        assertApprox(connResult.finalPoint.z, 1.0, "z")
    }

end PositionTrackerSuite
