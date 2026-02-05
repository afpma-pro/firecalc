/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.firebox

import org.scalacheck.Gen
import afpma.firecalc.dto.v1.Firebox_V1
import afpma.firecalc.dto.common.{HeatOutputReduced, OutsideAirLocationInHeater}
import afpma.firecalc.dto.generators.base.{PrimitiveGenerators, PipeShapeGenerators}
import afpma.firecalc.units.coulombutils.*

trait Firebox_V1_Generators extends PrimitiveGenerators, PipeShapeGenerators:

    def genHeatOutputReduced: Gen[
        HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal
    ] =
        Gen.oneOf(
            HeatOutputReduced.NotDefined,
            HeatOutputReduced.HalfOfNominal.makeWithoutValue
        )

    def genVersion: Gen[Either["Version 1", "Version 2"]] =
        Gen.oneOf(0, 1).map: choice =>
            if choice == 0 then
                Left("Version 1" : String).asInstanceOf[Either["Version 1", "Version 2"]]
            else
                Right("Version 2" : String).asInstanceOf[Either["Version 1", "Version 2"]]

    def genTraditional_V1: Gen[Firebox_V1.Traditional] =
        for
            heat_output_reduced <- genHeatOutputReduced
            firebox_depth <- genFireboxDimension
            firebox_width <- genFireboxDimension
            firebox_height <- Gen.choose(40.0, 100.0).map(_.cm)
            pressure_loss_coefficient <- genPressureLossCoeff
            total_air_intake_surface <- Gen.choose(50.0, 150.0).map(d => (d.cm2))
            glass_width <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height <- Gen.choose(20.0, 50.0).map(_.cm)
        yield Firebox_V1.Traditional(
            heat_output_reduced = heat_output_reduced,
            firebox_depth = firebox_depth,
            firebox_width = firebox_width,
            firebox_height = firebox_height,
            pressure_loss_coefficient_from_door = pressure_loss_coefficient,
            total_air_intake_surface_area_on_door = total_air_intake_surface,
            glass_width = glass_width,
            glass_height = glass_height
        )

    def genEcoLabeled_V1: Gen[Firebox_V1.EcoLabeled] =
        for
            heat_output_reduced <- genHeatOutputReduced
            version <- genVersion
            air_intake_shape <-
                if version.isRight then Gen.some(genPipeShape)
                else Gen.const(None)
            firebox_depth <- genFireboxDimension
            firebox_width <- genFireboxDimension
            firebox_height <- Gen.choose(40.0, 100.0).map(_.cm)
            door_opening_width <- genFireboxDimension
            glass_width <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height <- Gen.choose(20.0, 50.0).map(_.cm)
            ash_pit_height <- Gen.choose(5.0, 15.0).map(_.cm)
            air_manifold_height <- Gen.choose(8.0, 15.0).map(_.cm)
            firebox_floor_thickness <- Gen.choose(5.0, 15.0).map(_.cm)
            firebox_inner_wall_thickness <- Gen.choose(4.0, 10.0).map(_.cm)
            firebox_outer_wall_thickness <- Gen.choose(4.0, 10.0).map(_.cm)
            air_column_thickness <- Gen.choose(2.0, 5.0).map(_.cm)
            width_between_air_columns_sides <- Gen.choose(2.0, 5.0).map(_.cm)
            width_between_air_columns_rear <- Gen.choose(2.0, 5.0).map(_.cm)
            reinforcement_bars_offset <- Gen.choose(2.0, 5.0).map(_.cm)
            injector_height <- Gen.choose(0.5, 2.0).map(_.cm)
        yield Firebox_V1.EcoLabeled(
            heat_output_reduced = heat_output_reduced,
            version = version,
            air_intake_shape = air_intake_shape,
            firebox_depth = firebox_depth,
            firebox_width = firebox_width,
            firebox_height = firebox_height,
            door_opening_width = door_opening_width,
            glass_width = glass_width,
            glass_height = glass_height,
            ash_pit_height = ash_pit_height,
            air_manifold_height = air_manifold_height,
            firebox_floor_thickness = firebox_floor_thickness,
            firebox_inner_wall_thickness = firebox_inner_wall_thickness,
            firebox_outer_wall_thickness = firebox_outer_wall_thickness,
            air_column_thickness = air_column_thickness,
            width_between_two_air_columns_sides = width_between_air_columns_sides,
            width_between_two_air_columns_rear = width_between_air_columns_rear,
            reinforcement_bars_offset_in_corners = reinforcement_bars_offset,
            injector_height = injector_height
        )

    def genAFPMA_PRSE_V1: Gen[Firebox_V1.AFPMA_PRSE] =
        for
            heat_output_reduced <- genHeatOutputReduced
            outside_air_location <- Gen.const(OutsideAirLocationInHeater.FromBottom)
            outside_air_conduit_shape <- genPipeShape
            firebox_depth <- genFireboxDimension
            firebox_width <- genFireboxDimension
            firebox_height <- Gen.choose(40.0, 100.0).map(_.cm)
            height_of_first_row <- Gen.choose(5.0, 15.0).map(_.cm)
            glass_width <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height <- Gen.choose(20.0, 50.0).map(_.cm)
            ash_pit_height <- Gen.choose(5.0, 15.0).map(_.cm)
            floor_thickness <- Gen.choose(5.0, 15.0).map(_.cm)
            combustion_air_manifold_height <- Gen.choose(8.0, 15.0).map(_.cm)
            outside_air_inlet_lip <- Gen.choose(2.0, 8.0).map(_.cm)
            height_of_air_feed <- Gen.choose(5.0, 15.0).map(_.cm)
            number_of_air_columns_firebox <- Gen.choose(1, 4)
            number_of_air_columns_door <- Gen.choose(1, 4)
        yield Firebox_V1.AFPMA_PRSE(
            heat_output_reduced = heat_output_reduced,
            outside_air_location_in_heater = outside_air_location,
            outside_air_conduit_shape = outside_air_conduit_shape,
            firebox_depth = firebox_depth,
            firebox_width = firebox_width,
            firebox_height = firebox_height,
            height_of_first_row_of_air_injectors = height_of_first_row,
            glass_width = glass_width,
            glass_height = glass_height,
            ash_pit_height = ash_pit_height,
            floor_thickness = floor_thickness,
            combustion_air_manifold_height = combustion_air_manifold_height,
            outside_air_inlet_lip = outside_air_inlet_lip,
            height_of_air_feed_to_columns = height_of_air_feed,
            number_of_air_columns_feeding_firebox = number_of_air_columns_firebox,
            number_of_air_columns_feeding_door = number_of_air_columns_door
        )

    def genFirebox_V1: Gen[Firebox_V1] =
        Gen.frequency(
            (5, genTraditional_V1),
            (3, genEcoLabeled_V1),
            (2, genAFPMA_PRSE_V1)
        )

end Firebox_V1_Generators
