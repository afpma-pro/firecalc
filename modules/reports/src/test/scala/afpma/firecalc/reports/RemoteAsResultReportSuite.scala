/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.alg.en15544.{FireboxContribution, OpPoint, RemoteFireboxData}
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.api.WireConversions.{fromWireResult, toWireResult}
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given

import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict

import cats.data.NonEmptyList
import cats.data.Validated
import cats.syntax.validated.*

import io.github.fatihcatalkaya.javatypst.JavaTypst
import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import io.taig.babel.Locales

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Golden-report test for round-3 decision D9: an `AsResult`-backed (black-box)
 * firebox renders its gas-path table as EXACTLY ONE aggregate row per pipe,
 * sourced from the `PipeResult` members (`lengthSum`, `heightSum`, ζ, `pu`,
 * `pRs`, `pRg`, `ph`, temperatures, velocities), with the descriptive-only
 * columns blank and no server-sent detail rows.
 *
 * Replaces the former Ecolabeled/CasType-C3 version removed by ticket 18: the
 * rendering mechanism under test is generic (it applies to ANY black-box
 * firebox contribution), so it is exercised here with the public
 * ExampleProject_15544 (a Traditional firebox) and a SYNTHETIC remote grid —
 * hand-built `PipeResult` values round-tripped through the wire representation
 * (`toWireResult` → `fromWireResult`), exactly as `BlackBoxAsResultSuite`
 * builds continuation state. No proprietary fixture, transformer, or engine
 * symbol participates: the withheld IP (per-section detail) never enters the
 * test, and the aggregate members are test data, not a private solve.
 *
 * The remote-backed application is built by wrapping the locally-built
 * application — via the loader's `withRemoteData` path — with a
 * `RemoteFireboxData` whose contributions are the operating-point-keyed
 * `AsResult` grids covering `OpPoint.all`.
 *
 * The fresh PDF lands under `target/test-reports/` (never in resources); the
 * tracked reference copy lives in `generated-reports/` and is compared below
 * (comparison is skipped until a golden is committed — see PdfGoldenAssert).
 */
