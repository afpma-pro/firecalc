/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.engine.wood_combustion.bs845.BS845_Alg
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Impl

class bs845_Suite extends AnyFreeSpec with Matchers {

    val wood = afpma.firecalc.engine.wood_combustion.Wood.from_ONORM_B_8303(humidity = 20.percent)

    val bs845: BS845_Alg = new BS845_Impl {}
    // import bs845.*

    val wComb = new WoodCombustionImpl

    "Wood composition helpers" - {

        val o2_perc_vol = 7.9.percent
        val expected_lambda = 1.6
               
        s"lambda = $expected_lambda if %O2 ≃ ${o2_perc_vol.showP}" in {
            wood.lambda_from_o2_dry(o2_perc_vol) `shouldEqual` (expected_lambda +- 0.1)
            wood.o2_dry_from_lambda(expected_lambda).value `shouldEqual` (o2_perc_vol.value +- 0.1)
        }

        val co2_perc_wet = wood.co2_wet_from_o2_wet(o2_perc_vol)

        s"lambda = $expected_lambda if %CO2 ≃ ${co2_perc_wet.showP}" in {
            wood.lambda_from_co2_wet(co2_perc_wet) `shouldEqual` (expected_lambda +- 0.1)
        }

        s"lambda = 2.95 if %CO2 ≃ 7.05 % vol dry (checking efficiency with lambda in 15544)" in {
            val co2_perc_dry = 7.05.percent
            wood.lambda_from_co2_dry(co2_perc_dry) `shouldEqual` (2.95 +- 0.1)
        }

        "CO2_max_wet" - {
            "is coherent with expected value" in {
                wood.co2_max_wet.value `shouldEqual` (19.1 +- 0.1)
            }
        }
        "CO2_max_dry" - {
            "is coherent with expected value" in {
                wood.co2_max_dry.value `shouldEqual` (21.3 +- 0.1)
            }
        }
        "perfect_combustion_efficiency given % O2 dry" - {
            "η ≃ 84.1% when tg = 100°C and O2 ≃ 8%" in {
                val co2_dry = wood.co2_dry_from_o2_dry(8.percent)
                val eff = bs845.perfect_combustion_efficiency_given_CO2_dry(
                    wood, 
                    t_flue_gas      = 100.degreesCelsius,
                    t_ambiant_air   = 0.degreesCelsius,
                    co2_dry_perc    = co2_dry
                )
                eff.value `shouldEqual` (84.4 +- 0.1)
            }

            "η ≃ 46.2% when tg = 550°C and CO2 dry ≃ 10%" in {
                val eff = bs845.perfect_combustion_efficiency_given_CO2_dry(
                    wood, 
                    t_flue_gas      = 550.degreesCelsius,
                    t_ambiant_air   = 0.degreesCelsius,
                    co2_dry_perc    = 10.percent
                )
                eff.value `shouldEqual` (46.2 +- 0.1)
            }
        }
    }
}