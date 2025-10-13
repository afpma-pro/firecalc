/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types

import cats.data.*
import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.*

import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.utils.{*, given}
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.units.coulombutils.{show_Pascals as _, *, given}
import algebra.instances.all.given
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.impl.en13384.EN13384_Strict_Application
import cats.Show
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384

case class CasType13384_Results(
    results: List[CasType13384_Result]
)

object CasType13384_Results:
    given showAsTable_Results: ShowAsTable[CasType13384_Results] =
        ShowAsTable.mkLightFor(
            "Comparaisons (nominal - réduit)",
            List("descr", "pz", "pze", "pb", "pz-pze", "pz-pb", "tg", "tob", "tiob", "tiob-tg") ::: "-" :: List("pz", "pze", "pb", "pz-pze", "pz-pb", "tg", "tob", "tiob", "tiob-tg"),
            xs =>
                given Show[QtyD[Pascal]] = afpma.firecalc.units.coulombutils.shows.defaults.show_Pascals_1
                xs.results.map: x =>
                    import x.nominal
                    import x.lowest
                    List(
                        x.descr,
                        nominal.pz.map(_.show).getOrElse("-"),
                        nominal.pze.map(_.show).getOrElse("-"),
                        nominal.pb.map(_.show).getOrElse("-"),
                        nominal.`pz-pze`.map(_.show).getOrElse("-"),
                        nominal.`pz-pb`.map(_.show).getOrElse("-"),
                        nominal.tg.map(_.show).getOrElse("-"),
                        nominal.tob.map(_.show).getOrElse("-"),
                        nominal.tiob.map(_.show).getOrElse("-"),
                        nominal.`tiob-tg`.map(_.show).getOrElse("-"),
                        "-",
                        lowest.pz.map(_.show).getOrElse("-"),
                        lowest.pze.map(_.show).getOrElse("-"),
                        lowest.pb.map(_.show).getOrElse("-"),
                        lowest.`pz-pze`.map(_.show).getOrElse("-"),
                        lowest.`pz-pb`.map(_.show).getOrElse("-"),
                        lowest.tg.map(_.show).getOrElse("-"),
                        lowest.tob.map(_.show).getOrElse("-"),
                        lowest.tiob.map(_.show).getOrElse("-"),
                        lowest.`tiob-tg`.map(_.show).getOrElse("-"),
                    )
                
        )
case class CasType13384_Result(
    val descr: String,
    val nominal: CasType13384_Result.Values,
    val lowest: CasType13384_Result.Values,
)

object CasType13384_Result:
    case class Values(
        pz: Option[Pressure],
        pze: Option[Pressure],
        pb: Option[Pressure],
        `pz-pze`: Option[Pressure],
        `pz-pb`: Option[Pressure],
        tg: Option[TCelsius],
        tob: Option[TCelsius],
        tiob: Option[TCelsius],
        `tiob-tg`: Option[TCelsius],
    )

    
    object Values:

        
        def makeFor(en13384_appl: EN13384_Strict_Application)(using HeatingAppliance)(using LoadQty): Values = 
            val pcond = 
                en13384_appl.pressureRequirements
                    .toOption
                    .flatMap:
                        case neg: PressureRequirements_13384.UnderNegPress => Some(neg)
                        case _ => None

            val tcond = en13384_appl.temperatureRequirements
            CasType13384_Result.Values(
                pz          = pcond.map(_.P_Z),
                pze         = pcond.map(_.P_Ze),
                pb          = pcond.map(_.P_B_min_draught),
                `pz-pze`    = pcond.map(x => x.P_Z - x.P_Ze),
                `pz-pb`     = pcond.map(x => x.P_Z - x.P_B_min_draught),
                tg          = tcond.tig.some,
                tob         = tcond.tob.some,
                tiob        = tcond.tiob.some,
                `tiob-tg`   = (tcond.tiob.value - tcond.tig.value).degreesCelsius.some,
            )



