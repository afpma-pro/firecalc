/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import java.nio.file.{Files, Paths}
import java.time.LocalDateTime

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
import afpma.firecalc.engine.testutil.SnapshotAssert

import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict

import io.github.fatihcatalkaya.javatypst.JavaTypst
import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import io.taig.babel.Locales

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test: generate a PDF report for the public EN 15544 example project
 * (a Traditional firebox) and verify the output file exists and is non-empty.
 *
 * Replaces the former CasType-C3 (Ecolabeled) smoke test removed by ticket 18:
 * the proprietary Austrian fixture now lives in the private repository, and
 * the generic report-rendering pipeline this test defends is exercised here
 * with a non-proprietary firebox kind.
 *
 * The application is built through [[FireCalcYAML_Loader]] — the same path
 * the payments backend uses — so the pipe chain carries the DTO framing
 * (initial direction / position) the report's pressure-requirement section
 * needs.
 *
 * Bypasses the report factory validation gate so that fixtures with
 * intentional validation warnings (flue gas velocity, pressure) still
 * produce a PDF.
 */
class ExampleProjectPdfReportSuite extends AnyFreeSpec with Matchers:

    /** The example project as a full DTO state (mirrors the payments fixture). */
    private def buildEngineState: FireCalcYAML_V7 =
        FireCalcYAML_V7                        (
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
            air_intake_pipes               = FramedAirIntakePipes.fromLegacy(ExampleProject_15544.conduit_air_descr),
            firebox                        = ExampleProject_15544.foyer_descr.transformInto[Firebox.Traditional],
            post_firebox_pipes             = FramedPostFireboxPipes.clean(
                initialDirection = PipeInitialDirection(
                    azimuth     = AzimuthDirection.Left,
                    inclination = InclinationDirection.Horizontal
                ),
                initialPosition  = PostFireboxStartPosition.Auto,
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (ExampleProject_15544.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(ExampleProject_15544.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (ExampleProject_15544.conduit_fumees_descr      )
                )
            )
        )

    "Example project (Traditional) - PDF report generation" in {

        given io.taig.babel.Locale = Locales.en

        // Build the application through the loader (the production path)
        val loader = FireCalcYAML_Loader(buildEngineState)
        val appOpt = loader.make_en15544_Strict_Application.toOption
        appOpt shouldBe defined
        val app: EN15544_Strict_Application = appOpt.get

        // Build the Typst report directly (no validation gate)
        val typstFactory =
            new TypstReportFactory_15544_Strict                 (
                isDraft                  = true,
                checkPressureReq13384    = false,
                checkTemperatureReq13384 = false
            ):
                override val en15544_app            : EN15544_Strict_Application         = app
                override val stove_proj_15544_strict: StoveProjectDescr_15544_Strict_Alg =
                    loader.stoveProjectDescr_EN15544_Strict
                override val atParams                  = app.primary.asInstanceOf[en15544_app.AtParams]
                override protected def currentDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)

        val typstString = typstFactory.build()
        typstString.length should be > 0

        // Assert Typst snapshot (deterministic text diff)
        SnapshotAssert.assertMatches(typstString, snapshotPath("example-project-traditional.typ"))

        // Render to PDF
        val pdfBytes = JavaTypst.render(typstString)
        pdfBytes.length should be > 0

        // Write to a tracked resource for git history
        val outPath = Paths
            .get(
                getClass.getClassLoader
                    .getResource("generated-reports")
                    .toURI
            )
            .resolve("example-project-traditional.pdf")

        Files.createDirectories(outPath.getParent)
        Files.write            (outPath, pdfBytes)

        val outFile = outPath.toFile
        outFile.exists() shouldBe true
        outFile.length() should be > 0L

    }

    private def snapshotPath(name: String): java.nio.file.Path =
        Paths
            .get(
                getClass.getClassLoader
                    .getResource("snapshots")
                    .toURI
            )
            .resolve("ExampleProjectPdfReportSuite")
            .resolve(name)

end ExampleProjectPdfReportSuite
