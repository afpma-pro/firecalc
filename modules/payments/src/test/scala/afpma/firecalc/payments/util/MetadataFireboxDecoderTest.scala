/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import java.util.Base64

import afpma.firecalc.payments.shared.api.FileDescriptionWithContent

import utest.*

object MetadataFireboxDecoderTest extends TestSuite:

    private val traditionalYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Traditional:
          |    heat_output_reduced: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_lowest_opening:
          |      value: "0.05"
          |      unit: meter
          |    pressure_loss_coefficient_from_door:
          |      value: "0.3"
          |      unit: "1"
          |    total_air_intake_surface_area_on_door:
          |      value: "0.0097"
          |      unit: "meter^2"
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin

    private val ecolabeledYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Ecolabeled:
          |    heat_output_reduced: null
          |    version:
          |      type: "either"
          |      left_or_right: "left"
          |      value: "Version 1"
          |    air_intake_shape: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_first_row_of_air_injectors:
          |      value: "0.1"
          |      unit: meter
          |    door_opening_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |    ash_pit_height:
          |      value: "0.05"
          |      unit: meter
          |    air_manifold_height:
          |      value: "0.05"
          |      unit: meter
          |    firebox_floor_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_inner_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_outer_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    air_column_thickness:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_sides:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_rear:
          |      value: "0.05"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R1:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R2:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R3:
          |      value: "0"
          |      unit: meter
          |    injector_height:
          |      value: "0"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin

    private val invalidYaml = "not: valid: yaml: ["

    private def encodeYaml(yaml: String): String =
        Base64.getEncoder.encodeToString(yaml.getBytes("UTF-8"))

    private def fileMetadata(base64Yaml: String): FileDescriptionWithContent =
        FileDescriptionWithContent(
            filename = "test.yaml",
            mimeType = "application/x-yaml",
            content  = base64Yaml
        )

    val tests = Tests {

        test("extractFirebox - traditional firebox") {
            val metadata = fileMetadata(encodeYaml(traditionalYaml))
            val result   = MetadataFireboxDecoder.extractFirebox(metadata)
            assert(result.isDefined)
        }

        test("extractFirebox - ecolabeled firebox") {
            val metadata = fileMetadata(encodeYaml(ecolabeledYaml))
            val result   = MetadataFireboxDecoder.extractFirebox(metadata)
            assert(result.isDefined)
        }

        test("extractFirebox - invalid YAML returns None") {
            val metadata = fileMetadata(encodeYaml(invalidYaml))
            val result   = MetadataFireboxDecoder.extractFirebox(metadata)
            assert(result.isEmpty)
        }

        test("extractFirebox - invalid base64 returns None") {
            val metadata = FileDescriptionWithContent(
                filename = "test.yaml",
                mimeType = "application/x-yaml",
                content  = "not-valid-base64!!!"
            )
            val result   = MetadataFireboxDecoder.extractFirebox(metadata)
            assert(result.isEmpty)
        }

        test("extractFirebox - empty content returns None") {
            val metadata = fileMetadata(encodeYaml(""))
            val result   = MetadataFireboxDecoder.extractFirebox(metadata)
            assert(result.isEmpty)
        }

    }
