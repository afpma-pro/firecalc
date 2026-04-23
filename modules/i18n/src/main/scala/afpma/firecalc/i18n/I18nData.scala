/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import afpma.firecalc.i18n.I18nData.*

import io.taig.babel.*
import io.taig.babel.StringFormat1
import io.taig.babel.StringFormat2
import io.taig.babel.StringFormat3
import io.taig.babel.StringFormat4
import io.taig.babel.StringFormat5
import io.taig.babel.StringFormat6

trait LocalizedAlg:
    val language: Language
    given Locale = Locale(language)

type Localized[A] = Locale ?=> A
object Localized:
    extension [A](la: Localized[A]) def map[B](f: A => B): Localized[B] = f(la)

case class LocalizedString(f: Locale => String) {
    def map (g      : String => String): LocalizedString = LocalizedString(f andThen g)
    def show(using l: Locale          ): String          = f(l)
}
object LocalizedString:
    def from(ls: Locale ?=> String) = LocalizedString(loc => ls(using loc))

final case class I18nData(
    add_element                    : AddElement,
    address                        : Address,
    append_layer_descr             : AppendLayerDescr,
    builder_errors                 : BuilderErrors,
    customer                       : Customer,
    firebox                        : Firebox_15544,
    firebox_names                  : FireboxNames,
    draft_min                      : String,
    draft_max                      : String,
    emissions_and_efficiency_values: EmissionsAndEfficiencyValues,
    en13384                        : EN13384,
    en15544                        : EN15544,
    en15544_errors                 : EN15544_Errors,
    topology_errors                : TopologyErrors,
    en16510                        : EN16510,
    errors                         : Errors,
    facing_type                    : FacingType,
    headers                        : Headers,
    heat_output_reduced            : HeatOutputReduced,
    heating_appliance              : HeatingAppliance,
    incremental_validation         : IncrementalValidation,
    inputs_data                    : String,
    inputs_error                   : Inputs_Error,
    local_conditions               : LocalConditions,
    local_regulations              : LocalRegulations,
    mecaflu                        : MecaFlu,
    min_load                       : MinLoad,
    no                             : String,
    panels                         : Panels,
    pipe_location                  : PipeLocation,
    pipe_type                      : PipeType,
    pollutant_names                : PolluantNames,
    pressure_requirements          : String,
    project_description            : ProjectDescription,
    set_prop                       : SetProp,
    stove_params                   : String,
    subtotal                       : String,
    technical_specifications       : TechnicalSpecficiations,
    terms                          : Terms,
    total                          : String,
    type_of_appliance              : TypeOfAppliance,
    type_of_load                   : TypeOfLoad,
    units                          : Units,
    yes                            : String,
    area_name                      : String,
    area_heating_status            : AreaHeatingStatus,
    reports                        : Reports,
    warnings                       : Warnings,
    not_defined                    : String,
    not_respected                  : String,
    missing_data                   : String,
    test_report                    : TestReportI18n,
    test_emission_value            : TestEmissionValueI18n,
    emission_values                : EmissionValuesI18n
)

