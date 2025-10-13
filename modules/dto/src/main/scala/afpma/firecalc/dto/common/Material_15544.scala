/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.units.coulombutils.*

import magnolia1.Transl

enum Material_15544(val roughness: Roughness):
    // 15544 - Tableau B.2

    @Transl(I(_.en15544.materials.tuyaux_en_chamotte))
    case TuyauxEnChamotte extends Material_15544(0.002.meters)

    @Transl(I(_.en15544.materials.blocs_de_chamotte))
    case BlocsDeChamotte extends Material_15544(0.003.meters)

object Material_15544:
    given ShowUsingLocale[Material_15544] = showUsingLocale:
        case TuyauxEnChamotte => I18N.en15544.materials.tuyaux_en_chamotte
        case BlocsDeChamotte  => I18N.en15544.materials.blocs_de_chamotte