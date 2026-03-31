/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import afpma.firecalc.engine.models.TSVTableString
import afpma.firecalc.engine.models.getUsingBilinearInterpolation
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.given

import cats.implicits.toShow

object readtable:

    sealed trait ReadTableError

    object ReadTableError:
        given ShowUsingLocale[ReadTableError] = showUsingLocale:
            case x: ValueOutOfBound     =>
                I18N.errors.value_out_of_bound(x.vTermName, x.v.show, x.vMin.show, x.vTermName, x.vMax.show)
            case x: CouldNotInterpolate =>
                I18N.errors.could_not_interpolate(x.resourceName, x.xHeader, x.yHeader, x.zHeader, x.xi.show, x.yi.show, x.err.show)

    case class ValueOutOfBound(
        vTermName: String,
        v        : Double,
        vMin     : Double,
        vMax     : Double
    ) extends ReadTableError

    case class CouldNotInterpolate(
        resourceName: String,
        xHeader: String,
        yHeader: String,
        zHeader: String,
        xi: Double,
        yi: Double,
        err: InterpolationError,
    ) extends ReadTableError

    def fromTSVTableRaw_withBiInterpolatation(
        resName          : String,
        tsvTableRawString: String,
        xHeader          : String,
        yHeader          : String,
        zHeader          : String,
        xi               : Double,
        yi               : Double,
        xMinMax          : (Double, Double),
        yMinMax          : (Double, Double)
    ): Either[ReadTableError, Double] = {
        val (xmin, xmax) = xMinMax
        val (ymin, ymax) = yMinMax

        if      (xi < xmin) Left(ValueOutOfBound(s"x ($xHeader)", xi, xmin, xmax))
        else if (xi > xmax) Left(ValueOutOfBound(s"x ($xHeader)", xi, xmin, xmax))
        else if (yi < ymin) Left(ValueOutOfBound(s"y ($yHeader)", yi, ymin, ymax))
        else if (yi > ymax) Left(ValueOutOfBound(s"y ($yHeader)", yi, ymin, ymax))
        else
            val data = TSVTableString.fromString(tsvTableRawString)
            data.getUsingBilinearInterpolation(xHeader, yHeader, zHeader)(xi, yi)
                .left
                .map: err =>
                    CouldNotInterpolate(resName, xHeader, yHeader, zHeader, xi, yi, err)
    }
