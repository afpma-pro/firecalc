/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import coulomb.syntax.withUnit

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error
import afpma.firecalc.engine.impl.en15544.strict.TestScaffold_15544

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import afpma.firecalc.firebox_calc_api.*
import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * The report-factory backstop of the carrier refusal (ADR-0010, PRD seam S3).
 *
 * Ticket 01 made each remote-only carrier's `constraints` report one blocking
 * refusal verdict for a locally resolved Austrian kind; `FoldedVerdicts` folds
 * it into `FireboxErrorCustom`, which fails `validateFireboxSpecificConstraints`
 * — the validator the factory's result-validation half of
 * `loadFireCalcProject_AndValidate` runs. This suite takes that verdict at the
 * highest existing JVM consumer seam: an Austrian project loaded through
 * `FireCalcReportFactory_15544_Strict` fails with the factory's own
 * `EN15544ValidationException` (a `FireCalcReportError`) carrying the refusal
 * reason, instead of yielding a factory from which a PDF could be produced.
 * No new seam, no new error type, no test-only plumbing: the raised `Left` IS
 * the "no cached or approximate Austrian result in the report path" proof —
 * a refused load leaves no factory instance, hence no PDF, and the factory
 * caches nothing across calls.
 *
 * The refusal must not disturb what works: a non-Austrian project renders a
 * PDF report through the same seam unchanged, and the remote-wrapped Austrian
 * path (base application + `RemoteFireboxData`, the wrapping payments and the
 * browser prefetch rely on) still builds, validates, and is not refused.
 *
 * Prior art: `CarrierRefusalSuite` (the S1 verdict, fixture stubs and the
 * shipped EN refusal text), `RemoteConstraintReportingSuite` /
 * `RemoteAsResultReportSuite` (remote-wrapped applications in reports),
 * `ReferenceProjects_Suite` (match/Left/fail on factory loads).
 */
