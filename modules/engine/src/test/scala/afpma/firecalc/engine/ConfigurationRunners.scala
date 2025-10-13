/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.*

import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.std.Inputs
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_Alg
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_MCE_Alg
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Labo_Alg
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg

trait ConfigurationRunners extends AnyFreeSpec with Matchers {

    private def showForMCEComparisonWithLabData[_Inputs <: Inputs[?]](ex: StoveProjectDescr_Alg, _en15544: EN15544_V_2023_Common_Application[_Inputs])(using p: _en15544.Params_15544) = 

        import ex.given_Locale

        given LocalRegulations = ex.localRegulations

        val showAsTableInstances = new afpma.firecalc.engine.ops.ShowAsTableInstances
        val showAsTableInstances_EN15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances
        val showAsTableInstances_EN13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances

        import showAsTableInstances.given
        import showAsTableInstances_EN15544.given
        import showAsTableInstances_EN13384.given

        println(_en15544.inputs.en13384NationalAcceptedData.showAsCliTable)
        println("\n")
        println(_en15544.inputs.localConditions.showAsCliTable)
        println("\n")
        println(_en15544.inputs.flueGasCondition.showAsCliTable)
        println("\n")
        println(_en15544.citedConstraints.showAsCliTable)
        // println(inputs.pipes.showAsCliTable)

        println(_en15544.inputs.design.firebox.showAsCliTable)

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        _en15544.combustionAir_PipeResult.toValidatedNel.getOrThrow
        _en15544.firebox_PipeResult.toValidatedNel.getOrThrow
        _en15544.flue_PipeResult.toValidatedNel.getOrThrow
        _en15544.connector_PipeResult.toValidatedNel.getOrThrow
        _en15544.chimney_PipeResult.toValidatedNel.getOrThrow

        val pipesResult_15544 = _en15544.outputs.pipesResult_15544.getOrThrow
        println(pipesResult_15544.showAsCliTable)

        println(_en15544.pressureRequirement_EN15544.toOption.map(_.showAsCliTable).getOrElse("ERROR (pressure requirement)"))
        println(_en15544.t_chimney_wall_top.showAsCliTable)

        println(_en15544.efficiencies_values.showAsCliTable)
        println(_en15544.flue_gas_triple_of_variates.toOption.map(_.showAsCliTable).getOrElse("ERROR (flue gas triple of variates)"))
        println(_en15544.estimated_output_temperatures.showAsCliTable)

        _en15544.pressureRequirements_EN13384 match 
            case Validated.Valid(a) => 
                println(a.showAsCliTable)
            case Validated.Invalid(nel) => 
                println("ERROR (pressure requirements EN13384)")
                nel.toList.map(_.show).foreach(println)
                fail()

    private def showDetailedNoteAsText[_Inputs <: Inputs[?]](ex: StoveProjectDescr_Alg, _en15544: EN15544_V_2023_Common_Application[_Inputs])(using p: _en15544.Params_15544) = 

        println("-------------------------------------------------")
        println(s"CONFIGURATION = ${ex.project.reference}")
        println("-------------------------------------------------")

        import ex.given_Locale

        given LocalRegulations = ex.localRegulations
        
        val showAsTableInstances = new afpma.firecalc.engine.ops.ShowAsTableInstances
        val showAsTableInstances_EN15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances
        val showAsTableInstances_EN13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances
        
        import showAsTableInstances.given
        import showAsTableInstances_EN15544.given
        import showAsTableInstances_EN13384.given

        println(_en15544.inputs.en13384NationalAcceptedData.showAsCliTable)
        println("\n")
        println(_en15544.inputs.localConditions.showAsCliTable)
        println("\n")
        println(_en15544.inputs.flueGasCondition.showAsCliTable)
        println("\n")
        println(_en15544.citedConstraints.showAsCliTable)
        // println(inputs.pipes.showAsCliTable)

