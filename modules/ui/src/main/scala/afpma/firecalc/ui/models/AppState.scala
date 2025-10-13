/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import scala.util.*

import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasPratique_15544_FDIM_EX_03
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.From_CalculPdM_V_0_2_32
import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.ui.instances.circe.given
import afpma.firecalc.ui.instances.defaultable

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import afpma.firecalc.dto.FireCalcYAML

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544

type AppState = FireCalcYAML

object AppState:

    // given Decoder[AppState] = FireCalcYAML_V1.decoder
    // given Encoder[AppState] = FireCalcYAML_V1.encoder

    val example_projet_15544 = FireCalcYAML_V1(
        locale                          = Locale(Languages.Fr),
        display_units                   = DisplayUnits.SI,
        standard_or_computation_method  = StandardOrComputationMethod.EN_15544_2023,
        project_description             = ProjectDescr(
            reference = ExampleProject_15544.project.reference,
            date      = ExampleProject_15544.project.date,
            country   = ExampleProject_15544.project.country
        ),
        local_conditions                = ExampleProject_15544.localConditions,
        stove_params                    = ExampleProject_15544.stoveParams,
        air_intake_descr                = ExampleProject_15544.conduit_air_descr,
        firebox                         = ExampleProject_15544.foyer_descr.transformInto[Firebox.EcoLabeled](using From_CalculPdM_V_0_2_32.transformer_inv_EcoLabeled),
        flue_pipe_descr                 = ExampleProject_15544.accumulateur_descr,
        connector_pipe_descr            = ExampleProject_15544.conduit_raccordement_descr,
        chimney_pipe_descr              = ExampleProject_15544.conduit_fumees_descr,
    )
        

    val init_as_CasType_15544_C3 = FireCalcYAML_V1(
        locale                          = Locale(Languages.Fr),
        display_units                   = DisplayUnits.SI,
        standard_or_computation_method  = StandardOrComputationMethod.EN_15544_2023,
        project_description             = ProjectDescr(
            reference = CasType_15544_C3.project.reference,
            date = CasType_15544_C3.project.date,
            country = CasType_15544_C3.project.country
        ),
        local_conditions                = CasType_15544_C3.localConditions,
        stove_params                    = CasType_15544_C3.stoveParams,
        air_intake_descr                = CasType_15544_C3.conduit_air_descr,
        firebox                         = CasType_15544_C3.foyer_descr.transformInto[Firebox.EcoLabeled](using From_CalculPdM_V_0_2_32.transformer_inv_EcoLabeled),
        flue_pipe_descr                 = CasType_15544_C3.accumulateur_descr,
        connector_pipe_descr            = CasType_15544_C3.conduit_raccordement_descr,
        chimney_pipe_descr              = CasType_15544_C3.conduit_fumees_descr,
    )

    val init_as_CasPratique_15544_FDIM_EX_03 = FireCalcYAML_V1(
        locale                          = Locale(Languages.Fr),
        display_units                   = DisplayUnits.SI,
        standard_or_computation_method  = StandardOrComputationMethod.EN_15544_2023,
        project_description             = ProjectDescr(
            reference = CasPratique_15544_FDIM_EX_03.project.reference,
            date = CasPratique_15544_FDIM_EX_03.project.date,
            country = CasPratique_15544_FDIM_EX_03.project.country
        ),
        local_conditions                = CasPratique_15544_FDIM_EX_03.localConditions,
        stove_params                    = CasPratique_15544_FDIM_EX_03.stoveParams,
        air_intake_descr                = CasPratique_15544_FDIM_EX_03.conduit_air_descr,
        firebox                         = CasPratique_15544_FDIM_EX_03.foyer_descr.transformInto[Firebox.EcoLabeled](using From_CalculPdM_V_0_2_32.transformer_inv_EcoLabeled),
        flue_pipe_descr                 = CasPratique_15544_FDIM_EX_03.accumulateur_descr,
        connector_pipe_descr            = CasPratique_15544_FDIM_EX_03.conduit_raccordement_descr,
        chimney_pipe_descr              = CasPratique_15544_FDIM_EX_03.conduit_fumees_descr,
    )

    val empty = FireCalcYAML_V1(
        locale                          = Locale(Languages.Fr),
        display_units                   = DisplayUnits.SI,
        standard_or_computation_method  = StandardOrComputationMethod.EN_15544_2023,
        project_description             = ProjectDescr.empty,  // Now only contains reference, date, country
        local_conditions                = LocalConditions.default,
        stove_params                    = StoveParamsUI.default_StoveParams.default,
        air_intake_descr                = Seq.empty,
        firebox                         = defaultable.firebox_traditional_empty.default,
        flue_pipe_descr                 = Seq.empty,
        connector_pipe_descr            = Seq.empty,
        chimney_pipe_descr              = Seq.empty,
    )

    val minimal = FireCalcYAML_V1(
        locale                          = Locale(Languages.Fr),
        display_units                   = DisplayUnits.SI,
        standard_or_computation_method  = StandardOrComputationMethod.EN_15544_2023,
        project_description             = ProjectDescr.empty,  // Now only contains reference, date, country
        local_conditions                = LocalConditions.default,
        stove_params                    = StoveParamsUI.default_StoveParams.default,
        air_intake_descr                = Seq.empty,
        firebox                         = defaultable.firebox_traditional_minimal.default,
        flue_pipe_descr                 = 
            import FluePipe_Module_EN15544.*
            Seq(
                roughness(3.mm),
                innerShape(rectangle(18.cm, 18.cm)),
                addSectionHorizontal("sortie de foyer",  30.cm),
                addSharpAngle_90deg("vers descente"),
                addSectionVertical("descente", -100.cm),
                addSharpAngle_90deg("vers section horizontale"),
                addSectionHorizontal("section horizontale", 200.cm),
                addSharpAngle_90deg("vers remontée"),
                addSectionVertical("remontée", 200.cm)
            ),
        connector_pipe_descr     = 
            import ConnectorPipe_Module.*
            Seq(
                roughness(Material_13384.WeldedSteel),
                innerShape(circle(20.cm)),
                layer(e = 1.mm, tr = SquareMeterKelvinPerWatt(56.0)),
                pipeLocation(PipeLocation.HeatedArea),
                addSectionVertical("connecteur", 5.cm),
            ),
        chimney_pipe_descr        = 
            import ChimneyPipe_Module.*
            Seq(
                roughness(Material_13384.WeldedSteel),
                innerShape(circle(200.mm)),
                layer(e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),
                pipeLocation(PipeLocation.HeatedArea),
                addSectionVertical("conduit double peau isolé.", 6.m)
            ),
    )
    
    val init = minimal

    // Circe encoding / decoding

    @deprecated("TODO: encode using dto module")
    def encodeToYaml(x: AppState): Try[String] = 
        val jsonEncodedTry = try {
            val xj = x.asJson 
            Success(xj)
        } catch
            case e => 
                scala.scalajs.js.Dynamic.global.console.log(s"json encoding failure for ${x}")
                scala.scalajs.js.Dynamic.global.console.log(s"error :")
                scala.scalajs.js.Dynamic.global.console.log(e.getMessage())
                scala.scalajs.js.Dynamic.global.console.log(e.getStackTrace().mkString("\n"))
                // Json.fromJsonObject(JsonObject(("failure", Json.True)))
                Failure(e)

        val yamlEncodedTry = jsonEncodedTry.map(yamlPrinter.print)
        yamlEncodedTry match
            case Success(y_out) =>
                // scala.scalajs.js.Dynamic.global.console.log(s"yaml output = \n${y_out}")
                ()
            case _ =>
                ()
        yamlEncodedTry

    @deprecated("TODO: decode using dto module")
    def decodeFromYaml(y: String): Try[AppState] = 
        yamlParser.parse(y) match
            case Left(pf) => 
                scala.scalajs.js.Dynamic.global.console.log(s"failed to decode yaml :\n$y")
                scala.scalajs.js.Dynamic.global.console.log(s"returning default 'AppState' = ${AppState.init}")
                scala.scalajs.js.Dynamic.global.console.log(s"error details: ${pf.getMessage()}\n${pf.toString()}")
                Success(AppState.init) // TODO: add popup info to notify user ?
                // Failure(pf)
            case Right(parsedYaml) =>
                val j = parsedYaml.noSpaces
                // scala.scalajs.js.Dynamic.global.console.log(s"parsedYaml =")
                // scala.scalajs.js.Dynamic.global.console.log(j)
                try
                    decode[AppState](j) match
                        case Left(err) => 
                            scala.scalajs.js.Dynamic.global.console.log(s"failed to decode json :\n$j")
                            scala.scalajs.js.Dynamic.global.console.log(s"returning default 'AppState' = ${AppState.init}")
                            scala.scalajs.js.Dynamic.global.console.log(s"error details: ${err.getMessage()}\n${err.toString()}")
                            // Failure(err)
                            Success(AppState.init) // TODO: add popup info to notify user ?
                        case Right(value) => 
                            // scala.scalajs.js.Dynamic.global.console.log(s"decoding ok found :\n$value")
                            Success(value)
                catch
                    case e =>
                        scala.scalajs.js.Dynamic.global.console.log(s"unexpected failure when decoding json :\n$j")
                        scala.scalajs.js.Dynamic.global.console.log(s"returning default 'AppState' = ${AppState.init}")
                        scala.scalajs.js.Dynamic.global.console.log(s"uncaught error :\n${e.getMessage()}\n${e.toString()}")
                        // Success(AppState.init)
                        // throw e
                        Failure(e)
end AppState