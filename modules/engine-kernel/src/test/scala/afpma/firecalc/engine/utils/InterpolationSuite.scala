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
                Map(1 -> 10, 3 -> 30, 2 -> 20).map((x, y) => (x.toDouble, y.toDouble))
            m1.getWithLinearInterpolation(2.5).shouldBe(Right(25.0))
        }

        "works on unordered data" in {
            val unordered = List[(Double, Double)](
                (3.0, 30.0),
                (2.0, 20.0)
            )
            val xi        = 2.5
            val yi        = unordered.getWithLinearInterpolation(xi)
            yi.shouldBe(Right(25.0))
        }

        "returns EmptyDataSet for empty input" in {
            val empty = List.empty[(Double, Double)]
            empty.getWithLinearInterpolation(1.0) shouldBe Left(InterpolationError.EmptyDataSet)
        }

        "returns ValueOutOfRange when xi is below all data points" in {
            val data   = List((10.0, 100.0), (20.0, 200.0))
            val result = data.getWithLinearInterpolation(5.0)
            result shouldBe Left(InterpolationError.ValueOutOfRange("xi", "yi", 5.0))
        }

        "returns ValueOutOfRange when xi is above all data points" in {
            val data   = List((10.0, 100.0), (20.0, 200.0))
            val result = data.getWithLinearInterpolation(25.0)
            result shouldBe Left(InterpolationError.ValueOutOfRange("xi", "yi", 25.0))
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

    "CustomInterpolator" - {

        // ── (1,1) — diagonal grid ─────────────────────────────────────

        "(1,1) interpolates on diagonal grid" in {
            // Each x-column has exactly 1 y-value
            val data = List(
                (10.0, 1.7, 6.0),
                (20.0, 2.5, 10.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At midpoint x=15, expected y ≈ 2.1
            val result = interp.interpolateAt(15.0, 2.1)
            result shouldBe a[Right[?, ?]]
            result.toOption.get shouldBe 8.0 +- 0.1
        }

        "(1,1) rejects yi out of tolerance" in {
            val data = List(
                (10.0, 1.7, 6.0),
                (20.0, 2.5, 10.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=15, expected y ≈ 2.1, but we pass y=5.0 (way off)
            interp.interpolateAt(15.0, 5.0) shouldBe a[Left[?, ?]]
        }

        "(1,1) returns exact value at grid point" in {
            val data = List(
                (10.0, 1.7, 6.0),
                (20.0, 2.5, 10.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(10.0, 1.7) shouldBe Right(6.0)
            interp.interpolateAt(20.0, 2.5) shouldBe Right(10.0)
        }

        // ── (1,n) — left column single, right column multi ───────────

        "(1,n) interpolates when left column has 1 y-value" in {
            // x=0 has y=[3] only, x=10 has y=[1, 3, 5]
            val data = List(
                (0.0, 3.0, 100.0),   // x=0, y=3
                (10.0, 1.0, 10.0),   // x=10, y=1
                (10.0, 3.0, 50.0),   // x=10, y=3
                (10.0, 5.0, 90.0)    // x=10, y=5
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5 (midpoint), yMin = 3 + (1-3)*0.5 = 2.0, yMax = 3 + (5-3)*0.5 = 4.0
            // At yi=3.0: s = (3-2)/(4-2) = 0.5
            //   y1_proj = 3 + 0.5*(3-3) = 3.0 → z1 = 100
            //   y2_proj = 1 + 0.5*(5-1) = 3.0 → z2 = 50
            //   z = 100 + (50-100)*0.5 = 75
            interp.interpolateAt(5.0, 3.0) shouldBe Right(75.0)
        }

        "(1,n) interpolates near boundary" in {
            val data = List(
                (0.0, 3.0, 100.0),
                (10.0, 1.0, 10.0),
                (10.0, 3.0, 50.0),
                (10.0, 5.0, 90.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5, yi=2.0 (bottom boundary = yMin)
            // s = 0 → y1_proj=3, y2_proj=1 → z1=100, z2=10 → z=55
            interp.interpolateAt(5.0, 2.0) shouldBe Right(55.0)

            // At x=5, yi=4.0 (top boundary = yMax)
            // s = 1 → y1_proj=3, y2_proj=5 → z1=100, z2=90 → z=95
            interp.interpolateAt(5.0, 4.0) shouldBe Right(95.0)
        }

        "(1,n) rejects yi out of range" in {
            val data = List(
                (0.0, 3.0, 100.0),
                (10.0, 1.0, 10.0),
                (10.0, 3.0, 50.0),
                (10.0, 5.0, 90.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            // At x=5, yMin=2, yMax=4. yi=1.0 is below yMin
            interp.interpolateAt(5.0, 1.0) shouldBe a[Left[?, ?]]
        }

        // ── (n,1) — left column multi, right column single ───────────

        "(n,1) interpolates when right column has 1 y-value" in {
            // x=0 has y=[1, 3, 5], x=10 has y=[3] only
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 3.0, 50.0),
                (0.0, 5.0, 90.0),
                (10.0, 3.0, 100.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5 (midpoint), yMin = 1 + (3-1)*0.5 = 2.0, yMax = 5 + (3-5)*0.5 = 4.0
            // At yi=3.0: s = (3-2)/(4-2) = 0.5
            //   y1_proj = 1 + 0.5*(5-1) = 3.0 → z1 = 50
            //   y2_proj = 3 + 0.5*(3-3) = 3.0 → z2 = 100
            //   z = 50 + (100-50)*0.5 = 75
            interp.interpolateAt(5.0, 3.0) shouldBe Right(75.0)
        }

        "(n,1) interpolates near boundary" in {
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 3.0, 50.0),
                (0.0, 5.0, 90.0),
                (10.0, 3.0, 100.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5, yi=2.0 (bottom boundary = yMin)
            // s = 0 → y1_proj=1, y2_proj=3 → z1=10, z2=100 → z=55
            interp.interpolateAt(5.0, 2.0) shouldBe Right(55.0)

            // At x=5, yi=4.0 (top boundary = yMax)
            // s = 1 → y1_proj=5, y2_proj=3 → z1=90, z2=100 → z=95
            interp.interpolateAt(5.0, 4.0) shouldBe Right(95.0)
        }

        "(n,1) rejects yi out of range" in {
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 3.0, 50.0),
                (0.0, 5.0, 90.0),
                (10.0, 3.0, 100.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(5.0, 5.0) shouldBe a[Left[?, ?]]
        }

        // ── (n,n) rectangular — both columns, same y-ranges ──────────

        "(n,n) rectangular grid gives same result as bilinear" in {
            // Standard 2x2 rectangular grid
            val data = List(
                (0.0, 0.0, 1.0),
                (0.0, 1.0, 2.0),
                (1.0, 0.0, 3.0),
                (1.0, 1.0, 4.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // Midpoint: bilinear gives (1+2+3+4)/4 = 2.5
            interp.interpolateAt(0.5, 0.5) shouldBe Right(2.5)
        }

        "(n,n) rectangular grid interpolates along x for known y" in {
            val data = List(
                (10.0, 1.6, 4.0),
                (10.0, 2.4, 3.0),
                (10.0, 3.2, 2.0),
                (10.0, 4.0, 1.0),
                (25.0, 1.6, 22.0),
                (25.0, 2.4, 20.0),
                (25.0, 3.2, 18.0),
                (25.0, 4.0, 16.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=17.5, y=1.6: z1=4, z2=22, z = 4 + 18*0.5 = 13
            interp.interpolateAt(17.5, 1.6) shouldBe Right(13.0)
        }

        "(n,n) rectangular grid interpolates along y for known x" in {
            val data = List(
                (10.0, 1.6, 4.0),
                (10.0, 2.4, 3.0),
                (10.0, 3.2, 2.0),
                (10.0, 4.0, 1.0),
                (25.0, 1.6, 22.0),
                (25.0, 2.4, 20.0),
                (25.0, 3.2, 18.0),
                (25.0, 4.0, 16.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=10, y=2.0: between (1.6,4) and (2.4,3)
            // z = 4 + (3-4)*(2.0-1.6)/(2.4-1.6) = 4 - 0.5 = 3.5
            interp.interpolateAt(10.0, 2.0) shouldBe Right(3.5)
        }

        // ── (n,n) non-rectangular — columns with different y-ranges ──

        "(n,n) non-rectangular grid: x2 wider than x1" in {
            // x=0 has y=[2, 4], x=10 has y=[1, 3, 5]
            val data = List(
                (0.0, 2.0, 20.0),
                (0.0, 4.0, 40.0),
                (10.0, 1.0, 10.0),
                (10.0, 3.0, 30.0),
                (10.0, 5.0, 50.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5 (midpoint), yMin = 2+(1-2)*0.5 = 1.5, yMax = 4+(5-4)*0.5 = 4.5
            // At yi=3.0: s = (3-1.5)/(4.5-1.5) = 0.5
            //   y1_proj = 2 + 0.5*(4-2) = 3.0 → between (2,20) and (4,40): z1 = 30
            //   y2_proj = 1 + 0.5*(5-1) = 3.0 → exact grid point: z2 = 30
            //   z = 30 + (30-30)*0.5 = 30
            interp.interpolateAt(5.0, 3.0) shouldBe Right(30.0)
        }

        "(n,n) non-rectangular grid: x1 wider than x2" in {
            // x=0 has y=[1, 3, 5], x=10 has y=[2, 4]
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 3.0, 30.0),
                (0.0, 5.0, 50.0),
                (10.0, 2.0, 20.0),
                (10.0, 4.0, 40.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5, yMin = 1+(2-1)*0.5 = 1.5, yMax = 5+(4-5)*0.5 = 4.5
            // At yi=3.0: s = (3-1.5)/(4.5-1.5) = 0.5
            //   y1_proj = 1 + 0.5*(5-1) = 3.0 → exact grid point: z1 = 30
            //   y2_proj = 2 + 0.5*(4-2) = 3.0 → between (2,20) and (4,40): z2 = 30
            //   z = 30 + (30-30)*0.5 = 30
            interp.interpolateAt(5.0, 3.0) shouldBe Right(30.0)
        }

        "(n,n) non-rectangular grid: no y-overlap" in {
            // x=0 has y=[1, 2], x=10 has y=[4, 5]
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 2.0, 20.0),
                (10.0, 4.0, 40.0),
                (10.0, 5.0, 50.0)
            )
            val interp = new CustomInterpolator("x", "y", data)

            // At x=5, yMin = 1+(4-1)*0.5 = 2.5, yMax = 2+(5-2)*0.5 = 3.5
            // At yi=3.0: s = (3-2.5)/(3.5-2.5) = 0.5
            //   y1_proj = 1 + 0.5*(2-1) = 1.5 → between (1,10) and (2,20): z1 = 15
            //   y2_proj = 4 + 0.5*(5-4) = 4.5 → between (4,40) and (5,50): z2 = 45
            //   z = 15 + (45-15)*0.5 = 30
            interp.interpolateAt(5.0, 3.0) shouldBe Right(30.0)
        }

        "(n,n) non-rectangular rejects yi out of range" in {
            val data = List(
                (0.0, 2.0, 20.0),
                (0.0, 4.0, 40.0),
                (10.0, 1.0, 10.0),
                (10.0, 5.0, 50.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            // At x=5, yMin=1.5, yMax=4.5
            interp.interpolateAt(5.0, 0.5) shouldBe a[Left[?, ?]]
            interp.interpolateAt(5.0, 5.0) shouldBe a[Left[?, ?]]
        }

        // ── Common: exact grid point and empty data ──────────────────

        "returns exact value at grid point for any configuration" in {
            val data = List(
                (0.0, 1.0, 10.0),
                (0.0, 3.0, 30.0),
                (10.0, 2.0, 20.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(0.0, 1.0) shouldBe Right(10.0)
            interp.interpolateAt(0.0, 3.0) shouldBe Right(30.0)
            interp.interpolateAt(10.0, 2.0) shouldBe Right(20.0)
        }

        "returns EmptyDataSet for empty data" in {
            val interp = new CustomInterpolator("x", "y", List.empty)
            interp.interpolateAt(1.0, 1.0) shouldBe Left(InterpolationError.EmptyDataSet)
        }

        "rejects xi out of range" in {
            val data = List(
                (10.0, 1.0, 10.0),
                (20.0, 1.0, 20.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(5.0, 1.0) shouldBe a[Left[?, ?]]
            interp.interpolateAt(25.0, 1.0) shouldBe a[Left[?, ?]]
        }

        // ── Boundary: xi == x_max / x_min with interpolated yi ──

        "interpolates at xi == x_max with interpolated yi" in {
            val data = List(
                (10.0, 1.6, 4.0), (10.0, 2.4, 3.0), (10.0, 3.2, 2.0), (10.0, 4.0, 1.0),
                (25.0, 1.6, 22.0), (25.0, 2.4, 20.0), (25.0, 3.2, 18.0), (25.0, 4.0, 16.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            // At x=25 (x_max), y=2.0: between (1.6,22) and (2.4,20) → z = 22 + (20-22)*(2.0-1.6)/(2.4-1.6) = 21
            interp.interpolateAt(25.0, 2.0) shouldBe Right(21.0)
        }

        "interpolates at xi == x_min with interpolated yi" in {
            val data = List(
                (10.0, 1.6, 4.0), (10.0, 2.4, 3.0), (10.0, 3.2, 2.0), (10.0, 4.0, 1.0),
                (25.0, 1.6, 22.0), (25.0, 2.4, 20.0), (25.0, 3.2, 18.0), (25.0, 4.0, 16.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            // At x=10 (x_min), y=2.0: between (1.6,4) and (2.4,3) → z = 4 + (3-4)*(2.0-1.6)/(2.4-1.6) = 3.5
            interp.interpolateAt(10.0, 2.0) shouldBe Right(3.5)
        }

        "interpolates at xi == x_max on non-rectangular grid" in {
            val data = List(
                (0.0, 2.0, 20.0), (0.0, 4.0, 40.0),
                (10.0, 1.0, 10.0), (10.0, 3.0, 30.0), (10.0, 5.0, 50.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            // At x=10 (x_max), y=2.0: between (1,10) and (3,30) → z = 10 + (30-10)*(2-1)/(3-1) = 20
            interp.interpolateAt(10.0, 2.0) shouldBe Right(20.0)
        }

        "rejects yi out of range at xi == x_max" in {
            val data = List(
                (10.0, 1.6, 4.0), (10.0, 2.4, 3.0),
                (25.0, 1.6, 22.0), (25.0, 2.4, 20.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(25.0, 5.0) shouldBe a[Left[?, ?]]
        }

        "still rejects xi > x_max" in {
            val data = List(
                (10.0, 1.6, 4.0), (10.0, 2.4, 3.0),
                (25.0, 1.6, 22.0), (25.0, 2.4, 20.0)
            )
            val interp = new CustomInterpolator("x", "y", data)
            interp.interpolateAt(30.0, 2.0) shouldBe a[Left[?, ?]]
        }
    }
}
