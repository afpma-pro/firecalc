/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import scala.collection.immutable.SortedMap

def bilinearInterpolation(
    x1: Double, x2: Double,
    y1: Double, y2: Double,
    z11: Double, z12: Double,
    z21: Double, z22: Double,
    x: Double, y: Double
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

// // Exemple d'utilisation
// val x1 = 0.0
// val x2 = 1.0
// val y1 = 0.0
// val y2 = 1.0
// val z11 = 10.0
// val z12 = 20.0
// val z21 = 15.0
// val z22 = 25.0
// val x = 0.5
// val y = 0.5

// val result = bilinearInterpolation(x1, x2, y1, y2, z11, z12, z21, z22, x, y)
// println(s"Valeur interpolée: $result")


extension (it: IterableOnce[(Double, Double)])
    def getWithLinearInterpolation(xi: Double): Option[Double] =

        val smap = SortedMap.from(it)

        val x1y1Opt = smap.foldLeft[Option[(Double, Double)]](None) {
            case (None, (x, y)) => 
                if (x <= xi) Some((x, y))
                else None
            case (x1y1 @ Some((x1, y1)), (x, y)) =>
                if (x <= xi)
                    Some(if (x >= x1) then (x, y) else (x1, y1))
                else
                    x1y1
            // case (x1y1, xy) =>
            //     throw new Exception(s"unexpected case (${x1y1}, ${xy})")
            //     None
        }

        val x2y2Opt = smap.foldRight[Option[(Double, Double)]](None) {
            case ((x, y), None)  => 
                if (x >= xi)
                    Some((x, y))
                else
                    None
            case ((x, y), x2y2 @ Some((x2, y2))) =>
                if (x >= xi)
                    Some(if (x < x2) then (x, y) else (x2, y2))
                else
                    x2y2
            // case (x2y2, xy) =>
            //     throw new Exception(s"unexpected case (${x2y2}, ${xy})")
            //     None
        }

        (x1y1Opt, x2y2Opt) match
            case (Some(x1, y1), Some(x2, y2)) =>
                if (x1 == x2)
                    assert(y1 == y2)
                    Some(y1)
                else
                    val coeff = (y2 - y1) / (x2 - x1)
                    val y = y1 + coeff * (xi - x1)
                    Some(y)
            case _ => None

extension (it: IterableOnce[(Double, Double, Double)])
    def getWithBilinearInterpolation(xi: Double, yi: Double): Option[Double] =
        val xyz_list = it.iterator.toList

        require(xyz_list.nonEmpty, "unexpected empty list for bilinear interpolation: can not interpolate")
        
        val xs = xyz_list.map(_._1).sorted
        val ys = xyz_list.map(_._2).sorted
        
        try
            val x1 = xs.filter(_ <= xi).last
            val x2 = xs.find(_ >= xi).get

            val y1 = ys.filter(_ <= yi).last
            val y2 = ys.find(_ >= yi).get

            val z11 = xyz_list.find((x,y,_) => x == x1 && y == y1).get._3
            val z12 = xyz_list.find((x,y,_) => x == x1 && y == y2).get._3
            val z21 = xyz_list.find((x,y,_) => x == x2 && y == y1).get._3
            val z22 = xyz_list.find((x,y,_) => x == x2 && y == y2).get._3

            // println(s"""|xi = $xi
            //             |yi = $yi
            //             |
            //             |x1  = $x1
            //             |x2  = $x2
            //             |
            //             |y1  = $y1
            //             |y2  = $y2
            //             |
            //             |z11 = $z11
            //             |z12 = $z12
            //             |z21 = $z21
            //             |z22 = $z22
            //             |""".stripMargin)
            
            // only linear interpolation on single x param
            if (x1 == x2)
                List((y1, z11), (y2, z12)).getWithLinearInterpolation(yi)
            // only linear interpolation on single y param
            else if (y1 == y2)
                List((x1, z12), (x2, z21)).getWithLinearInterpolation(xi)
            // no interpolation needed
            else if (x1 == x2 && y1 == y2)
                Some(z11)
            // interpolation on both x and y param
            else
                val zi = bilinearInterpolation(x1, x2, y1, y2, z11, z12, z21, z22, xi, yi)
                Some(zi)
        catch
            case e: NoSuchElementException => throw e
            case e => throw e
        