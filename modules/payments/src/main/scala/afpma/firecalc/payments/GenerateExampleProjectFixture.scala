/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given

import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale

/**
 * Generates test fixture files (project.fcalc + project.fcalc.base64) from ExampleProject_15544.
 *
 * These files mirror EngineState.example_projet_15544 (UI module) so they always stay in sync
 * with the engine's example project. Use them for manual integration testing of the backend.
 *
 * Usage:
 *   sbt "payments/runMain afpma.firecalc.payments.GenerateExampleProjectFixture"
 */
object GenerateExampleProjectFixture:

    /** Constructs the same FireCalcYAML as EngineState.example_projet_15544 (UI module). */
    def exampleFireCalcYaml: FireCalcYAML =
        FireCalcYAMLMigrations.migrateV5ToV6(
            FireCalcYAML_V5                        (
                locale                         = Locale(Languages.Fr),
                display_units                  = DisplayUnits.SI,
                standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
                project_description            = ProjectDescr(
                    reference = ExampleProject_15544.project.reference,
                    date      = ExampleProject_15544.project.date,
                    country   = ExampleProject_15544.project.country
                ),
                local_conditions               = ExampleProject_15544.localConditions,
                stove_params                   = ExampleProject_15544.stoveParams,
                air_intake_descr               = ExampleProject_15544.conduit_air_descr,
                firebox                        = ExampleProject_15544.foyer_descr.transformInto[Firebox.Traditional],
                flue_pipe_descr                = ExampleProject_15544.accumulateur_descr,
                connector_pipe_descr           = ExampleProject_15544.conduit_raccordement_descr,
                chimney_pipe_descr             = ExampleProject_15544.conduit_fumees_descr
            )
        )

    /** Encodes the example project to a YAML string. */
    def generateYamlContent(): String =
        FireCalcYAMLMigrations.encodeToYamlTry(exampleFireCalcYaml).get

    /** Encodes the example project to a base64 string (UTF-8 YAML → base64). */
    def generateBase64Content(): String =
        java.util.Base64.getEncoder.encodeToString(generateYamlContent().getBytes("UTF-8"))

    def main(args: Array[String]): Unit =
        val wd     = os.pwd
        val resDir = wd / "modules" / "payments" / "src" / "main" / "resources"
        val fcalc  = resDir / "project.fcalc"
        val base64 = resDir / "project.fcalc.base64"

        val yamlContent   = generateYamlContent()
        val base64Content = generateBase64Content()

        os.write.over(fcalc, yamlContent   )
        os.write.over(base64, base64Content)

        println(s"[OK] Written ${yamlContent.length} bytes  → ${fcalc.toString}"  )
        println(s"[OK] Written ${base64Content.length} bytes → ${base64.toString}")

end GenerateExampleProjectFixture
