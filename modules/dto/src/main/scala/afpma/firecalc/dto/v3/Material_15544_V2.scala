/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v3

import cats.syntax.show.toShow

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.units.coulombutils.{*, given}

import magnolia1.Transl

// 15544 - Tableau B.2
enum Material_15544_V2(
    val name: String,
    val roughness: Roughness
):
    @Transl(I(_.en15544.materials.tuyaux_en_chamotte))
    case TuyauxEnChamotte(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 0.002.meters
    ) extends Material_15544_V2("chamotte_pipe", roughness)

    @Transl(I(_.en15544.materials.blocs_de_chamotte))
    case BlocsDeChamotte(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 0.003.meters
    ) extends Material_15544_V2("chamotte_block", roughness)

object Material_15544_V2:
    
    /**
     * All available material options with default roughness values
     */
    val values: List[Material_15544_V2] = List(
        TuyauxEnChamotte(),
        BlocsDeChamotte()
    )
    
    /**
     * Helper method to create a new instance with updated roughness
     * while preserving the material type
     */
    extension (m: Material_15544_V2)
        def withRoughness(newRoughness: Roughness): Material_15544_V2 =
            m match
                case _: TuyauxEnChamotte => TuyauxEnChamotte(newRoughness)
                case _: BlocsDeChamotte  => BlocsDeChamotte(newRoughness)
    
    private def showAndAppendValue(enumShow: String, v: Roughness): String = 
        // s"$enumShow (${v.show})"
        s"$enumShow"
    
    given ShowUsingLocale[Material_15544_V2] = showUsingLocale:
        case TuyauxEnChamotte(r) => showAndAppendValue(I18N.en15544.materials.tuyaux_en_chamotte, r)
        case BlocsDeChamotte(r)  => showAndAppendValue(I18N.en15544.materials.blocs_de_chamotte, r)