/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384
import afpma.firecalc.engine.models.en13384.typedefs.TemperatureRequirements_13384
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error
import afpma.firecalc.engine.utils.{*, given}

import cats.Show
import cats.data.*
import cats.syntax.all.*

import coulomb.ops.standard.all.given

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/** Factory for creating CasType13384_Result.Values from EN13384 application results. */
object CasType13384_ResultFactory:

    def makeValuesFor(en13384_appl: EN13384_1_A1_2019_Common_Application)(using
        HeatingAppliance
    )(using LoadQty): CasType13384_Result.Values =
        val pcond =
            en13384_appl.pressureRequirements.toOption
                .flatMap:
                    case neg: PressureRequirements_13384.UnderNegPress =>
                        Some(neg)
                    case _ => None

        val tcond = en13384_appl.temperatureRequirements
        CasType13384_Result.Values       (
            pz        = pcond.map(_.P_Z),
            pze       = pcond.map(_.P_Ze),
            pb        = pcond.map(_.P_B_min_draught),
            `pz-pze`  = pcond.map(x => x.P_Z - x.P_Ze),
            `pz-pb`   = pcond.map(x => x.P_Z - x.P_B_min_draught),
            tg        = tcond.tig.some,
            tob       = tcond.tob.some,
            tiob      = tcond.tiob.some,
            `tiob-tg` = (tcond.tiob.value - tcond.tig.value).degreesCelsius.some
        )

/**
 * Common trait for running EN 13384 cas types tests.
 * Provides shared logic for computing and displaying results.
 */
