/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.standard.*

import cats.data.Validated

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the pre-solve zero-geometry fail-fast of the generic firebox-pipe
 * contribution: non-positive width (A), depth (B), or height (H) must return
 * `Validated.Invalid(NonEmptyList[NonPositivePipeGeometry])` — in A, B, H
 * order — *before* the incremental pipe builder runs (spec §7.1). Valid
 * geometries keep building the contribution unchanged.
 */
class FireboxContributionZeroGeometryPreCheckSuite extends AnyFlatSpec with Matchers:

    private def traditional(width: Length, depth: Length, height: Length): TraditionalFirebox =
        TraditionalFirebox                      (
            firebox_depth_B                       = depth,
            firebox_width_A                       = width,
            firebox_height_H                      = height,
            pressure_loss_coefficient_from_door   = 0.3.unitless,
            total_air_intake_surface_area_on_door = 92.cm2,
            glass_width                           = 0.15.meters,
            glass_height                          = 0.20.meters,
            ash_pit_height_AF                     = 5.cm
        )

    private def contribution(fb: TraditionalFirebox) =
        traditionalEngine.fireboxContribution(fb)

    private def expectInvalid(c: TraditionalFirebox): List[NonPositivePipeGeometry] =
        contribution(c) match
            case Validated.Invalid(nel) =>
                nel.toList.map:
                    case e: NonPositivePipeGeometry => e
                    case other => fail(s"expected NonPositivePipeGeometry, got $other")
            case Validated.Valid(v)     => fail(s"expected zero-geometry fail-fast, got valid $v")

    private def quantities(items: List[NonPositivePipeGeometry]): List[NonPositivePipeGeometry.Quantity] =
        items.map(_.quantity)

    "GenericFireboxContribution_Strict" should "accept strictly positive A/B/H geometry" in {
        contribution(traditional(33.2.cm, 44.0.cm, 51.3.cm)).isValid shouldBe true
    }

    it should "fail fast on zero width with the FireboxWidthA item" in {
        val items = expectInvalid(traditional(0.0.meters, 44.0.cm, 51.3.cm))
        quantities(items) shouldBe List(NonPositivePipeGeometry.Quantity.FireboxWidthA)
        items.head.value shouldBe 0.0.meters
        items.head.sectionTyp shouldBe FireboxPipeT
    }

    it should "fail fast on zero depth with the FireboxDepthB item" in {
        quantities(expectInvalid(traditional(33.2.cm, 0.0.meters, 51.3.cm))) shouldBe
            List(NonPositivePipeGeometry.Quantity.FireboxDepthB)
    }

    it should "fail fast on zero height with the FireboxHeightH item" in {
        quantities(expectInvalid(traditional(33.2.cm, 44.0.cm, 0.0.meters))) shouldBe
            List(NonPositivePipeGeometry.Quantity.FireboxHeightH)
    }

    it should "report negative dimensions as non-positive too" in {
        quantities(expectInvalid(traditional((-1.0).meters, 44.0.cm, 51.3.cm))) shouldBe
            List(NonPositivePipeGeometry.Quantity.FireboxWidthA)
    }

    it should "collect every offending dimension in A, B, H order" in {
        quantities(expectInvalid(traditional(0.0.meters, (-2.0).meters, 0.0.meters))) shouldBe
            List(
                NonPositivePipeGeometry.Quantity.FireboxWidthA,
                NonPositivePipeGeometry.Quantity.FireboxDepthB,
                NonPositivePipeGeometry.Quantity.FireboxHeightH
            )
    }
