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
import io.taig.babel.StringFormat6
import io.taig.babel.StringFormat7

object I18nData_Common1:

    case class DirectionBadge(
        label                                     : String,
        tooltip_direction                         : String,
        tooltip_azimuth                           : StringFormat1,
        tooltip_elevation                         : StringFormat1,
        tooltip_roll                              : StringFormat1,
        tooltip_convention_up                     : String,
        tooltip_convention_horizontal             : String,
        tooltip_convention_down                   : String,
        cardinal_up                               : String,
        cardinal_down                             : String,
        cardinal_rear                             : String,
        cardinal_front                            : String,
        cardinal_right                            : String,
        cardinal_left                             : String,
        cardinal_rear_right                       : String,
        cardinal_front_right                      : String,
        cardinal_front_left                       : String,
        cardinal_rear_left                        : String,
        relative_left                             : String,
        relative_right                            : String,
        relative_up                               : String,
        relative_down                             : String,
        relative_theta                            : String,
        relative_dir_label                        : String,
        abs_dir_label                             : String,
        direction_incompatible_warning            : String,
        firebox_split_direction_overridden_warning: String,
        cardinal_horizontal                       : String,
        custom_btn                                : String,
        custom_dialog_title                       : String,
        custom_option                             : StringFormat1,
        az_el                                     : StringFormat2
    )

    case class AddElement(
        _self                              : String,
        add_section_element                : String,
        add_direction_change_element       : String,
        AddSectionSlopped                  : String,
        AddSectionHorizontal               : String,
        AddSectionVertical                 : String,
        AddAngleAdjustable                 : String,
        AddSharpeAngle_0_to_180            : String,
        AddSharpeAngle_0_to_90             : String,
        AddSharpeAngle_0_to_90_Unsafe      : String,
        AddSmoothCurve_90                  : String,
        AddSmoothCurve_90_Unsafe           : String,
        AddCircularArc_60                  : String,
        AddSmoothCurve_60                  : String,
        AddSmoothCurve_60_Unsafe           : String,
        AddElbows_2x45                     : String,
        AddElbows_3x30                     : String,
        AddElbows_4x22p5                   : String,
        AddPressureDiff                    : String,
        AddSectionDecrease                 : String,
        AddSectionIncrease                 : String,
        AddSectionShapeChange              : String,
        AddFlowResistance                  : String,
        AddFlowResistance_butterfly_damper : String,
        AddFlowResistance_wire_mesh_screen : String,
        AddFlowResistance_solid_steel_grate: String,
        cross_section                      : String,
        name                               : String,
        zeta                               : String
    )

    case class Address(
        header      : String,
        num         : String,
        street      : String,
        zip         : String,
        city        : String,
        region_state: String,
        country     : String
    )

    case class AppendLayerDescr(
        _self                              : String,
        FromLambda                         : String,
        FromLambdaUsingThickness           : String,
        FromThermalResistanceUsingThickness: String,
        FromThermalResistance              : String,
        AirSpaceUsingOuterShape            : String,
        AirSpaceUsingThickness             : String
    )

    case class Customer(
        first_name: String,
        last_name : String,
        phone     : String,
        email     : String
    )

    case class Errors(
        term_should_be_defined                 : StringFormat2,
        term_should_be_greater_or_eq_than      : StringFormat3,
        term_should_be_greater_than            : StringFormat3,
        term_should_be_less_or_eq_than         : StringFormat3,
        term_should_be_less_than               : StringFormat3,
        term_should_be_between_inclusive       : StringFormat4,
        term_constraint_min_error              : StringFormat3,
        term_constraint_max_error              : StringFormat3,
        glass_area_too_large                   : StringFormat2,
        glass_surface_ratio_not_confirmed      : String,
        t_burnout_not_set                      : String,
        firebox_height_out_of_range            : StringFormat3,
        inconsistent_max_load_accross_inputs   : StringFormat3,
        firebox_base_surface_not_in_range      : StringFormat3,
        firebox_base_ratio_invalid             : StringFormat5,
        firebox_base_min_width                 : StringFormat2,
        co2_calculation_only_for_wood_boilers  : String,
        injector_velocity_below_minimum        : StringFormat2,
        injector_velocity_above_maximum        : StringFormat2,
        flue_gas_velocity_error_single_boundary: StringFormat6,
        flue_gas_velocity_error_both_boundaries: StringFormat6,
        velocity_position_at_start             : String,
        velocity_position_at_end               : String,
        missing_flow_rate                      : String,
        air_intake_pipe_shape_mismatch         : StringFormat2,
        air_intake_pipe_shape_topology_mismatch: StringFormat2,
        value_out_of_bound                     : StringFormat5,
        could_not_interpolate                  : StringFormat7,
        empty_data_set                         : String,
        parse_error                            : StringFormat1,
        value_out_of_range                     : StringFormat2,
        value_out_of_range_with_range          : StringFormat6,
        missing_grid_point                     : StringFormat2,
        firebox_type_disabled                  : StringFormat1
    )

    case class Headers(
        firebox_description                : String,
        client_project_data                : String,
        constraints_validation             : String,
        efficiencies_values                : String,
        emissions_and_efficiency_values    : String,
        estimated_output_temperatures_15544: String,
        flue_gas_triple_of_variates        : String,
        heating_appliance_for_13384        : String,
        local_conditions                   : String,
        pipes_details                      : String,
        pressure_requirements_13384        : String,
        pressure_requirements_15544        : String,
        project_description                : String,
        reference_temperatures             : String,
        technical_specifications           : String,
        temperature_requirements_13384     : String,
        temperature_requirements_15544     : String,
        temperature_at_chimney_outlet      : String,
        wind_pressure                      : String
    )

    case class HeatOutputReduced(
        defined_as_default        : StringFormat1,
        defined_as_half_of_nominal: String,
        defined_when_tested       : StringFormat1
    )

    case class Panels(
        air_intake                                : String,
        air_distribution                          : String,
        chimney_pipe                              : String,
        client_project                            : String,
        firebox                                   : String,
        connector_pipe                            : String,
        channel_pipe                              : String,
        geographical_location_and_external_factors: String,
        output_and_other_parameters               : String,
        total                                     : String,
        channel_pipe_length_with_min              : StringFormat2,
        channel_pipe_length                       : StringFormat1,
        channel_pipe_length_with_cum              : StringFormat2,
        channel_pipe_length_with_cum_and_min      : StringFormat3,
        channel_pipe_length_min_suffix            : StringFormat1
    )

    case class SplitMerge(
        _self                                   : String,
        SplitSingleFlowIntoTwoFlowsWith90DegTurn: String,
        MergeTwoFlowsIntoSingleWith90DegTurn    : String,
        newInnerShape                           : String,
        symmetryPlaneAbsDir                     : String,
        collinearVectors                        : String,
        absDirVertical                          : String,
        offset                                  : String
    )

    case class TechnicalSpecficiations(
        sizing_method                           : String,
        nominal_heat_output                     : String,
        nominal_heat_output_short               : String,
        heating_cycle                           : String,
        maximum_load                            : String,
        maximum_load_short                      : String,
        minimum_load                            : String,
        facing_type                             : String,
        facing_type_sentence                    : StringFormat1,
        inner_construction_material             : String,
        inner_construction_material_within_specs: String
    )

    case class Terms(
        name                    : String,
        angle                   : String,
        area                    : String,
        azimuth                 : String,
        curvature_radius        : String,
        diameter                : String,
        absolute_direction      : String,
        height                  : String,
        horizontal_length       : String,
        inclination             : String,
        inner_shape             : String,
        length                  : String,
        number_of_parallel_flows: String,
        number_of_segments      : String,
        outer_shape             : String,
        pipe_shape              : I18nData_Common2.PipeShape,
        pressure_difference     : String,
        radius                  : String,
        roll                    : String,
        roughness               : String,
        side                    : String,
        speed                   : String,
        temperature             : String,
        temperature_celsius     : String,
        temperature_farenheit   : String,
        temperature_kelvin      : String,
        thermal_conductivity_λ  : String,
        thermal_resistance_Rth  : String,
        thickness               : String,
        undefined               : String,
        elevation_gain          : String,
        width                   : String,
        width_to_height_ratio   : String,
        x                       : String,
        y                       : String,
        z                       : String,
        zeta                    : String,
        zeta_ζ                  : String
    )
