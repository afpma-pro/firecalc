/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import magnolia1.Transl

@deprecated("use Material_15544_V2", "2026-02-02")
enum Material_15544_V1(val roughness: Roughness):
    // 15544 - Tableau B.2

    @Transl(I(_.en15544.materials.tuyaux_en_chamotte))
    case TuyauxEnChamotte extends Material_15544_V1(0.002.meters)

    @Transl(I(_.en15544.materials.blocs_de_chamotte))
    case BlocsDeChamotte extends Material_15544_V1(0.003.meters)

@deprecated("use Material_15544_V2", "2026-02-02")
object Material_15544_V1:
    given ShowUsingLocale[Material_15544_V1] = showUsingLocale:
        case TuyauxEnChamotte => I18N.en15544.materials.tuyaux_en_chamotte
        case BlocsDeChamotte  => I18N.en15544.materials.blocs_de_chamotte
