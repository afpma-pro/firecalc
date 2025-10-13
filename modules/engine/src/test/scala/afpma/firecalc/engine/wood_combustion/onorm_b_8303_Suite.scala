/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

class onorm_b_8303_Suite extends AnyFreeSpec with Matchers {

    val wood = afpma.firecalc.engine.wood_combustion.Wood.from_ONORM_B_8303(humidity = 20.percent)

    "Wood specified in ORNOM_B_8303 with 20% humidity" - {
        
        "has lower calorific value (dry) of ~4.78 kWh/kg" in {
            val wc = wood.lower_calorific_value_dry
                .toUnit[Kilo * Watt * Hour / Kilogram]
                .value 
            wc `shouldEqual` (4.78 +- 0.01)
        }

        "has lower calorific value (wet) of ~3.69 kWh/kg" in {
            val dry = wood.lower_calorific_value_dry
            val wet = afpma.firecalc.engine.wood_combustion.PCI_Conversion.cibe_fr.PCI_sur_brut(dry, wood.humidity)
            wet.value `shouldEqual` (3.69 +- 0.01)
        }
    }

    "Wood specified in ORNOM_B_8303 with 12 % humidity" - {

        val wood2 = afpma.firecalc.engine.wood_combustion.Wood.from_ONORM_B_8303(humidity = 12.percent)

        "has lower calorific value (dry) of ~4.82 kWh/kg" in {
            val wc = wood2.lower_calorific_value_dry
                .toUnit[Kilo * Watt * Hour / Kilogram]
                .value 
            wc `shouldEqual` (4.82 +- 0.01)
        }

        "has lower calorific value (wet) of ~4.16 kWh/kg" in {
            val dry = wood2.lower_calorific_value_dry
            val wet = afpma.firecalc.engine.wood_combustion.PCI_Conversion.cibe_fr.PCI_sur_brut(dry, wood2.humidity)
            wet.value `shouldEqual` (4.16 +- 0.01)
        }
    }
}