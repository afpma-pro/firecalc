/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.dto.v7.FireCalcYAML_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7 as PostFireboxPipeDescrSlot

import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544

import io.taig.babel.Languages
import io.taig.babel.Locale
import utest.*

/**
 * Shared Austrian (Ecolabeled) fixture for the payments remote-PDF suites:
 * the DTO project, its encoded YAML, the decoded round-trip, the degenerate
 * no-sizing-input variants, and the named-calculation-error assertion. Used by
 * [[RemotePdfAcceptanceSuite]] and [[RemotePdfNoSizingInputSuite]].
 */
object EcolabeledRemoteFixtures:

    // The Austrian (Ecolabeled) project fixture: an ExampleProject_15544-shaped
    // project (public Traditional post-firebox pipes + an Ecolabeled DTO
    // firebox). It is built as a DTO and encoded to YAML so the service under
    // test exercises its real parse→load→request path. (The PurchaseRoutesTest
    // V6 literal has no post-firebox pipes, so the report factory cannot
    // render a PDF from it.)
    private val ecolabeledFixture: Firebox_V4.Ecolabeled =
        Firebox_V4.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 54.cm,
            firebox_width                           = 54.cm,
            firebox_height                          = 81.3.cm,
            height_of_first_row_of_air_injectors    = 10.cm,
            door_opening_width                      = 52.cm,
            glass_width                             = 50.cm,
            glass_height                            = 40.cm,
            ash_pit_height                          = 8.cm,
            air_manifold_height                     = 11.cm,
            firebox_floor_thickness                 = 8.cm,
            firebox_inner_wall_thickness            = 6.cm,
            firebox_outer_wall_thickness            = 6.cm,
            air_column_thickness                    = 3.5.cm,
            width_between_two_air_columns_sides     = 4.5.cm,
            width_between_two_air_columns_rear      = 4.5.cm,
            reinforcement_bars_offset_in_corners_R1 = 4.5.cm,
            reinforcement_bars_offset_in_corners_R2 = 4.5.cm,
            reinforcement_bars_offset_in_corners_R3 = 4.5.cm,
            injector_height                         = 0.8.cm
        )

    private val ecolabeledProject: FireCalcYAML_V7 =
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
            stove_params                   = StoveParams.fromMaxLoadAndStoragePeriod(
                maximum_load   = 26.0.kg,
                heating_cycle  = 12.hours,
                min_efficiency = 78.percent,
                facing_type    = FacingType.WithoutAirGap
            ),
            air_intake_pipes               = FramedAirIntakePipes.fromLegacy(ExampleProject_15544.conduit_air_descr),
            firebox                        = ecolabeledFixture,
            post_firebox_pipes             = FramedPostFireboxPipes.clean(
                initialDirection = afpma.firecalc.dto.common.PipeInitialDirection(
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

    /** The encoded Austrian project the suites hand to the service under test. */
    val ecolabeledYaml = FireCalcYAMLMigrations.encodeToYamlTry(ecolabeledProject).get

    /** The decoded Austrian project the service under test receives as YAML. */
    val decodedEcolabeled = FireCalcYAMLMigrations.decodeAndMigrateTry(ecolabeledYaml).get

    /**
     * The degenerate stored project: the encoded Austrian project with its
     * `maximum_load: 26 kg` block replaced by an explicit null, i.e.
     * `sizing_method: MaxLoad` + `maximum_load: null` — the exact shape the
     * decoder maps to `Option.None` (see the `!!null` scalars in the dto
     * fixtures). Built by editing the YAML because `StoveParams`' smart
     * constructors refuse to produce a null max load directly. The block is
     * the encoder's exact output for the fixture above (key line keeps its
     * trailing space); sanity-checked in the consuming test, not at suite
     * initialization, so a broken fixture fails the test that depends on it.
     */
    val degenerateYaml: String =
        ecolabeledYaml.replace(
            "  maximum_load: \n    value: \"26.0\"\n    unit: \"kilogram\"\n",
            "  maximum_load: !!null\n"
        )

    /**
     * The NominalHeatOutput twin of [[degenerateYaml]]: the encoded Austrian
     * project with its `sizing_method` scalar flipped from `MaxLoad` to
     * `NominalHeatOutput`, i.e. `sizing_method: NominalHeatOutput` +
     * `nominal_heat_output: null` — the encoder already emits the null
     * (`fromMaxLoadAndStoragePeriod` leaves the field empty), so the flip is
     * the only surgery needed; the retained `maximum_load` block is ignored
     * by the NominalHeatOutput arm of `StoveParams`. Same one-replace idiom
     * and in-test sanity-check contract as [[degenerateYaml]].
     */
    val nominalHeatDegenerateYaml: String =
        ecolabeledYaml.replace(
            "  sizing_method: \"MaxLoad\"\n",
            "  sizing_method: \"NominalHeatOutput\"\n"
        )

    /**
     * Extract the [[CalculationReportError.CalculationError]] reason from a
     * failed report result; any other outcome fails the calling test. The
     * canonical extract point for the exact-equality assertions;
     * [[assertCalculationErrorMentions]] inlines its own match so its failure
     * message can name the expected substring.
     */
    def calculationErrorReason(
        result: Either[CalculationReportError, java.io.File]
    ): String =
        result match {
            case Left(CalculationReportError.CalculationError(reason, _)) => reason
            case other                                                    =>
                throw new java.lang.AssertionError(s"expected CalculationError, got $other")
        }

    /**
     * Assert that `result` is a [[CalculationReportError.CalculationError]]
     * whose reason mentions `expected`; any other outcome fails the test.
     * Non-CalculationError outcomes fail with the expected substring in the
     * message (the shared [[calculationErrorReason]] extractor alone cannot
     * carry it), so failures say which mention was being checked.
     */
    def assertCalculationErrorMentions(
        result  : Either[CalculationReportError, java.io.File],
        expected: String
    ): Unit =
        val reason = result match {
            case Left(CalculationReportError.CalculationError(reason, _)) => reason
            case other                                                    =>
                throw new java.lang.AssertionError(
                    s"expected CalculationError mentioning '$expected', got $other"
                )
        }
        assert(reason.contains(expected))
