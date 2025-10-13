/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import algebra.instances.all.given

import afpma.firecalc.engine.models.gtypedefs.ThermalConductivity
import afpma.firecalc.engine.models.gtypedefs.D_h
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en13384.std.ThermalResistance.CoefficientOfForm

class ThermalResistance_Suite extends AnyFreeSpec with Matchers:

    val en13384 = new EN13384_1_A1_2019_Formulas {}

    "ThermalResistance" - {

        "when thickness 'e' is small, linear or circular geometries should give same value" in {
            val e0 = 2.mm
            val λ0 = 0.1.W_per_mK

            val dhi = 10.cm
            val dho = dhi + 2 * e0

            val trRound = en13384.thermal_resistance_for_layer_calc(
                CoefficientOfForm.wrap(1.0),
                dhi,
                dho,
                λ0
            )

            val trLinear = e0 / λ0
            val trRound_exp = dhi / ( 2.0 * λ0 ) * math.log( (dho / dhi).value )

            println(s"""|trRound (actual)   = $trRound
                        |trRound (expected) = $trRound_exp
                        |trLinear (approx)  = $trLinear
                        |"""".stripMargin)

            trRound_exp.value `shouldEqual` (trRound.value +- 0.001)
            trRound.value `shouldEqual` (trLinear.value +- 0.01)

            
        }

        "example for 'e' = 0.1 mm" - {

            "linear or circular should match" in {
                val e0 = 0.1.mm
                val λ0 = 15.W_per_mK

                val dhi = 50.mm
                val dho = dhi + 2 * e0

                val trRound = en13384.thermal_resistance_for_layer_calc(
                    CoefficientOfForm.wrap(1.0),
                    dhi,
                    dho,
                    λ0
                )

                val trLinear = e0 / λ0
                val trRound_exp = dhi / ( 2.0 * λ0 ) * math.log( (dho / dhi).value )

                println(s"""|trRound (actual)   = $trRound
                            |trRound (expected) = $trRound_exp
                            |trLinear (approx)  = $trLinear
                            |"""".stripMargin)

                trRound_exp.value `shouldEqual` (trRound.value +- 0.001)
                trRound.value `shouldEqual` (trLinear.value +- 0.01)

            }
        }

        "cas concret" - {

            "Therminox TI di = 100mm - 25mm isolant " in {
                val e0 = 25.mm
                val λ0 = 0.035.W_per_mK

                val dhi: D_h = 100.mm

                import afpma.firecalc.dto.all.*
                
                val layers = List(
                    FromLambdaUsingThickness(0.4.mm, 15.W_per_mK),
                    FromLambdaUsingThickness(e0, λ0),
                    FromLambdaUsingThickness(0.4.mm, 15.W_per_mK),
                )

                val tr = en13384.thermal_resistance_for_layers_calc(
                    mean_gas_temp = 0.degreesCelsius,
                    startGeom = PipeShape.circle(dhi),
                    layers = layers
                )

                // println(s"""|dhi = ${dhi.showP}
                //             |dho = ${layers.xs.last.dho.showP}
                //             |R = ${tr.showP}
                //             |"""".stripMargin)
                tr.isRight `shouldBe` true
                tr.toOption.get.value `shouldEqual` (0.5755 +- 0.01)
            }

            "selon cas type 13384 - C2 (avec t = 40°C)" in {   

                val dhi: D_h = 100.mm
                val innerShape = PipeShape.circle(dhi)

                import afpma.firecalc.dto.all.*

                val layers = List(
                    // tubaginox
                    FromThermalResistanceUsingThickness(
                        thickness   = 2.4.mm,
                        thermal_resistance          = 0.0.m2_K_per_W), 

                    // lame d'air ventilée selon DTU 24.1 (ouverture de 20cm2 en bas et 5cm2 en haut)
                    AirSpaceUsingOuterShape(
                        PipeShape.rectangle(20.cm, 20.cm), 
                        VentilDirection.SameDirAsFlueGas, 
                        VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1
                    ),

                    // boisseau
                    FromThermalResistanceUsingThickness(
                        thickness           = 11.5.cm,
                        thermal_resistance  = 0.12.m2_K_per_W,
                    )
                )

                val tr = en13384.thermal_resistance_for_layers_calc(
                    mean_gas_temp = 40.degreesCelsius,
                    startGeom = innerShape,
                    layers = layers
                )

                println(s"""|dhi = ${dhi.showP}
                            |dho = ${layers.compute_outer_shape(innerShape).dh.showP}
                            |R = ${tr.showP}
                            |"""".stripMargin)

                tr.isRight `shouldBe` true
                tr.toOption.get.value `shouldEqual` (0.2036 +- 0.01)

            }
        }

    }