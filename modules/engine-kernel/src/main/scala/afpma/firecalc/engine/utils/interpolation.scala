/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import scala.collection.immutable.SortedMap
import scala.util.boundary
import scala.util.boundary.break

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.given

import cats.implicits.toShow


enum InterpolationError:
    case EmptyDataSet
    case ParseError(msg: String)
    case ValueOutOfRange(xTerm: String, yTerm: String, xi: Double, yi: Option[Double] = None, yi_min_expected: Option[Double] = None, yi_max_expected: Option[Double] = None)
    case MissingGridPoint(xi: Double, yi: Double)

object InterpolationError:

    given ShowUsingLocale[InterpolationError] = showUsingLocale:
        case EmptyDataSet     =>
            I18N.errors.empty_data_set
        case ParseError(msg)  =>
            I18N.errors.parse_error(msg)
        case ValueOutOfRange(xTerm, yTerm, xi, yi, Some(ymin), Some(ymax))  =>
            I18N.errors.value_out_of_range_with_range(xTerm, yTerm, xi.show, yi.map(_.show).getOrElse("-"), ymin.show, ymax.show)
        case ValueOutOfRange(xTerm, yTerm, xi, yi, _, _)  =>
            I18N.errors.value_out_of_range(xi.show, yi.map(_.show).getOrElse("-"))
        case MissingGridPoint(xi, yi) =>
            I18N.errors.missing_grid_point(xi.show, yi.show)

/** Standard bilinear interpolation on a rectangular cell.
  *
  * Given four corner values z11, z12, z21, z22 at the corners of
  * the rectangle [x1,x2] x [y1,y2], compute z(x, y) by weighted
  * combination of the four corners.
  *
  * {{{
  *   y2  z12 -------- z22
  *        |     (x,y)  |
  *   y1  z11 -------- z21
  *        x1           x2
  * }}}
  *
  * @param z11 value at (x1, y1)
  * @param z12 value at (x1, y2)
  * @param z21 value at (x2, y1)
  * @param z22 value at (x2, y2)
  */
def bilinearInterpolation(
    x1 : Double,
    x2 : Double,
    y1 : Double,
    y2 : Double,
    z11: Double,
    z12: Double,
    z21: Double,
    z22: Double,
    x  : Double,
    y  : Double
): Double = {
    require(x1 <= x && x <= x2, "x must be between x1 et x2")
    require(y1 <= y && y <= y2, "y must be between y1 et y2")

    // Poids pour l'interpolation
    val w11 = (x2 - x) * (y2 - y)
    val w21 = (x - x1) * (y2 - y)
    val w12 = (x2 - x) * (y - y1)
    val w22 = (x - x1) * (y - y1)

    // Normalisation des poids
    val normalizer = (x2 - x1) * (y2 - y1)

    // Calcul de z(x, y) en combinant les contributions des coins
    (z11 * w11 + z21 * w21 + z12 * w12 + z22 * w22) / normalizer
}

extension (it: IterableOnce[(Double, Double)])

    /** Linear interpolation on a 1-D data set of (x, y) pairs.
      *
      * Finds the two bounding data points (x1, y1) and (x2, y2) such that
      * x1 <= xi <= x2, then returns y1 + (y2 - y1) * (xi - x1) / (x2 - x1).
      * If xi matches a data point exactly, returns its y value.
      *
      * @return Left(ValueOutOfRange) if xi is outside the data range,
      *         Left(EmptyDataSet) if the data is empty.
      */
    def getWithLinearInterpolation(xi: Double): Either[InterpolationError, Double] =

        val smap = SortedMap.from(it)

        if smap.isEmpty then Left(InterpolationError.EmptyDataSet)
        else
            val x1y1Opt = smap.foldLeft[Option[(Double, Double)]](None) {
                case (None, (x, y)                 ) =>
                    if (x <= xi) Some((x, y))
                    else None
                case (x1y1 @ Some((x1, y1)), (x, y)) =>
                    if (x <= xi)
                        Some(if (x >= x1) then (x, y) else (x1, y1))
                    else
                        x1y1
            }

            val x2y2Opt = smap.foldRight[Option[(Double, Double)]](None) {
                case ((x, y), None                 ) =>
                    if (x >= xi)
                        Some((x, y))
                    else
                        None
                case ((x, y), x2y2 @ Some((x2, y2))) =>
                    if (x >= xi)
                        Some(if (x < x2) then (x, y) else (x2, y2))
                    else
                        x2y2
            }

            (x1y1Opt, x2y2Opt) match
                case (Some(x1, y1), Some(x2, y2)) =>
                    if (x1 == x2)
                        assert(y1 == y2)
                        Right (y1      )
                    else
                        val coeff = (y2 - y1) / (x2 - x1)
                        val y     = y1 + coeff * (xi - x1)
                        Right(y)
                case _ => Left(InterpolationError.ValueOutOfRange("xi", "yi", xi))


