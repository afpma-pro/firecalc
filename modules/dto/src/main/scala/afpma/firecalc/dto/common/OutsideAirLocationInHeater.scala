/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

enum OutsideAirLocationInHeater:
    case FromBottom

object OutsideAirLocationInHeater:
    type FromBottom = FromBottom.type

    given ShowUsingLocale[OutsideAirLocationInHeater] =
        showUsingLocale(_ => I18N.firebox.afpma_prse.air_intake_direction_from_bottom)
