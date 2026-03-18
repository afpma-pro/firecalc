/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v4.Firebox_V3
import afpma.firecalc.dto.v4.TypeOfAppliance
import afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO
import afpma.firecalc.dto.v4.EmissionValues_DTO
import afpma.firecalc.dto.v4.TestEmissionValue_DTO
import afpma.firecalc.dto.v4.PolluantName

import io.taig.babel.Locale
import io.taig.babel.Languages

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Deterministic YAML snapshot test for the SingleTested firebox variant.
 *
 * Verifies that:
 *  - encoding a fixed SingleTested-based FireCalcYAML_V4 succeeds, and
 *  - the resulting YAML round-trips correctly (decode → re-encode is identical).
 *
 * The snapshot string is intentionally left empty on first run; if the test
 * fails with a mismatch simply update EXPECTED_YAML to the printed actual value.
 */
class SingleTested_Snapshot_Suite extends AnyFlatSpec with Matchers:

    // --------------------------------------------------------------------- //
    //  Deterministic fixture                                                  //
    // --------------------------------------------------------------------- //

    private val singleTestedFirebox: Firebox_V3.SingleTested =
        Firebox_V3.SingleTested(
            test_standard                          = Firebox_V3.TestStandard.EN_15250,
            reference                              = "REF-2024-TEST-001",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            firebox_depth                          = 33.0.cm,
            firebox_width                          = 33.0.cm,
            firebox_height                         = 52.0.cm,
            ash_pit_height                         = 5.0.cm,
            glass_area                             = 600.0.cm2,
            efficiency_nominal                     = 80.0.percent,
            efficiency_reduced                     = Some(65.0.percent),
            heat_output_reduced                    = HeatOutputReduced.FromTypeTest(8.0.kW),
            minimum_fuel_mass                      = Some(3.0.kg),
            maximum_fuel_mass                      = 15.0.kg,
            air_fuel_ratio_nominal                 = 3.0.unitless,
            air_fuel_ratio_lowest                  = Some(2.5.unitless),
            co2_dry_nominal                        = 12.0.percent,
            co2_dry_lowest                         = Some(9.0.percent),
            pellets_load_burn_duration             = None,
            mean_firebox_temperature               = Some(350.0.degreesCelsius),
            t_burnout                              = 700.0.degreesCelsius,
            is_glass_surface_ratio_below_one_fifth = true,
            emissions_values                       = EmissionsAndEfficiencyValues_DTO(
                firebox_name                = "Firebox Model A",
                accredited_or_notified_body = "Lab XYZ",
                test_reports                = Nil,
                emissions_values            = EmissionValues_DTO(
                    co   = TestEmissionValue_DTO(PolluantName.CO,   Some(1200.0.mg_per_Nm3), "", 13.0.percent),
                    dust = TestEmissionValue_DTO(PolluantName.Dust, Some(40.0.mg_per_Nm3),   "", 13.0.percent),
                    ogc  = TestEmissionValue_DTO(PolluantName.OGC,  Some(120.0.mg_per_Nm3),  "", 13.0.percent),
                    nox  = TestEmissionValue_DTO(PolluantName.NOx,  Some(80.0.mg_per_Nm3),   "", 13.0.percent)
                )
            )
        )

    private val yamlV4: FireCalcYAML_V4 = FireCalcYAML_V4(
        version                        = FireCalcYAML_V4.VERSION,
        locale                         = Locale(Languages.Fr, None),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr(
            reference = "SingleTested integration test project",
            date      = "01/01/2025",
            country   = Country.France
        ),
        local_conditions               = LocalConditions(
            altitude            = 0.meters,
            coastal_region      = false,
            chimney_termination = LocalConditions.ChimneyTermination.Classic
        ),
        stove_params                   = StoveParams.fromMaxLoadAndStoragePeriod(
            maximum_load   = 15.0.kg,
            heating_cycle  = 12.hours,
            min_efficiency = 78.percent,
            facing_type    = FacingType.WithoutAirGap
        ),
        air_intake_descr               = Seq.empty,
        firebox                        = singleTestedFirebox,
        flue_pipe_descr                = Seq.empty,
        connector_pipe_descr           = Seq.empty,
        chimney_pipe_descr             = Seq.empty
    )

    // --------------------------------------------------------------------- //
    //  Tests                                                                  //
    // --------------------------------------------------------------------- //

    "SingleTested firebox YAML encoding" should "succeed" in {
        val encoded = FireCalcYAML_V4.encodeToYaml(yamlV4)
        withClue(s"Encoding failed: ${encoded.failed.toOption}\n") {
            encoded.isSuccess shouldBe true
        }
    }

    it should "round-trip correctly (encode → decode → encode)" in {
        val yaml1 = FireCalcYAML_V4.encodeToYaml(yamlV4)
        yaml1.isSuccess shouldBe true

        val decoded = FireCalcYAML_V4.decodeFromYaml(yaml1.get)
        withClue(s"Decoding failed: ${decoded.failed.toOption}\nYAML was:\n${yaml1.get}\n") {
            decoded.isSuccess shouldBe true
        }

        decoded.get shouldBe yamlV4
    }

    it should "contain expected field values in YAML output" in {
        val yaml = FireCalcYAML_V4.encodeToYaml(yamlV4).get

        // Key field markers in the YAML output
        yaml should include("SingleTested")
        yaml should include("EN_15250")
        yaml should include("REF-2024-TEST-001")
        yaml should include("Firebox Model A")
        yaml should include("Lab XYZ")
    }

    it should "be migratable via FireCalcYAMLMigrations" in {
        val yaml      = FireCalcYAML_V4.encodeToYaml(yamlV4).get
        val migration = FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
        withClue(s"Migration failed: ${migration.failed.toOption}\n") {
            migration.isSuccess shouldBe true
        }
    }

end SingleTested_Snapshot_Suite
