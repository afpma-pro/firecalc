/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1
import io.taig.babel.StringFormat2
import io.taig.babel.StringFormat3
import io.taig.babel.StringFormat4
import io.taig.babel.StringFormat5

object I18nData_Common3:

    case class BuilderErrors(
        errors_in_other_section_type: String,
        results_not_computed        : String
    )

    case class ForbiddenDto(
        _self                       : String,
        generic                     : StringFormat1,
        set_number_of_flows         : StringFormat3,
        set_inner_shape_prevent_auto: StringFormat1,
        element_at                  : StringFormat1
    )

    case class TopologyErrors(
        missing_chimney                : String,
        chimney_not_last               : String,
        missing_terminal_connector_slot: String,
        head_region_ends_with_connector: String
    )

    case class EN16510(
        η_s: String
    )

    case class Warnings(
        firebox_afpma_prse_not_validated: String
    )

    case class TestReportI18n(
        _self: String,
        name : String,
        date : String
    )

    case class TestEmissionValueI18n(
        _self        : String,
        polluant_name: String,
        value        : String,
        test_method  : String,
        o2ref        : String
    )

    case class EmissionValuesI18n(
        _self: String,
        co   : String,
        dust : String,
        ogc  : String,
        nox  : String
    )

    case class CountryNames(
        france  : String,
        belgique: String
    )

    case class MecaFlu(
        errors: MecaFlu_Errors
    )

    case class MecaFlu_Errors(
        // Thermal resistance errors (already using I18N)
        thermal_resistance_not_applicable_for_combustion_air: String,
        thermal_resistance_requires_straight_section        : StringFormat1,
        thermal_resistance_calculation_errors               : StringFormat1,
        heat_transfer_coefficient_errors                    : StringFormat1,
        mean_temperature_calculation_errors                 : StringFormat1,
        no_straight_section_for_temperature_calc            : StringFormat1,
        missing_upstream_seed_values                        : StringFormat1,

        // Firebox type errors
        unexpected_firebox_type: StringFormat1,

        // Pipe type errors
        unexpected_pipe_type: StringFormat1,

        // Cross section errors
        could_not_determine_cross_section_area: StringFormat1,

        // Air space errors
        could_not_determine_air_space_detailed: StringFormat1,

        // Ratio validation errors
        use_unsafe_to_skip_ratio_validation: StringFormat1,

        // Dynamic friction errors
        dynamic_friction_error: StringFormat1,

        // Temperature errors
        invalid_chimney_wall_temperature: StringFormat1,

        // Exception errors
        unexpected_throwable: StringFormat1
    )

    case class EN15544_Errors(
        invalid_pressure_requirement                    : StringFormat1,
        efficiency_is_too_low                           : StringFormat2,
        local_struct_error                              : StringFormat1,
        singular_flow_resistance_coeff_error            : StringFormat1,
        missing_alpha3_angle_for_short_flue_pipe_section: StringFormat1,
        could_not_select_coeff_for_interpolation        : StringFormat2,
        unexpected_ratio_ld_dh                          : StringFormat1,
        no_given_ratio_ld_dh                            : String,
        invalid_shape_parameter                         : StringFormat2,
        value_out_of_bound                              : StringFormat5,
        could_not_compute_individual_coeff              : StringFormat2,
        pressure_requirement_display                    : StringFormat3,
        missing_section_geometry_change                 : StringFormat4,
        can_not_start_with_a_direction_change           : StringFormat1,
        can_not_end_with_a_direction_change             : StringFormat1,
        two_successive_direction_change_not_allowed     : StringFormat2,
        two_successive_straight_section_not_allowed     : StringFormat2,
        holes_should_not_happen                         : StringFormat1,
        flue_pipe_length_below_minimum                  : StringFormat2,
        direction_change_not_in_pipe_chain              : StringFormat2,
        split_merge_90_at_end_of_chain                  : StringFormat2
    )
