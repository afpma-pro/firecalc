/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.dto.v3.AddFlowOnlyPipeElement_15544_V2
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v3.SetFlowOnlyPipeProp_15544_V2
import afpma.firecalc.dto.v4.AddThermalPipeElement_13384_V3
import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
import afpma.firecalc.dto.v4.TypeOfAppliance

import afpma.firecalc.engine.api.FireCalcYAML_Loader

import io.taig.babel.Locale
import io.taig.babel.Languages

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Integration test: load a FireCalcYAML_V4 with a SingleTested firebox via
 * FireCalcYAML_Loader, run the EN 15544 strict calculation, and assert that the
 * result is valid (no crash / no error).
 */
class SingleTested_Integration_Suite extends AnyFlatSpec with Matchers:

    // --------------------------------------------------------------------- //
    //  Deterministic fixture                                                  //
    // --------------------------------------------------------------------- //

    private val singleTestedFirebox: Firebox_V3.SingleTested =
        Firebox_V3.SingleTested(
            test_standard                          = Firebox_V3.TestStandard.EN_15250,
            reference                              = "REF-2024-INTEG-001",
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
            mean_firebox_temperature               = Some(350.0.degreesCelsius),
            t_burnout                              = 700.0.degreesCelsius,
            is_glass_surface_ratio_below_one_fifth = true,
            emissions_firebox_name                 = "Firebox Integration Model",
            emissions_accredited_body              = "Lab ABC",
            emissions_co                           = 1200.0.mg_per_Nm3,
            emissions_dust                         = 40.0.mg_per_Nm3,
            emissions_ogc                          = 120.0.mg_per_Nm3,
            emissions_nox                          = 80.0.mg_per_Nm3
        )

    private val fluePipeDescr = Seq(
        SetFlowOnlyPipeProp_15544_V2.SetRoughness(3.0.mm),
        SetFlowOnlyPipeProp_15544_V2.SetInnerShape(PipeShape.Rectangle(11.1.cm, 12.2.cm)),
        AddFlowOnlyPipeElement_15544_V2.AddSectionHorizontal("sortie foyer", 28.1.cm),
        AddFlowOnlyPipeElement_15544_V2.AddSharpeAngle_0_to_180(
            "virage 90 deg",
            90.0.degrees,
            None
        ),
        SetFlowOnlyPipeProp_15544_V2.SetInnerShape(PipeShape.Rectangle(11.1.cm, 11.1.cm)),
        AddFlowOnlyPipeElement_15544_V2.AddSectionVertical("colonne ascendante", 3.20.m)
    )

    private val connectorPipeDescr = Seq(
        SetThermalPipeProp_13384_V3.SetMaterial(Material_13384_V2.WeldedSteel()),
        SetThermalPipeProp_13384_V3.SetInnerShape(PipeShape.Circle(130.0.mm)),
        SetThermalPipeProp_13384_V3.SetLayer(2.0.mm, WattsPerMeterKelvin(50.0)),
        SetThermalPipeProp_13384_V3.SetPipeLocation(PipeLocation.HeatedArea),
        AddThermalPipeElement_13384_V3.AddSectionVertical("buse", 5.0.cm)
    )

    private val chimneyPipeDescr = Seq(
        SetThermalPipeProp_13384_V3.SetMaterial(Material_13384_V2.WeldedSteel()),
        SetThermalPipeProp_13384_V3.SetInnerShape(PipeShape.Circle(130.0.mm)),
        SetThermalPipeProp_13384_V3.SetLayer(26.0.mm, WattsPerMeterKelvin(0.260)),
        SetThermalPipeProp_13384_V3.SetPipeLocation(PipeLocation.HeatedArea),
        AddThermalPipeElement_13384_V3.AddSectionVertical("etage", 90.0.cm),
        SetThermalPipeProp_13384_V3.SetPipeLocation(PipeLocation.OutsideOrExterior),
        AddThermalPipeElement_13384_V3.AddSectionVertical("sortie de toit", 60.0.cm)
    )

    private val project: FireCalcYAML_V4 = FireCalcYAML_V4(
        version                        = FireCalc_Version(4),
        locale                         = Locale(Languages.Fr, None),
        display_units                  = DisplayUnits.SI,
        standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
        project_description            = ProjectDescr(
            reference = "SingleTested EN15544 integration test",
            date      = "25/02/2025",
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
        flue_pipe_descr                = fluePipeDescr,
        connector_pipe_descr           = connectorPipeDescr,
        chimney_pipe_descr             = chimneyPipeDescr
    )

    // --------------------------------------------------------------------- //
    //  Tests                                                                  //
    // --------------------------------------------------------------------- //

    "SingleTested EN15544 strict calculation" should
        "succeed without errors" in {
        val loader = FireCalcYAML_Loader(project)
        val result = loader.make_en15544_Strict_Application
        val errors = result.fold(_.toList.map(_.toString).mkString(", "), _ => "")
        withClue(s"EN15544 strict calculation failed with: $errors\n") {
            result.isValid shouldBe true
        }
    }

    "SingleTested YAML round-trip then EN15544 strict" should
        "produce a valid application after encode → decode" in {
        val yamlTry = FireCalcYAML_V4.encodeToYaml(project)
        withClue(s"Encoding failed: ${yamlTry.failed.toOption}\n") {
            yamlTry.isSuccess shouldBe true
        }
        val yaml = yamlTry.get

        val decodedTry = FireCalcYAML_V4.decodeFromYaml(yaml)
        withClue(s"Decoding failed: ${decodedTry.failed.toOption}\nYAML:\n$yaml\n") {
            decodedTry.isSuccess shouldBe true
        }

        val loader = FireCalcYAML_Loader(decodedTry.get)
        val result = loader.make_en15544_Strict_Application
        val errors = result.fold(_.toList.map(_.toString).mkString(", "), _ => "")
        withClue(s"EN15544 strict calculation failed after round-trip: $errors\n") {
            result.isValid shouldBe true
        }
    }

end SingleTested_Integration_Suite
