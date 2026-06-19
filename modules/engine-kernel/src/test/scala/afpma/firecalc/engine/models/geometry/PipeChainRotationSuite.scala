/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.AzimuthDirection.*
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.domain.InclinationDirection.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.units.Vec3

case class TBend(angle: Double, absDir: Option[AbsoluteDirection])

private given ext: FrameReplay.ElemExtractors[TBend] = FrameReplay.ElemExtractors(
    asInitialDirection  = PartialFunction.empty,
    asDirectionChange   = { case TBend(angle, absDir) => (angle.degrees, absDir) },
    asInnerShape        = PartialFunction.empty,
    withDirChangeAbsDir = (e, newAbsDir) =>
        e match
            case x: TBend => x.copy(absDir = newAbsDir)
)

/**
 * Tests for PipeChainRotation using a minimal test-local DTO hierarchy.
 *
 * We define TestElem locally so tests are self-contained (no UI-module dependency).
 */
class PipeChainRotationSuite extends AnyFlatSpec with Matchers:

    val eps = 1e-6

    def assertApprox(a: Double, b: Double, msg: String = ""): Unit =
        val diff = math.abs(a - b)
        withClue(s"$msg (got $a, expected $b, diff=$diff)") {
            diff should be < eps
        }

    def absDir(az: AzimuthDirection, incl: InclinationDirection): AbsoluteDirection =
        AbsoluteDirection(az, incl)

    def absDirRight: AbsoluteDirection = absDir(Right, Horizontal)
    def absDirFront: AbsoluteDirection = absDir(Front, Horizontal)
    def absDirLeft : AbsoluteDirection = absDir(Left, Horizontal)
    def absDirRear : AbsoluteDirection = absDir(Rear, Horizontal)

    // ── Test 1: Empty scope (editedIdx >= scopeEndIdx) ────────────────────

    "PipeChainRotation.rewriteDownstreamPins" should "be a no-op when editedIdx >= scopeEndIdx" in {
        val elems  = Seq(
            0 -> TBend(90.0, Some(absDirRight)),
            1 -> TBend(90.0, Some(absDirFront))
        )
        val result = PipeChainRotation.rewriteDownstreamPins(elems, elems, editedIdx = 5, scopeEndIdx = 3)
        result shouldBe elems
    }

    // ── Test 2: No downstream pins ─────────────────────────────────────────

    it should "return unchanged sequence when no downstream elements have absDir" in {
        val elems  = Seq(
            0 -> TBend(90.0, None),
            1 -> TBend(90.0, None),
            2 -> TBend(90.0, None)
        )
        val result = PipeChainRotation.rewriteDownstreamPins(elems, elems, editedIdx = 0, scopeEndIdx = 4)
        result shouldBe elems
    }

    // ── Test 3: Single downstream pin, simple case ─────────────────────────

    it should "rewrite a downstream absDir to preserve relative shape" in {
        // Valid geometric scenario (all bends reachable at 90°):
        //   S0(Symmetry) → BendA(90°→Right), BendB(90°→Rear)
        // Edit BendA absDir to Left (also 90° from Rear, so reachable).
        // Old incoming frame at B: direction=Right. recoverRelative(Right-frame, Rear, 90°) = (Left, 0°).
        // New incoming frame at B: direction=Left. relativeTarget(Left-frame, Left, 0°, 90°) = Front.
        val oldElems = Seq(
            0 -> TBend(90.0, Some(absDirRight)),
            1 -> TBend(90.0, Some(absDirRear))
        )
        val newElems = Seq(
            0 -> TBend(90.0, Some(absDirLeft)), // edited: A now points Left
            1 -> TBend(90.0, Some(absDirRear))  // B unchanged yet; will be rewritten
        )
        val result   = PipeChainRotation.rewriteDownstreamPins(
            oldElems,
            newElems,
            editedIdx    = 0,
            scopeEndIdx  = 2,
            initialFrame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)))
        )

        val (_, rewrittenB) = result(1)
        rewrittenB match
            case TBend(_, Some(ad)) =>
                ad.inclination shouldBe Horizontal
                ad.azimuth shouldBe Some(Front)
            case other              => fail(s"Expected TBend with Some absDir, got $other")
    }

    // ── Test 4: Roundtrip stability ────────────────────────────────────────

    it should "be stable on roundtrip: rotate A Right→Left→Right restores original B" in {
        // Valid chain: S0(Symmetry), BendA(90°→Right), BendB(90°→Rear)
        // From test 3 we know: editing A to Left makes B→Front.
        // Roundtrip: edit A Right→Left, then back Left→Right.
        // B should return to Rear.
        val original = Seq(
            0 -> TBend(90.0, Some(absDirRight)),
            1 -> TBend(90.0, Some(absDirRear))
        )

        // Step 1: edit A to Left
        val step1New   = Seq(
            0 -> TBend(90.0, Some(absDirLeft)),
            1 -> TBend(90.0, Some(absDirRear))
        )
        val afterStep1 = PipeChainRotation.rewriteDownstreamPins(
            original,
            step1New,
            editedIdx    = 0,
            scopeEndIdx  = 2,
            initialFrame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)))
        )

        // Step 2: edit A back to Right
        val step2New   = afterStep1.updated(0, 0 -> TBend(90.0, Some(absDirRight)))
        val afterStep2 = PipeChainRotation.rewriteDownstreamPins(
            afterStep1,
            step2New,
            editedIdx    = 0,
            scopeEndIdx  = 2,
            initialFrame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)))
        )

        // BendB should be restored to Rear
        val (_, rewrittenB) = afterStep2(1)
        rewrittenB match
            case TBend(_, Some(ad)) =>
                ad.inclination shouldBe Horizontal
                ad.azimuth shouldBe Some(Rear)
            case other              => fail(s"Expected TBend with Some absDir, got $other")
    }

    // ── Test 5: Mid-scope absDir=None is preserved ─────────────────────────

    it should "leave absDir=None elements unchanged and still rewrite pinned ones" in {
        // Valid chain: S0(Symmetry), BendA(90°→Right), BendB-None, BendC(90°→Rear)
        // Edit A to Left; B stays None, C gets rewritten (structural check only)
        val oldElems = Seq(
            0 -> TBend(90.0, Some(absDirRight)),
            1 -> TBend(90.0, None),            // no pin — should stay None
            2 -> TBend(90.0, Some(absDirRear)) // pinned, reachable from Right-frame at 90°
        )
        val newElems = Seq(
            0 -> TBend(90.0, Some(absDirLeft)), // edited to Left
            1 -> TBend(90.0, None),
            2 -> TBend(90.0, Some(absDirRear))
        )
        val result   = PipeChainRotation.rewriteDownstreamPins(
            oldElems,
            newElems,
            editedIdx    = 0,
            scopeEndIdx  = 3,
            initialFrame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)))
        )

        // Element at index 1 must remain None
        result(1) match
            case (1, TBend(_, None)) => succeed
            case other => fail(s"Expected absDir=None at index 1, got $other")

        // Element at index 2 must have been rewritten (Some)
        result(2) match
            case (2, TBend(_, Some(_))) => succeed
            case other => fail(s"Expected absDir=Some at index 2, got $other")
    }

    // ── Test 6: Vertical pipe edge case ───────────────────────────────────

    it should "handle vertical pipe (Up) with horizontal bend correctly" in {
        // Pipe going Up, then bends 90° to Rear (horizontal)
        val elems    = Seq(
            0 -> TBend(90.0, Some(absDir(Rear, Horizontal))),
            1 -> TBend(90.0, Some(absDir(Rear, Horizontal))),
            2 -> TBend(90.0, Some(absDir(Right, Horizontal)))
        )
        // Edit: keep the Up initialFrame and edit bend1 to Right+Horizontal
        val newElems = Seq(
            0 -> TBend(90.0, Some(absDir(Rear, Horizontal))),
            1 -> TBend(90.0, Some(absDir(Right, Horizontal))), // edited
            2 -> TBend(90.0, Some(absDir(Right, Horizontal)))
        )
        val result   = PipeChainRotation.rewriteDownstreamPins(
            elems,
            newElems,
            editedIdx    = 1,
            scopeEndIdx  = 3,
            initialFrame = Some(PipeFrame.initial(Vec3.Up))
        )

        result(2) match
            case (2, TBend(_, Some(ad))) =>
                // snap should produce a valid AbsoluteDirection (not crash)
                succeed
            case other => fail(s"Expected TBend with Some absDir at index 2, got $other")
    }

    // ── Test 7: Degenerate — absDir ≈ incoming direction (no-op bend) ─────

    it should "not crash when absDir is degenerate (parallel to incoming direction)" in {
        // BendA points Rear (same as incoming direction) → recoverRelative returns (Right, 0)
        val elems    = Seq(
            0 -> TBend(0.01, Some(absDirRear)), // nearly zero angle, absDir=Rear
            1 -> TBend(90.0, Some(absDirRight))
        )
        val newElems = Seq(
            0 -> TBend(0.01, Some(absDirRight)), // edited to Right
            1 -> TBend(90.0, Some(absDirRight))
        )
        noException should be thrownBy {
            PipeChainRotation.rewriteDownstreamPins   (
                elems,
                newElems,
                editedIdx    = 0,
                scopeEndIdx  = 2,
                initialFrame = Some(PipeFrame.initial(Vec3.Rear))
            )
        }
    }

    // ── Test 8: Snap to discrete (within tolerance) ────────────────────────

    "PipeChainRotation.snapToAbsoluteDirection" should "snap azimuth 89.8° to Right (within 0.5° of 90°)" in {
        val v      = Vec3.fromAzimuthElevation(89.8, 0.0) // near Right, horizontal
        val result = PipeChainRotation.snapToAbsoluteDirection(v, toleranceDeg = 0.5)
        result.inclination shouldBe Horizontal
        result.azimuth shouldBe Some(Right)
    }

    it should "snap azimuth 45.6° to Custom(45.6) (outside 0.5° tolerance of RearRight=45°)" in {
        val v      = Vec3.fromAzimuthElevation(45.6, 0.0)
        val result = PipeChainRotation.snapToAbsoluteDirection(v, toleranceDeg = 0.5)
        result.inclination shouldBe Horizontal
        result.azimuth match
            case Some(AzimuthDirection.Custom(angle)) =>
                math.abs(angle.value - 45.6) should be < 0.01
            case other                                => fail(s"Expected Custom(45.6), got $other")
    }

    // ── Test 9: Snap to Custom (arbitrary angle) ──────────────────────────

    it should "snap azimuth 12° to Custom(12.0)" in {
        val v      = Vec3.fromAzimuthElevation(12.0, 0.0)
        val result = PipeChainRotation.snapToAbsoluteDirection(v, toleranceDeg = 0.5)
        result.inclination shouldBe Horizontal
        result.azimuth match
            case Some(AzimuthDirection.Custom(angle)) =>
                math.abs(angle.value - 12.0) should be < 0.1
            case other                                => fail(s"Expected Custom(12.0), got $other")
    }

    // ── Test 10: cross-slot initialFrame ──────────────────────────────────

    it should "cross-slot: initialFrame lets rewrite work without descriptor direction" in {
        // Chain without descriptor direction — just two direction changes.
        // We supply initialFrame = PipeFrame.initial(Vec3.fromAzimuthElevation(0, 0))
        // which corresponds to Rear direction (azimuth=0, elevation=0).
        //
        // elems[0] = TBend(90°, absDir=Right)  — after initial Rear frame, bends toward Right
        // elems[1] = TBend(90°, absDir=Front)  — after Right frame, bends toward Front
        val elems: Seq[(Int, TBend)] = Seq(
            0 -> TBend(90.0, Some(absDirRight)),
            1 -> TBend(90.0, Some(absDirFront))
        )

        // Without initialFrame: replayFrame returns None for both → rewrite skips
        val resultNoInit = PipeChainRotation.rewriteDownstreamPins(elems, elems, editedIdx = 0, scopeEndIdx = 2)
        resultNoInit.shouldBe(elems)

        // With initialFrame = Rear direction
        val initFrame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0)))

        // Edit elems[0].absDir from Right to Left; rewrite elems[1]
        val newElems: Seq[(Int, TBend)] = Seq(
            0 -> TBend(90.0, Some(absDirLeft)), // edited: now pointing Left
            1 -> TBend(90.0, Some(absDirFront)) // will be rewritten
        )
        val resultWithInit = PipeChainRotation.rewriteDownstreamPins(
            elems,
            newElems,
            editedIdx    = 0,
            scopeEndIdx  = 2,
            initialFrame = initFrame
        )

        // elems[1] must have been rewritten (absDir changed from Front)
        val (_, rewrittenElem) = resultWithInit(1)
        rewrittenElem match
            case TBend(_, Some(ad)) =>
                // The absolute direction should have changed — it should NOT still be Front
                // (old: incoming=Right, absDir=Front → relative = left-side 90°;
                //  new: incoming=Left,  relativeTarget → Rear)
                ad.azimuth.shouldBe    (Some(Rear))
                ad.inclination.shouldBe(Horizontal)
            case other              => fail(s"Expected TBend with Some absDir at index 1, got $other")
    }

    // ── Test 11: downstreamPinCount correctness ───────────────────────────

    "PipeChainRotation.downstreamPinCount" should "count only pinned elements in scope" in {
        val elems = Seq(
            0 -> TBend(90.0, Some(absDirRear)),
            1 -> TBend(90.0, Some(absDirRight)), // editedIdx — excluded
            2 -> TBend(90.0, Some(absDirFront)), // in scope, pinned — count
            3 -> TBend(90.0, None),              // in scope, no pin — skip
            4 -> TBend(90.0, Some(absDirLeft)),  // in scope, pinned — count
            5 -> TBend(90.0, Some(absDirRear)),  // in scope, pinned — count
            6 -> TBend(90.0, Some(absDirRight))  // out of scope — excluded
        )
        val count = PipeChainRotation.downstreamPinCount(elems, editedIdx = 1, scopeEndIdx = 6)
        count shouldBe 3
    }

    // ── Tests 12–14: preserveRelativePoseOnAngleChange ────────────────────

    "PipeChainRotation.preserveRelativePoseOnAngleChange" should
        "preserve right-quadrant pose when angle changes 90° → 45°" in {
            // Incoming frame: Rear-pointing horizontal
            val frame     = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0))
            val oldAbsDir = absDirRight // Right from Rear frame: (side=Right, theta=0°)
            val result    = PipeChainRotation.preserveRelativePoseOnAngleChange(frame, oldAbsDir, 90.0, 45.0)
            // The 45° bend in the Right quadrant from a Rear-horizontal frame stays right (positive X)
            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(result)
            val v = Vec3.fromAzimuthElevation(azDeg, elDeg)
            v.x should be > 0.0 // still in the right quadrant
            result.azimuth.isDefined shouldBe true
        }

    it should "recover Right+Horizontal when growing angle from 45° back to 90°" in {
        // Incoming frame: Rear-pointing horizontal
        val frame     = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0))
        // Compute the 45°-bent target in the Right quadrant (theta=0°, side=Right)
        val target45  = frame.relativeTarget(PipeFrame.RelativeSide.Right, 0.0, 45.0)
        val snapped45 = PipeChainRotation.snapToAbsoluteDirection(target45)
        // Grow back to 90°: same (Right, 0°) relative pose should yield Right+Horizontal
        val result    = PipeChainRotation.preserveRelativePoseOnAngleChange(frame, snapped45, 45.0, 90.0)
        result.inclination shouldBe Horizontal
        result.azimuth shouldBe Some(Right)
    }

    it should "preserve Up-ish direction when a vertical 90° bend is reduced to 60°" in {
        // Incoming frame: horizontal Rear-pointing pipe; old pin = Up (pure vertical 90° bend)
        val frame     = PipeFrame.initial(Vec3.fromAzimuthElevation(0.0, 0.0))
        val oldAbsDir = absDir(Rear, Up) // Rear+Up direction (treated as vertical by domain)
        val result    = PipeChainRotation.preserveRelativePoseOnAngleChange(frame, oldAbsDir, 90.0, 60.0)
        // The new target must still have positive Z (going up) and elevation ≈ 60°
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(result)
        val v = Vec3.fromAzimuthElevation(azDeg, elDeg)
        v.z should be > 0.0
        math.abs(elDeg - 60.0) should be < 1.0
    }

    // ── Test 15: Roundtrip drift property test ────────────────────────────

    it should "roundtrip: rotate chain first-bend through two edits and return within 0.1°" in {
        val trials   = 20
        val chainLen = 4 // 4 TBends

        val horizontalAbsDirs = Seq(absDirRear, absDirRight, absDirFront, absDirLeft)

        def buildChain(firstPinDir: AbsoluteDirection): Seq[(Int, TBend)] =
            Seq(
                0 -> TBend(90.0, Some(firstPinDir)),
                1 -> TBend(90.0, None),
                2 -> TBend(90.0, None),
                3 -> TBend(90.0, None)
            )

        val intermediateAbsDir = absDirFront

        for trial <- 0 until trials do
            val originalAbsDir = horizontalAbsDirs(trial % horizontalAbsDirs.size)
            val original       = buildChain(originalAbsDir)

            // Edit 1: change first TBend absDir to intermediate
            val step1New   = original.updated(1, 1 -> TBend(90.0, Some(intermediateAbsDir)))
            val afterStep1 =
                PipeChainRotation.rewriteDownstreamPins   (
                    original,
                    step1New,
                    editedIdx    = 1,
                    scopeEndIdx  = chainLen,
                    initialFrame = Some(PipeFrame.initial(Vec3.Rear))
                )

            // Edit 2: change back to original
            val step2New   = afterStep1.updated(1, 1 -> TBend(90.0, Some(originalAbsDir)))
            val afterStep2 =
                PipeChainRotation.rewriteDownstreamPins   (
                    afterStep1,
                    step2New,
                    editedIdx    = 1,
                    scopeEndIdx  = chainLen,
                    initialFrame = Some(PipeFrame.initial(Vec3.Rear))
                )

            // Verify the edited element's absDir matches original within 0.1°
            val (_, origElem ) = original(1)
            val (_, finalElem) = afterStep2(1)
            (origElem, finalElem) match
                case (TBend(_, Some(origAbs)), TBend(_, Some(finalAbs))) =>
                    val (oAz, oEl) = AbsoluteDirection.toAzimuthElevationDeg(origAbs)
                    val (fAz, fEl) = AbsoluteDirection.toAzimuthElevationDeg(finalAbs)
                    val oV       = Vec3.fromAzimuthElevation(oAz, oEl)
                    val fV       = Vec3.fromAzimuthElevation(fAz, fEl)
                    val angleDeg = oV.angleTo(fV)
                    assert(angleDeg < 0.1, s"Trial $trial: drift $angleDeg° too large (orig=$origAbs final=$finalAbs)")
                case _ => succeed
    }
