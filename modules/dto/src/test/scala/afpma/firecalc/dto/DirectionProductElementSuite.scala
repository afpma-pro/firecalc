/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_13384_V3.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.units.coulombutils.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verify that productElement indices match field declaration order for DC subtypes.
 * This catches Scala.js-specific issues with case classes inheriting `override val` fields.
 */
class DirectionProductElementSuite extends AnyFreeSpec with Matchers:

    val fd = Some(AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up))

    "AddSharpeAngle_0_to_90 productElement order" in {
        val dc = AddSharpeAngle_0_to_90("test", 45.0.degrees, fd)
        dc.productArity.shouldBe(3)
        dc.productElement(0).shouldBe("test")
        dc.productElement(1).shouldBe(45.0.degrees)
        dc.productElement(2).shouldBe(fd)
    }

    "AddAngleAdjustable productElement order" in {
        val dc = AddAngleAdjustable("test", 90.0.degrees, 0.5.unitless, fd)
        dc.productArity.shouldBe(4)
        dc.productElement(0).shouldBe("test")
        dc.productElement(1).shouldBe(90.0.degrees)
        dc.productElement(2).shouldBe(0.5.unitless)
        dc.productElement(3).shouldBe(fd)
    }

    "AddSmoothCurve_90 productElement order" in {
        val dc = AddSmoothCurve_90("test", 15.0.cm, fd)
        dc.productArity.shouldBe(3)
        dc.productElement(0).shouldBe("test")
        dc.productElement(1).shouldBe(15.0.cm)
        dc.productElement(2).shouldBe(fd)
    }

    "AddElbows_2x45 productElement order" in {
        val dc = AddElbows_2x45("test", 15.0.cm, fd)
        dc.productArity.shouldBe(3)
        dc.productElement(0).shouldBe("test")
        dc.productElement(1).shouldBe(15.0.cm)
        dc.productElement(2).shouldBe(fd)
    }

    "productElement returns correct types (not cast-breaking)" in {
        val dc = AddSharpeAngle_0_to_90("test", 45.0.degrees, fd)
        // The angle field should be a Double (Angle = QtyD[Degree] = Double at runtime)
        dc.productElement(1).isInstanceOf[Double].shouldBe(true)
        // The absDir field should NOT be a Double
        dc.productElement(2).isInstanceOf[Option[?]].shouldBe(true)
    }

    "productElement with None absDir" in {
        val dc = AddSharpeAngle_0_to_90("test", 45.0.degrees, None)
        dc.productElement(0).shouldBe("test")
        dc.productElement(1).shouldBe(45.0.degrees)
        dc.productElement(2).shouldBe(None)
    }

end DirectionProductElementSuite
