/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_15544_Alg
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_Alg
import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.utils.*

import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

trait ConfigurationRunners extends AnyFreeSpec with Matchers {

    given Locale = Locales.en // acceptable to force Locale in tests

    private def showDetailedNoteAsText(
        ex      : StoveProjectDescr_Alg,
        _en15544: EN15544_V_2023_Common_Application,
        ap      : _en15544.AtParams
    ) =

        println("-------------------------------------------------")
        println(s"CONFIGURATION = ${ex.project.reference}"         )
        println("-------------------------------------------------")

        import ex.given_Locale
        given _en15544.Params_15544 = ap.params

        given LocalRegulations            = ex.localRegulations
        given EN15544_V_2023_Formulas_Alg = _en15544.formulas

        val showAsTableInstances         = new afpma.firecalc.engine.ops.ShowAsTableInstances
        val showAsTableInstances_EN15544 = new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances_15544
        val showAsTableInstances_EN13384 = new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances_13384

        import showAsTableInstances.given
        import showAsTableInstances_EN15544.given
        import showAsTableInstances_EN13384.given

        println(_en15544.inputs.en13384NationalAcceptedData.showAsCliTable)
        println("\n"                                                      )
        println(_en15544.inputs.localConditions.showAsCliTable            )
        println("\n"                                                      )
        println(_en15544.inputs.flueGasCondition.showAsCliTable           )
        println("\n"                                                      )
        println(_en15544.citedConstraints.showAsCliTable                  )
        // println(inputs.pipes.showAsCliTable)

        println(_en15544.inputs.design.firebox.showAsCliTable)

        _en15544.airIntake_PipeResult.toValidatedNel.getOrThrow
        ap.combustionAir_PipeResult.getOrThrow
        ap.firebox_PipeResult.getOrThrow
        ap.postFireboxPipeResults.getOrThrow

        val pipesResult_15544 = ap.outputs.pipesResult_15544.getOrThrow
        println(pipesResult_15544.showAsCliTable)

        println(ap.pressureRequirement_EN15544.toOption.map(_.showAsCliTable).getOrElse("ERROR (pressure requirement)"))
        println(ap.t_chimney_wall_top.getOrThrow.showAsCliTable                                                        )

        println(_en15544.efficiencies_values.showAsCliTable    )
        println(
            ap.flue_gas_triple_of_variates.toOption
                .map(_.showAsCliTable)
                .getOrElse("ERROR (flue gas triple of variates)")
        )
        println(ap.estimated_output_temperatures.showAsCliTable)

        _en15544.pressureRequirements_EN13384 match
            case Validated.Valid(a)     =>
                println(a.showAsCliTable)
            case Validated.Invalid(nel) =>
                println                       ("ERROR (pressure requirements EN13384)")
                nel.toList.map(_.show).foreach(println                                )
                fail                          (                                       )

    def run_exercice_15544_strict(ex_15544_strict: StoveProjectDescr_15544_Alg) =
        val out = ex_15544_strict.en15544_Alg.map: _strict =>
            showDetailedNoteAsText(ex_15544_strict, _strict, _strict.atDraftMin_LoadNominal)
        out.fold(
            nel => nel.toList.foreach(e => fail(e.show)),
            _ => ()
        )
    end run_exercice_15544_strict

    def run_exercice_15544_mce(ex_15544_mce: StoveProjectDescr_15544_Alg) =
        val out = ex_15544_mce.en15544_Alg.map: _mce =>
            showDetailedNoteAsText(ex_15544_mce, _mce, _mce.atDraftMax_LoadNominal)
        out.fold(
            nel => nel.toList.foreach(println),
            _ => ()
        )
    end run_exercice_15544_mce

    // NOTE: run_15544_labo and run_15544_mce_for_lab_comparison have been moved to
    // downstream modules (labo/ConfigurationRunners_Labo, fdim/ConfigurationRunners)
    // that can access the MCE/Labo-specific API objects.

}