        println(_en15544.inputs.design.firebox.showAsCliTable)

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        _en15544.combustionAir_PipeResult.toValidatedNel.getOrThrow
        _en15544.firebox_PipeResult.toValidatedNel.getOrThrow
        _en15544.flue_PipeResult.toValidatedNel.getOrThrow
        _en15544.connector_PipeResult.toValidatedNel.getOrThrow
        _en15544.chimney_PipeResult.toValidatedNel.getOrThrow

        val pipesResult_15544 = _en15544.outputs.pipesResult_15544.getOrThrow
        println(pipesResult_15544.showAsCliTable)

        println(_en15544.pressureRequirement_EN15544.toOption.map(_.showAsCliTable).getOrElse("ERROR (pressure requirement)"))
        println(_en15544.t_chimney_wall_top.showAsCliTable)

        println(_en15544.efficiencies_values.showAsCliTable)
        println(_en15544.flue_gas_triple_of_variates.toOption.map(_.showAsCliTable).getOrElse("ERROR (flue gas triple of variates)"))
        println(_en15544.estimated_output_temperatures.showAsCliTable)

        _en15544.pressureRequirements_EN13384 match 
            case Validated.Valid(a) => 
                println(a.showAsCliTable)
            case Validated.Invalid(nel) => 
                println("ERROR (pressure requirements EN13384)")
                nel.toList.map(_.show).foreach(println)
                fail()

    def run_exercice_15544_strict(ex_15544_strict: StoveProjectDescr_EN15544_Strict_Alg) =
            val out = ex_15544_strict.en15544_Alg.map: _strict =>
                given pReq: DraftCondition = DraftCondition.DraftMinOrPositivePressureMax
                import LoadQty.givens.nominal
                given _strict.Params_15544 = (pReq, nominal)
                showDetailedNoteAsText(ex_15544_strict, _strict)
            out.fold(
                nel => nel.toList.foreach(e => fail(e)), 
                _ => ()
            )
    end run_exercice_15544_strict

    def run_exercice_15544_mce(ex_15544_mce: StoveProjectDescr_EN15544_MCE_Alg) =
        val out = ex_15544_mce.en15544_Alg.map: _mce =>
            given _mce.Params_15544 = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)  // draft max ?
            showDetailedNoteAsText(ex_15544_mce, _mce)
        out.fold(
            nel => nel.toList.foreach(println), _ => ()
        )
    end run_exercice_15544_mce

    def run_15544_labo(ex_15544_labo: StoveProjectDescr_EN15544_Labo_Alg) =
        import scala.language.adhocExtensions

        val config = ex_15544_labo

        val out = config.en15544_Alg.map: mce_labo =>

            given mce_labo.Params_15544 = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)  // draft max ?

            println("[ at load = nominal ]")
            println(s"CO2 wet = ${config.fluegas_co2_wet_nominal.showP}")
            println(s"CO2 dry = ${config.fluegas_co2_dry_nominal.showP}")
 
            println(s"O2 wet  = ${config.fluegas_o2_wet_nominal.showP}")
            println(s"O2 dry  = ${config.fluegas_o2_dry_nominal.showP}")
            println("")

            showDetailedNoteAsText(ex_15544_labo, mce_labo)

        out.fold(
            nel => nel.toList.foreach(e => fail(e)), 
            _ => ()
        )
    end run_15544_labo

    def run_15544_mce_for_lab_comparison(ex_15544_labo: StoveProjectDescr_EN15544_Labo_Alg) =
        import scala.language.adhocExtensions

        val config = ex_15544_labo

        val out = config.en15544_Alg.map: mce_labo =>
            given mce_labo.Params_15544 = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)  // tirage max ?
            showForMCEComparisonWithLabData(ex_15544_labo, mce_labo)
        out.fold(
            nel => nel.toList.foreach(e => fail(e)), 
            _ => ()
        )
    end run_15544_mce_for_lab_comparison


}