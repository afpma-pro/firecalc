/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.formatters

import afpma.firecalc.units.Vec3
import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.given
import io.taig.babel.Locale

object Vec3Formatter:

    def toDisplayString(v: Vec3)(using l: Locale): String =
        val n = v.normalized
        def isExact(val_ : Double) = val_ == 0.0 || val_ == 1.0 || val_ == -1.0
        val geo = I18N.direction_badge

        if isExact(n.x) && isExact(n.y) && isExact(n.z) then

            (n.x, n.y, n.z) match
                case (0.0, 0.0, 1.0 ) => geo.cardinal_up
                case (0.0, 0.0, -1.0) => geo.cardinal_down
                case (0.0, 1.0, 0.0 ) => geo.cardinal_rear
                case (0.0, -1.0, 0.0) => geo.cardinal_front
                case (1.0, 0.0, 0.0 ) => geo.cardinal_right
                case (-1.0, 0.0, 0.0) => geo.cardinal_left
                case _ => azElString(n)(using l)
        else if math.abs(n.z) < 1e-6 then horizontalName(n, geo).getOrElse(azElString(n)(using l))
        else
            val horNorm = Vec3(n.x, n.y, 0.0).norm
            if horNorm == 0.0 then azElString(n)(using l)
            else
                val horS = Vec3(n.x / horNorm, n.y / horNorm, 0.0)
                horizontalName(horS, geo) match
                    case Some(horName) =>
                        val elev    = math.toDegrees(math.atan2(n.z, horNorm))
                        val sign    = if elev >= 0 then "\u2191" else "\u2193"
                        val elevStr = fmtD1         (math.abs(elev)          )
                        s"$horName $sign${elevStr}\u00b0"
                    case None          => azElString(n)(using l)

    private def horizontalName(h: Vec3, geo: I18nData.DirectionBadge): Option[String] =
        val eps = 1e-6
        def near(a: Double, b: Double) = math.abs(a - b) < eps
        if near(h.x, 0.0) && near(h.y, 1.0) then Some(geo.cardinal_rear)
        else if near(h.x, 0.0) && near(h.y, -1.0) then Some(geo.cardinal_front)
        else if near(h.x, 1.0) && near(h.y, 0.0) then Some(geo.cardinal_right)
        else if near(h.x, -1.0) && near(h.y, 0.0) then Some(geo.cardinal_left)
        else
            val d = math.sqrt(0.5)
            if near(h.x, d) && near(h.y, d) then Some(geo.cardinal_rear_right)
            else if near(h.x, d) && near(h.y, -d) then Some(geo.cardinal_front_right)
            else if near(h.x, -d) && near(h.y, -d) then Some(geo.cardinal_front_left)
            else if near(h.x, -d) && near(h.y, d) then Some(geo.cardinal_rear_left)
            else None

    private def azElString(n: Vec3)(using l: Locale): String =
        val (az, el) = n.toAzimuthElevation
        val geo = I18N.direction_badge
        geo.az_el(fmtD1(az), fmtD1(el))

    private def fmtD1(d: Double): String =
        BigDecimal(d).setScale(1, BigDecimal.RoundingMode.HALF_UP).toString()
