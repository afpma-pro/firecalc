/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.i18n.ShowUsingLocale

import io.taig.babel.Locale
import io.taig.babel.Locales

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the pre-solve zero-geometry fail-fast surface: directly
 * [[NonPositivePipeGeometry.checkAll]] (caller-list collection order and the
 * strictly-positive boundary), and the localized rendering of
 * [[NonPositivePipeGeometry]] with its dispatch through the generic
 * `ShowUsingLocale[IncrementalValidation_Error]` given (the wire `code` is
 * the class name, the `message` the i18n template).
 */
class NonPositivePipeGeometrySuite extends AnyFlatSpec with Matchers:

    private def showEn(err: IncrementalValidation_Error): String =
        given Locale = Locales.en
        ShowUsingLocale[IncrementalValidation_Error].show(err)

    private def showFr(err: IncrementalValidation_Error): String =
        given Locale = Locales.fr
        ShowUsingLocale[IncrementalValidation_Error].show(err)

    "NonPositivePipeGeometry" should "render the strict-positivity message in English" in {
        val err = NonPositivePipeGeometry(
            NonPositivePipeGeometry.Quantity.FireboxWidthA,
            0.0.meters,
            FireboxPipeT
        )
        showEn(err) shouldBe "'Firebox width (A)' must be strictly positive (value = 0.000 m)"
    }

    it should "render the strict-positivity message in French" in {
        val err = NonPositivePipeGeometry(
            NonPositivePipeGeometry.Quantity.FireboxHeightH,
            0.0.meters,
            FireboxPipeT
        )
        showFr(err) shouldBe "'Hauteur du foyer (H)' doit avoir une valeur strictement positive (valeur = 0.000 m)"
    }

    it should "resolve every quantity name under the current locale" in {
        given Locale = Locales.en
        // Every case must resolve to a distinct, non-empty localized name
        // (guards the i18n key wiring, including the Ecolabeled cases the
        // private Austrian overlay produces).
        import NonPositivePipeGeometry.Quantity
        // Enumerate the enum itself, so a newly added case cannot silently escape
        // the i18n guard below.
        val quantities: List[Quantity] = Quantity.values.toList
        val names = quantities.map(_.name)
        withClue("empty quantity name: ")(names.forall(_.nonEmpty) shouldBe true)
        names.distinct should have size quantities.size.toLong
        Quantity.FireboxWidthA.name shouldBe "Firebox width (A)"
        Quantity.FireboxDepthB.name shouldBe "Firebox depth (B)"
        Quantity.FireboxHeightH.name shouldBe "Firebox height (H)"
    }

    it should "carry the offending quantity, value, and section type" in {
        val err = NonPositivePipeGeometry(
            NonPositivePipeGeometry.Quantity.FireboxDepthB,
            0.0.meters,
            FireboxPipeT
        )
        err.sectionTyp shouldBe FireboxPipeT
        err.quantity shouldBe NonPositivePipeGeometry.Quantity.FireboxDepthB
        err.value shouldBe 0.0.meters
    }

    it should "return None from checkAll for an empty list" in {
        NonPositivePipeGeometry.checkAll(Nil, FireboxPipeT) shouldBe None
    }

    it should "return None from checkAll when every value is strictly positive" in {
        val items = List(
            NonPositivePipeGeometry.Quantity.FireboxWidthA  -> 0.5.meters,
            NonPositivePipeGeometry.Quantity.FireboxDepthB  -> 0.4.meters,
            NonPositivePipeGeometry.Quantity.FireboxHeightH -> 1.0.meters
        )
        NonPositivePipeGeometry.checkAll(items, FireboxPipeT) shouldBe None
    }

    it should "collect only the offending items from checkAll in caller list order" in {
        // Deliberately H, B, A: neither enum declaration order (which matches
        // §7.1's A, B, H listing) nor any other canonical order — checkAll
        // must preserve what the caller passed, and drop the
        // strictly-positive entry.
        val items     = List(
            NonPositivePipeGeometry.Quantity.FireboxHeightH -> 0.0.meters,
            NonPositivePipeGeometry.Quantity.FireboxDepthB  -> (-2.0).meters,
            NonPositivePipeGeometry.Quantity.FireboxWidthA  -> 0.5.meters
        )
        val found     = NonPositivePipeGeometry.checkAll(items, FireboxPipeT)
        val offenders = found.map(_.toList.map(e => (e.quantity, e.value, e.sectionTyp)))
        offenders shouldBe Some(
            List  (
                (NonPositivePipeGeometry.Quantity.FireboxHeightH, 0.0.meters, FireboxPipeT  ),
                (NonPositivePipeGeometry.Quantity.FireboxDepthB, (-2.0).meters, FireboxPipeT)
            )
        )
    }

    it should "treat exactly zero as offending and any positive epsilon as valid in checkAll" in {
        val q    = NonPositivePipeGeometry.Quantity
        val zero = NonPositivePipeGeometry.checkAll(List(q.FireboxWidthA -> 0.0.meters), FireboxPipeT)
        zero.map(_.toList.map(_.quantity)) shouldBe Some(List(q.FireboxWidthA))
        // …while the smallest positive metre value passes.
        val tiny = NonPositivePipeGeometry.checkAll(
            List(q.FireboxWidthA -> Double.MinPositiveValue.meters),
            FireboxPipeT
        )
        tiny shouldBe None
    }
