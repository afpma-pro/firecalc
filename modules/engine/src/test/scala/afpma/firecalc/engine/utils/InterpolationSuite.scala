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
            m1.getWithLinearInterpolation(2.5).shouldBe(Right(25.0))
        }

        "works on unordered data" in {
            val unordered = List[(Double, Double)](
                (3.0, 30.0),
                (2.0, 20.0)
            )
            val xi = 2.5
            val yi = unordered.getWithLinearInterpolation(xi)
            yi.shouldBe(Right(25.0))
        }

        "returns EmptyDataSet for empty input" in {
            val empty = List.empty[(Double, Double)]
            empty.getWithLinearInterpolation(1.0) shouldBe Left(InterpolationError.EmptyDataSet)
        }

        "returns ValueOutOfRange when xi is below all data points" in {
            val data = List((10.0, 100.0), (20.0, 200.0))
            val result = data.getWithLinearInterpolation(5.0)
            result shouldBe Left(InterpolationError.ValueOutOfRange(5.0))
        }

        "returns ValueOutOfRange when xi is above all data points" in {
            val data = List((10.0, 100.0), (20.0, 200.0))
            val result = data.getWithLinearInterpolation(25.0)
            result shouldBe Left(InterpolationError.ValueOutOfRange(25.0))
        }

        "returns exact value when xi matches a data point" in {
            val data = List((10.0, 100.0), (20.0, 200.0))
            data.getWithLinearInterpolation(10.0) shouldBe Right(100.0)
        }
    }

    "getWithBilinearInterpolation" - {

        val grid = List(
            (0.0, 0.0, 1.0),
            (0.0, 1.0, 2.0),
            (1.0, 0.0, 3.0),
            (1.0, 1.0, 4.0)
        )

        "returns EmptyDataSet for empty input" in {
            val empty = List.empty[(Double, Double, Double)]
            empty.getWithBilinearInterpolation(0.5, 0.5) shouldBe Left(InterpolationError.EmptyDataSet)
        }

        "returns ValueOutOfRange when xi is outside grid" in {
            val result = grid.getWithBilinearInterpolation(2.0, 0.5)
            result shouldBe a[Left[?, ?]]
            result.left.toOption.get shouldBe a[InterpolationError.ValueOutOfRange]
        }

        "returns ValueOutOfRange when yi is outside grid" in {
            val result = grid.getWithBilinearInterpolation(0.5, 2.0)
            result shouldBe a[Left[?, ?]]
            result.left.toOption.get shouldBe a[InterpolationError.ValueOutOfRange]
        }

        "interpolates correctly at grid midpoint" in {
            val result = grid.getWithBilinearInterpolation(0.5, 0.5)
            result shouldBe Right(2.5)
        }

        "returns exact value at grid corner" in {
            grid.getWithBilinearInterpolation(0.0, 0.0) shouldBe Right(1.0)
            grid.getWithBilinearInterpolation(1.0, 1.0) shouldBe Right(4.0)
        }
    }
}
