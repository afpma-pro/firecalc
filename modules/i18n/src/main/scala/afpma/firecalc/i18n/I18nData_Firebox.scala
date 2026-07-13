/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1
import io.taig.babel.StringFormat3

object I18nData_Firebox:

    case class Firebox_15544(
        firebox_depth                              : String,
        firebox_width                              : String,
        firebox_height                             : String,
        ash_pit_height                             : String,
        afpma_prse                                 : Firebox_15544.AFPMA_PRSE,
        base_geometry                              : String,
        dimensions_summary_w_d_h                   : StringFormat3,
        firebox_glass_surface_ratio_below_one_fifth: String,
        glass_area                                 : String,
        ref                                        : String,
        typ                                        : String,
        load_size_nominal                          : String,
        ecolabeled                                 : Firebox_15544.Ecolabeled,
        tested                                     : Firebox_15544.Tested,
        traditional                                : Firebox_15544.Traditional,
        single_tested                              : Firebox_15544.SingleTested,
        door_15a_firebox                           : Firebox_15544.Door15aFirebox,
        air_intake_pipe_missing_warning            : String
    )

    object Firebox_15544:

        case class AFPMA_PRSE(
            outside_air_location_in_heater       : String,
            outside_air_location_from_bottom     : String,
            outside_air_conduit_shape            : String,
            height_of_first_row_of_air_injectors : String,
            ash_pit_height                       : String,
            floor_thickness                      : String,
            combustion_air_manifold_height       : String,
            outside_air_inlet_lip                : String,
            height_of_air_feed_to_columns        : String,
            number_of_air_columns_feeding_firebox: String,
            number_of_air_columns_feeding_door   : String
        )

        case class Ecolabeled(
            version                                  : String,
            version_1_with_airbox                    : String,
            version_2_without_airbox                 : String,
            version_2_air_intake_shape               : String,
            door_opening_width                       : String,
            glass_width                              : String,
            glass_height                             : String,
            ash_pit_height_AF                        : String,
            height_of_first_row_of_air_injectors_X   : String,
            distance_between_air_injectors_Y         : String,
            air_manifold_height_W                    : String,
            firebox_floor_thickness                  : String,
            inner_wall_thickness_D1                  : String,
            outer_wall_thickness_D2                  : String,
            air_column_thickness_S                   : String,
            width_between_two_air_columns_sides_E    : String,
            width_between_two_air_columns_rear_E     : String,
            reinforcement_bars_offset_in_corners     : String,
            reinforcement_bars_offset_in_corners_R1  : String,
            reinforcement_bars_offset_in_corners_R2  : String,
            reinforcement_bars_offset_in_corners_R3  : String,
            injector_height_Z                        : String,
            injector_surface_area                    : String,
            injector_surface_area_obstructed_max_perc: StringFormat1,
            injector_width_rear_wall_Lr              : String,
            injector_width_side_wall_Ls              : String,
            injector_width_door_wall_Lt              : String,
            injector_height_door_wall_Zt             : String,
            air_column_thickness_door_wall_St        : String,
            computed_values                          : String
        )

        case class Tested(
            efficiency_nominal         : String,
            efficiency_reduced         : String,
            heat_output_nominal        : String,
            heat_output_reduced        : String,
            load_size_nominal          : String,
            load_size_reduced          : String,
            air_to_fuel_ratio_nominal  : String,
            air_to_fuel_ratio_reduced  : String,
            co2_perc_by_vol_dry_nominal: String,
            co2_perc_by_vol_dry_reduced: String,
            average_firebox_temperature: String,
            firebox_exit_temperature   : String
        )

        case class Traditional(
            firebox_floor_shape                  : String,
            width_to_depth_ratio                 : String,
            pressure_loss_coefficient_from_door  : String,
            total_air_intake_surface_area_on_door: String,
            glass_surface_area                   : String,
            glass_height                         : String,
            glass_width                          : String,
            air_injector_surface_area            : String,
            air_manifold_height_W                : String,
            firebox_floor_thickness              : String,
            ash_pit_height_AF                    : String
        )

        case class SingleTested(
            test_standard                         : String,
            reference                             : String,
            efficiency_nominal                    : String,
            efficiency_reduced                    : String,
            heat_output_reduced                   : String,
            minimum_fuel_mass                     : String,
            maximum_fuel_mass                     : String,
            air_fuel_ratio_nominal                : String,
            air_fuel_ratio_lowest                 : String,
            co2_dry_nominal                       : String,
            co2_dry_lowest                        : String,
            pellets_load_burn_duration            : String,
            mean_firebox_temperature              : String,
            t_burnout                             : String,
            is_glass_surface_ratio_below_one_fifth: String,
            emissions_firebox_name                : String,
            emissions_accredited_body             : String,
            emissions_co                          : String,
            emissions_dust                        : String,
            emissions_ogc                         : String,
            emissions_nox                         : String
        )

        case class Door15aFirebox(
            sb                             : String,
            sb_min                         : String,
            sb_max                         : String,
            mb_min                         : String,
            mb_max                         : String,
            load_size_nominal              : String,
            pressure_loss_table            : String,
            expected_air_intake_pipe_shapes: String,
            actual_air_intake_pipe_shape   : String
        )
