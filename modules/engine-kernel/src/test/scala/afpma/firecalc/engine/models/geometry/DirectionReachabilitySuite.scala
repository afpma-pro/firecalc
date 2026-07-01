/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.units.Vec3

class DirectionReachabilitySuite extends AnyFlatSpec with Matchers:

    sealed trait TestElem
    case class TBend(angleDeg: Double, absDir: Option[(Double, Double)]) extends TestElem

    private given ext: FrameReplay.ElemExtractors[TestElem] = FrameReplay.ElemExtractors(
        asInitialDirection = PartialFunction.empty,
        asDirectionChange  = { case TBend(angle, absDir) =>
            (
                angle.degrees,
                absDir.map((az, el) =>
                    AbsoluteDirection(AzimuthDirection.fromDegrees(az), InclinationDirection.fromDegrees(el))
                )
            )
        },
        asInnerShape       = PartialFunction.empty
    )

    // ── Test 1: Valid chain — all bends reachable ──────────────────────────

    "checkSlot" should "return empty failures when all bends are reachable" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, Some((90.0, 0.0))) // Rear → Right (90° apart, reachable)
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Test 2: One unreachable bend ───────────────────────────────────────

    it should "detect a single unreachable direction change" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, Some((180.0, 0.0))) // Rear → Front (180° apart, NOT reachable with 90°)
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe List(1)
    }

    // ── Test 3: Multiple unreachable bends ─────────────────────────────────

    it should "detect multiple unreachable direction changes" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, Some((180.0, 0.0))), // Rear → Front (180°, unreachable), frame becomes Up
            2 -> TBend(90.0, Some((0.0, 0.0))),   // Up → Rear (90°, reachable), frame becomes Rear
            3 -> TBend(90.0, Some((180.0, 0.0)))  // Rear → Front (180°, unreachable)
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe List(1, 3)
    }

    // ── Test 4: No direction changes ───────────────────────────────────────

    it should "return empty failures when there are no direction changes" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq.empty[(Int, TestElem)]
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Test 5: Mix of absDir = None and Some reachable ────────────────────

    it should "ignore direction changes with absDir = None" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, None),             // no absDir set — skip
            2 -> TBend(90.0, Some((90.0, 0.0))) // reachable
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Test 6: absDir = None on a DC element — no error ───────────────────

    it should "not flag direction changes where absDir is not yet set" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, None) // user hasn't set direction yet → no error
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Additional: frame threading across elements ────────────────────────

    it should "thread the frame correctly across reachable direction changes" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, Some((90.0, 0.0))), // Rear → Right (90°), reachable → frame = Right
            2 -> TBend(90.0, Some((0.0, 0.0)))   // Right → Rear (90°), reachable → frame = Rear
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Additional: initialFrame parameter ─────────────────────────────────

    it should "use the initialFrame when descriptors do not provide direction" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            0 -> TBend(90.0, Some((90.0, 0.0))) // reachable from Rear
        )
        val (failures, _) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
    }

    // ── Additional: empty sequence ─────────────────────────────────────────

    it should "return empty failures for an empty element sequence" in {
        val (failures, frame) = DirectionReachability.checkSlot(Seq.empty[(Int, TestElem)], None)
        failures shouldBe empty
        frame shouldBe None
    }

    // ── Additional: final frame propagation ────────────────────────────────

    it should "return the final frame for downstream slot use" in {
        val initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)) // Rear
        val elems        = Seq(
            1 -> TBend(90.0, Some((90.0, 0.0))) // Rear → Right
        )
        val (failures, frame) = DirectionReachability.checkSlot[TestElem](elems, Some(initialFrame))
        failures shouldBe empty
        frame should not be None
    }
end DirectionReachabilitySuite
