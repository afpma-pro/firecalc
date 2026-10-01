/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.OutsideAirLocationInHeater
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.biblio.kov.firebox_emissions.Standing_Standard_Burning_Firebox
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import cats.data.Validated

import coulomb.syntax.withUnit

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * The remote-only carriers refuse a locally resolved Austrian kind (ADR-0010).
 *
 * `Ecolabeled` and `Door15aCatalog` have no in-process calculator in the public
 * artifact; a deployment that resolves them to `Local` used to produce a silent
 * degenerate result — no Austrian outputs, no violation. Each carrier's
 * `constraints` now reports one blocking refusal verdict, which `FoldedVerdicts`
 * folds into `FireboxErrorCustom`, so the application fails validation at the
 * seam its consumers already read.
 *
 * Taken at the application build and validation seam
 * (`RemoteConstraintReportingSuite` is the prior art for the same vocabulary on
 * the remote side): the refusal fires locally, the non-Austrian kinds are
 * untouched, and an Austrian application carrying remote data reports only the
 * wire-sourced verdicts.
 */
class CarrierRefusalSuite extends AnyFreeSpec with Matchers:

    // ── DTO fixture (stub pattern per RemoteConstraintReportingSuite) ──

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

    private def stubTraditionalDTO: Firebox.Traditional =
        Firebox.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.44.meters,
            firebox_width                         = 0.42.meters,
            firebox_height                        = 0.78.meters,
            height_of_lowest_opening              = 0.05.meters,
            pressure_loss_coefficient_from_door   = 0.3.unitless,
            total_air_intake_surface_area_on_door = 150.0.cm2,
            glass_width                           = 0.15.meters,
            glass_height                          = 0.20.meters
        )

    /** Canonical PRSE geometry, declaring the 200 mm circular intake the scaffold draws. */
    private def stubAfpmaPrseDTO: Firebox.AFPMA_PRSE =
        Firebox.AFPMA_PRSE                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            outside_air_location_in_heater        = OutsideAirLocationInHeater.FromBottom,
            outside_air_conduit_shape             = PipeShape.Circle(20.0.cm),
            firebox_depth                         = 0.5.meters,
            firebox_width                         = 0.5.meters,
            firebox_height                        = 0.5.meters,
            height_of_first_row_of_air_injectors  = 0.1.meters,
            glass_width                           = 0.2.meters,
            glass_height                          = 0.2.meters,
            ash_pit_height                        = 0.1.meters,
            floor_thickness                       = 0.05.meters,
            combustion_air_manifold_height        = 0.1.meters,
            outside_air_inlet_lip                 = 0.05.meters,
            height_of_air_feed_to_columns         = 0.1.meters,
            number_of_air_columns_feeding_firebox = 4,
            number_of_air_columns_feeding_door    = 2
        )

    private def stubSingleTestedDTO: Firebox.SingleTested =
        Firebox.SingleTested                             (
            reference                              = "test",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            test_standard                          = Firebox.TestStandard.EN_13229,
            firebox_depth                          = 0.44.meters,
            firebox_width                          = 0.42.meters,
            firebox_height                         = 0.6.meters,
            ash_pit_height                         = 0.05.meters,
            is_glass_surface_ratio_below_one_fifth = true,
            glass_area                             = 0.25.squareMeters,
            mean_firebox_temperature               = Some(700.degreesCelsius),
            t_burnout                              = Some(550.degreesCelsius),
            efficiency_nominal                     = 80.percent,
            efficiency_reduced                     = None,
            heat_output_reduced                    = HeatOutputReduced.NotDefined,
            minimum_fuel_mass                      = Some(10.kg),
            maximum_fuel_mass                      = 18.5.kg,
            air_fuel_ratio_nominal                 = 2.5.unitless,
            air_fuel_ratio_lowest                  = None,
            co2_dry_nominal                        = 8.percent,
            co2_dry_lowest                         = None,
            pellets_load_burn_duration             = Some(75.minutes),
            emissions_values                       = stubEmis
        )

    /**
     * `CustomForLab` has no DTO subtype, so it cannot be built through the YAML
     * loader; its guard is taken at the engine interface the fold consumes.
     */
    private def customForLabModel: Firebox_15544.Traditional.CustomForLab =
        Firebox_15544.Traditional.CustomForLab               (
            reference                = LocalizedString(_ => "carrier-refusal"),
            type_of_appliance        = TypeOfAppliance.WoodLogs,
            emissions_values         = Standing_Standard_Burning_Firebox,
            pn_reduced               = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            dimensions               = Firebox_15544.Dimensions(
                base   = Firebox_15544.Dimensions.Base.Squared(width = 39.0.cm, depth = 55.0.cm),
                height = 58.3.cm
            ),
            glass_area               = 200.0.cm2,
            height_of_lowest_opening = 5.0.cm
        )

    private def stubStoveParams(maxLoad: Double): StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = maxLoad.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    private def buildEngineState(firebox: Firebox, maxLoad: Double = 26.0): FireCalcYAML_V7 =
        FireCalcYAML_V7                        (
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = TestScaffold_15544.project,
            local_conditions               = TestScaffold_15544.localConditions,
            stove_params                   = stubStoveParams(maxLoad),
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

    /**
     * The application as a locally resolved deployment builds it. The build
     * itself stays valid — the refusal is a verdict of the constraint fold, not
     * an input error — which is what keeps the remote prefetch alive (ADR-0010).
     */
    private def localApp(firebox: Firebox, maxLoad: Double = 26.0): EN15544_Strict_Application =
        val appV = FireCalcYAML_Loader(buildEngineState(firebox, maxLoad)).make_en15544_Strict_Application
        appV.isValid shouldBe true
        appV.toOption.get

    private val noPipeContext =
        FireboxConstraintContext(mB = 0.0.kg, flow_rate = None, airIntakePipeShape = None)

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

    private def violated(term: ConstraintTerm, message: String): ConstraintVerdict =
        ConstraintVerdict(
            term,
            term.toString,
            ConstraintDescriptor.Opaque,
            ConstraintOutcome.Violated("TestCode", message, blocking = true)
        )

    private def remoteWrapped(
        firebox : Firebox,
        verdicts: List[ConstraintVerdict]
    ): EN15544_Strict_Application =
        EN15544_Strict_Application.withRemoteData(localApp(firebox), remoteDataWith(verdicts))

    /** The refusal verdicts among `verdicts`; empty means the carrier did not refuse. */
    private def refusals(verdicts: List[ConstraintVerdict]): List[ConstraintVerdict] =
        verdicts.filter:
            case ConstraintVerdict(_, "no_calculator_configured", _, _) => true
            case _                                                      => false

    /** The shipped EN refusal text, with the EN kind name interpolated. */
    private def refusalEn(kindName: String): String =
        s"No calculator is configured for '$kindName' — the firebox calculation service is not available in this deployment"

    /** The shipped FR refusal text, with the FR kind name interpolated. */
    private def refusalFr(kindName: String): String =
        s"Aucun calculateur n'est configuré pour '$kindName' — le service de calcul du foyer n'est pas disponible dans ce déploiement"

    "Locally resolved Ecolabeled" - {

        "the carrier reports one blocking refusal verdict naming the kind" in {
            given Locale = Locale(Languages.En)
            val carrier  = RemoteOnlyFireboxes_Strict.EcolabeledCarrier(stubEcolabeledDTO)
            val verdicts = RemoteOnlyFireboxes_Strict.ecolabeledRemoteOnlyEngine.constraints(
                carrier,
                ctx  = None,
                fctx = noPipeContext
            )

            verdicts should have size 1
            verdicts.head.term shouldBe ConstraintTerm.Custom
            verdicts.head.descriptor shouldBe ConstraintDescriptor.Opaque
            verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                code     = "no_calculator_configured",
                message  = refusalEn("Austrian Eco firebox"),
                blocking = true
            )
        }

        "the application fails the firebox-specific validation with the refusal" in {
            given Locale = Locale(Languages.En)
            val app      = localApp(stubEcolabeledDTO)

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List(refusalEn("Austrian Eco firebox"))
            app.validateResultsExceptEmissionsValues(TestScaffold_15544.project.country).isValid shouldBe false
        }

        "the refusal is not reported as EN 15544 cited constraint physics" in {
            localApp(stubEcolabeledDTO).primary.validateCitedConstraints.isValid shouldBe true
        }

        "the refusal is shipped in French as well" in {
            given Locale = Locale(Languages.Fr)
            val carrier  = RemoteOnlyFireboxes_Strict.EcolabeledCarrier(stubEcolabeledDTO)
            val verdicts = RemoteOnlyFireboxes_Strict.ecolabeledRemoteOnlyEngine.constraints(
                carrier,
                ctx  = None,
                fctx = noPipeContext
            )

            verdicts should have size 1
            verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                code     = "no_calculator_configured",
                message  = refusalFr("Foyer Ecolabellisé"),
                blocking = true
            )
        }
    }

    "Locally resolved Door15aCatalog" - {

        "the carrier reports one blocking refusal verdict naming the kind" in {
            given Locale = Locale(Languages.En)
            val carrier  = RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier(stubDoor15aDTO)
            val verdicts = RemoteOnlyFireboxes_Strict.door15aCatalogRemoteOnlyEngine.constraints(
                carrier,
                ctx  = None,
                fctx = noPipeContext
            )

            verdicts should have size 1
            verdicts.head.term shouldBe ConstraintTerm.Custom
            verdicts.head.descriptor shouldBe ConstraintDescriptor.Opaque
            verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                code     = "no_calculator_configured",
                message  = refusalEn("15a door"),
                blocking = true
            )
        }

        "the application fails the firebox-specific validation with the refusal" in {
            given Locale = Locale(Languages.En)
            val app      = localApp(stubDoor15aDTO)

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List(refusalEn("15a door"))
            app.validateResultsExceptEmissionsValues(TestScaffold_15544.project.country).isValid shouldBe false
        }

        "the refusal is not reported as EN 15544 cited constraint physics" in {
            localApp(stubDoor15aDTO).primary.validateCitedConstraints.isValid shouldBe true
        }

        "the refusal is shipped in French as well" in {
            given Locale = Locale(Languages.Fr)
            val carrier  = RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier(stubDoor15aDTO)
            val verdicts = RemoteOnlyFireboxes_Strict.door15aCatalogRemoteOnlyEngine.constraints(
                carrier,
                ctx  = None,
                fctx = noPipeContext
            )

            verdicts should have size 1
            verdicts.head.outcome shouldBe ConstraintOutcome.Violated    (
                code     = "no_calculator_configured",
                message  = refusalFr("Foyer 15a"),
                blocking = true
            )
        }
    }

    // ── The refusal must not fire where a calculator does exist ────────

    "Non-Austrian kinds report no refusal" - {

        // The three loader-built kinds are built exactly as the Austrian
        // applications above are, and the assertion is taken at
        // `validateFireboxSpecificConstraints` — the validator the refusal lands in
        // (a `ConstraintTerm.Custom` verdict is firebox-specific). The whole-project
        // gate also runs the EN 15544 pipe physics (injector velocities, pressure
        // requirement), which these minimal stubs do not claim to satisfy: full-gate
        // validity for the non-Austrian kinds stays pinned by
        // `SingleTestedValidationSuite` and
        // `TraditionalFireboxConstraintDispatchSuite`, which this change leaves
        // byte-unchanged.
        "traditional" in {
            val app = localApp(stubTraditionalDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "afpma-prse" in {
            val app = localApp(stubAfpmaPrseDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "single-tested" in {
            val app = localApp(stubSingleTestedDTO, maxLoad = 18.5)
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "custom-for-lab" in {
            // No DTO subtype, hence no loader path for this kind: the guard is
            // taken at the engine interface the constraint fold consumes.
            given Locale = Locale(Languages.En)
            val entry    = FireboxEngineRegistry_Strict.all.find(_.kind == FireboxKind.CustomForLab).get
            val firebox  = entry.narrow(customForLabModel)

            firebox.isDefined shouldBe true
            refusals(entry.instance.constraints(firebox.get, ctx = None, fctx = noPipeContext)) shouldBe empty
        }
    }

    "Austrian kinds carrying remote data" - {

        "the Ecolabeled application stays valid with satisfied wire verdicts" in {
            val app = remoteWrapped(stubEcolabeledDTO, List(satisfied(ConstraintTerm.NominalLoadInRange)))
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "the Ecolabeled application reports only the wire-sourced verdicts" in {
            given Locale = Locale(Languages.En)
            val app      = remoteWrapped(
                stubEcolabeledDTO,
                List(violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range"))
            )

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List("nominal load out of range")
        }

        "the Door15aCatalog application stays valid with satisfied wire verdicts" in {
            val app = remoteWrapped(stubDoor15aDTO, List(satisfied(ConstraintTerm.NominalLoadInRange)))
            app.primary.validateFireboxSpecificConstraints.isValid shouldBe true
        }

        "the Door15aCatalog application reports only the wire-sourced verdicts" in {
            given Locale = Locale(Languages.En)
            val app      = remoteWrapped(
                stubDoor15aDTO,
                List(violated(ConstraintTerm.NominalLoadInRange, "nominal load out of range"))
            )

            app.primary.validateFireboxSpecificConstraints.swap.toOption.get.toList
                .map(ShowUsingLocale[MCalc_Error].show) shouldBe List("nominal load out of range")
        }
    }
