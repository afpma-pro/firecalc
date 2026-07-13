/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1

object I18nData_EN13384:

    case class EN13384(
        air_space_detailed                              : String,
        _air_space_detailed                             : EN13384.AirSpaceDetailed,
        air_space_ventilated                            : String,
        air_space_dead                                  : String,
        _ambiant_air_temperatures                       : EN13384.AmbiantAirTemperatures,
        ambiant_air_temperatures                        : String,
        _ambiant_air_temperature                        : EN13384.AmbiantAirTemperature,
        duct_type_non_concentric_high_thermal_resistance: String,
        duct_type_non_concentric_low_thermal_resistance : String,
        duct_type_concentric                            : String,
        flue_gas_conditions                             : String,
        flue_gas_conditions_wet                         : String,
        flue_gas_conditions_dry                         : String,
        exterior_air_temperature                        : String,
        materials                                       : EN13384_Materials,
        requirements                                    : EN13384.Requirements,
        reuse_tuo                                       : String,
        terms                                           : EN13384_Terms,
        unheated_area_height                            : String,
        errors                                          : EN13384_Errors
    )

    object EN13384:

        case class AirSpaceDetailed(
            without_air_space: String,
            _ventil_direction: AirSpaceDetailed.VentilDirection,
            ventil_direction : String,
            _ventil_openings : AirSpaceDetailed.VentilOpenings,
            ventil_openings  : String,
            with_air_space   : String
        )
        object AirSpaceDetailed:
            case class VentilDirection(
                undefined_dir         : String,
                same_dir_as_fluegas   : String,
                inverse_dir_as_fluegas: String
            )
            case class VentilOpenings(
                no_opening                                  : String,
                annular_area_fully_opened                   : String,
                partially_opened_in_accordance_with_dtu_24_1: String
            )

        case class AmbiantAirTemperature(
            long : String,
            short: String
        )

        case class AmbiantAirTemperatures(
            at_chimney_outlet : String,
            for_boiler_room   : String,
            for_heated_areas  : String,
            for_exterior_areas: String,
            for_unheated_areas: String
        )
        case class Requirements(
            no_condensation: String
        )

    case class EN13384_Materials(
        WeldedSteel    : String,
        Glass          : String,
        Plastic        : String,
        Aluminium      : String,
        ClayFlueLiners : String,
        Bricks         : String,
        SolderedMetal  : String,
        Concrete       : String,
        Fibrociment    : String,
        Masonry        : String,
        CorrugatedMetal: String
    )

    case class EN13384_Terms(
        P_B       : String,
        P_L       : String,
        P_Z       : String,
        P_Ze      : String,
        P_Zmax    : String,
        P_Zemax   : String,
        P_ZO      : String,
        P_ZOmin   : String,
        P_ZOe     : String,
        P_ZOemin  : String,
        P_Zexcess : String,
        P_ZVexcess: String,
        T_ig      : String,
        T_ob      : String,
        T_iob     : String,
        T_sp      : String
    )

    case class EN13384_Errors(
        side_ratio_too_high_for_rectangular_form             : StringFormat1,
        cannot_end_layers_description_on_dead_air_space      : String,
        could_not_compute_thermal_resistance                 : StringFormat1,
        en13384_error_message                                : StringFormat1,
        duct_type_error                                      : StringFormat1,
        no_outside_surface_found                             : StringFormat1,
        zero_length_pipe                                     : StringFormat1,
        re_is_above_10million                                : StringFormat1,
        psi_ratio_is_greater_than_3                          : StringFormat1,
        prandtl_too_small                                    : StringFormat1,
        prandtl_too_big                                      : StringFormat1,
        no_outside_surface_for_tu_calculation                : String,
        invalid_duct_type_only_non_concentric_high_resistance: String
    )