class RemoteAsResultReportSuite extends AnyFreeSpec with Matchers:

    /** A full EN 15544 project state (ExampleProject_15544 as a DTO). */
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

    /**
     * One synthetic grid entry: a hand-built firebox-path `PipeResult` whose
     * members are a fixed, valid continuation state (the contract `AsResult`
     * owes). The values are test data standing in for a server solve.
     */
    private def stubPipeResult: PipeResult =
        PipeResult.fromValues                  (
            pt                   = FireboxPipeT,
            lengthSum            = 1.meters,
            heightSum            = 1.meters,
            pd                   = None,
            ζ                    = None,
            pu                   = 0.pascals.validNel,
            pRs                  = 0.pascals,
            pRg                  = 0.pascals,
            ph                   = 2.pascals,
            gas_temp_start       = 250.degreesCelsius,
            gas_temp_middle      = 240.degreesCelsius,
            gas_temp_end         = 230.degreesCelsius,
            density_middle       = Some(0.8.kg_per_m3),
            density_mean         = Some(0.85.kg_per_m3),
            v_start              = Some(1.5.m_per_s),
            v_end                = Some(1.4.m_per_s),
            last_density_mean    = Some(0.9.kg_per_m3),
            last_density_middle  = Some(0.95.kg_per_m3),
            last_velocity_mean   = Some(1.6.m_per_s),
            last_velocity_middle = Some(1.7.m_per_s)
        )

    "AsResult-backed report renders one aggregate row per black-box pipe" in {

        given io.taig.babel.Locale = Locales.fr

        val loader = FireCalcYAML_Loader(buildEngineState)

        // The base (local) application must build cleanly — the wrapper
        // delegates all non-firebox behaviour to it.
        val localAppV = loader.make_en15544_Strict_Application
        localAppV.isValid shouldBe true

        // ── Synthetic remote grids ────────────────────────────────────────
        // Round-trip each hand-built `PipeResult` through the wire
        // representation (`toWireResult` → `fromWireResult`) to faithfully
        // simulate what the client receives: `WirePipeResult` carries ONLY
        // the aggregate members (the per-section detail is the withheld IP),
        // so decoding yields a `PipeResult.WithoutSections` — the black-box
        // result D9 renders as one aggregate row. The grid covers
        // `OpPoint.all`, the full set of operating points the standard
        // evaluates.
        def roundTrip(pr: afpma.firecalc.engine.models.PipeResult): afpma.firecalc.engine.models.PipeResult =
            fromWireResult(toWireResult(pr))

        val fireboxGrid = NonEmptyList.fromListUnsafe(
            OpPoint.all.map(op => (op, roundTrip(stubPipeResult)))
        )

        val remoteData = RemoteFireboxData[
            FireboxPipe_Module_15544.FullDescr,
            CombustionAirPipe_Module_15544.FullDescr
        ]      (
            fireboxContribution       = Validated.valid(FireboxContribution.AsResult(fireboxGrid)),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = Nil,
            reportData                = afpma.firecalc.firebox_calc_api.FireboxReportData(
                fireboxDescription     = afpma.firecalc.firebox_calc_api.WireTable(None, None, Nil),
                emissionsAndEfficiency = afpma.firecalc.firebox_calc_api.WireTable(None, None, Nil),
                combustionAirPipeRows  = Nil,
                fireboxPipeRows        = Nil,
                ecolabeledOutputs      = None
            )
        )

        // ── Remote-backed application via the loader's withRemoteData path ─
        val remoteAppV = loader.make_en15544_Strict_Application(remoteData = Some(remoteData))
        remoteAppV.isValid shouldBe true
        val remoteApp  = remoteAppV.toOption.get

        // ── Build the Typst report from the remote-backed application ─────
        val typstFactory =
            new TypstReportFactory_15544_Strict                 (
                isDraft                  = true,
                checkPressureReq13384    = false,
                checkTemperatureReq13384 = false
            ):
                override val en15544_app            : EN15544_Strict_Application         = remoteApp
                override val stove_proj_15544_strict: StoveProjectDescr_15544_Strict_Alg =
                    loader.stoveProjectDescr_EN15544_Strict
                override val atParams                  = remoteApp.primary.asInstanceOf[en15544_app.AtParams]
                override protected def currentDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)

        val typstString = typstFactory.build()
        typstString.length should be > 0

        // ── D9 honesty checks on the rendered table ───────────────────────
        // The black-box firebox pipe renders as EXACTLY ONE labelled
        // aggregate row (id/name = the localized "subtotal" marker), sourced
        // from the `PipeResult` aggregate members. The descriptive-only
        // columns a black-box result cannot know render blank ("-"), and NO
        // detail rows are emitted for it (detail rows are the withheld IP).
        // The locally-computed post-firebox pipes keep their detail rows —
        // they are not black-box.
        val tableRows = typstString.linesIterator
            .map(_.trim)
            .filter(l => l.startsWith("[") && l.endsWith("],"))
            .toList

        // Exactly one aggregate row for the black-box firebox pipe.
        val aggregateRows = tableRows.filter(_.contains("[sous-total]"))
        withClue(
            s"aggregate rows (expected exactly 1 — the black-box firebox pipe):\n${aggregateRows.mkString("\n")}\n"
        ) {
            aggregateRows.size shouldBe 1
        }

        // The firebox aggregate row: type "Foyer", labelled, with the standing
        // pressure (pH) populated and the descriptive geometry columns blank.
        val fireboxAgg = aggregateRows.find(_.startsWith("[Foyer]"))
        fireboxAgg shouldBe defined
        withClue("firebox aggregate row must carry a populated pH: ") {
            fireboxAgg.get should include("[2.00 Pa]")
        }

        // No detail rows for the black-box pipe: every "Foyer" row is the
        // aggregate row (no per-section rows like "[Foyer], [0], …").
        val fireboxRows = tableRows.filter(_.startsWith("[Foyer]"))
        withClue("firebox must have no detail rows: ") {
            fireboxRows.size shouldBe 1
        }

        // ── Render to PDF and compare against the golden ──────────────────
        val pdfBytes = JavaTypst.render(typstString)
        pdfBytes.length should be > 0

        val outPath = Paths.get(
            "modules/reports/target/test-reports",
            "remote-asresult-report.pdf"
        )
        Files.createDirectories(outPath.getParent)
        Files.write            (outPath, pdfBytes)

        val outFile = outPath.toFile
        outFile.exists() shouldBe true
        outFile.length() should be > 0L

        val goldenPath = Paths.get(
            "modules/reports/src/test/resources/generated-reports",
            "remote-asresult-report.pdf"
        )
        PdfGoldenAssert.assertMatchesGolden(outPath, goldenPath, info(_))
    }

end RemoteAsResultReportSuite