object I18nData:

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
        ecolabeled                                 : Firebox_15544.Ecolabeled,
        tested                                     : Firebox_15544.Tested,
        traditional                                : Firebox_15544.Traditional,
        single_tested                              : Firebox_15544.SingleTested,
        door_15a_firebox                           : Firebox_15544.Door15aFirebox
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

    case class FireboxNames(
        traditional      : String,
        ecolabeled       : String,
        ecolabeled_v1    : String,
        ecolabeled_v2    : String,
        afpma_prse       : String,
        certified        : String,
        custom_lab_tested: String,
        single_tested    : String,
        door_15a_firebox : String
    )

    case class PolluantNames(
        _self   : String,
        CO      : String,
        Dust    : String,
        OGC     : String,
        NOx     : String,
        Dust_OGC: String
    )

    case class FacingType(
        with_air_gap   : String,
        without_air_gap: String
    )

    case class EmissionsAndEfficiencyValues(
        _self                            : String,
        accredited_or_notified_body      : String,
        firebox_name                     : String,
        test_reports                     : String,
        emissions_values                 : String,
        min_efficiency_firebox_reduced   : String,
        min_efficiency_firebox_nominal   : String,
        min_efficiency_full_stove_reduced: String,
        min_efficiency_full_stove_nominal: String,
        xxx_at_NpO2                      : StringFormat2
    )

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

    case class EN15544(
        angle_to_original_direction: String,
        materials                  : EN15544_Materials,
        pressure_requirements      : EN15544_PressureRequirements,
        terms                      : EN15544_Terms,
        terms_xtra                 : EN15544_Terms_Xtra
    )

    case class EN15544_Materials(
        blocs_de_chamotte : String,
        tuyaux_en_chamotte: String
    )

    case class EN15544_PressureRequirements(
        sum_of_all_resistances: String,
        sum_of_all_buyoancies : String,
        pressure_difference   : String
    )

    case class EN15544_TermDef(
        name : String,
        descr: String
    )

    case class EN15544_Terms(
        GlassArea  : EN15544_TermDef,
        L_N        : EN15544_TermDef,
        A_BR       : EN15544_TermDef,
        H_BR       : EN15544_TermDef,
        O_BR       : EN15544_TermDef,
        m_BU       : EN15544_TermDef,
        U_BR       : EN15544_TermDef,
        A_GS       : EN15544_TermDef,
        L_Z        : EN15544_TermDef,
        m_B        : EN15544_TermDef,
        m_B_min    : EN15544_TermDef,
        P_n        : EN15544_TermDef,
        P_n_reduced: EN15544_TermDef,
        t_n        : EN15544_TermDef
    )

    case class EN15544_Terms_Xtra(
        n_min                       : EN15544_TermDef,
        height_of_the_lowest_opening: EN15544_TermDef,
        Table_1_Factor_a            : EN15544_TermDef,
        Table_1_Factor_b            : EN15544_TermDef,
        t_ext                       : EN15544_TermDef,
        m_G                         : EN15544_TermDef,
        m_L                         : EN15544_TermDef,
        t_outside_air_mean          : EN15544_TermDef,
        t_combustion_air            : EN15544_TermDef,
        t_BR                        : EN15544_TermDef,
        t_burnout                   : EN15544_TermDef,
        t_fluepipe                  : EN15544_TermDef,
        t_connector_pipe            : EN15544_TermDef,
        t_connector_pipe_mean       : EN15544_TermDef,
        c_P                         : EN15544_TermDef,
        σ_CO2                       : EN15544_TermDef,
        σ_H2O                       : EN15544_TermDef,
        η                           : EN15544_TermDef,
        t_F                         : EN15544_TermDef,
        t_flue_gas                  : EN15544_TermDef,
        RequiredDeliveryPressure    : EN15544_TermDef,
        t_chimney_wall_top_out      : EN15544_TermDef,
        t_chimney_out               : EN15544_TermDef,
        t_stove_out                 : EN15544_TermDef,
        necessary_delivery_pressure : EN15544_TermDef,
        flue_gas_mass_rate          : EN15544_TermDef,
        t_BU                        : EN15544_TermDef
    )

    case class EN16510(
        η_s: String
    )

    case class Errors(
        term_should_be_greater_or_eq_than    : StringFormat3,
        term_should_be_greater_than          : StringFormat3,
        term_should_be_less_or_eq_than       : StringFormat3,
        term_should_be_less_than             : StringFormat3,
        term_should_be_between_inclusive     : StringFormat4,
        term_constraint_min_error            : StringFormat3,
        term_constraint_max_error            : StringFormat3,
        glass_area_too_large                 : StringFormat2,
        glass_surface_ratio_not_confirmed    : String,
        firebox_height_out_of_range          : StringFormat3,
        firebox_base_surface_not_in_range    : StringFormat3,
        firebox_base_ratio_invalid           : StringFormat5,
        firebox_base_min_width               : StringFormat2,
        co2_calculation_only_for_wood_boilers: String,
        injector_velocity_below_minimum      : StringFormat2,
        injector_velocity_above_maximum      : StringFormat2,
        flue_gas_velocity_error_single_boundary  : StringFormat6,
        flue_gas_velocity_error_both_boundaries  : StringFormat6,
        velocity_position_at_start               : String,
        velocity_position_at_end                 : String,
        missing_flow_rate                    : String,
        air_intake_pipe_shape_mismatch       : StringFormat2,
        value_out_of_bound                   : StringFormat5,
        could_not_interpolate                : StringFormat7,
        empty_data_set                       : String,
        parse_error                          : StringFormat1,
        value_out_of_range                   : StringFormat2,
        value_out_of_range_with_range        : StringFormat6,
        missing_grid_point                   : StringFormat2
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

    case class HeatingAppliance(
        efficiency_nominal: String,
        efficiency_reduced: String,
        fluegas           : HeatingAppliance.FlueGas,
        powers            : HeatingAppliance.Powers,
        temperatures      : HeatingAppliance.Temperatures,
        massFlows         : HeatingAppliance.MassFlows,
        pressures         : HeatingAppliance.Pressures,
        volumeFlows       : HeatingAppliance.VolumeFlows
    )

    object HeatingAppliance:
        case class FlueGas(
            co2_dry_perc_nominal: String,
            co2_dry_perc_reduced: String,
            h2o_perc_nominal    : String,
            h2o_perc_reduced    : String
        )
        case class Powers(
            heat_output_nominal: String,
            heat_output_reduced: String
        )
        case class Temperatures(
            flue_gas_temp_nominal: String,
            flue_gas_temp_reduced: String
        )
        case class MassFlows(
            flue_gas_mass_flow_nominal      : String,
            flue_gas_mass_flow_reduced      : String,
            combustion_air_mass_flow_nominal: String,
            combustion_air_mass_flow_reduced: String
        )
        case class Pressures(
            underPressure         : String,
            underPressure_negative: String,
            underPressure_positive: String,
            flue_gas_draft_min    : String,
            flue_gas_draft_max    : String,
            flue_gas_pdiff_min    : String,
            flue_gas_pdiff_max    : String
        )

        case class VolumeFlows(
            flue_gas_volume_flow_nominal      : String,
            flue_gas_volume_flow_reduced      : String,
            combustion_air_volume_flow_nominal: String,
            combustion_air_volume_flow_reduced: String
        )

    case class Inputs_Error(
        invald_type_of_appliance         : Inputs_Error.InvalidTypeOfAppliance,
        stove_params_sizing_input_missing: String
    )

    object Inputs_Error:
        case class InvalidTypeOfAppliance(
            pellets_incompatible_with_wood_log_fuel_type : String,
            wood_logs_incompatible_with_pellets_fuel_type: String
        )

    case class LocalConditions(
        altitude           : String,
        coastal_region     : String,
        chimney_termination: LocalConditions.ChimneyTermination
    )

    object LocalConditions:
        case class ChimneyTermination(
            explain                 : String,
            chimney_location_on_roof: ChimneyTermination.ChimneyLocationOnRoof,
            adjacent_buildings      : ChimneyTermination.AdjacentBuildings
        )
        object ChimneyTermination:
            import ChimneyLocationOnRoof.*
            case class ChimneyLocationOnRoof(
                explain                                              : String,
                chimney_height_above_ridgeline                       : ChimneyHeightAboveRidgeline,
                horizontal_distance_between_chimney_and_ridgeline    : HorizontalDistanceBetweenChimneyAndRidgeline,
                slope                                                : Slope,
                outside_air_intake_and_chimney_locations             : OutsideAirIntakeAndChimneyLocations,
                horizontal_distance_between_chimney_and_ridgeline_bis: HorizontalDistanceBetweenChimneyAndRidgelineBis
            )
            object ChimneyLocationOnRoof:
                case class ChimneyHeightAboveRidgeline(
                    explain       : String,
                    more_than_40cm: String,
                    less_than_40cm: String
                )
                case class HorizontalDistanceBetweenChimneyAndRidgeline(
                    explain       : String,
                    less_than_2m30: String,
                    more_than_2m30: String
                )
                case class Slope(
                    explain                : String,
                    less_than_25deg        : String,
                    between_25deg_and_40deg: String,
                    more_than_40deg        : String
                )
                case class OutsideAirIntakeAndChimneyLocations(
                    explain                        : String,
                    on_different_sides_of_the_ridge: String,
                    on_same_side_of_the_ridge      : String
                )
                case class HorizontalDistanceBetweenChimneyAndRidgelineBis(
                    explain     : String,
                    less_than_1m: String,
                    more_than_1m: String
                )

            import AdjacentBuildings.*
            case class AdjacentBuildings(
                explain                                                   : String,
                horizontal_distance_between_chimney_and_adjacent_buildings: HorizontalDistanceBetweenChimneyAndAdjacentBuildings,
                horizontal_angle_between_chimney_and_adjacent_buildings   : HorizontalAngleBetweenChimneyAndAdjacentBuildings,
                vertical_angle_between_chimney_and_adjacent_buildings     : VerticalAngleBetweenChimneyAndAdjacentBuildings
            )
            object AdjacentBuildings:
                case class HorizontalDistanceBetweenChimneyAndAdjacentBuildings(
                    explain      : String,
                    less_than_15m: String,
                    more_than_15m: String
                )
                case class HorizontalAngleBetweenChimneyAndAdjacentBuildings(
                    explain        : String,
                    more_than_30deg: String,
                    less_than_30deg: String
                )
                case class VerticalAngleBetweenChimneyAndAdjacentBuildings(
                    explain                      : String,
                    more_than_10deg_above_horizon: String,
                    less_than_10deg_above_horizon: String
                )

    case class LocalRegulations(
        regulation_ref: String,
        country       : String
    )

    case class MinLoad(
        defined_as_half_of_nominal: StringFormat1,
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

    case class PipeShape(
        _self    : String,
        circle   : String,
        rectangle: String,
        square   : String
    )

    case class PipeLocation(
        _column_header     : String,
        short              : String,
        boiler_room        : String,
        heated_area        : String,
        unheated_inside    : String,
        outside_or_exterior: String,
        custom_area        : String
    )

    case class PipeType(
        air_intake    : String,
        combustion_air: String,
        firebox       : String,
        connector     : String,
        channel       : String,
        chimney       : String
    )

    case class ProjectDescription(
        reference_and_filename: String,
        date                  : String,
        country               : String
    )

    case class SetProp(
        _self                            : String,
        _geometric_properties            : String,
        _position_and_direction          : String,
        _material_and_roughness          : String,
        define_layers                    : String,
        SetPropertiesInBatch             : String,
        SetInnerShape                    : String,
        SetOuterShape                    : String,
        SetThickness                     : String,
        SetRoughness                     : String,
        SetMaterial                      : String,
        SetLayer                         : String,
        SetLayers                        : String,
        SetAirSpaceAfterLayers           : String,
        SetPipeLocation                  : String,
        SetDuctType                      : String,
        SetNumberOfFlows                 : String,
        SetNumberOfFlows_fieldName       : String,
        SetNumberOfFlows_NumberOfChannels: String,
        SetNumberOfFlows_Join            : String,
        SetInitialDirection              : String,
        SetInitialPosition               : String,
        SetFinalPosition                 : String,
        LinedFlue                        : String,
        LinedFlue_liner                  : String,
        LinedFlue_casing                 : String,
        LinedFlue_sync_casing            : String,
        LinedFlue_sync_airspace          : String,
        shortcuts                        : SetProp.Shortcuts
    )

    object SetProp:
        case class Shortcuts(
            start_a_new_pipe : String,
            add_new_connector: String
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
        pipe_shape              : PipeShape,
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

    case class TypeOfAppliance(
        descr   : String,
        pellets : String,
        woodlogs: String
    )

    case class TypeOfLoad(
        descr  : String,
        nominal: String,
        reduced: String
    )

    final case class Units(
        btu_per_hour                : String,
        celsius                     : String,
        centimeter                  : String,
        degree                      : String,
        foot                        : String,
        hour                        : String,
        minute                      : String,
        inch                        : String,
        kelvin                      : String,
        kilogram                    : String,
        kilowatt                    : String,
        meter                       : String,
        millimeter                  : String,
        pascal                      : String,
        percent                     : String,
        pound                       : String,
        square_centimeter           : String,
        square_inch                 : String,
        square_meter                : String,
        square_meter_kelvin_per_watt: String,
        unitless                    : String,
        watt_per_meter_kelvin       : String,
        mg_per_Nm3                  : String
    )

    case class AreaHeatingStatus(
        _self     : String,
        heated    : String,
        not_heated: String
    )

    case class Reports(
        headings: Reports.Headings,
        document: Reports.Document
    )

    object Reports:
        case class Headings(
            input_data        : String,
            compliance_en15544: String,
            compliance_en13384: String
        )

        case class Document(
            title                                  : String,
            software_label                         : String,
            software_name                          : String,
            versions_label                         : String,
            reports_module_version                 : StringFormat1,
            engine_module_version                  : StringFormat1,
            certification_text_with_source_and_date: String,
            no_certification_text                  : String
        )

    case class Warnings(
        firebox_afpma_prse_not_validated: String
    )

    case class IncrementalValidation(
        _self                     : String,
        not_defined_yet           : IncrementalValidation.NotDefinedYet,
        property_must_be_set      : IncrementalValidation.PropertyMustBeSet,
        property_must_be_defined  : IncrementalValidation.PropertyMustBeDefined,
        prerequisites             : IncrementalValidation.Prerequisites,
        conflicts                 : IncrementalValidation.Conflicts,
        forbidden_element_position: IncrementalValidation.ForbiddenElementPosition
    )

    object IncrementalValidation:

        case class NotDefinedYet(
            flue_pipe                         : String,
            chimney_pipe                      : String,
            add_element_missing_after_set_prop: StringFormat1
        )
        case class PropertyMustBeSet(
            inner_geometry        : StringFormat1,
            outer_geometry        : StringFormat1,
            geometry              : StringFormat1,
            roughness             : StringFormat1,
            layers                : StringFormat1,
            air_space_after_layers: StringFormat1,
            pipe_location         : StringFormat1,
            duct_type             : StringFormat1
        )

        case class PropertyMustBeDefined(
            section_geometry         : String,
            next_section_length      : String,
            pressure_loss            : String,
            pressure_loss_table_error: StringFormat1
        )

        case class Prerequisites(
            thickness_requires_inner_geometry         : String,
            layer_requires_section_geometry           : String,
            layers_require_inner_shape                : String,
            direction_change_requires_section_geometry: String,
            final_dir_without_initial_direction       : String,
            geometry_without_initial_direction        : String
        )

        case class Conflicts(
            cannot_set_geometry_before_change      : String,
            section_change_requires_circle         : StringFormat1,
            flow_resistance_requires_geometry      : StringFormat1,
            pressure_diff_requires_geometry        : StringFormat1,
            flow_resistance_requires_geometry_15544: StringFormat1,
            casing_too_small_for_liner             : StringFormat2
        )

        case class ForbiddenElementPosition(
            forbidden_at_start: StringFormat1,
            forbidden_at_end  : StringFormat1
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
        flue_pipe_length_below_minimum                  : StringFormat2
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

    case class BuilderErrors(
        errors_in_other_section_type: String
    )

    case class TopologyErrors(
        missing_chimney                : String,
        chimney_not_last               : String,
        missing_terminal_connector_slot: String,
        head_region_ends_with_connector: String
    )
