/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.utils.*

opaque type TSVTableString = List[Map[String, String]]

private def str2double: Conversion[String, Double] =
    (_: String)
        .replaceAll(",", ".")
        .toDouble

object TSVTableString:

    def fromString(rawString: String, sep: String = "\t"): TSVTableString = 
        val lines = rawString.split("\n")
        val header = lines.head
        val headers = header.split(sep)
        val data = lines.tail.toList
        data.map: d => 
            val values = d.trim.split(sep)
            (headers zip values).toMap

extension (tt: TSVTableString)

    def toList: List[Map[String, String]] = tt

    def extractColsAs(
        firstHeader: String,
        secondHeader: String
    ): List[(Double, Double)] =
        tt.extractCols(firstHeader, secondHeader).map { (s1, s2) =>
            (str2double(s1), str2double(s2))
        }

    def extractCols(
        firstHeader: String,
        secondHeader: String
    ): List[(String, String)] =
        tt.map { m =>
            (m.get(firstHeader), m.get(secondHeader)) match
                case (Some(k), Some(v)) => Some((k, v))
                case _                  => None
        }.flatten

    def extract3ColsAs(
        xHeader: String,
        yHeader: String,
        zHeader: String,
    ): List[(Double, Double, Double)] =
        tt.extract3Cols(xHeader, yHeader, zHeader).map { (sx, sy, sz) =>
            (str2double(sx), str2double(sy), str2double(sz))
        }

    def extract3Cols(
        xHeader: String,
        yHeader: String,
        zHeader: String,
    ): List[(String, String, String)] =
        tt.map { m =>
            (m.get(xHeader), m.get(yHeader), m.get(zHeader)) match
                case (Some(x), Some(y), Some(z))    => Some((x, y, z))
                case _                              => None
        }.flatten

    def getUsingLinearInterpolation(xHeader: String, yHeader: String)(
        xi: Double
    ): Option[Double] =
        val it = tt.extractColsAs(xHeader, yHeader)
        it.getWithLinearInterpolation(xi)

    def getUsingBilinearInterpolation(xHeader: String, yHeader: String, zHeader: String)(
        xi: Double, yi: Double,
    ): Option[Double] =
        val it = tt.extract3ColsAs(xHeader, yHeader, zHeader)
        it.getWithBilinearInterpolation(xi, yi)
