/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import magnolia1.Transl

@Transl(I(_.pollutant_names._self))
enum PolluantName:
    case CO, Dust, OGC, NOx, `Dust+OGC`

object PolluantName:
    @Transl(I(_.pollutant_names.CO))
    type CO         = PolluantName.CO.type
    @Transl(I(_.pollutant_names.Dust))
    type Dust       = PolluantName.Dust.type
    @Transl(I(_.pollutant_names.OGC))
    type OGC        = PolluantName.OGC.type
    @Transl(I(_.pollutant_names.NOx))
    type NOx        = PolluantName.NOx.type
    @Transl(I(_.pollutant_names.Dust_OGC))
    type `Dust+OGC` = PolluantName.`Dust+OGC`.type

    given ShowUsingLocale[PolluantName] = showUsingLocale:
        case CO         => I18N.pollutant_names.CO
        case Dust       => I18N.pollutant_names.Dust
        case OGC        => I18N.pollutant_names.OGC
        case NOx        => I18N.pollutant_names.NOx
        case `Dust+OGC` => I18N.pollutant_names.Dust_OGC