/** Interpolator for non-rectangular (trapezoidal) grids of (x, y) -> z data.
  *
  * The grid has multiple x-columns, each with its own set of y-values.
  * Columns may have different y-ranges and different numbers of y-points.
  * The valid interpolation domain forms a trapezoid between adjacent columns.
  *
  * Used by PressureLossTSVTableString for pressure loss interpolation
  * from (mass_kg, sb_cm) -> pressure_pa.
  *
  * == Grid configurations handled ==
  *
  * {{{
  *   (1,1) diagonal       (1,n) fan-out       (n,1) fan-in        (n,n) trapezoid
  *
  *   y|                   y|                   y|                   y|
  *    |          o         |          o         | o                  | o         o
  *    |        /           |        / |         | | \                | | \     / |
  *    |      /             |      /   |         | |   \              | |   \ /   |
  *    |    o               |    o     o         | o     o            | o   / \   o
  *    |                    |          |         |   \                | | /     \ |
  *    |                    |          o         |     o              | o         o
  *    +--------x           +--------x           +--------x           +--------x
  *      x1  x2              x1  x2              x1  x2              x1  x2
  * }}}
  *
  * == Algorithm overview ==
  *
  * 1. '''Exact match''': if (xi, yi) is an exact grid point, return its z directly.
  * 2. '''(1,1) diagonal''': both bounding columns have a single y-value each.
  *    The domain is a line, not a surface. Validate that yi is close (within
  *    tolerance) to the expected y at xi, then interpolate z linearly along x.
  * 3. '''General case''' (1,n), (n,1), (n,n): uses parametric projection.
  *    See `_interpolateAt_others` for details.
  *
  * @param xTerm human-readable name for the x-axis (used in error messages)
  * @param yTerm human-readable name for the y-axis (used in error messages)
  * @param it    the grid data as (x, y, z) triples
  */
