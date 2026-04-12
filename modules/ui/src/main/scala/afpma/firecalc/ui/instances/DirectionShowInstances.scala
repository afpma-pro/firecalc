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
        case AzimuthDirection.RearRight  => s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_right}"
        case AzimuthDirection.Right      => I18N_UI.direction_badge.cardinal_right
        case AzimuthDirection.FrontRight => s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_right}"
        case AzimuthDirection.Front      => I18N_UI.direction_badge.cardinal_front
        case AzimuthDirection.FrontLeft  => s"${I18N_UI.direction_badge.cardinal_front}-${I18N_UI.direction_badge.cardinal_left}"
        case AzimuthDirection.Left       => I18N_UI.direction_badge.cardinal_left
        case AzimuthDirection.RearLeft   => s"${I18N_UI.direction_badge.cardinal_rear}-${I18N_UI.direction_badge.cardinal_left}"
        case AzimuthDirection.Custom(a)  => s"${a.value}°"

    given show_InclinationDirection: Locale => Show[InclinationDirection] = Show.show:
        case InclinationDirection.Up         => I18N_UI.direction_badge.cardinal_up
        case InclinationDirection.Down       => I18N_UI.direction_badge.cardinal_down
        case InclinationDirection.Horizontal => I18N_UI.direction_badge.cardinal_horizontal
        case InclinationDirection.Custom(a)  => s"${a.value}°"

end DirectionShowInstances