trait CasTypesRunner_13384 extends AnyFreeSpec with Matchers:

    def compute_and_show_results(
        ex: v2024_10_Alg & v0_2024_10.StoveProjectDescr_EN13384_Strict_Alg,
        compareTo: CasType13384_Result,
    ) = 
        val out = (
            ex.en13384_appl, 
            ex.heatingAppliance
        ).mapN: (
            en13384_appl, 
            _heatingAppliance
        ) =>

            given HeatingAppliance  = en13384_appl.heatingAppliance_final(using _heatingAppliance)

            val result_nominal = 
                import LoadQty.givens.nominal
                CasType13384_Result.Values.makeFor(en13384_appl)

            val result_lowest = 
                import LoadQty.givens.reduced
                CasType13384_Result.Values.makeFor(en13384_appl)

            val result = CasType13384_Result(ex.project.reference, result_nominal, result_lowest)
            val results = CasType13384_Results(result :: compareTo :: Nil)
            println(results.showAsCliTable)
            
        out.fold(
            nel => nel.toList.foreach(e => fail(e)), 
            _ => ()
        )

    def run_cas_type_13384_strict(ex: v2024_10_Alg & v0_2024_10.StoveProjectDescr_EN13384_Strict_Alg) = 
        val out = (
            ex.en13384_appl, 
            ex.heatingAppliance
        ).mapN: (
            en13384_appl, 
            _heatingAppliance
        ) =>

            import ex.given_Locale

            val showAsTableInstances: afpma.firecalc.engine.ops.ShowAsTableInstances = 
                new afpma.firecalc.engine.ops.ShowAsTableInstances
            val _showAsTableInstances_EN13384: afpma.firecalc.engine.ops.en13384.ShowAsTableInstances = 
                new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances

            import showAsTableInstances.given
            import _showAsTableInstances_EN13384.given

            given HeatingAppliance  = en13384_appl.heatingAppliance_final(using _heatingAppliance)

            def seperate_tables = println("\n".repeat(3))

            println(s"""|=============================================
                        |
                        | DESCRIPTION
                        |
                        |=============================================""".stripMargin)

            seperate_tables

            println(ex.project.showAsCliTable)

            seperate_tables

            // println(s"Type d'ambiance : ${en13384_appl.inputs.flueGasConditionSeche.show}")

            println(en13384_appl.inputs.localConditions.showAsCliTable)

            seperate_tables

            println(en13384_appl.inputs.nationalAcceptedData.showAsCliTable)

            seperate_tables

            println(en13384_appl.inputs.flueGasCondition.showAsCliTable)

            seperate_tables

            println(en13384_appl.reference_temperatures.showAsCliTable)

            seperate_tables

            val _ = 
                import Params_13384.givens.DraftMin_LoadNominal
                println(Params_13384.show)
                val pr = en13384_appl.pipesResult_13384
                println(pr.mapShow(s"TABLEAU ${Params_13384.show}")(_.showAsCliTable))

            seperate_tables

            val _ = 
                import Params_13384.givens.DraftMax_LoadNominal
                println(Params_13384.show)
                val pr = en13384_appl.pipesResult_13384
                println(pr.mapShow(s"TABLEAU ${Params_13384.show}")(_.showAsCliTable))

            seperate_tables

            println(s"""|=============================================
                        |
                        | CONFORMITÉ avec EN 13384-1
                        |
                        |=============================================""".stripMargin)

            seperate_tables

            println(HeatingAppliance.summon.showAsCliTable)

            seperate_tables

            // val asp_density = pipesResult_13384.airIntake.last_density_mean.get
            // val asp_vol_rate = ((en13384_appl.mB_dot / asp_density).value * 3600).withUnit[(Meter ^ 3) / Hour]

            // println(s"Densité air de combustion : ${asp_density.showP}")
            // println(s"Débit air de combustion : ${en13384_appl.mB_dot.showP}")
            // println(s"Volume d'air de combustion : ${asp_vol_rate.showP}")

            val pressure_cond_nominal = 
                import LoadQty.givens.nominal
                en13384_appl.pressureRequirements
            println(pressure_cond_nominal.mapShow("EXIGENCES DE PRESSION (EN 13384-1) // Allure nominale")(_.showAsCliTable))

            seperate_tables

            val pressure_cond_lowest = 
                import LoadQty.givens.reduced
                en13384_appl.pressureRequirements
            println(pressure_cond_lowest.mapShow("EXIGENCES DE PRESSION (EN 13384-1) // Allure réduite")(_.showAsCliTable))

            seperate_tables

            val temperature_req_at_nominal =
                import LoadQty.givens.nominal
                en13384_appl.temperatureRequirements
            println(temperature_req_at_nominal.showAsCliTable)

            seperate_tables

            val temperature_req_at_lowest =
                import LoadQty.givens.reduced
                en13384_appl.temperatureRequirements
            println(temperature_req_at_lowest.showAsCliTable)

            seperate_tables

        out.fold(
            nel => nel.toList.foreach(e => fail(e)), 
            _ => ()
        )
end CasTypesRunner_13384