class CustomInterpolator(
    xTerm: String,
    yTerm: String,
    it: IterableOnce[(Double, Double, Double)]
):

    private def valueOutOfRange(
        xi: Double, 
        yi: Option[Double], 
        yi_min_expected: Option[Double] = None, 
        yi_max_expected: Option[Double] = None
    ): InterpolationError = 
        InterpolationError.ValueOutOfRange(xTerm, yTerm, xi, yi, yi_min_expected, yi_max_expected)

    private val xyz_list = it.iterator.toList

    private def getGridPoint(x: Double, y: Double): Either[InterpolationError, (Double, Double, Double)] = 
        xyz_list.find(el => el._1 == x && el._2 == y).toRight(InterpolationError.MissingGridPoint(x, y))

    private def get_z_atGridPoint(x: Double, y: Double): Either[InterpolationError, Double] = 
        getGridPoint(x, y).map(_._3)

    /** Interpolate z along a single x-column at a given y-target.
      *
      * Finds the two bounding grid points yL and yH in the sorted `ys` list
      * such that yL <= yTarget <= yH, then linearly interpolates z between
      * z(x, yL) and z(x, yH).
      *
      * {{{
      *   z(x, yH) --- o       yH >= yTarget
      *                |
      *          ------+--      yTarget (interpolated)
      *                |
      *   z(x, yL) --- o       yL <= yTarget
      * }}}
      *
      * For a single-element column (yL == yH == yTarget), returns z(x, yL) directly.
      *
      * @param x       the x-coordinate of the column
      * @param ys      sorted list of y-coordinates in this column
      * @param yTarget the y-coordinate to interpolate at
      */
    private def interpolateZ_atColumn(
        x: Double,
        ys: List[Double],
        yTarget: Double
    ): Either[InterpolationError, Double] = 
        boundary:
            // Find bounding y-values: largest y <= yTarget and smallest y >= yTarget
            val yL = ys.filter(_ <= yTarget).lastOption  // ys is sorted, so lastOption = max
                .getOrElse(break(Left(valueOutOfRange(x, Some(yTarget)))))
            val yH = ys.find(_ >= yTarget)               // first match = min
                .getOrElse(break(Left(valueOutOfRange(x, Some(yTarget)))))

            // Linear interpolation between z(x, yL) and z(x, yH)
            // When yL == yH (exact match or single-element column), returns z(x, yL)
            List(
                (yL, get_z_atGridPoint(x, yL).fold(e => break(Left(e)), identity)),
                (yH, get_z_atGridPoint(x, yH).fold(e => break(Left(e)), identity))
            ).getWithLinearInterpolation(yTarget)

    /** Interpolate z at an arbitrary point (xi, yi) within the grid.
      *
      * @return Right(z) on success, Left(error) if the point is out of range,
      *         the data is empty, or a grid point is missing.
      */
    def interpolateAt(xi: Double, yi: Double): Either[InterpolationError, Double] = 
        boundary:
            if (xyz_list.isEmpty)
                break(Left(InterpolationError.EmptyDataSet))

            // Fast path: if (xi, yi) is an exact grid point, return z directly
            val zOpt = get_z_atGridPoint(xi, yi)
            if (zOpt.isRight) then break(zOpt)

            // Otherwise, interpolate between bounding columns
            _interpolateAt_others(xi, yi)


    /** Interpolate z at (xi, yi) using the two bounding x-columns.
      *
      * == Step 1: Find bounding columns ==
      *
      * {{{
      *   x1 <= xi < x2   (x1 = largest grid-x <= xi, x2 = smallest grid-x > xi)
      * }}}
      *
      * == Step 2: Compute the valid y-domain at xi ==
      *
      * The valid domain between two columns forms a trapezoid. The bottom
      * boundary (yMin) and top boundary (yMax) are linearly interpolated
      * from each column's y-range extremes:
      *
      * {{{
      *   y ^
      *     |
      * y1H o.............     yMax interpolated between y1H and y2H
      *     |  `.          `.
      * y2H |    `.          o
      *     |      `.      .'|
      *     |     [xi,yi].'  |      <-- the point we want to interpolate
      *     |        `. .'   |
      * y2L |          o     |
      *     |       .'  `.   |
      * y1L o.....'        `.|      yMin interpolated between y1L and y2L
      *     |                |
      *     +----+-----+----+---> x
      *         x1     xi   x2
      * }}}
      *
      * == Step 3: Dispatch by column size ==
      *
      * '''(1,1) diagonal''': both columns have a single y-point. The domain
      * degenerates to a line. Validate yi is within tolerance, then linear
      * interpolation along x.
      *
      * '''General case''' (1,n), (n,1), (n,n): uses parametric projection.
      *
      * == Step 4: Parametric projection ==
      *
      * Compute a normalized position s in [0, 1] within the trapezoid:
      *
      * {{{
      *     s = (yi - yMin) / (yMax - yMin)
      * }}}
      *
      * Then project s onto each column to get the y-coordinate to sample:
      *
      * {{{
      *     y1_proj = y1L + s * (y1H - y1L)    (position within x1's y-range)
      *     y2_proj = y2L + s * (y2H - y2L)    (position within x2's y-range)
      * }}}
      *
      * Visually, for s = 0.5 (midpoint):
      *
      * {{{
      *   y ^
      *     |
      * y1H o                  o y2H     s=1.0 (top boundary)
      *     |                  |
      * y1p o- - - - -[xi,yi]- o y2p     s=0.5 (projected)
      *     |                  |
      * y1L o                  o y2L     s=0.0 (bottom boundary)
      *     |                  |
      *     +--------+---------+---> x
      *             x1   xi   x2
      * }}}
      *
      * Finally, interpolate z along each column at the projected y, then
      * linearly interpolate between the two column-z values along x:
      *
      * {{{
      *     z1 = interpolateZ_atColumn(x1, y1s, y1_proj)
      *     z2 = interpolateZ_atColumn(x2, y2s, y2_proj)
      *     z  = z1 + (z2 - z1) * (xi - x1) / (x2 - x1)
      * }}}
      *
      * This approach handles all grid configurations uniformly:
      *  - (1,n): y1_proj = y1L always (single point), z1 is trivially the single z value.
      *  - (n,1): y2_proj = y2L always (single point), z2 is trivially the single z value.
      *  - (n,n) rectangular: y1_proj == y2_proj == yi, reduces to standard bilinear.
      *  - (n,n) non-rectangular: projects proportionally onto each column's range.
      */
    private def _interpolateAt_others(xi: Double, yi: Double): Either[InterpolationError, Double] = 
        val xs = xyz_list.map(_._1).sorted

        boundary:
            // --- Step 1: find the two bounding x-columns ---
            val x1 = xs.filter(_ <= xi).lastOption.getOrElse(break(Left(valueOutOfRange(xi, None))))
            val x2 = xs.find(_ > xi).getOrElse(break(Left(valueOutOfRange(xi, None))))

            // Collect sorted y-values and their range for each column
            val y1s = xyz_list.filter(_._1 == x1).map(_._2).sorted
            val y1L = y1s.min
            val y1H = y1s.max

            val y2s = xyz_list.filter(_._1 == x2).map(_._2).sorted
            val y2L = y2s.min
            val y2H = y2s.max

            // --- Step 2: compute the valid y-domain at xi ---
            // yMin/yMax define the trapezoid boundaries at xi
            val yMin = List(
                (x1, y1L),
                (x2, y2L)
            )
            .getWithLinearInterpolation(xi)
            .fold(e => break(Left(e)), identity)

            val yMax = List(
                (x1, y1H),
                (x2, y2H)
            )
            .getWithLinearInterpolation(xi)
            .fold(e => break(Left(e)), identity)

            // --- Step 3: dispatch by column size ---

            if (y1s.size == 1 && y2s.size == 1)
                // (1,1) diagonal: domain degenerates to a line between (x1,y1) and (x2,y2).
                // Validate yi is close to the expected y (within tolerance), then
                // interpolate z linearly along x.
                val y1 = y1s.head
                val y2 = y2s.head

                val z1 = get_z_atGridPoint(x1, y1s.head).fold(e => break(Left(e)), identity)
                val z2 = get_z_atGridPoint(x2, y2s.head).fold(e => break(Left(e)), identity)

                // Expected y at xi on the diagonal line
                val y_exp = List(
                    (x1, y1),
                    (x2, y2)
                )
                .getWithLinearInterpolation(xi)
                .fold(e => break(Left(e)), identity)

                // Enforce yi is on (or very close to) the diagonal
                val tol = 0.06
                if (!((y_exp - tol <= yi) && (yi <= y_exp + tol))) 
                    break(Left(valueOutOfRange(xi, Some(yi), yi_min_expected = Some(y_exp), yi_max_expected = Some(y_exp))))

                List(
                    (x1, z1),
                    (x2, z2)
                )
                .getWithLinearInterpolation(xi)
                .fold(e => break(Left(e)), Right.apply)

            else if (yi < yMin || yi > yMax)
                // Out of range: yi is outside the trapezoid at xi
                Left(valueOutOfRange(xi, Some(yi), yi_min_expected = Some(yMin), yi_max_expected = Some(yMax)))

            else
                // --- Step 4: parametric projection for (1,n), (n,1), (n,n) ---

                // Normalized position within the trapezoid: s=0 at yMin, s=1 at yMax
                val yRange = yMax - yMin
                val s = if (yRange < 1e-12) 0.5 else (yi - yMin) / yRange

                // Project s onto each column's y-range
                val y1_proj = y1L + s * (y1H - y1L)
                val y2_proj = y2L + s * (y2H - y2L)

                // Interpolate z along each column at the projected y
                val z1 = interpolateZ_atColumn(x1, y1s, y1_proj)
                    .fold(e => break(Left(e)), identity)
                val z2 = interpolateZ_atColumn(x2, y2s, y2_proj)
                    .fold(e => break(Left(e)), identity)

                // Linearly interpolate z between the two columns along x
                List((x1, z1), (x2, z2))
                    .getWithLinearInterpolation(xi)
                    .fold(e => break(Left(e)), Right.apply)

