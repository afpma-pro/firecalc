/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

enum TypeOfAppliance:
    case Pellets
    case WoodLogs

object TypeOfAppliance:
    given ShowUsingLocale[TypeOfAppliance] = showUsingLocale:
        case TypeOfAppliance.Pellets  => I18N.type_of_appliance.pellets
        case TypeOfAppliance.WoodLogs => I18N.type_of_appliance.woodlogs