class CarrierRefusalReportFactorySuite extends AnyFreeSpec with Matchers:

    // ── DTO fixtures (stubs per CarrierRefusalSuite / RemoteConstraintReportingSuite) ──

    private def stubEcolabeledDTO: Firebox.Ecolabeled =
        Firebox.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 0.54.meters,
            firebox_width                           = 0.54.meters,
            firebox_height                          = 0.813.meters,
            height_of_first_row_of_air_injectors    = 0.10.meters,
            door_opening_width                      = 0.52.meters,
            glass_width                             = 0.50.meters,
            glass_height                            = 0.40.meters,
            ash_pit_height                          = 0.08.meters,
            air_manifold_height                     = 0.11.meters,
            firebox_floor_thickness                 = 0.08.meters,
            firebox_inner_wall_thickness            = 0.06.meters,
            firebox_outer_wall_thickness            = 0.06.meters,
            air_column_thickness                    = 0.035.meters,
            width_between_two_air_columns_sides     = 0.045.meters,
            width_between_two_air_columns_rear      = 0.045.meters,
            reinforcement_bars_offset_in_corners_R1 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R2 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R3 = 0.045.meters,
            injector_height                         = 0.008.meters
        )

    private def stubEmis: EmissionsAndEfficiencyValues_DTO =
        EmissionsAndEfficiencyValues_DTO               (
            firebox_name                = "test",
            accredited_or_notified_body = "test",
            test_reports                = Nil,
            emissions_values            = EmissionValues_DTO(
                co   = TestEmissionValue_DTO(PolluantName.CO, None, "", 0.0.percent),
                dust = TestEmissionValue_DTO(PolluantName.Dust, None, "", 0.0.percent),
                ogc  = TestEmissionValue_DTO(PolluantName.OGC, None, "", 0.0.percent),
                nox  = TestEmissionValue_DTO(PolluantName.NOx, None, "", 0.0.percent)
            )
        )

    private def stubDoor15aDTO: Firebox.Door15aFirebox_Catalog =
        Firebox.Door15aFirebox_Catalog                  (
            reference                   = "test",
            firebox_depth               = 1.0.cm,
            firebox_width               = 1.0.cm,
            firebox_height              = 1.0.cm,
            load_size_nominal           = Some(10.0.kg),
            sb                          = 2.4.withUnit[Centimeter],
            sb_min                      = None,
            sb_max                      = None,
            mb_min                      = None,
            mb_max                      = None,
            pressure_loss_table_raw     = "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16",
            expectedAirIntakePipeShapes = List(PipeShape.Circle(20.0.cm)),
            actualAirIntakePipeShape    = PipeShape.Circle(20.0.cm),
            co2_dry_nominal             = 0.1.percent,
            co2_dry_lowest              = None,
            emissions_values            = stubEmis,
            glass_area                  = 1.0.cm2,
            height_of_lowest_opening    = 1.0.cm,
            heat_output_reduced         = HeatOutputReduced.NotDefined,
            image                       = None
        )

    private def stubStoveParams: StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = 26.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    /** An Austrian project as a locally resolved (misconfigured) deployment holds it. */
    private def austrianEngineState(firebox: Firebox): FireCalcYAML_V7 =
        FireCalcYAML_V7                        (
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = TestScaffold_15544.project,
            local_conditions               = TestScaffold_15544.localConditions,
            stove_params                   = stubStoveParams,
            air_intake_pipes               = FramedAirIntakePipes.fromLegacy(TestScaffold_15544.conduit_air_descr),
            firebox                        = firebox,
            post_firebox_pipes             = FramedPostFireboxPipes(
                PipeInitialDirection.default,
                PostFireboxStartPosition.Auto,
                slots = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (TestScaffold_15544.accumulateur_descr        ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(TestScaffold_15544.conduit_raccordement_descr),
                    PostFireboxPipeDescrSlot.ChimneySlot  (TestScaffold_15544.conduit_fumees_descr      )
                )
            )
        )

    /** The public ExampleProject_15544 (a Traditional firebox) as a DTO. */
    private def traditionalEngineState: FireCalcYAML_V7 =
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

    private def stubReportData: FireboxReportData =
        FireboxReportData    (
            fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
            emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
            combustionAirPipeRows  = Nil,
            fireboxPipeRows        = Nil,
            ecolabeledOutputs      = None
        )

    /** The wire payload a correctly configured deployment serves for these kinds. */
    private def remoteDataWith(verdicts: List[ConstraintVerdict]): RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ] =
        RemoteFireboxData      (
            fireboxContribution       = Validated.valid(FireboxContribution.Without),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = verdicts,
            reportData                = stubReportData
        )

    private def satisfied(term: ConstraintTerm): ConstraintVerdict =
        ConstraintVerdict(term, term.toString, ConstraintDescriptor.Opaque, ConstraintOutcome.Satisfied)

    /**
     * The shipped EN refusal text with the EN kind name interpolated — the
     * hand-written mirror of `builder_errors.no_calculator_configured` (same
     * literal `CarrierRefusalSuite` pins at the engine seam). The constraint
     * fold resolves the verdict under `Locale.en` by design
     * (`en15544_common_application.scala`), so this is what the factory's
     * validation errors carry regardless of the factory locale.
     */
    private def refusalEn(kindName: String): String =
        s"No calculator is configured for '$kindName' — the firebox calculation service is not available in this deployment"

    /**
     * Load an Austrian project through the factory's validating entry and
     * require the existing validation exception, naming the refused kind.
     */
    private def assertFactoryRefuses(fc: FireCalcYAML_V7, kindName: String): Unit =
        given Locale = Locales.en
        val loaded   = FireCalcReportFactory_15544_Strict
            .init()
            .loadFireCalcProject_AndValidate(StoveProjectDescr.makeFor_EN15544_Strict(fc))
        loaded match
            // A `Left` is also the "no PDF, no cached or approximate Austrian
            // result" witness: no factory instance is returned to render one.
            case Left(err: EN15544ValidationException) =>
                withClue(s"refusal reason missing from validation errors ${err.validationErrors}: ") {
                    err.validationErrors.exists(_.contains(refusalEn(kindName))) shouldBe true
                }
            case Left(err)                             =>
                fail(
                    s"expected EN15544ValidationException refusing '$kindName', " +
                        s"got ${err.getClass.getSimpleName}: ${err.getMessage}"
                )
            case Right(_)                              =>
                fail(
                    s"the report factory must refuse a locally resolved '$kindName' project, but it loaded a report factory"
                )

    /**
     * The wrapping the remote flow relies on (RemoteAsResultReportSuite /
     * RemoteConstraintReportingSuite pattern): the refusal sits in the
     * carriers' `constraints`, never the contribution methods, so the base
     * application still builds and the remote-wrapped validation reports
     * only the wire-sourced verdicts.
     */
    private def assertNotRefused(fc: FireCalcYAML_V7, kindName: String): Unit =
        given Locale = Locales.en
        val loader   = FireCalcYAML_Loader(fc)

        // 1. The base build the remote wrapping depends on stays valid.
        val baseV = loader.make_en15544_Strict_Application
        withClue("base application build must not be disturbed by the carrier refusal: ") {
            baseV.isValid shouldBe true
        }

        // 2. The remote-wrapped application validates with the wire verdicts.
        val remoteAppV      = loader.make_en15544_Strict_Application(
            remoteData = Some(remoteDataWith(List(satisfied(ConstraintTerm.NominalLoadInRange))))
        )
        remoteAppV.isValid shouldBe true
        val app             = remoteAppV.toOption.get
        val fireboxSpecific = app.primary.validateFireboxSpecificConstraints
        val cited           = app.primary.validateCitedConstraints
        withClue("a satisfied wire verdict must not fail the validators the refusal rides: ") {
            fireboxSpecific.isValid shouldBe true
            cited.isValid shouldBe true
        }

        // 3. The whole-project gate (the factory's own validation half)
        //    surfaces no refusal — at most the scaffold's pipe physics,
        //    which is pinned at the engine seam, not by this ticket.
        app.validateResultsExceptEmissionsValues(TestScaffold_15544.project.country) match
            case Valid(_)   => succeed
            case Invalid(e) =>
                withClue("remote-wrapped gate must not carry the refusal reason: ") {
                    e.toList
                        .map(ShowUsingLocale[MCalc_Error].show)
                        .foreach(_ should not include refusalEn(kindName))
                }

    "Austrian projects through the report factory" - {

        "an Ecolabeled project fails with the existing validation exception naming the missing calculator" in {
            assertFactoryRefuses(austrianEngineState(stubEcolabeledDTO), "Austrian Eco firebox")
        }

        "a Door15aCatalog project fails with the existing validation exception naming the missing calculator" in {
            assertFactoryRefuses(austrianEngineState(stubDoor15aDTO), "15a door")
        }
    }

    "Non-Austrian projects through the same seam" - {

        // The untouched success path at the highest existing consumer seam: the
        // factory loads the project AND renders the report PDF. The payments
        // mirror of this path (`PdfReportGenerationTest`) and the routing
        // evidence (`RemotePdfAcceptanceSuite`) stay byte-unchanged.
        "a Traditional project still loads and renders a PDF report, not refused" in {
            given Locale     = Locales.en
            val stoveProject = StoveProjectDescr.makeFor_EN15544_Strict(traditionalEngineState)
            FireCalcReportFactory_15544_Strict
                .init()
                .loadFireCalcProject_AndValidate(stoveProject) match
                case Right(factory) =>
                    factory.makePDFBuffer                 (
                        isDraft                  = false,
                        checkPressureReq13384    = false,
                        checkTemperatureReq13384 = false
                    ) match
                        case Right(pdfBytes) =>
                            pdfBytes.length should be > 0
                        case Left(err)       =>
                            fail(
                                s"Traditional report PDF generation failed: ${err.getClass.getSimpleName}: ${err.getMessage}"
                            )
                case Left(err)      =>
                    fail(
                        s"the report factory must not refuse a Traditional project, " +
                            s"got ${err.getClass.getSimpleName}: ${err.getMessage}"
                    )
        }
    }

    "Remote-wrapped Austrian projects (base app + remote data)" - {

        "an Ecolabeled application still builds and is not refused once remote data backs it" in {
            assertNotRefused(austrianEngineState(stubEcolabeledDTO), "Austrian Eco firebox")
        }

        "a Door15aCatalog application still builds and is not refused once remote data backs it" in {
            assertNotRefused(austrianEngineState(stubDoor15aDTO), "15a door")
        }
    }

end CarrierRefusalReportFactorySuite
