/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.impl.en15544.common.*

import afpma.firecalc.units.coulombutils.*

open class EN15544_MCE_Formulas(
    override val net_calorific_value_of_wet_wood: HeatCapacity,
    override val net_calorific_value_of_dry_wood: HeatCapacity,
) extends EN15544_V_2023_Common_Formulas

    // override def net_calorific_value_of_wet_wood(
    //     ncv_dry: HeatCapacity, 
    //     hum: Percentage,
    //     kind: KindOfWood
    // ): HeatCapacity = 
    //     pciConversion.PCI_sur_brut(ncv_dry, hum)

    // override def net_calorific_value_of_dry_wood(kind: KindOfWood) = 
    //     // Source: Mesure des caractéristiques des combustibles bois « Evaluation et proposition de méthodes d’analyse de combustible » ADEME Critt Bois – Fibois – CTBA JUIN 2001 Ademe 2021
    //     // https://cibe.fr/wp-content/uploads/2017/02/21-Mesures-PCI-bois-combustible-CRITT-bois-FIBOIS-CTBA.pdf
    //     kind match
    //         case KindOfWood.HardWood => 5.070.kWh_per_kg 
    //         case KindOfWood.SoftWood => 5.330.kWh_per_kg
        

object EN15544_MCE_Formulas:
    def make(
        net_calorific_value_of_wet_wood: HeatCapacity,
        net_calorific_value_of_dry_wood: HeatCapacity
    ) = new EN15544_MCE_Formulas(
        net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood, 
        net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
    )

