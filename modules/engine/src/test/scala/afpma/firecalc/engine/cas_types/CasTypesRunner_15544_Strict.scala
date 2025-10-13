/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types


import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.*

import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

import afpma.firecalc.engine.models.en15544.std.Inputs
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.api.v0_2024_10

trait CasTypesRunner_15544_Strict extends AnyFreeSpec with Matchers:

    private def showDebug[_Inputs <: Inputs[?]](
        cas_type: v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg & afpma.firecalc.engine.cas_types.v2024_10_Alg, 
        _en15544: EN15544_V_2023_Common_Application[_Inputs]
    )(using p: _en15544.Params_15544) = 

        import cas_type.given_Locale

        given LocalRegulations = cas_type.localRegulations
        
        val showAsTableInstances = new afpma.firecalc.engine.ops.ShowAsTableInstances
        val showAsTableInstances_EN15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances
        val showAsTableInstances_EN13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances
        
        import showAsTableInstances.given
        import showAsTableInstances_EN15544.given
        import showAsTableInstances_EN13384.given

        def seperate_tables = println("\n".repeat(3))

        println(s"""|=============================================
                    |
                    | DESCRIPTION
                    |
                    |=============================================""".stripMargin)

        seperate_tables

        println(cas_type.project.showAsCliTable)

        seperate_tables

        println(_en15544.outputs.technicalSpecs.showAsCliTable)

        seperate_tables
        
        println(_en15544.inputs.design.firebox.showAsCliTable)

        seperate_tables

        println(s"""|=============================================
                    |
                    | CONFORMITÉ avec EN 15544:2023
                    |
                    |=============================================""".stripMargin)
        
        seperate_tables

        println(_en15544.citedConstraints.showAsCliTable)

        seperate_tables

        import afpma.firecalc.engine.standard.MecaFlu_Error.given

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        _en15544.combustionAir_PipeResult.toValidatedNel.getOrThrow
        _en15544.firebox_PipeResult.toValidatedNel.getOrThrow
        _en15544.flue_PipeResult.toValidatedNel.getOrThrow
        _en15544.connector_PipeResult.toValidatedNel.getOrThrow
        _en15544.chimney_PipeResult.toValidatedNel.getOrThrow

        val pipesResult_15544 = _en15544.outputs.pipesResult_15544.getOrThrow
        println(pipesResult_15544.showAsCliTable)

        seperate_tables

        // CONTROLE DU FONCTIONNEMENT selon EN 15544

        println(_en15544.pressureRequirement_EN15544.getOrThrow.showAsCliTable)

        seperate_tables

        println(_en15544.estimated_output_temperatures.showAsCliTable)
        println(_en15544.t_chimney_wall_top.showAsCliTable)

        seperate_tables

        println(_en15544.emissions_and_efficiency_values.showAsCliTable)
        //// println(_en15544.efficiencies_values.showAsCliTable)

        seperate_tables

        println(_en15544.flue_gas_triple_of_variates.getOrThrow.showAsCliTable)

        seperate_tables

        println(s"""|=============================================
                    |
                    | CONFORMITÉ avec EN 13384-1
                    |
                    |=============================================""".stripMargin)

        seperate_tables

        val heatingAppliance_13384 = _en15544.en13384_heatingAppliance_final.getOrThrow
        println(heatingAppliance_13384.showAsCliTable)

        seperate_tables

        println(_en15544.inputs.localConditions.showAsCliTable)
        println(_en15544.en13384_application.P_L.showAsCliTable)

        seperate_tables

        println(_en15544.outputs.reference_temperatures.showAsCliTable)

        seperate_tables

        println(_en15544.temperatureRequirements_EN13384.showAsCliTable)

        seperate_tables

        println(_en15544.pressureRequirements_EN13384.getOrThrow.showAsCliTable)
    end showDebug        

    def run_cas_type_15544_strict(cas_type: v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg & afpma.firecalc.engine.cas_types.v2024_10_Alg) =
        val out = cas_type.en15544_Alg.map: _strict =>
            given pReq: DraftCondition = DraftCondition.DraftMinOrPositivePressureMax
            import LoadQty.givens.nominal
            given _strict.Params_15544 = (pReq, nominal)
            showDebug(cas_type, _strict)
        out.fold(
            nel => nel.toList.foreach(e => fail(e)), 
            _ => ()
        )
    end run_cas_type_15544_strict
end CasTypesRunner_15544_Strict