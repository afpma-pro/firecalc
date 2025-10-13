/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.utils
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}


import algebra.instances.all.given

class wood_combustion_Suite extends AnyFreeSpec with Matchers:

    val wl = 14.kg
    val wm = afpma.firecalc.engine.wood_combustion.Wood.HumidMass(wl)
    val wh = 12.percent

    val combDuration = 1.hours
    val exp_mass_flow = 36.8.g_per_s

    val w = Wood(
        humidity = wh,
        atomic_composition = Map(
            "C" -> 50.percent,
            "H" -> 6.percent,
            "O" -> 42.percent,
            "N" -> 1.percent,
            "Others" -> 1.percent
        )
    )
    


    val wmf = wm.toHumidMassFlow(combDuration)

    val extAir = ExteriorAir(
        temperature         = 0.degreesCelsius,
        relative_humidity   = 74.percent,
        pressure            = 102183.pascals
    )

    def mix(lambda: Double) = CombustionMix(
        lambda  = lambda,
        ext_air = extAir,
        wood    = w
    )

    val wComb = new WoodCombustionImpl
    import wComb.*

    s"given a load of ${wl.show} of wood and a combustion duration of ${combDuration}" - {
        
        s"mass flow should be ${exp_mass_flow.showP}" in {
            given Wood.HumidMass = wm

            val ci_stoecchio = afpma.firecalc.engine.wood_combustion.CombustionInputs.byMass(wm)(mix(lambda = 1.0))

            println(s"masse matière seche = ${w.mass_dry.showP}")

            println(s"masse H2O = ${w.mass_H2O.showP}")
            println(s"masse C = ${w.mass_atomic_el("C").showP}")
            println(s"masse H = ${w.mass_atomic_el("H").showP}")
            println(s"masse O = ${w.mass_atomic_el("O").showP}")
            println(s"masse N = ${w.mass_atomic_el("N").showP}")
            println("")
            println(s"bois // nb moles H2O = ${w.moles_H2O.showP}")
            println(s"bois // nb moles C = ${w.moles_el("C").showP}")
            println(s"bois // nb moles H = ${w.moles_el("H").showP}")
            println(s"bois // nb moles O = ${w.moles_el("O").showP}")
            println(s"bois // nb moles N = ${w.moles_el("N").showP}")
            println("")
            println(s"air (si λ = 1) // nb moles H2O = ${extAir.moles_H2O(w, lambda = 1).showP}")
            println(s"air (si λ = 1) // nb moles O   = ${extAir.moles_O(w, lambda = 1).showP}")
            println(s"air (si λ = 1) // nb moles N   = ${extAir.moles_N(w, lambda = 1).showP}")
            println("")
            println(s"combustion sans CO (si λ = 1) // nb moles CO2 = ${ci_stoecchio.output_perfect_moles("CO2").showP}")
            println(s"combustion sans CO (si λ = 1) // nb moles H2O = ${ci_stoecchio.output_perfect_moles_H2O.showP}")
            println(s"combustion sans CO (si λ = 1) // nb moles O2  = ${ci_stoecchio.output_perfect_moles("O2").showP}")
            println(s"combustion sans CO (si λ = 1) // nb moles N2  = ${ci_stoecchio.output_perfect_moles("N2").showP}")
            println("")
            println("")
            println("")
            
            println(s"""| totaux            entrée \t | \t sortie \t
                        |     C         =   ${ci_stoecchio.input_all_moles_el("C").showP}\t ${ci_stoecchio.output_perfect_moles("C").showP}    
                        |     H         =   ${ci_stoecchio.input_all_moles_el("H").showP}\t ${ci_stoecchio.output_perfect_moles("H").showP}    
                        |     O         =   ${ci_stoecchio.input_all_moles_el("O").showP}\t ${ci_stoecchio.output_perfect_moles("O").showP}    
                        |     N         =   ${ci_stoecchio.input_all_moles_el("N").showP}\t ${ci_stoecchio.output_perfect_moles("N").showP}    
                        |
                        | total sortie
                        |
                        | """.stripMargin)

            val ci = afpma.firecalc.engine.wood_combustion.CombustionInputs.byMass(wm)(mix(lambda = 1.6))

            println(s"air (si λ = 1.6) // nb moles H2O = ${extAir.moles_H2O(w, lambda = 1.6).showP}")
            println(s"air (si λ = 1.6) // nb moles O   = ${extAir.moles_O(w, lambda = 1.6).showP}")
            println(s"air (si λ = 1.6) // nb moles N   = ${extAir.moles_N(w, lambda = 1.6).showP}")
            println("")
            println(s"combustion sans CO (si λ = 1.6) // nb moles CO2 = ${ci.output_perfect_moles("CO2").showP}")
            println(s"combustion sans CO (si λ = 1.6) // nb moles H2O = ${ci.output_perfect_moles_H2O.showP}")
            println(s"combustion sans CO (si λ = 1.6) // nb moles O2  = ${ci.output_perfect_moles("O2").showP}")
            println(s"combustion sans CO (si λ = 1.6) // nb moles N2  = ${ci.output_perfect_moles("N2").showP}")
            println("")
            println(s"""| totaux            entrée \t | \t sortie \t
                        |     C         =   ${ci.input_all_moles_el("C").showP}\t ${ci.output_perfect_moles("C").showP}    
                        |     H         =   ${ci.input_all_moles_el("H").showP}\t ${ci.output_perfect_moles("H").showP}    
                        |     O         =   ${ci.input_all_moles_el("O").showP}\t ${ci.output_perfect_moles("O").showP}    
                        |     N         =   ${ci.input_all_moles_el("N").showP}\t ${ci.output_perfect_moles("N").showP}    
                        |
                        | total sortie
                        |
                        | """.stripMargin)

            val cif = afpma.firecalc.engine.wood_combustion.CombustionInputs.byMassFlow(wm.toHumidMassFlow(combDuration))(mix(lambda = 1.6))
            
            println(cif.output_perfect_massflow_tot.showP)
            cif.output_perfect_massflow_tot.toUnit[Gram / Second].value `shouldEqual` (exp_mass_flow.value +- 0.1)
        }

    }

    "According to EN15544:2023, λ is set at 2.95" - {

        "σ_CO2 is specified at 7.05%" - {
            
            "value is percentage on wet or dry ?" in {
                
                val w = 17.percent
                val h = w / ( 1.0 - w.value / 100.0)

                val wood = Wood(
                    humidity = h,
                    atomic_composition = Map(
                        "C" -> utils.wet_to_dry(w)(38.percent),
                        "H" -> utils.wet_to_dry(w)(5.percent),
                        "O" -> utils.wet_to_dry(w)(100.percent - w - 38.percent - 5.percent - 1.percent),
                        "Others" -> 1.percent
                    )
                )

                val xC_dry = wood.atomic_composition.get("C").get
                val xC_wet = wood.wood_el_from_dry_to_wet(xC_dry)

                println(s"""| teneur en eau du bois = ${w.showP}
                            | humidité du bois      = ${h.showP}
                            |
                            | xC_wet = ${xC_wet.showP}
                            | xC_dry = ${xC_dry.showP}
                            |""".stripMargin)


                val wComb = new WoodCombustionImpl
                
                import wComb.*
                
                val wm = afpma.firecalc.engine.wood_combustion.Wood.HumidMass(1.kg)

                val mix = CombustionMix(
                    lambda = 2.95,
                    ext_air = ExteriorAir(
                        temperature = 25.degreesCelsius,
                        relative_humidity = 100.percent,
                        pressure = 101300.pascals
                    ),
                    wood
                )
                val ci = afpma.firecalc.engine.wood_combustion.CombustionInputs.byMass(wm)(mix)
                val fgCO2_hum = ci.output_perfect_percbyvol_humid("CO2")
                val fgCO2_dry = ci.output_perfect_percbyvol_dry("CO2")
                val fgH2O = ci.output_perfect_percbyvol_humid_H2O
                println(s"""| Pour λ = 2.95
                            |
                            | CO2 : ${fgCO2_hum} (en volume sur humide)
                            | CO2 : ${fgCO2_dry} (en volume sur sec)
                            |
                            | H2O : ${fgH2O.showP} (en volume)
                            |""".stripMargin)

            }
        }

        "λ = 2.95 est-il retrouvé pour %CO2 dry de 7.05 %" in {
            // val bs845 = new BS845_Impl {}
            

            val w = 17.percent
            val h = w / ( 1.0 - w.value / 100.0)

            val wood = Wood(
                humidity = h,
                atomic_composition = Map(
                    "C" -> utils.wet_to_dry(w)(38.percent),
                    "H" -> utils.wet_to_dry(w)(5.percent),
                    "O" -> utils.wet_to_dry(w)(100.percent - w - 38.percent - 5.percent - 1.percent),
                    "Others" -> 1.percent
                )
            )

            val lambdafromO2dry = wood.lambda_from_o2_dry(10.3.percent)
            val lambda2 = wood.lambda_from_co2_dry(7.05.percent)

            val fg_o2_dry_2 = wood.o2_dry_from_lambda(lambda2)
            val fg_co2_dry_2 = wood.co2_dry_from_o2_dry(fg_o2_dry_2)
            
            println(s"co2 max dry = ${wood.co2_max_dry} ")
            println(s"co2 max wet = ${wood.co2_max_wet} ")
            
            // println(s"v0d_a0_ratio = ${wood.v0d_a0_ratio} ")
            println(s"fg_o2_dry_2 = $fg_o2_dry_2 ")
            
            println(s"lambdafromO2dry = $lambdafromO2dry")
            println(s"lambda2 = $lambda2 par rapport à 2.95 ???")
            
            println(s"fg_co2_dry_2 = $fg_co2_dry_2 par rapport à 7.05 %")
        }

        "λ = ??? correpond à %CO2 dry de 12%" in {
            // val bs845 = new BS845_Impl {}
            

            val w = 10.percent // granulés de bois
            val h = w / ( 1.0 - w.value / 100.0)

            val wood = Wood(
                humidity = h,
                atomic_composition = Map(
                    "C" -> utils.wet_to_dry(w)(38.percent),
                    "H" -> utils.wet_to_dry(w)(5.percent),
                    "O" -> utils.wet_to_dry(w)(100.percent - w - 38.percent - 5.percent - 1.percent),
                    "Others" -> 1.percent
                )
            )

            val lambda2 = wood.lambda_from_co2_dry(12.percent)

            val fg_o2_dry_2 = wood.o2_dry_from_lambda(lambda2)
            // val fg_co2_dry_2 = wood.co2_dry_from_o2_dry(fg_o2_dry_2)
            val fg_co2_dry_2 = wood.co2_dry_from_lambda(1.85)

            println(s"co2 max dry = ${wood.co2_max_dry} ")
            println(s"co2 max wet = ${wood.co2_max_wet} ")

            // println(s"v0d_a0_ratio = ${wood.v0d_a0_ratio} ")
            println(s"fg_o2_dry_2 = $fg_o2_dry_2 ")
            println(s"lambda2 = $lambda2 par rapport à 2.95 ???")
            println(s"fg_co2_dry_2 = $fg_co2_dry_2 par rapport à 7.05 %")
        }

        "Wood" - {

            "check implementation du pdf combustion bois" in {
                val w = 20.percent
                val h = w / ( 1.0 - w.value / 100.0)

                val wood = Wood(
                    humidity = h,
                    atomic_composition = Map(
                        "C" -> 50.percent,
                        "H" -> 6.percent,
                        "O" -> 44.percent,
                    )
                )

                val lambda = 2.0

                println(s"""| V_a   = ${wood.V_a.showP}
                            | 
                            | V_CO2 = ${wood.V_CO2.showP}
                            | V_H2O = ${wood.V_H2O.showP}
                            | V_O2  = ${wood.V_O2.showP}
                            |
                            | V_fp  = ${wood.V_fp.showP}
                            | V_hum = ${wood.V_hum.showP}
                            | V_f   = ${wood.V_f.showP}
                            |
                            | co2_max_dry   = ${wood.co2_max_dry.showP}
                            | co2_max_wet   = ${wood.co2_max_wet.showP}
                            |
                            | co2_dry_from_lambda(${lambda})   = ${wood.co2_dry_from_lambda(lambda).showP}
                            | co2_wet_from_lambda(${lambda})   = ${wood.co2_wet_from_lambda(lambda).showP}
                            |
                            | o2_dry(_from_lambdal(${lambda})    = ${wood.o2_dry_from_lambda(lambda).showP}
                            | o2_wet(_from_lambdal(${lambda})    = ${wood.o2_wet_from_lambda(lambda).showP}
                            | 
                            | fluegas_h2o_for_lambda(${lambda})  = ${wood.fluegas_h2o_for_lambda(lambda).showP}
                            |
                            |""".stripMargin)

            }
        }
    }
