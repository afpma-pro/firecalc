/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verifies the shared air-intake topology shape check:
 * declared intake shape vs topology-computed pipe-end shape.
 *
 * The comparison is tolerance-aware (same variant, 0.1 cm) via
 * [[PipeShape.equalsTolerance]] and yields
 * [[AirIntakePipeShapeTopologyMismatch]] carrying the `show` rendering of
 * both shapes — the same error Door15a check 2 has always emitted.
 */
class AirIntakeShapeChecksSuite extends AnyFlatSpec with Matchers:
    import PipeShape.*

    "AirIntakeShapeChecks.topologyMismatch" should "return None for identical declared and computed shapes" in {
        val shape = Circle(0.2.meters)

        AirIntakeShapeChecks.topologyMismatch(shape, shape) shouldBe None
    }

    it should "return None when the shapes differ only by floating-point drift within the 0.1 cm tolerance" in {
        // 0.2 m vs 0.19999999999999998 m both round to 20.0 cm at first-decimal precision
        val declared = Circle(0.2.meters)
        val computed = Circle(0.19999999999999998.meters)

        AirIntakeShapeChecks.topologyMismatch(declared, computed) shouldBe None
    }

    it should "return AirIntakePipeShapeTopologyMismatch when the dimension differs beyond tolerance" in {
        val declared = Circle(0.2.meters)
        val computed = Circle(0.25.meters)

        AirIntakeShapeChecks.topologyMismatch(declared, computed) shouldBe
            Some(AirIntakePipeShapeTopologyMismatch(declared = "◯ 20.0 cm", computed = "◯ 25.0 cm"))
    }

    it should "return AirIntakePipeShapeTopologyMismatch for different variants with equal dimensions" in {
        val declared = Circle(0.2.meters)
        val computed = Square(0.2.meters)

        AirIntakeShapeChecks.topologyMismatch(declared, computed) shouldBe
            Some(AirIntakePipeShapeTopologyMismatch(declared = "◯ 20.0 cm", computed = "□ 20.0 cm"))
    }

    it should "treat rectangle comparison as order-sensitive" in {
        val declared = Rectangle(0.3.meters, 0.2.meters)
        val computed = Rectangle(0.2.meters, 0.3.meters)

        AirIntakeShapeChecks.topologyMismatch(declared, computed) shouldBe
            Some(
                AirIntakePipeShapeTopologyMismatch(declared = "▭ 30.0 cm x 20.0 cm", computed = "▯ 20.0 cm x 30.0 cm")
            )
    }

end AirIntakeShapeChecksSuite
