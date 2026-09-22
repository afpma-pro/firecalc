/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.standard.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the error selection of the EN 13384-1 dynamic-friction-coefficient
 * helpers: the three radius/diameter shapes (2x45 segment elbow a/Dh, smooth
 * 90/60 bends R/Dh) refine the kernel's generic out-of-bound error into
 * [[SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound]] with the
 * derived direction and the correct target, while the non-radius open-angle
 * elbow keeps the generic [[SingularFlowResistanceCoeffError.ValueOutOfBound]].
 *
 * The R/Dh shapes' lower table bound is 0.0 — unreachable for a non-negative
 * radius — so the below-bound direction is pinned by the a/Dh shape here and by
 * both directions in the rendering suite (engine-kernel).
 */
class RadiusDiameterRatioErrorSelectionSuite extends AnyFlatSpec with Matchers:

    import FlowOnlyPipeDescr_13384.*

    private val circle30 = PipeShape.Circle(0.30.meters)

    /** R = 28 cm on a 30 cm pipe → a/Dh ≈ 0.77 < 1.0 (the user-reported case). */
    private val segmentElbowBelowMin =
        CoudeASegment90Avec2A45(R = 0.28.meters, Dh = 0.30.meters, None, circle30)

    /** R = 1 m on a 30 cm pipe → R/Dh ≈ 3.33 > 2.0. */
    private val smoothBend90AboveMax =
        CoudeCourbe90(R = 1.0.meters, Dh = 0.30.meters, Ld = 3.0.meters, None, circle30)
    private val smoothBend60AboveMax =
        CoudeCourbe60(R = 1.0.meters, Dh = 0.30.meters, Ld = 3.0.meters, None, circle30)

    /** gamma = 100° > 90° — non-radius shape, must keep the generic error. */
    private val openAngleElbowAboveMax =
        AngleVifDe0A90(ɣ = 100.degrees, Ld = 3.0.meters, Dh = 0.30.meters, None, circle30)

    private def dfc(sc: SlotContext): DynamicFrictionCoeff_13384 =
        DynamicFrictionCoeff_13384()(using FluePipeT, sc)

    private def firstError[A](op: DynamicFrictionCoeffOp[A], shape: A): SingularFlowResistanceCoeffErrorI =
        op.dynamicFrictionCoeff(shape).toEither match
            case Left(errs) => errs.head
            case Right(z)   => fail(s"expected an interpolation error, got ζ = $z")

    "CoudeASegment90 below the a/Dh table minimum" should "be refined into RadiusDiameterRatioOutOfBound (radius too small)" in {
        val sc   = SlotContext.forSlot(SlotIndex.unsafe(3))
        val inst = dfc(sc)
        import inst.given
        val err  = firstError(DynamicFrictionCoeffOp.apply[CoudeASegment90], segmentElbowBelowMin)
        err match
            case e: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[?] =>
                e.tooSmall shouldBe true
                e.vTermName shouldBe "a/Dh"
                e.v shouldBe (0.56 * math.tan(math.Pi / 8) / 0.30) +- 1e-9
                e.vMin shouldBe 1.0
                e.vMax shouldBe 5.0
                e.sectionTyp shouldBe FluePipeT
                e.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(3))
                e.elRef shouldBe None
            case other => fail(s"expected RadiusDiameterRatioOutOfBound, got $other")
    }

    "CoudeCourbe90 above the R/Dh table maximum" should "be refined into RadiusDiameterRatioOutOfBound (diameter too small)" in {
        val sc   = SlotContext.forSlot(SlotIndex.unsafe(3))
        val inst = dfc(sc)
        import inst.given
        val err  = firstError(DynamicFrictionCoeffOp.apply[CoudeCourbe90], smoothBend90AboveMax)
        err match
            case e: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[?] =>
                e.tooSmall shouldBe false
                e.vTermName shouldBe "R/Dh"
                e.v shouldBe (1.0 / 0.30) +- 1e-9
                e.vMin shouldBe 0.0
                e.vMax shouldBe 2.0
                e.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(3))
                e.elRef shouldBe None
            case other => fail(s"expected RadiusDiameterRatioOutOfBound, got $other")
    }

    "CoudeCourbe60 above the R/Dh table maximum" should "be refined into RadiusDiameterRatioOutOfBound (diameter too small)" in {
        val sc   = SlotContext.forSlot(SlotIndex.unsafe(3))
        val inst = dfc(sc)
        import inst.given
        val err  = firstError(DynamicFrictionCoeffOp.apply[CoudeCourbe60], smoothBend60AboveMax)
        err match
            case e: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[?] =>
                e.tooSmall shouldBe false
                e.vTermName shouldBe "R/Dh"
                e.v shouldBe (1.0 / 0.30) +- 1e-9
                e.vMin shouldBe 0.0
                e.vMax shouldBe 2.0
                e.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(3))
                e.elRef shouldBe None
            case other => fail(s"expected RadiusDiameterRatioOutOfBound, got $other")
    }

    "AngleVifDe0A90 above the gamma table maximum" should "keep the generic ValueOutOfBound" in {
        val sc   = SlotContext.forSlot(SlotIndex.unsafe(3))
        val inst = dfc(sc)
        import inst.given
        val err  = firstError(DynamicFrictionCoeffOp.apply[AngleVifDe0A90], openAngleElbowAboveMax)
        err match
            case e: SingularFlowResistanceCoeffError.ValueOutOfBound[?] =>
                e.vTermName shouldBe "gamma"
                e.v shouldBe 100.0
                e.vMin shouldBe 0.0
                e.vMax shouldBe 90.0
                e.elRef shouldBe None
            case other => fail(s"expected ValueOutOfBound, got $other")
    }

end RadiusDiameterRatioErrorSelectionSuite
