/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran&#231;aise du Po&#234;le Ma&#231;onn&#233; Artisanal
 */

package afpma.firecalc.firebox_calc_api

import io.circe.{parser}
import io.circe.syntax.*
import Codecs.given

import afpma.firecalc.dto.all.HeatOutputReduced
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Comprehensive test suite for the flat firebox field encoding in
 * FireboxCalcRequest. Verifies round-trips, schema fidelity, negative
 * cases, and structural drift detection.
 */
class FireboxFieldEncodingSuite extends AnyFreeSpec with Matchers {

    // =========================================================================
    // Round-trip tests for firebox subtypes
    // =========================================================================

    "Traditional firebox round-trips with flat encoding" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = Firebox_V4.Traditional(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                firebox_depth                         = 0.442.withUnit[Meter],
                firebox_width                         = 0.332.withUnit[Meter],
                firebox_height                        = 0.641.withUnit[Meter],
                height_of_lowest_opening              = 0.1.withUnit[Meter],
                pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson.noSpaces
        val decoded = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    "Traditional firebox with HalfOfNominal heat_output_reduced round-trips" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = Firebox_V4.Traditional(
                heat_output_reduced                   = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
                firebox_depth                         = 0.5.withUnit[Meter],
                firebox_width                         = 0.4.withUnit[Meter],
                firebox_height                        = 0.7.withUnit[Meter],
                height_of_lowest_opening              = 0.15.withUnit[Meter],
                pressure_loss_coefficient_from_door   = 1.5.withUnit[1],
                total_air_intake_surface_area_on_door = 0.02.withUnit[(Meter ^ 2)],
                glass_width                           = 0.35.withUnit[Meter],
                glass_height                          = 0.35.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "fr",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson.noSpaces
        val decoded = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    "Ecolabeled firebox round-trips with flat encoding" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Ecolabeled,
            firebox            = Firebox_V4.Ecolabeled(
                heat_output_reduced                     = HeatOutputReduced.NotDefined,
                version                                 = Left("Version 1"),
                air_intake_shape                        = None,
                firebox_depth                           = 0.5.withUnit[Meter],
                firebox_width                           = 0.4.withUnit[Meter],
                firebox_height                          = 0.7.withUnit[Meter],
                height_of_first_row_of_air_injectors    = 0.2.withUnit[Meter],
                door_opening_width                      = 0.3.withUnit[Meter],
                glass_width                             = 0.3.withUnit[Meter],
                glass_height                            = 0.3.withUnit[Meter],
                ash_pit_height                          = 0.1.withUnit[Meter],
                air_manifold_height                     = 0.15.withUnit[Meter],
                firebox_floor_thickness                 = 0.05.withUnit[Meter],
                firebox_inner_wall_thickness            = 0.06.withUnit[Meter],
                firebox_outer_wall_thickness            = 0.08.withUnit[Meter],
                air_column_thickness                    = 0.04.withUnit[Meter],
                width_between_two_air_columns_sides     = 0.2.withUnit[Meter],
                width_between_two_air_columns_rear      = 0.25.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R1 = 0.02.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R2 = 0.03.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R3 = 0.04.withUnit[Meter],
                injector_height                         = 0.1.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson.noSpaces
        val decoded = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    "AFPMA_PRSE firebox round-trips with flat encoding" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.AfpmaPrse,
            firebox            = Firebox_V4.AFPMA_PRSE(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                outside_air_location_in_heater        = afpma.firecalc.dto.common.OutsideAirLocationInHeater.FromBottom,
                outside_air_conduit_shape             = afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter]),
                firebox_depth                         = 0.5.withUnit[Meter],
                firebox_width                         = 0.4.withUnit[Meter],
                firebox_height                        = 0.7.withUnit[Meter],
                height_of_first_row_of_air_injectors  = 0.2.withUnit[Meter],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter],
                ash_pit_height                        = 0.1.withUnit[Meter],
                floor_thickness                       = 0.05.withUnit[Meter],
                combustion_air_manifold_height        = 0.15.withUnit[Meter],
                outside_air_inlet_lip                 = 0.02.withUnit[Meter],
                height_of_air_feed_to_columns         = 0.1.withUnit[Meter],
                number_of_air_columns_feeding_firebox = 2,
                number_of_air_columns_feeding_door    = 1
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson.noSpaces
        val decoded = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    "SingleTested firebox round-trips with flat encoding" in {
        val emissionsDto = afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO(
            firebox_name                = "TestFirebox",
            accredited_or_notified_body = "TestBody",
            test_reports                = Nil,
            emissions_values            = afpma.firecalc.dto.v4.EmissionValues_DTO(
                co   = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.CO,
                    Some(10.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                dust = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.Dust,
                    Some(5.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                ogc  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.OGC,
                    Some(8.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                nox  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.NOx,
                    Some(20.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                )
            )
        )
        val req          = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.SingleTested,
            firebox            = Firebox_V4.SingleTested(
                reference                              = "TEST-001",
                type_of_appliance                      = afpma.firecalc.dto.all.TypeOfAppliance.WoodLogs,
                test_standard                          = Firebox_V4.TestStandard.EN_15250,
                firebox_depth                          = 0.5.withUnit[Meter],
                firebox_width                          = 0.4.withUnit[Meter],
                firebox_height                         = 0.7.withUnit[Meter],
                ash_pit_height                         = 0.1.withUnit[Meter],
                is_glass_surface_ratio_below_one_fifth = true,
                glass_area                             = 0.09.withUnit[(Meter ^ 2)],
                mean_firebox_temperature               = None,
                t_burnout                              = None,
                efficiency_nominal                     = 75.0.percent,
                efficiency_reduced                     = None,
                heat_output_reduced                    = HeatOutputReduced.NotDefined,
                minimum_fuel_mass                      = None,
                maximum_fuel_mass                      = 5.0.withUnit[Kilogram],
                air_fuel_ratio_nominal                 = 6.0.withUnit[1],
                air_fuel_ratio_lowest                  = None,
                co2_dry_nominal                        = 13.0.percent,
                co2_dry_lowest                         = None,
                pellets_load_burn_duration             = None,
                emissions_values                       = emissionsDto,
                image                                  = None
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json         = req.asJson.noSpaces
        val decoded      = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    "Door15aCatalog firebox round-trips with flat encoding" in {
        val emissionsDto = afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO(
            firebox_name                = "Door15aTest",
            accredited_or_notified_body = "TestBody",
            test_reports                = Nil,
            emissions_values            = afpma.firecalc.dto.v4.EmissionValues_DTO(
                co   = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.CO,
                    Some(10.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                dust = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.Dust,
                    Some(5.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                ogc  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.OGC,
                    Some(8.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                nox  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.NOx,
                    Some(20.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                )
            )
        )
        val req          = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Door15aCatalog,
            firebox            = Firebox_V4.Door15aFirebox_Catalog(
                reference                   = "DOOR-001",
                firebox_depth               = 0.5.withUnit[Meter],
                firebox_width               = 0.4.withUnit[Meter],
                firebox_height              = 0.7.withUnit[Meter],
                sb                          = 50.0.withUnit[Centimeter],
                expectedAirIntakePipeShapes = List(afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter])),
                actualAirIntakePipeShape    = afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter]),
                co2_dry_nominal             = 13.0.percent,
                emissions_values            = emissionsDto,
                glass_area                  = 0.09.withUnit[(Meter ^ 2)],
                height_of_lowest_opening    = 0.1.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json         = req.asJson.noSpaces
        val decoded      = parser.decode[FireboxCalcRequest](json)
        decoded shouldBe Right(req)
    }

    // =========================================================================
    // Schema-fidelity tests: verify encoded JSON structure
    // =========================================================================

    "Encoded Traditional firebox has no wrapper key" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = Firebox_V4.Traditional(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                firebox_depth                         = 0.442.withUnit[Meter],
                firebox_width                         = 0.332.withUnit[Meter],
                firebox_height                        = 0.641.withUnit[Meter],
                height_of_lowest_opening              = 0.1.withUnit[Meter],
                pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson
        val firebox = json.hcursor.downField("firebox").focus.get
        // No wrapper key "Traditional" should exist
        firebox.hcursor.downField("Traditional").succeeded shouldBe false
        // Direct subtype fields should exist
        firebox.hcursor.downField("heat_output_reduced").succeeded shouldBe true
        firebox.hcursor.downField("firebox_depth").succeeded shouldBe true
        firebox.hcursor.downField("firebox_width").succeeded shouldBe true
        firebox.hcursor.downField("firebox_height").succeeded shouldBe true
    }

    "Encoded firebox object has expected field count for Traditional" in {
        val req        = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = Firebox_V4.Traditional(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                firebox_depth                         = 0.442.withUnit[Meter],
                firebox_width                         = 0.332.withUnit[Meter],
                firebox_height                        = 0.641.withUnit[Meter],
                height_of_lowest_opening              = 0.1.withUnit[Meter],
                pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json       = req.asJson
        val fireboxObj = json.hcursor.downField("firebox").focus.get.asObject.get
        // Traditional has 9 fields
        fireboxObj.size shouldBe 9
    }

    "Encoded Ecolabeled firebox has no wrapper key" in {
        val req     = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Ecolabeled,
            firebox            = Firebox_V4.Ecolabeled(
                heat_output_reduced                     = HeatOutputReduced.NotDefined,
                version                                 = Left("Version 1"),
                air_intake_shape                        = None,
                firebox_depth                           = 0.5.withUnit[Meter],
                firebox_width                           = 0.4.withUnit[Meter],
                firebox_height                          = 0.7.withUnit[Meter],
                height_of_first_row_of_air_injectors    = 0.2.withUnit[Meter],
                door_opening_width                      = 0.3.withUnit[Meter],
                glass_width                             = 0.3.withUnit[Meter],
                glass_height                            = 0.3.withUnit[Meter],
                ash_pit_height                          = 0.1.withUnit[Meter],
                air_manifold_height                     = 0.15.withUnit[Meter],
                firebox_floor_thickness                 = 0.05.withUnit[Meter],
                firebox_inner_wall_thickness            = 0.06.withUnit[Meter],
                firebox_outer_wall_thickness            = 0.08.withUnit[Meter],
                air_column_thickness                    = 0.04.withUnit[Meter],
                width_between_two_air_columns_sides     = 0.2.withUnit[Meter],
                width_between_two_air_columns_rear      = 0.25.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R1 = 0.02.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R2 = 0.03.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R3 = 0.04.withUnit[Meter],
                injector_height                         = 0.1.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json    = req.asJson
        val firebox = json.hcursor.downField("firebox").focus.get
        firebox.hcursor.downField("Ecolabeled").succeeded shouldBe false
        firebox.hcursor.downField("version").succeeded shouldBe true
        firebox.hcursor.downField("heat_output_reduced").succeeded shouldBe true
    }

    // =========================================================================
    // Negative test: CustomForLab kind fails to decode
    // =========================================================================

    "Decoding request with customForLab kind fails" in {
        val badJson = """{
            "apiVersion": "v1",
            "kind": "customForLab",
            "firebox": {
                "heat_output_reduced": "NotDefined",
                "firebox_depth": {"value": "0.442", "unit": "m"},
                "firebox_width": {"value": "0.332", "unit": "m"},
                "firebox_height": {"value": "0.641", "unit": "m"},
                "height_of_lowest_opening": {"value": "0.1", "unit": "m"},
                "pressure_loss_coefficient_from_door": {"value": "1.0", "unit": "1"},
                "total_air_intake_surface_area_on_door": {"value": "0.01", "unit": "m^2"},
                "glass_width": {"value": "0.3", "unit": "m"},
                "glass_height": {"value": "0.3", "unit": "m"}
            },
            "params": {},
            "airIntakePipeShape": null,
            "locale": "en",
            "standard": "strict"
        }"""
        parser.decode[FireboxCalcRequest](badJson) match
            case Left(_)  => succeed
            case Right(_) =>
                fail("Expected decoding to fail for customForLab kind")
    }

    // =========================================================================
    // Negative test: kind/firebox type mismatch is rejected
    // =========================================================================

    "Decoding request with kind/firebox type mismatch fails" in {
        // Send kind: "traditional" with an Ecolabeled firebox body
        // (has Ecolabeled-specific field "version"). Decoder must reject.
        val badJson = """{
            "apiVersion": "v1",
            "kind": "traditional",
            "firebox": {
                "heat_output_reduced": "NotDefined",
                "version": {"Left": "Version 1"},
                "air_intake_shape": null,
                "firebox_depth": {"value": "0.5", "unit": "m"},
                "firebox_width": {"value": "0.4", "unit": "m"},
                "firebox_height": {"value": "0.7", "unit": "m"},
                "height_of_first_row_of_air_injectors": {"value": "0.2", "unit": "m"},
                "door_opening_width": {"value": "0.3", "unit": "m"},
                "glass_width": {"value": "0.3", "unit": "m"},
                "glass_height": {"value": "0.3", "unit": "m"},
                "ash_pit_height": {"value": "0.1", "unit": "m"},
                "air_manifold_height": {"value": "0.15", "unit": "m"},
                "firebox_floor_thickness": {"value": "0.05", "unit": "m"},
                "firebox_inner_wall_thickness": {"value": "0.06", "unit": "m"},
                "firebox_outer_wall_thickness": {"value": "0.08", "unit": "m"},
                "air_column_thickness": {"value": "0.04", "unit": "m"},
                "width_between_two_air_columns_sides": {"value": "0.2", "unit": "m"},
                "width_between_two_air_columns_rear": {"value": "0.25", "unit": "m"},
                "reinforcement_bars_offset_in_corners_R1": {"value": "0.02", "unit": "m"},
                "reinforcement_bars_offset_in_corners_R2": {"value": "0.03", "unit": "m"},
                "reinforcement_bars_offset_in_corners_R3": {"value": "0.04", "unit": "m"},
                "injector_height": {"value": "0.1", "unit": "m"}
            },
            "params": {},
            "airIntakePipeShape": null,
            "locale": "en",
            "standard": "strict"
        }"""
        parser.decode[FireboxCalcRequest](badJson) match
            case Left(_)  => succeed
            case Right(_) =>
                fail("Expected decoding to fail for kind/firebox type mismatch")
    }

    // =========================================================================
    // Kind-to-constructor mapping verification
    // =========================================================================

    "WireFireboxKind.constructorName returns correct mapping for Traditional" in {
        WireFireboxKind.Traditional.constructorName shouldBe Some("Traditional")
    }

    "WireFireboxKind.constructorName returns correct mapping for Ecolabeled" in {
        WireFireboxKind.Ecolabeled.constructorName shouldBe Some("Ecolabeled")
    }

    "WireFireboxKind.constructorName returns correct mapping for AfpmaPrse" in {
        WireFireboxKind.AfpmaPrse.constructorName shouldBe Some("AFPMA_PRSE")
    }

    "WireFireboxKind.constructorName returns correct mapping for SingleTested" in {
        WireFireboxKind.SingleTested.constructorName shouldBe Some("SingleTested")
    }

    "WireFireboxKind.constructorName returns correct mapping for Door15aCatalog" in {
        WireFireboxKind.Door15aCatalog.constructorName shouldBe Some("Door15aFirebox_Catalog")
    }

    "WireFireboxKind.constructorName returns None for CustomForLab" in {
        WireFireboxKind.CustomForLab.constructorName shouldBe None
    }

    // =========================================================================
    // Structural drift guard: hardcoded expected field sets
    // =========================================================================

    "Encoded Traditional firebox has all expected fields" in {
        val req            = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Traditional,
            firebox            = Firebox_V4.Traditional(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                firebox_depth                         = 0.442.withUnit[Meter],
                firebox_width                         = 0.332.withUnit[Meter],
                firebox_height                        = 0.641.withUnit[Meter],
                height_of_lowest_opening              = 0.1.withUnit[Meter],
                pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
                total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json           = req.asJson
        val fireboxObj     = json.hcursor.downField("firebox").focus.get.asObject.get
        // Fields expected on the wire for Traditional (matches case class params)
        val expectedFields = Set(
            "heat_output_reduced",
            "firebox_depth",
            "firebox_width",
            "firebox_height",
            "height_of_lowest_opening",
            "pressure_loss_coefficient_from_door",
            "total_air_intake_surface_area_on_door",
            "glass_width",
            "glass_height"
        )
        val actualFields   = fireboxObj.toList.map(_._1).toSet
        // No missing fields
        val missing        = expectedFields -- actualFields
        assert(missing.isEmpty, s"Missing fields in encoded Traditional firebox: $missing")
        // No extra fields
        val extra = actualFields -- expectedFields
        assert(extra.isEmpty, s"Extra fields in encoded Traditional firebox: $extra")
    }

    "Encoded Ecolabeled firebox has all expected fields" in {
        val req            = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Ecolabeled,
            firebox            = Firebox_V4.Ecolabeled(
                heat_output_reduced                     = HeatOutputReduced.NotDefined,
                version                                 = Left("Version 1"),
                air_intake_shape                        = None,
                firebox_depth                           = 0.5.withUnit[Meter],
                firebox_width                           = 0.4.withUnit[Meter],
                firebox_height                          = 0.7.withUnit[Meter],
                height_of_first_row_of_air_injectors    = 0.2.withUnit[Meter],
                door_opening_width                      = 0.3.withUnit[Meter],
                glass_width                             = 0.3.withUnit[Meter],
                glass_height                            = 0.3.withUnit[Meter],
                ash_pit_height                          = 0.1.withUnit[Meter],
                air_manifold_height                     = 0.15.withUnit[Meter],
                firebox_floor_thickness                 = 0.05.withUnit[Meter],
                firebox_inner_wall_thickness            = 0.06.withUnit[Meter],
                firebox_outer_wall_thickness            = 0.08.withUnit[Meter],
                air_column_thickness                    = 0.04.withUnit[Meter],
                width_between_two_air_columns_sides     = 0.2.withUnit[Meter],
                width_between_two_air_columns_rear      = 0.25.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R1 = 0.02.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R2 = 0.03.withUnit[Meter],
                reinforcement_bars_offset_in_corners_R3 = 0.04.withUnit[Meter],
                injector_height                         = 0.1.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json           = req.asJson
        val fireboxObj     = json.hcursor.downField("firebox").focus.get.asObject.get
        // Fields expected on the wire for Ecolabeled (matches case class params)
        val expectedFields = Set(
            "heat_output_reduced",
            "version",
            "air_intake_shape",
            "firebox_depth",
            "firebox_width",
            "firebox_height",
            "height_of_first_row_of_air_injectors",
            "door_opening_width",
            "glass_width",
            "glass_height",
            "ash_pit_height",
            "air_manifold_height",
            "firebox_floor_thickness",
            "firebox_inner_wall_thickness",
            "firebox_outer_wall_thickness",
            "air_column_thickness",
            "width_between_two_air_columns_sides",
            "width_between_two_air_columns_rear",
            "reinforcement_bars_offset_in_corners_R1",
            "reinforcement_bars_offset_in_corners_R2",
            "reinforcement_bars_offset_in_corners_R3",
            "injector_height"
        )
        val actualFields   = fireboxObj.toList.map(_._1).toSet
        // No missing fields
        val missing        = expectedFields -- actualFields
        assert(missing.isEmpty, s"Missing fields in encoded Ecolabeled firebox: $missing")
        // No extra fields
        val extra = actualFields -- expectedFields
        assert(extra.isEmpty, s"Extra fields in encoded Ecolabeled firebox: $extra")
    }

    "Encoded SingleTested firebox has all expected fields" in {
        val emissionsDto   = afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO(
            firebox_name                = "DriftGuard",
            accredited_or_notified_body = "TestBody",
            test_reports                = Nil,
            emissions_values            = afpma.firecalc.dto.v4.EmissionValues_DTO(
                co   = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.CO,
                    Some(10.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                dust = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.Dust,
                    Some(5.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                ogc  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.OGC,
                    Some(8.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                nox  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.NOx,
                    Some(20.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                )
            )
        )
        val req            = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.SingleTested,
            firebox            = Firebox_V4.SingleTested(
                reference                              = "DRIFT-001",
                type_of_appliance                      = afpma.firecalc.dto.all.TypeOfAppliance.WoodLogs,
                test_standard                          = Firebox_V4.TestStandard.EN_15250,
                firebox_depth                          = 0.5.withUnit[Meter],
                firebox_width                          = 0.4.withUnit[Meter],
                firebox_height                         = 0.7.withUnit[Meter],
                ash_pit_height                         = 0.1.withUnit[Meter],
                is_glass_surface_ratio_below_one_fifth = true,
                glass_area                             = 0.09.withUnit[(Meter ^ 2)],
                mean_firebox_temperature               = None,
                t_burnout                              = None,
                efficiency_nominal                     = 75.0.percent,
                efficiency_reduced                     = None,
                heat_output_reduced                    = HeatOutputReduced.NotDefined,
                minimum_fuel_mass                      = None,
                maximum_fuel_mass                      = 5.0.withUnit[Kilogram],
                air_fuel_ratio_nominal                 = 6.0.withUnit[1],
                air_fuel_ratio_lowest                  = None,
                co2_dry_nominal                        = 13.0.percent,
                co2_dry_lowest                         = None,
                pellets_load_burn_duration             = None,
                emissions_values                       = emissionsDto,
                image                                  = None
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json           = req.asJson
        val fireboxObj     = json.hcursor.downField("firebox").focus.get.asObject.get
        // All fields expected on the wire for SingleTested (required + optional)
        val expectedFields = Set(
            "reference",
            "type_of_appliance",
            "test_standard",
            "firebox_depth",
            "firebox_width",
            "firebox_height",
            "ash_pit_height",
            "is_glass_surface_ratio_below_one_fifth",
            "glass_area",
            "mean_firebox_temperature",
            "t_burnout",
            "efficiency_nominal",
            "efficiency_reduced",
            "heat_output_reduced",
            "minimum_fuel_mass",
            "maximum_fuel_mass",
            "air_fuel_ratio_nominal",
            "air_fuel_ratio_lowest",
            "co2_dry_nominal",
            "co2_dry_lowest",
            "pellets_load_burn_duration",
            "emissions_values",
            "image"
        )
        val actualFields   = fireboxObj.toList.map(_._1).toSet
        val missing        = expectedFields -- actualFields
        assert(missing.isEmpty, s"Missing fields in encoded SingleTested firebox: $missing")
        // No extra fields
        val extra = actualFields -- expectedFields
        assert(extra.isEmpty, s"Extra fields in encoded SingleTested firebox: $extra")
    }

    "Encoded AFPMA_PRSE firebox has all expected fields" in {
        val req            = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.AfpmaPrse,
            firebox            = Firebox_V4.AFPMA_PRSE(
                heat_output_reduced                   = HeatOutputReduced.NotDefined,
                outside_air_location_in_heater        = afpma.firecalc.dto.common.OutsideAirLocationInHeater.FromBottom,
                outside_air_conduit_shape             = afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter]),
                firebox_depth                         = 0.5.withUnit[Meter],
                firebox_width                         = 0.4.withUnit[Meter],
                firebox_height                        = 0.7.withUnit[Meter],
                height_of_first_row_of_air_injectors  = 0.2.withUnit[Meter],
                glass_width                           = 0.3.withUnit[Meter],
                glass_height                          = 0.3.withUnit[Meter],
                ash_pit_height                        = 0.1.withUnit[Meter],
                floor_thickness                       = 0.05.withUnit[Meter],
                combustion_air_manifold_height        = 0.15.withUnit[Meter],
                outside_air_inlet_lip                 = 0.05.withUnit[Meter],
                height_of_air_feed_to_columns         = 0.1.withUnit[Meter],
                number_of_air_columns_feeding_firebox = 2,
                number_of_air_columns_feeding_door    = 2
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json           = req.asJson
        val fireboxObj     = json.hcursor.downField("firebox").focus.get.asObject.get
        // Fields expected on the wire for AFPMA_PRSE (matches case class params)
        val expectedFields = Set(
            "heat_output_reduced",
            "outside_air_location_in_heater",
            "outside_air_conduit_shape",
            "firebox_depth",
            "firebox_width",
            "firebox_height",
            "height_of_first_row_of_air_injectors",
            "glass_width",
            "glass_height",
            "ash_pit_height",
            "floor_thickness",
            "combustion_air_manifold_height",
            "outside_air_inlet_lip",
            "height_of_air_feed_to_columns",
            "number_of_air_columns_feeding_firebox",
            "number_of_air_columns_feeding_door"
        )
        val actualFields   = fireboxObj.toList.map(_._1).toSet
        // No missing fields
        val missing        = expectedFields -- actualFields
        assert(missing.isEmpty, s"Missing fields in encoded AFPMA_PRSE firebox: $missing")
        // No extra fields
        val extra = actualFields -- expectedFields
        assert(extra.isEmpty, s"Extra fields in encoded AFPMA_PRSE firebox: $extra")
    }

    "Encoded Door15aCatalog firebox has all expected fields" in {
        val emissionsDto   = afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO(
            firebox_name                = "DriftGuard",
            accredited_or_notified_body = "TestBody",
            test_reports                = Nil,
            emissions_values            = afpma.firecalc.dto.v4.EmissionValues_DTO(
                co   = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.CO,
                    Some(10.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                dust = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.Dust,
                    Some(5.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                ogc  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.OGC,
                    Some(8.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                ),
                nox  = afpma.firecalc.dto.v4.TestEmissionValue_DTO(
                    afpma.firecalc.domain.PolluantName.NOx,
                    Some(20.0.mg_per_Nm3),
                    "EN 13241",
                    13.0.percent
                )
            )
        )
        val req            = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireFireboxKind.Door15aCatalog,
            firebox            = Firebox_V4.Door15aFirebox_Catalog(
                reference                   = "DRIFT-001",
                firebox_depth               = 0.5.withUnit[Meter],
                firebox_width               = 0.4.withUnit[Meter],
                firebox_height              = 0.7.withUnit[Meter],
                sb                          = 50.0.withUnit[Centimeter],
                expectedAirIntakePipeShapes = List(afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter])),
                actualAirIntakePipeShape    = afpma.firecalc.domain.PipeShape.Circle(0.1.withUnit[Meter]),
                co2_dry_nominal             = 13.0.percent,
                emissions_values            = emissionsDto,
                glass_area                  = 0.09.withUnit[(Meter ^ 2)],
                height_of_lowest_opening    = 0.1.withUnit[Meter]
            ),
            params             = Standard15544Params(None, None),
            airIntakePipeShape = None,
            locale             = "en",
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )
        val json           = req.asJson
        val fireboxObj     = json.hcursor.downField("firebox").focus.get.asObject.get
        // Required fields expected on the wire for Door15aCatalog
        val expectedFields = Set(
            "reference",
            "firebox_depth",
            "firebox_width",
            "firebox_height",
            "sb",
            "expectedAirIntakePipeShapes",
            "actualAirIntakePipeShape",
            "co2_dry_nominal",
            "emissions_values",
            "glass_area",
            "height_of_lowest_opening"
        )
        val actualFields   = fireboxObj.toList.map(_._1).toSet
        val missing        = expectedFields -- actualFields
        assert(missing.isEmpty, s"Missing fields in encoded Door15aCatalog firebox: $missing")
        // No phantom fields from SingleTested
        val phantomFields = Set(
            "efficiency_nominal",
            "efficiency_reduced",
            "minimum_fuel_mass",
            "maximum_fuel_mass",
            "air_fuel_ratio_nominal",
            "air_fuel_ratio_lowest"
        )
        val foundPhantoms = actualFields.intersect(phantomFields)
        assert(foundPhantoms.isEmpty, s"Phantom SingleTested fields in Door15aCatalog: $foundPhantoms")
    }

    // =========================================================================
    // Negative test: throwing unit codec surfaces as DecodingFailure
    // =========================================================================

    "Decoding request with a bad unit string yields a DecodingFailure, not an exception" in {
        // The dto QtyD decoder throws Exception on an unexpected unit string;
        // the FireboxCalcRequest decoder must catch it and surface it as a
        // circe DecodingFailure (400 malformed_request) instead of letting it
        // escape the decode call.
        val badJson = """{
            "apiVersion": "v1",
            "kind": "traditional",
            "firebox": {
                "heat_output_reduced": "NotDefined",
                "firebox_depth": {"value": "0.442", "unit": "parrots"},
                "firebox_width": {"value": "0.332", "unit": "m"},
                "firebox_height": {"value": "0.641", "unit": "m"},
                "height_of_lowest_opening": {"value": "0.1", "unit": "m"},
                "pressure_loss_coefficient_from_door": {"value": "1.0", "unit": "1"},
                "total_air_intake_surface_area_on_door": {"value": "0.01", "unit": "m^2"},
                "glass_width": {"value": "0.3", "unit": "m"},
                "glass_height": {"value": "0.3", "unit": "m"}
            },
            "params": {},
            "airIntakePipeShape": null,
            "locale": "en",
            "standard": "strict"
        }"""
        parser.decode[FireboxCalcRequest](badJson) match
            case Left(df) =>
                withClue(s"unexpected DecodingFailure message: ${df.getMessage}") {
                    df.getMessage.contains("firebox decode error") shouldBe true
                }
            case Right(_) =>
                fail("Expected decoding to fail for an unrecognized unit string")
    }
}
