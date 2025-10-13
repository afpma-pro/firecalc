/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict

import cats.data.*
import cats.data.Validated.Valid

import afpma.firecalc.engine.models.CombustionAirPipe_EN15544

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.*
import afpma.firecalc.engine.models.{FluePipe_EN15544, ChimneyPipe, ConnectorPipe, FireboxPipe_EN15544, AirIntakePipe_Module}


class exercice_15544_strict_algebra_Suite extends AnyFreeSpec with Matchers {

    type VNel[A] = Validated[NonEmptyList[String], A]

    "ex01_colonne_ascendante" - {

        "incremental description" - {

            "// CONDUIT ARRIVEE AIR" - {
    
                "n'est pas présent" in {
                    strict_ex01_colonne_ascendante.airIntakePipe.toOption.get shouldBe a [AirIntakePipe_Module.NoVentilationOpenings]
                }
    
            }
            
            "// FOYER" - {
    
                "can be converted to" - {
        
                    "firebox pipe" in {
                        val ccPipe = strict_ex01_colonne_ascendante.fireboxPipe
                        ccPipe shouldBe a [Valid[FireboxPipe_EN15544]]
                        // println(ccPipe.toOption.get.show)
                    }
        
                    "combustion air pipe" in {
                        val combustionAirPipe = strict_ex01_colonne_ascendante.combustionAirPipe
                        combustionAirPipe shouldBe a [Valid[CombustionAirPipe_EN15544]]
                        // println(combustionAirPipe.toOption.get.show)
                    }
                }
    
            }
    
            "// ACCUMULATEUR" - {
    
                "can be converted to FluePipe" in {
    
                    val fluePipe = strict_ex01_colonne_ascendante.fluePipe
                    fluePipe shouldBe a [Valid[FluePipe_EN15544]]
                    // println(fluePipe.toOption.get.showAsCliTable)
                }
    
            }
    
            "// CONDUIT RACCORDEMENT" - {
    
                "can be converted to ConnectorPipe" in {
    
                    val connetingPipe = strict_ex01_colonne_ascendante.connectorPipe
                    connetingPipe shouldBe a [Valid[ConnectorPipe]]
                    // println(connetingPipe.toOption.get.showAsCliTable)
                }
    
            }
    
            "// CONDUIT DE FUMEES" - {
    
                "can be converted to ChimneyPipe" in {
    
                    val chimneyPipe = strict_ex01_colonne_ascendante.chimneyPipe
                    chimneyPipe shouldBe a [Valid[ChimneyPipe]]
                    // println(chimneyPipe.toOption.get.showAsCliTable)
                }
    
            }
            
        }

        // "firecalc en15544 strict" - {

        //     "for ex01" in {
        //         strict_ex01_colonne_ascendante.inputsVNel.map: inputs =>
        //             val _en15544 = EN15544_V_2023.make(EN15544_V_2023_Formulas.make)(inputs)
                    
        //             given PressureRequirements = PressureRequirements.DraftMinOrPositivePressureMax

        //             import _en15544.given
        //             println(inputs.showAsCliTable)
        //             println(_en15544.citedConstraints.showAsCliTable)
        //             // println(inputs.pipes.showAsCliTable)
        //             println(_en15544.showAsTable_Pipes.showAsCliTable(_en15544.inputs.pipes))
                    
        //             println(_en15544.pressureRequirement_EN15544.toOption.map(_.showAsCliTable).getOrElse("ERROR (pressure requirement)"))
        //             println(_en15544.t_chimney_wall_top.toOption.map(_.showAsCliTable).getOrElse("ERROR (dew point condition)"))

        //             println(_en15544.η.showAsCliTable)
        //             println(_en15544.flue_gas_triple_of_variates.toOption.map(_.showAsCliTable).getOrElse("ERROR (flue gas triple of variates)"))
        //             println(_en15544.estimated_output_temperatures.toOption.map(_.showAsCliTable).getOrElse("ERROR (estimated temperatures)"))

        //             _en15544.pressureRequirements_EN13384 match 
        //                 case Validated.Valid(a) => println(a.showAsCliTable)
        //                 case Validated.Invalid(nel) => 
        //                     println("ERROR (pressure requirements EN13384)")
        //                     nel.toList.map(_.show).foreach(println)
        //                     fail()
        //     }
        // }

    }

}

