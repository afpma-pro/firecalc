/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import afpma.firecalc.engine.models.ElementRef
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.PipeIdx
import afpma.firecalc.i18n.ShowUsingLocale

import cats.Show

import io.taig.babel.Locale
import io.taig.babel.Locales

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the localized rendering of the flow-resistance-coefficient error ADT for
 * [[SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound]]: a two-line
 * message (plain-language guidance on top, the unchanged technical line below),
 * direction-dependent guidance (below the minimum → curvature radius too small,
 * above the maximum → diameter too small), the optional element-reference suffix
 * on the plain-language line, and the generic
 * [[SingularFlowResistanceCoeffError.ValueOutOfBound]] rendering (with and without
 * the suffix) as a regression guard.
 */
class RadiusDiameterRatioRenderingSuite extends AnyFlatSpec with Matchers:

    case class TestShape(label: String)
    given Show[TestShape] = Show.show[TestShape](s => s"TestShape(${s.label})")

    private def showEn(err: SingularFlowResistanceCoeffError): String =
        given Locale = Locales.en
        ShowUsingLocale[SingularFlowResistanceCoeffError].show(err)

    private def showFr(err: SingularFlowResistanceCoeffError): String =
        given Locale = Locales.fr
        ShowUsingLocale[SingularFlowResistanceCoeffError].show(err)

    /** a/Dh = 0.8 < 1.0 — 2x45 segment elbow on a too-large pipe. */
    private def ratioBelowMin: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape] =
        SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape](
            TestShape("CoudeASegment90"),
            FluePipeT,
            "a/Dh",
            0.8,
            1.0,
            5.0
        )(using summon[Show[TestShape]], None)

    /** R/Dh = 3.3 > 2.0 — smooth bend with an oversized radius. */
    private def ratioAboveMax: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape] =
        SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape](
            TestShape("CoudeCourbe90"),
            FluePipeT,
            "R/Dh",
            3.3,
            0.0,
            2.0
        )(using summon[Show[TestShape]], None)

    private def genericOutOfBound: SingularFlowResistanceCoeffError.ValueOutOfBound[TestShape] =
        SingularFlowResistanceCoeffError.ValueOutOfBound[TestShape](
            TestShape("AngleVifDe0A90"),
            FluePipeT,
            "gamma",
            100.0,
            0.0,
            90.0
        )(using summon[Show[TestShape]], None)

    /** The element reference attached to the "referenced" cases below. */
    private val elRef3: ElementRef = ElementRef(PipeIdx(3), "Coude 90°")

    private def ratioBelowMinReferenced: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape] =
        SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[TestShape](
            TestShape("CoudeASegment90"),
            FluePipeT,
            "a/Dh",
            0.8,
            1.0,
            5.0,
            Some     (elRef3           )
        )(using summon[Show[TestShape]], None)

    private def genericOutOfBoundReferenced: SingularFlowResistanceCoeffError.ValueOutOfBound[TestShape] =
        SingularFlowResistanceCoeffError.ValueOutOfBound[TestShape](
            TestShape("AngleVifDe0A90"),
            FluePipeT,
            "gamma",
            100.0,
            0.0,
            90.0,
            Some     (elRef3          )
        )(using summon[Show[TestShape]], None)

    "RadiusDiameterRatioOutOfBound" should "derive the direction from the stored value" in {
        ratioBelowMin.tooSmall shouldBe true
        ratioAboveMax.tooSmall shouldBe false
    }

    it should "render the radius-too-small guidance above the technical line in English" in {
        showEn(ratioBelowMin) shouldBe
            "Curvature radius too small for this diameter\n" +
            "Value out of bound > could not interpolate on 'a/Dh' = 0.8 (expected 1.0 <= a/Dh <= 5.0)"
    }

    it should "render the radius-too-small guidance above the technical line in French" in {
        showFr(ratioBelowMin) shouldBe
            "Rayon de courbure trop petit pour ce diamètre\n" +
            "Valeur hors limite : interpolation impossible au point 'a/Dh' = 0.8 (expected 1.0 <= 'a/Dh' <= 5.0)"
    }

    it should "render the diameter-too-small guidance above the technical line in English" in {
        showEn(ratioAboveMax) shouldBe
            "Diameter too small for this curvature radius\n" +
            "Value out of bound > could not interpolate on 'R/Dh' = 3.3 (expected 0.0 <= R/Dh <= 2.0)"
    }

    it should "render the diameter-too-small guidance above the technical line in French" in {
        showFr(ratioAboveMax) shouldBe
            "Diamètre trop petit pour ce rayon de courbure\n" +
            "Valeur hors limite : interpolation impossible au point 'R/Dh' = 3.3 (expected 0.0 <= 'R/Dh' <= 2.0)"
    }

    "ValueOutOfBound" should "keep its single-line rendering unchanged in English" in {
        showEn(genericOutOfBound) shouldBe
            "Value out of bound > could not interpolate on 'gamma' = 100.0 (expected 0.0 <= gamma <= 90.0)"
    }

    it should "keep its single-line rendering unchanged in French" in {
        showFr(genericOutOfBound) shouldBe
            "Valeur hors limite : interpolation impossible au point 'gamma' = 100.0 (expected 0.0 <= 'gamma' <= 90.0)"
    }

    // ---- with element reference: suffix on the plain-language line (line 1) ----

    it should "append the element reference to the guidance line in English" in {
        showEn(ratioBelowMinReferenced) shouldBe
            "Curvature radius too small for this diameter (element #3 'Coude 90°')\n" +
            "Value out of bound > could not interpolate on 'a/Dh' = 0.8 (expected 1.0 <= a/Dh <= 5.0)"
    }

    it should "append the element reference to the guidance line in French" in {
        showFr(ratioBelowMinReferenced) shouldBe
            "Rayon de courbure trop petit pour ce diamètre (élément #3 'Coude 90°')\n" +
            "Valeur hors limite : interpolation impossible au point 'a/Dh' = 0.8 (expected 1.0 <= 'a/Dh' <= 5.0)"
    }

    "ValueOutOfBound" should "append the element reference to its single line in English" in {
        showEn(genericOutOfBoundReferenced) shouldBe
            "Value out of bound > could not interpolate on 'gamma' = 100.0 (expected 0.0 <= gamma <= 90.0) " +
            "(element #3 'Coude 90°')"
    }

    it should "append the element reference to its single line in French" in {
        showFr(genericOutOfBoundReferenced) shouldBe
            "Valeur hors limite : interpolation impossible au point 'gamma' = 100.0 (expected 0.0 <= 'gamma' <= 90.0) " +
            "(élément #3 'Coude 90°')"
    }

    // The existing no-reference cases above pin the exact rendered strings — that is
    // the byte-identical-rendering guard for this feature. This case adds the
    // explicit suffix-absence guard.

    it should "render no element-reference suffix when unreferenced" in {
        showEn(ratioBelowMin) should not include "element #"
        showFr(ratioBelowMin) should not include "élément #"
        showEn(genericOutOfBound) should not include "element #"
        showFr(genericOutOfBound) should not include "élément #"
    }

    // ---- withElementRef: the section handler's stamping method, at the concrete type ----

    "withElementRef" should "attach the element reference to an unreferenced error" in {
        showEn(genericOutOfBound.withElementRef(ElementRef(PipeIdx(1), "X"))) shouldBe
            "Value out of bound > could not interpolate on 'gamma' = 100.0 (expected 0.0 <= gamma <= 90.0) " +
            "(element #1 'X')"
    }

    // ---- withElementIndex: rewrite only the index of the element reference ----

    "withElementIndex" should "rewrite only the index in the element-reference suffix" in {
        // typed existentially: the UI remap consumes these errors with unknown shape type
        val ratio: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[?] = ratioBelowMinReferenced
        showEn(ratio.withElementIndex(PipeIdx(2))) shouldBe
            "Curvature radius too small for this diameter (element #2 'Coude 90°')\n" +
            "Value out of bound > could not interpolate on 'a/Dh' = 0.8 (expected 1.0 <= a/Dh <= 5.0)"
        val generic: SingularFlowResistanceCoeffError.ValueOutOfBound[?] = genericOutOfBoundReferenced
        showEn(generic.withElementIndex(PipeIdx(2))) shouldBe
            "Value out of bound > could not interpolate on 'gamma' = 100.0 (expected 0.0 <= gamma <= 90.0) " +
            "(element #2 'Coude 90°')"
    }

    it should "be a no-op when no element reference is attached" in {
        val ratio: SingularFlowResistanceCoeffError.RadiusDiameterRatioOutOfBound[?] = ratioBelowMin
        showEn(ratio.withElementIndex(PipeIdx(2))) shouldBe
            "Curvature radius too small for this diameter\n" +
            "Value out of bound > could not interpolate on 'a/Dh' = 0.8 (expected 1.0 <= a/Dh <= 5.0)"
        val generic: SingularFlowResistanceCoeffError.ValueOutOfBound[?] = genericOutOfBound
        showEn(generic.withElementIndex(PipeIdx(2))) shouldBe
            "Value out of bound > could not interpolate on 'gamma' = 100.0 (expected 0.0 <= gamma <= 90.0)"
    }

end RadiusDiameterRatioRenderingSuite
