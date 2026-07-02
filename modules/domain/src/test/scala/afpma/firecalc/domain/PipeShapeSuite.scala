/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import afpma.firecalc.units.coulombutils.*

class PipeShapeSuite extends AnyFreeSpec with Matchers:

    "PipeShape.equalsTolerance" - {

        "Circle" - {
            "same values compare equal" in {
                val s1 = PipeShape.Circle(0.173.m)
                val s2 = PipeShape.Circle(0.173.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "floating-point drift within 0.1 cm compares equal" in {
                val s1 = PipeShape.Circle(0.17299999999999999.m)
                val s2 = PipeShape.Circle(0.17300000000000002.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "different values > 0.1 cm apart compare false" in {
                val s1 = PipeShape.Circle(0.173.m)
                val s2 = PipeShape.Circle(0.200.m)
                s1.equalsTolerance(s2) shouldBe false
            }
        }

        "Square" - {
            "same values compare equal" in {
                val s1 = PipeShape.Square(0.15.m)
                val s2 = PipeShape.Square(0.15.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "floating-point drift within 0.1 cm compares equal" in {
                val s1 = PipeShape.Square(0.14999999999999999.m)
                val s2 = PipeShape.Square(0.15000000000000002.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "different values > 0.1 cm apart compare false" in {
                val s1 = PipeShape.Square(0.15.m)
                val s2 = PipeShape.Square(0.20.m)
                s1.equalsTolerance(s2) shouldBe false
            }
        }

        "Rectangle" - {
            "same values compare equal" in {
                val s1 = PipeShape.Rectangle(0.20.m, 0.15.m)
                val s2 = PipeShape.Rectangle(0.20.m, 0.15.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "floating-point drift within 0.1 cm on both dims compares equal" in {
                val s1 = PipeShape.Rectangle(0.19999999999999998.m, 0.15000000000000002.m)
                val s2 = PipeShape.Rectangle(0.20000000000000004.m, 0.14999999999999999.m)
                s1.equalsTolerance(s2) shouldBe true
            }

            "different a dimension > 0.1 cm apart compares false" in {
                val s1 = PipeShape.Rectangle(0.20.m, 0.15.m)
                val s2 = PipeShape.Rectangle(0.30.m, 0.15.m)
                s1.equalsTolerance(s2) shouldBe false
            }

            "different b dimension > 0.1 cm apart compares false" in {
                val s1 = PipeShape.Rectangle(0.20.m, 0.15.m)
                val s2 = PipeShape.Rectangle(0.20.m, 0.25.m)
                s1.equalsTolerance(s2) shouldBe false
            }
        }

        "cross-variant comparisons" - {
            "Circle vs Square always compares false" in {
                val s1 = PipeShape.Circle(0.15.m)
                val s2 = PipeShape.Square(0.15.m)
                s1.equalsTolerance(s2) shouldBe false
            }

            "Circle vs Rectangle always compares false" in {
                val s1 = PipeShape.Circle(0.15.m)
                val s2 = PipeShape.Rectangle(0.15.m, 0.15.m)
                s1.equalsTolerance(s2) shouldBe false
            }

            "Square vs Rectangle with same dimensions compares false" in {
                val s1 = PipeShape.Square(0.15.m)
                val s2 = PipeShape.Rectangle(0.15.m, 0.15.m)
                s1.equalsTolerance(s2) shouldBe false
            }
        }
    }

end PipeShapeSuite
