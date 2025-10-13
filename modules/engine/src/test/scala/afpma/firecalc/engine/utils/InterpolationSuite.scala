/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils


import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.engine.utils.*

class InterpolationSuite extends AnyFreeSpec with Matchers {

    "getWithLinearInterpolation" - {
        
        "works on ordered data" in {
            val m1 =
                Map(1 -> 10, 3 -> 30, 2 -> 20).map((x, y) =>
                    (x.toDouble, y.toDouble)
                )
            m1.getWithLinearInterpolation(2.5).shouldBe(Some(25.0))
        }

        "works on unordered data" in {
            val unordered = List[(Double, Double)](
                (3.0, 30.0),
                (2.0, 20.0)
            )
            val xi = 2.5
            val yi = unordered.getWithLinearInterpolation(xi)
            yi.shouldBe(Some(25.0))
        }
    }
}
