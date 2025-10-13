/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

enum PolluantName:
    case CO, Dust, OGC, NOx, `Dust+OGC`

object PolluantName:
    given ShowUsingLocale[PolluantName] = showUsingLocale:
        case CO => I18N.pollutant_names.CO
        case Dust => I18N.pollutant_names.Dust
        case OGC => I18N.pollutant_names.OGC
        case NOx => I18N.pollutant_names.NOx
        case `Dust+OGC` => I18N.pollutant_names.Dust_OGC
