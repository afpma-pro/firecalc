/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544

import cats.data.Validated

import coulomb.syntax.withUnit

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale

/**
 * The carrier-refusal fixture family (ADR-0010), shared by the refusal suites:
 * `CarrierRefusalSuite` (the engine seam) and `CarrierRefusalReportFactorySuite`
 * (the report-factory seam, via the test->test dependency) — both read these
 * stubs instead of carrying near-identical copies. Lives beside
 * `TestScaffold_15544`, from which it takes the generic project
 * scaffolding, but deliberately separate from it: the scaffold is
 * Austrian-free by contract, and these stubs are the Austrian DTOs the
 * refusal fires on.
 *
 * The wording literals are hand-written mirrors of the shipped
 * `builder_errors.no_calculator_configured` copy (EN and FR, with the
 * `firebox_names` kind interpolations): a silent wording drift must fail
 * here, not in a browser.
 */
object RefusalStubs_15544:

    /** The minimal emissions record the DTO stubs share. */
    def stubEmis: EmissionsAndEfficiencyValues_DTO =
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

    def stubEcolabeledDTO: Firebox.Ecolabeled =
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

    def stubDoor15aDTO: Firebox.Door15aFirebox_Catalog =
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

    def stubReportData: FireboxReportData =
        FireboxReportData    (
            fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
            emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
            combustionAirPipeRows  = Nil,
            fireboxPipeRows        = Nil,
            ecolabeledOutputs      = None
        )

    /** The wire payload a correctly configured deployment serves for these kinds. */
    def remoteDataWith(verdicts: List[ConstraintVerdict]): RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ] =
        RemoteFireboxData      (
            fireboxContribution       = Validated.valid(FireboxContribution.Without),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = verdicts,
            reportData                = stubReportData
        )

    def satisfied(term: ConstraintTerm): ConstraintVerdict =
        ConstraintVerdict(term, term.toString, ConstraintDescriptor.Opaque, ConstraintOutcome.Satisfied)

    def violated(term: ConstraintTerm, message: String): ConstraintVerdict =
        ConstraintVerdict(
            term,
            term.toString,
            ConstraintDescriptor.Opaque,
            ConstraintOutcome.Violated("TestCode", message, blocking = true)
        )

    private def stubStoveParams(maxLoad: Double): StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = maxLoad.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    /** An Austrian project as a locally resolved (misconfigured) deployment holds it. */
    def austrianEngineState(firebox: Firebox, maxLoad: Double = 26.0): FireCalcYAML_V7 =
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

    /** The shipped EN refusal text, with the EN kind name interpolated. */
    def refusalEn(kindName: String): String =
        s"No calculator is configured for '$kindName' — the firebox calculation service is not available in this deployment"

    /**
     * The shipped FR refusal text, with the FR kind name interpolated — the FR
     * twin of [[refusalEn]] in `builder_errors.no_calculator_configured`. The
     * constraint fold resolves the EN verdict under `Locale.en` by design
     * (`en15544_common_application.scala`), so EN is what the fold's verdicts
     * carry; FR is what the FR-localized surfaces render.
     */
    def refusalFr(kindName: String): String =
        s"Aucun calculateur n'est configuré pour '$kindName' — le service de calcul du foyer n'est pas disponible dans ce déploiement"

end RefusalStubs_15544
