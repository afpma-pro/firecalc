/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types


import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.utils.*

import cats.implicits.toShow

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

trait CasTypesRunner_15544_Strict extends AnyFreeSpec with Matchers:

    given Locale = Locales.en // acceptable to force Locale in tests

    private def showDebug(
        cas_type: v0_2024_10.StoveProjectDescr_15544_Strict_Alg & afpma.firecalc.engine.cas_types.v2024_10_Alg,
        _en15544: EN15544_V_2023_Common_Application,
        ap: _en15544.AtParams
    ) =

        import cas_type.given_Locale
        given _en15544.Params_15544 = ap.params

        given LocalRegulations = cas_type.localRegulations
        given EN15544_V_2023_Formulas_Alg = _en15544.formulas
        
        val showAsTableInstances = new afpma.firecalc.engine.ops.ShowAsTableInstances
        val showAsTableInstances_15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances_15544
        val showAsTableInstances_13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances_13384
        
        import showAsTableInstances.given
        import showAsTableInstances_15544.given
        import showAsTableInstances_13384.given

        def seperate_tables = println("\n".repeat(3))

        println("""|=============================================
                    |
                    | DESCRIPTION
                    |
                    |=============================================""".stripMargin)

        seperate_tables

        println(cas_type.project.showAsCliTable)

        seperate_tables

        println(ap.outputs.technicalSpecs.showAsCliTable)

        seperate_tables
        
        println(_en15544.inputs.design.firebox.showAsCliTable)

        seperate_tables

        println("""|=============================================
                    |
                    | CONFORMITÉ avec EN 15544:2023
                    |
                    |=============================================""".stripMargin)
        
        seperate_tables

        println(_en15544.citedConstraints.showAsCliTable)

        seperate_tables


        println(s""" Calcul avec Params = ${ap.params}""")

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        ap.combustionAir_PipeResult.getOrThrow
        ap.firebox_PipeResult.getOrThrow
        ap.flue_PipeResult.getOrThrow
        ap.connector_PipeResult.getOrThrow
        ap.chimney_PipeResult.getOrThrow

        val pipesResult_15544 = ap.outputs.pipesResult_15544.getOrThrow
        println(pipesResult_15544.showAsCliTable)

        seperate_tables

        // CONTROLE DU FONCTIONNEMENT selon EN 15544

        println(ap.pressureRequirement_EN15544.getOrThrow.showAsCliTable)

        seperate_tables

        println(ap.estimated_output_temperatures.showAsCliTable)
        println(ap.t_chimney_wall_top.getOrThrow.showAsCliTable)

        seperate_tables

        println(_en15544.emissions_and_efficiency_values.showAsCliTable)
        //// println(_en15544.efficiencies_values.showAsCliTable)

        seperate_tables

        println(ap.flue_gas_triple_of_variates.getOrThrow.showAsCliTable)

        seperate_tables

        println("""|=============================================
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

        println(ap.outputs.reference_temperatures.showAsCliTable)

        seperate_tables

        println(_en15544.temperatureRequirements_EN13384.getOrThrow.showAsCliTable)

        seperate_tables

        println(_en15544.pressureRequirements_EN13384.getOrThrow.showAsCliTable)
    end showDebug

    def run_cas_type_15544_strict(cas_type: v0_2024_10.StoveProjectDescr_15544_Strict_Alg & afpma.firecalc.engine.cas_types.v2024_10_Alg) =
        val out = cas_type.en15544_Alg.map: _strict =>
            showDebug(cas_type, _strict, _strict.atDraftMin_LoadNominal)
        out.fold(
            nel => nel.toList.foreach(e => fail(e.show)),
            _ => ()
        )
    end run_cas_type_15544_strict
end CasTypesRunner_15544_Strict