trait CasTypesRunner_13384_Common extends AnyFreeSpec with Matchers:

    given Locale = Locales.en // acceptable to force Locale in tests

    /**
     * Type alias for the project description algebra.
     * Implementors should provide the concrete type.
     */
    type ProjectDescr_Alg <: v2024_10_Alg & v0_2024_10_strict.StoveProjectDescr_13384_Alg

    /** Extract the EN13384 application from the project description. */
    def extractEn13384Appl(
        ex: ProjectDescr_Alg
    ): VNelMcalcErr[EN13384_1_A1_2019_Common_Application]

    private def compute_and_show_results_impl(
        ex       : ProjectDescr_Alg,
        compareTo: CasType13384_Result
    )(emit: String => Unit): VNelMcalcErr[Unit] =
        (
            extractEn13384Appl(ex),
            ex.heatingAppliance
        ).mapN:
            (
                en13384_appl,
                _heatingAppliance
            ) =>
                given HeatingAppliance = en13384_appl.heatingAppliance_final(using
                    _heatingAppliance
                )

                val result_nominal =
                    import LoadQty.givens.nominal
                    CasType13384_ResultFactory.makeValuesFor(en13384_appl)

                val result_lowest =
                    import LoadQty.givens.reduced
                    CasType13384_ResultFactory.makeValuesFor(en13384_appl)

                val result  = CasType13384_Result(
                    ex.project.reference,
                    result_nominal,
                    result_lowest
                )
                val results = CasType13384_Results(result :: compareTo :: Nil)
                emit(results.showAsCliTable)

    /** Compute and show results comparing with expected values. */
    def compute_and_show_results(
        ex       : ProjectDescr_Alg,
        compareTo: CasType13384_Result
    ): Unit =
        compute_and_show_results_impl(ex, compareTo)(println).fold(
            nel => nel.toList.foreach(e => fail(e.show)),
            _ => ()
        )

    def compute_and_show_results_asString(
        ex       : ProjectDescr_Alg,
        compareTo: CasType13384_Result
    ): VNelMcalcErr[String] =
        val sb = new StringBuilder
        compute_and_show_results_impl(ex, compareTo)(s => sb.append(s).append("\n")).map(_ => sb.toString)

    private def run_cas_type_13384_impl(ex: ProjectDescr_Alg)(emit: String => Unit): VNelMcalcErr[Unit] =
        (
            extractEn13384Appl(ex),
            ex.heatingAppliance
        ).mapN:
            (
                en13384_appl,
                _heatingAppliance
            ) =>
                import ex.given_Locale

                val showAsTableInstances         : afpma.firecalc.engine.ops.ShowAsTableInstances               =
                    new afpma.firecalc.engine.ops.ShowAsTableInstances
                val _showAsTableInstances_EN13384: afpma.firecalc.engine.ops.en13384.ShowAsTableInstances_13384 =
                    new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances_13384

                import showAsTableInstances.given
                import _showAsTableInstances_EN13384.given

                given showAsTable_pressReq13384: ShowAsTable[PressureRequirements_13384] =
                    _showAsTableInstances_EN13384.mkShowAsTable_PressureRequirements_EN13384(checkAndShowReq = true)

                given showAsTable_tempReq13384: ShowAsTable[TemperatureRequirements_13384] =
                    _showAsTableInstances_EN13384.showAsTable_temperatureRequirements_en13384(checkAndShowReq = true)

                given HeatingAppliance = en13384_appl.heatingAppliance_final(using
                    _heatingAppliance
                )

                def seperate_tables = emit("\n".repeat(3))

                emit("""|=============================================
                        |
                        | DESCRIPTION
                        |
                        |=============================================""".stripMargin)

                seperate_tables

                emit(ex.project.showAsCliTable)

                seperate_tables

                emit(en13384_appl.inputs.localConditions.showAsCliTable)

                seperate_tables

                emit(en13384_appl.inputs.nationalAcceptedData.showAsCliTable)

                seperate_tables

                emit(en13384_appl.inputs.flueGasCondition.showAsCliTable)

                seperate_tables

                emit(en13384_appl.reference_temperatures.showAsCliTable)

                seperate_tables

                val _ =
                    import Params_13384.givens.DraftMin_LoadNominal
                    emit(Params_13384.show)
                    val pr = en13384_appl.pipesResult_13384
                    emit(
                        pr.mapShow(s"TABLEAU ${Params_13384.show}")(_.showAsCliTable)
                    )

                seperate_tables

                val _ =
                    import Params_13384.givens.DraftMax_LoadNominal
                    emit(Params_13384.show)
                    val pr = en13384_appl.pipesResult_13384
                    emit(
                        pr.mapShow(s"TABLEAU ${Params_13384.show}")(_.showAsCliTable)
                    )

                seperate_tables

                emit("""|=============================================
                        |
                        | CONFORMITÉ avec EN 13384-1
                        |
                        |=============================================""".stripMargin)

                seperate_tables

                emit(HeatingAppliance.summon.showAsCliTable)

                seperate_tables

                val pressure_cond_nominal =
                    import LoadQty.givens.nominal
                    en13384_appl.pressureRequirements
                emit(
                    pressure_cond_nominal.mapShow(
                        "EXIGENCES DE PRESSION (EN 13384-1) // Allure nominale"
                    )(_.showAsCliTable)
                )

                seperate_tables

                val pressure_cond_lowest =
                    import LoadQty.givens.reduced
                    en13384_appl.pressureRequirements
                emit(
                    pressure_cond_lowest.mapShow(
                        "EXIGENCES DE PRESSION (EN 13384-1) // Allure réduite"
                    )(_.showAsCliTable)
                )

                seperate_tables

                val temperature_req_at_nominal =
                    import LoadQty.givens.nominal
                    en13384_appl.temperatureRequirements
                emit(temperature_req_at_nominal.showAsCliTable)

                seperate_tables

                val temperature_req_at_lowest =
                    import LoadQty.givens.reduced
                    en13384_appl.temperatureRequirements
                emit(temperature_req_at_lowest.showAsCliTable)

                seperate_tables

    /** Run a full EN 13384 cas type test with detailed output. */
    def run_cas_type_13384(ex: ProjectDescr_Alg): Unit =
        run_cas_type_13384_impl(ex)(println).fold(
            nel => nel.toList.foreach(e => fail(e.show)),
            _ => ()
        )

    def run_cas_type_13384_asString(ex: ProjectDescr_Alg): VNelMcalcErr[String] =
        val sb = new StringBuilder
        run_cas_type_13384_impl(ex)(s => sb.append(s).append("\n")).map(_ => sb.toString)
end CasTypesRunner_13384_Common
