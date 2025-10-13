/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Formulas

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.conversions.kJ_per_kg
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

class EN15544_Strict_Formulas extends EN15544_V_2023_Common_Formulas:

    override def net_calorific_value_of_wet_wood = 
        4.16.kWh_per_kg // See 4.2.1

    override def net_calorific_value_of_dry_wood = 
        // Source: Mesure des caractéristiques des combustibles bois « Evaluation et proposition de méthodes d’analyse de combustible » ADEME Critt Bois – Fibois – CTBA JUIN 2001 Ademe 2021
        // https://cibe.fr/wp-content/uploads/2017/02/21-Mesures-PCI-bois-combustible-CRITT-bois-FIBOIS-CTBA.pdf
        18500.kJ_per_kg.toUnit[Kilo * Watt * Hour / Kilogram]

object EN15544_Strict_Formulas:
    def make = new EN15544_Strict_Formulas