extension (it: IterableOnce[(Double, Double, Double)])

    /** Bilinear interpolation on a rectangular grid of (x, y, z) triples.
      *
      * Finds the four bounding grid points forming a rectangle around (xi, yi)
      * and applies standard bilinear interpolation. Falls back to linear
      * interpolation when xi or yi is exactly on a grid line.
      *
      * Note: this requires a '''rectangular''' grid where every (x, y) combination
      * exists. For non-rectangular grids, use `CustomInterpolator` instead.
      */
    def getWithBilinearInterpolation(xi: Double, yi: Double): Either[InterpolationError, Double] =
        val xyz_list = it.iterator.toList

        if xyz_list.isEmpty then Left(InterpolationError.EmptyDataSet)
        else
            val xs = xyz_list.map(_._1).sorted
            val ys = xyz_list.map(_._2).sorted

            boundary:
                val x1 = xs
                    .filter(_ <= xi)
                    .lastOption
                    .getOrElse(break(Left(InterpolationError.ValueOutOfRange("xi", "yi", xi, Some(yi)))))
                val x2 = xs.find(_ >= xi).getOrElse(break(Left(InterpolationError.ValueOutOfRange("xi", "yi", xi, Some(yi)))))

                val y1 = ys
                    .filter(_ <= yi)
                    .lastOption
                    .getOrElse(break(Left(InterpolationError.ValueOutOfRange("xi", "yi", xi, Some(yi)))))
                val y2 = ys.find(_ >= yi).getOrElse(break(Left(InterpolationError.ValueOutOfRange("xi", "yi", xi, Some(yi)))))

                val z11 = xyz_list
                    .find((x, y, _) => x == x1 && y == y1)
                    .getOrElse(break(Left(InterpolationError.MissingGridPoint(xi, yi))))
                    ._3
                val z12 = xyz_list
                    .find((x, y, _) => x == x1 && y == y2)
                    .getOrElse(break(Left(InterpolationError.MissingGridPoint(xi, yi))))
                    ._3
                val z21 = xyz_list
                    .find((x, y, _) => x == x2 && y == y1)
                    .getOrElse(break(Left(InterpolationError.MissingGridPoint(xi, yi))))
                    ._3
                val z22 = xyz_list
                    .find((x, y, _) => x == x2 && y == y2)
                    .getOrElse(break(Left(InterpolationError.MissingGridPoint(xi, yi))))
                    ._3

                // only linear interpolation on single x param
                if      (x1 == x2) List((y1, z11), (y2, z12)).getWithLinearInterpolation(yi)
                // only linear interpolation on single y param
                else if (y1 == y2) List((x1, z12), (x2, z21)).getWithLinearInterpolation(xi)
                // interpolation on both x and y param
                else
                    val zi = bilinearInterpolation(x1, x2, y1, y2, z11, z12, z21, z22, xi, yi)
                    Right(zi)
