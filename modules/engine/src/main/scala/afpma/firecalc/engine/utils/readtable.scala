/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import afpma.firecalc.engine.models.TSVTableString
import afpma.firecalc.engine.models.getUsingBilinearInterpolation

object readtable:

    sealed abstract class ReadTableError(val msg: String)

    case class ValueOutOfBound(
        vTermName: String,
        v: Double,
        vMin: Double,
        vMax: Double
    ) extends ReadTableError(
        s"value out of bound > could not interpolate on '$vTermName' = $v (expected $vMin <= $vTermName <= $vMax)"
    )

    case class CouldNotInterpolate(override val msg: String) extends ReadTableError(msg)

    def fromTSVTableRaw_withBiInterpolatation(
        resName: String,
        tsvTableRawString: String,
        xHeader: String,
        yHeader: String,
        zHeader: String,
        xi: Double,
        yi: Double,
        xMinMax: (Double, Double),
        yMinMax: (Double, Double),
    ): Either[ReadTableError, Double] = {
        val (xmin, xmax) = xMinMax
        val (ymin, ymax) = yMinMax

        if (xi < xmin) Left(ValueOutOfBound(s"x ($xHeader)", xi, xmin, xmax))
        else if (xi > xmax) Left(ValueOutOfBound(s"x ($xHeader)", xi, xmin, xmax))
        else if (yi < ymin) Left(ValueOutOfBound(s"y ($yHeader)", yi, ymin, ymax))
        else if (yi > ymax) Left(ValueOutOfBound(s"y ($yHeader)", yi, ymin, ymax))
        else
            val data = TSVTableString.fromString(tsvTableRawString)
            for
                out <- data.getUsingBilinearInterpolation(xHeader, yHeader, zHeader)(xi, yi) match
                    case Some(coeff) => Right(coeff)
                    case None => Left(CouldNotInterpolate(
                        s"interpolation error for resource $resName, xHeader=$xHeader, yHeader=$yHeader, zHeader=$zHeader, xi=$xi, yi=$yi"))
            yield out
    }
