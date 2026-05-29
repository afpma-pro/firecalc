/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import io.scalaland.chimney.dsl.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v6.FireCalcYAML_V6
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.cas_types.en15544.v20241001.ExampleProject_15544
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.LocalRegulations
import afpma.firecalc.units.coulombutils.*

import io.taig.babel.Locale
import io.taig.babel.Languages

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SingleTestedValidationSuite extends AnyFreeSpec with Matchers:

    // ── DTO fixture helpers ──────────────────────────────────────────

    private def stubEmissionsDTO(
        coMgNm3: Option[Double]
    ): EmissionsAndEfficiencyValues_DTO =
        val base = LocalRegulations.fr.wood_logs
        EmissionsAndEfficiencyValues_DTO               (
            firebox_name                = "Test firebox",
            accredited_or_notified_body = "Test Lab",
            test_reports                = Nil,
            emissions_values            = EmissionValues_DTO(
                co   = base.max_co.get.copy(valueO = coMgNm3.map(_.mg_per_Nm3)).transformInto[TestEmissionValue_DTO],
                dust = base.max_dust.get
                    .copy(valueO = base.max_dust.get.valueO.map(v => (v.value / 2.0).mg_per_Nm3))
                    .transformInto[TestEmissionValue_DTO],
                ogc  = base.max_ogc.get
                    .copy(valueO = base.max_ogc.get.valueO.map(v => (v.value / 2.0).mg_per_Nm3))
                    .transformInto[TestEmissionValue_DTO],
                nox  = base.max_nox.get
                    .copy(valueO = base.max_nox.get.valueO.map(v => (v.value / 2.0).mg_per_Nm3))
                    .transformInto[TestEmissionValue_DTO]
            )
        )

    private def stubSingleTestedDTO(
        maxFuelMass                               : Double,
        coMgNm3                                   : Option[Double] = None,
        fireboxDepth                              : Length         = 0.44.meters,
        fireboxWidth                              : Length         = 0.42.meters,
        fireboxHeight                             : Length         = 0.6.meters
    ): Firebox.SingleTested =
        Firebox.SingleTested(
            reference                              = "Test SingleTested",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            test_standard                          = Firebox.TestStandard.EN_13229,
            firebox_depth                          = fireboxDepth,
            firebox_width                          = fireboxWidth,
            firebox_height                         = fireboxHeight,
            ash_pit_height                         = 0.05.meters,
            is_glass_surface_ratio_below_one_fifth = true,
            glass_area                             = 0.25.squareMeters,
            mean_firebox_temperature               = Some(700.degreesCelsius),
            t_burnout                              = 550.degreesCelsius,
            efficiency_nominal                     = 80.percent,
            efficiency_reduced                     = None,
            heat_output_reduced                    = HeatOutputReduced.NotDefined,
            minimum_fuel_mass                      = Some(10.kg),
            maximum_fuel_mass                      = maxFuelMass.kg,
            air_fuel_ratio_nominal                 = 2.5.unitless,
            air_fuel_ratio_lowest                  = None,
            co2_dry_nominal                        = 8.percent,
            co2_dry_lowest                         = None,
            pellets_load_burn_duration             = Some(75.minutes),
            emissions_values                       = stubEmissionsDTO(coMgNm3)
        )

    private def stubStoveParams(maxLoad: Double): StoveParams =
        StoveParams.fromMaxLoadAndStoragePeriod  (
            maximum_load   = maxLoad.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        )

    /** Build a FireCalcYAML_V6 that wires ExampleProject_15544 pipes around a SingleTested firebox. */
    private def buildEngineState(
        firebox                            : Firebox.SingleTested,
        stoveParams                        : StoveParams
    ): FireCalcYAML_V6 =
        FireCalcYAML_V6(
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr(
                reference = ExampleProject_15544.project.reference,
                date      = ExampleProject_15544.project.date,
                country   = ExampleProject_15544.project.country
            ),
            local_conditions               = ExampleProject_15544.localConditions,
            stove_params                   = stoveParams,
            air_intake_descr               = ExampleProject_15544.conduit_air_descr,
            firebox                        = firebox,
            post_firebox_pipes             = Seq(
                PostFireboxPipeDescrSlot.FlueSlot     (ExampleProject_15544.accumulateur_descr        ),
                PostFireboxPipeDescrSlot.ConnectorSlot(ExampleProject_15544.conduit_raccordement_descr),
                PostFireboxPipeDescrSlot.ChimneySlot  (ExampleProject_15544.conduit_fumees_descr      )
            )
        )

    /** Load a FireCalcYAML into the strict application for testing. */
    private def loadApp(
        yaml: FireCalcYAML_V6
    ): EN15544_Strict_Application =
        val loader = FireCalcYAML_Loader(yaml)
        val appV   = loader.make_en15544_Strict_Application
        appV.isValid shouldBe true
        appV.toOption.get

    "SingleTested firebox validation" - {

        // ── m_B / nominal-load consistency ────────────────────────────

        "max fuel mass == stove params nominal load → constraints pass" in {
            val app = loadApp(buildEngineState(stubSingleTestedDTO(18.5), stubStoveParams(18.5)))
            app.validateResultsExceptEmissionsValues(Country.France).isValid shouldBe true
        }

        "max fuel mass != stove params nominal load → InconsistentMaxLoadAccrossInputs" in {
            val app    = loadApp(buildEngineState(stubSingleTestedDTO(18.5), stubStoveParams(20.0)))
            val result = app.validateResultsExceptEmissionsValues(Country.France)
            result.isValid shouldBe false
        }

        // ── Emissions ─────────────────────────────────────────────────

        "CO below Flamme-Verte limit → no unmet emission criteria" in {
            val app =
                loadApp(buildEngineState(stubSingleTestedDTO(18.5, coMgNm3 = Some(1000.0)), stubStoveParams(18.5)))
            val results = app.check_emissions_and_efficiency_values_with_local_regulations(
                LocalRegulations.fr.wood_logs
            )
            results.unmetCriterias shouldBe empty
        }

        "CO above Flamme-Verte limit → unmet emission criteria" in {
            val app =
                loadApp(buildEngineState(stubSingleTestedDTO(18.5, coMgNm3 = Some(2000.0)), stubStoveParams(18.5)))
            val results = app.check_emissions_and_efficiency_values_with_local_regulations(
                LocalRegulations.fr.wood_logs
            )
            results.unmetCriterias should not be empty
        }

        // ── RemovedFireboxSizingConstraints dispatch ────────────────────

        "narrow width (20cm < 23cm base min) still passes via RemovedFireboxSizingConstraints" in {
            val app = loadApp(
                buildEngineState(
                    stubSingleTestedDTO (
                        maxFuelMass  = 18.5,
                        fireboxDepth = 0.44.meters,
                        fireboxWidth = 0.20.meters
                    ),
                    stubStoveParams     (18.5)
                )
            )
            app.validateResultsExceptEmissionsValues(Country.France).isValid shouldBe true
        }
    }
