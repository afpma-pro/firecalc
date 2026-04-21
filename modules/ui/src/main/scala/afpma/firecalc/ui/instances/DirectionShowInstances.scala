/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import cats.Show

import io.taig.babel.Locale

/** Translated Show instances for direction enums, reusable across all PropertyShow classes. */
object DirectionShowInstances:

    given show_AzimuthDirection: Locale => Show[AzimuthDirection] = Show.show:
        case AzimuthDirection.Rear       => I18N_UI.direction_badge.cardinal_rear
        case AzimuthDirection.RearRight  =>
            s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_right}"
        case AzimuthDirection.Right      => I18N_UI.direction_badge.cardinal_right
        case AzimuthDirection.FrontRight =>
            s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_right}"
        case AzimuthDirection.Front      => I18N_UI.direction_badge.cardinal_front
        case AzimuthDirection.FrontLeft  =>
            s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_left}"
        case AzimuthDirection.Left       => I18N_UI.direction_badge.cardinal_left
        case AzimuthDirection.RearLeft   =>
            s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_left}"
        case AzimuthDirection.Custom(a)  => s"${a.value}°"

    given show_InclinationDirection: Locale => Show[InclinationDirection] = Show.show:
        case InclinationDirection.Up         => I18N_UI.direction_badge.cardinal_up
        case InclinationDirection.Down       => I18N_UI.direction_badge.cardinal_down
        case InclinationDirection.Horizontal => I18N_UI.direction_badge.cardinal_horizontal
        case InclinationDirection.Custom(a)  => s"${a.value}°"

end DirectionShowInstances

/** Shared inclination-first formatter for direction values. Reused by badge, form, and Show instances. */
object DirectionFormat:
    import afpma.firecalc.dto.all.{AzimuthDirection, InclinationDirection}
    import io.taig.babel.Locale
    import afpma.firecalc.ui.i18n.implicits.I18N_UI

    def compact(az: AzimuthDirection, incl: InclinationDirection)(using Locale): String =
        compact(AzimuthDirection.toDegrees(az), InclinationDirection.toDegrees(incl))

    def compact(azDeg: Double, elDeg: Double)(using Locale): String =
        val i18n         = I18N_UI.direction_badge
        val isVertical   = math.abs(math.abs(elDeg) - 90.0) < 1e-6
        val isHorizontal = math.abs(elDeg) < 1.0
        val inclStr =
            if isVertical then if elDeg > 0 then i18n.cardinal_up else i18n.cardinal_down
            else if isHorizontal then i18n.cardinal_horizontal
            else
                val sign = if elDeg >= 0 then "↑" else "↓"
                val mag  = String.format(java.util.Locale.ROOT, "%.1f", math.abs(elDeg))
                s"${sign}${mag}°"
        if isVertical then inclStr
        else
            val azStr = azimuthLabelForDeg(azDeg)
            s"$inclStr · $azStr"

    private def azimuthLabelForDeg(deg: Double)(using Locale): String =
        import afpma.firecalc.dto.all.AzimuthDirection.*
        AzimuthDirection.fromDegrees(deg, 1.0) match
            case Custom(a) =>
                val mag = String.format(java.util.Locale.ROOT, "%.1f", a.value)
                s"↻${mag}°"
            case Rear       => I18N_UI.direction_badge.cardinal_rear
            case RearRight  => I18N_UI.direction_badge.cardinal_rear_right
            case Right      => I18N_UI.direction_badge.cardinal_right
            case FrontRight => I18N_UI.direction_badge.cardinal_front_right
            case Front      => I18N_UI.direction_badge.cardinal_front
            case FrontLeft  => I18N_UI.direction_badge.cardinal_front_left
            case Left       => I18N_UI.direction_badge.cardinal_left
            case RearLeft   => I18N_UI.direction_badge.cardinal_rear_left
