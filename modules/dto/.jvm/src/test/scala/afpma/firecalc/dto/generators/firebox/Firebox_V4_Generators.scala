/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.firebox

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.TypeOfAppliance
import afpma.firecalc.dto.v5.Firebox_V4

import org.scalacheck.Gen

/**
 * Firebox_V4_Generators
 *
 * Extends V3 generators for the V5/V6 Firebox_V4 sealed trait.
 *
 * Changes from Firebox_V3:
 * - Ecolabeled: splits `reinforcement_bars_offset_in_corners` into R1, R2, R3 fields.
 * - Adds `Door15aFirebox_Catalog` variant (catalog-sourced firebox).
 * - Traditional, AFPMA_PRSE, SingleTested fields are structurally identical to V3.
 */
trait Firebox_V4_Generators extends Firebox_V3_Generators:

    def genTraditional_V4: Gen[Firebox_V4.Traditional] =
        for
            heat_output_reduced      <- genHeatOutputReduced
            firebox_depth            <- genFireboxDimension
            firebox_width            <- genFireboxDimension
            firebox_height           <- Gen.choose(40.0, 100.0).map(_.cm)
            height_of_lowest_opening <- Gen.choose(5.0, 15.0).map(_.cm)
            pressure_loss_coeff      <- genPressureLossCoeff
            total_air_intake_surface <- Gen.choose(50.0, 150.0).map(_.cm2)
            glass_width              <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height             <- Gen.choose(20.0, 50.0).map(_.cm)
        yield Firebox_V4.Traditional(
            heat_output_reduced                    = heat_output_reduced,
            firebox_depth                          = firebox_depth,
            firebox_width                          = firebox_width,
            firebox_height                         = firebox_height,
            height_of_lowest_opening               = height_of_lowest_opening,
            pressure_loss_coefficient_from_door    = pressure_loss_coeff,
            total_air_intake_surface_area_on_door  = total_air_intake_surface,
            glass_width                            = glass_width,
            glass_height                           = glass_height
        )

    def genEcolabeled_V4: Gen[Firebox_V4.Ecolabeled] =
        for
            heat_output_reduced             <- genHeatOutputReduced
            version                         <- genVersion
            air_intake_shape                <-
                if version.isRight then Gen.some(genPipeShape)
                else Gen.const(None)
            firebox_depth                   <- genFireboxDimension
            firebox_width                   <- genFireboxDimension
            firebox_height                  <- Gen.choose(40.0, 100.0).map(_.cm)
            height_of_first_row             <- Gen.choose(5.0, 15.0).map(_.cm)
            door_opening_width              <- genFireboxDimension
            glass_width                     <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height                    <- Gen.choose(20.0, 50.0).map(_.cm)
            ash_pit_height                  <- Gen.choose(5.0, 15.0).map(_.cm)
            air_manifold_height             <- Gen.choose(8.0, 15.0).map(_.cm)
            firebox_floor_thickness         <- Gen.choose(5.0, 15.0).map(_.cm)
            firebox_inner_wall_thickness    <- Gen.choose(4.0, 10.0).map(_.cm)
            firebox_outer_wall_thickness    <- Gen.choose(4.0, 10.0).map(_.cm)
            air_column_thickness            <- Gen.choose(2.0, 5.0).map(_.cm)
            width_between_sides             <- Gen.choose(2.0, 5.0).map(_.cm)
            width_between_rear              <- Gen.choose(2.0, 5.0).map(_.cm)
            r1                              <- Gen.choose(2.0, 5.0).map(_.cm)
            r2                              <- Gen.choose(2.0, 5.0).map(_.cm)
            r3                              <- Gen.choose(2.0, 5.0).map(_.cm)
            injector_height                 <- Gen.choose(0.5, 2.0).map(_.cm)
        yield Firebox_V4.Ecolabeled(
            heat_output_reduced                        = heat_output_reduced,
            version                                    = version,
            air_intake_shape                           = air_intake_shape,
            firebox_depth                              = firebox_depth,
            firebox_width                              = firebox_width,
            firebox_height                             = firebox_height,
            height_of_first_row_of_air_injectors       = height_of_first_row,
            door_opening_width                         = door_opening_width,
            glass_width                                = glass_width,
            glass_height                               = glass_height,
            ash_pit_height                             = ash_pit_height,
            air_manifold_height                        = air_manifold_height,
            firebox_floor_thickness                    = firebox_floor_thickness,
            firebox_inner_wall_thickness               = firebox_inner_wall_thickness,
            firebox_outer_wall_thickness               = firebox_outer_wall_thickness,
            air_column_thickness                       = air_column_thickness,
            width_between_two_air_columns_sides        = width_between_sides,
            width_between_two_air_columns_rear         = width_between_rear,
            reinforcement_bars_offset_in_corners_R1    = r1,
            reinforcement_bars_offset_in_corners_R2    = r2,
            reinforcement_bars_offset_in_corners_R3    = r3,
            injector_height                            = injector_height
        )

    def genAFPMA_PRSE_V4: Gen[Firebox_V4.AFPMA_PRSE] =
        for
            heat_output_reduced                   <- genHeatOutputReduced
            outside_air_location                  <- Gen.const(
                afpma.firecalc.dto.common.OutsideAirLocationInHeater.FromBottom
            )
            outside_air_conduit_shape             <- genPipeShape
            firebox_depth                         <- genFireboxDimension
            firebox_width                         <- genFireboxDimension
            firebox_height                        <- Gen.choose(40.0, 100.0).map(_.cm)
            height_of_first_row                   <- Gen.choose(5.0, 15.0).map(_.cm)
            glass_width                           <- Gen.choose(15.0, 55.0).map(_.cm)
            glass_height                          <- Gen.choose(20.0, 50.0).map(_.cm)
            ash_pit_height                        <- Gen.choose(5.0, 15.0).map(_.cm)
            floor_thickness                       <- Gen.choose(5.0, 15.0).map(_.cm)
            combustion_air_manifold_height        <- Gen.choose(8.0, 15.0).map(_.cm)
            outside_air_inlet_lip                 <- Gen.choose(2.0, 8.0).map(_.cm)
            height_of_air_feed                    <- Gen.choose(5.0, 15.0).map(_.cm)
            number_of_air_columns_firebox         <- Gen.choose(1, 4)
            number_of_air_columns_door            <- Gen.choose(1, 4)
        yield Firebox_V4.AFPMA_PRSE(
            heat_output_reduced                        = heat_output_reduced,
            outside_air_location_in_heater             = outside_air_location,
            outside_air_conduit_shape                  = outside_air_conduit_shape,
            firebox_depth                              = firebox_depth,
            firebox_width                              = firebox_width,
            firebox_height                             = firebox_height,
            height_of_first_row_of_air_injectors       = height_of_first_row,
            glass_width                                = glass_width,
            glass_height                               = glass_height,
            ash_pit_height                             = ash_pit_height,
            floor_thickness                            = floor_thickness,
            combustion_air_manifold_height             = combustion_air_manifold_height,
            outside_air_inlet_lip                      = outside_air_inlet_lip,
            height_of_air_feed_to_columns              = height_of_air_feed,
            number_of_air_columns_feeding_firebox      = number_of_air_columns_firebox,
            number_of_air_columns_feeding_door         = number_of_air_columns_door
        )

    def genSingleTested_V4: Gen[Firebox_V4.SingleTested] =
        for
            test_standard              <- genTestStandard
            reference                  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
            type_of_appliance          <- Gen.oneOf(TypeOfAppliance.Pellets, TypeOfAppliance.WoodLogs)
            firebox_depth              <- genFireboxDimension
            firebox_width              <- genFireboxDimension
            firebox_height             <- Gen.choose(40.0, 100.0).map(_.cm)
            ash_pit_height             <- Gen.choose(5.0, 15.0).map(_.cm)
            efficiency_nominal         <- Gen.choose(70.0, 92.0).map(_.percent)
            efficiency_reduced         <- Gen.option(Gen.choose(50.0, 80.0).map(_.percent))
            heat_output_reduced        <- genHeatOutputReducedTested
            minimum_fuel_mass          <- Gen.option(Gen.choose(2.0, 8.0).map(_.kg))
            maximum_fuel_mass          <- Gen.choose(8.0, 30.0).map(_.kg)
            air_fuel_ratio_nominal     <- Gen.choose(3.0, 8.0).map(_.unitless)
            air_fuel_ratio_lowest      <- Gen.option(Gen.choose(2.0, 5.0).map(_.unitless))
            co2_dry_nominal            <- Gen.choose(8.0, 16.0).map(_.percent)
            co2_dry_lowest             <- Gen.option(Gen.choose(6.0, 12.0).map(_.percent))
            pellets_load_burn_duration <- Gen.option(Gen.choose(20.0, 120.0).map(_.minutes))
            mean_firebox_temperature   <- Gen.option(Gen.choose(200.0, 600.0).map(_.degreesCelsius))
            t_burnout                  <- Gen.choose(500.0, 900.0).map(_.degreesCelsius)
            is_glass_below_one_fifth   <- Gen.oneOf(true, false)
            glass_area                 <- Gen.choose(500.0, 2000.0).map(_.cm2)
            emissions_values           <- genEmissionsAndEfficiencyValues_DTO
        yield Firebox_V4.SingleTested(
            test_standard                          = test_standard,
            reference                              = reference,
            type_of_appliance                      = type_of_appliance,
            firebox_depth                          = firebox_depth,
            firebox_width                          = firebox_width,
            firebox_height                         = firebox_height,
            ash_pit_height                         = ash_pit_height,
            efficiency_nominal                     = efficiency_nominal,
            efficiency_reduced                     = efficiency_reduced,
            heat_output_reduced                    = heat_output_reduced,
            minimum_fuel_mass                      = minimum_fuel_mass,
            maximum_fuel_mass                      = maximum_fuel_mass,
            air_fuel_ratio_nominal                 = air_fuel_ratio_nominal,
            air_fuel_ratio_lowest                  = air_fuel_ratio_lowest,
            co2_dry_nominal                        = co2_dry_nominal,
            co2_dry_lowest                         = co2_dry_lowest,
            pellets_load_burn_duration             = pellets_load_burn_duration,
            mean_firebox_temperature               = mean_firebox_temperature,
            t_burnout                              = t_burnout,
            is_glass_surface_ratio_below_one_fifth = is_glass_below_one_fifth,
            glass_area                             = glass_area,
            emissions_values                       = emissions_values
        )

    def genFirebox_V4: Gen[Firebox_V4] =
        Gen.frequency(
            (4, genTraditional_V4),
            (3, genEcolabeled_V4),
            (2, genAFPMA_PRSE_V4),
            (1, genSingleTested_V4)
        )

end Firebox_V4_Generators
