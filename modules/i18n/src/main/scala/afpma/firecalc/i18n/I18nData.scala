/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import afpma.firecalc.i18n.I18nData.*

import io.taig.babel.*

trait LocalizedAlg:
    val language: Language
    given Locale = Locale(language)

type Localized[A] = Locale ?=> A
object Localized:
    extension [A](la: Localized[A])
        def map[B](f: A => B): Localized[B] = f(la)

case class LocalizedString(f: Locale => String) {
    def map(g: String => String): LocalizedString = LocalizedString(f andThen g)
    def show(using l: Locale): String = f(l)
}
object LocalizedString:
    def from(ls: Locale ?=> String) = LocalizedString(loc => ls(using loc))

final case class I18nData(
    add_element: AddElement,
    address: Address,
    append_layer_descr: AppendLayerDescr,
    customer: Customer,
    firebox: Firebox_15544,
    firebox_names: FireboxNames,
    draft_min: String,
    draft_max: String,
    emissions_and_efficiency_values: EmissionsAndEfficiencyValues,
    en13384: EN13384,
    en15544: EN15544,
    en16510: EN16510,
    errors: Errors,
    facing_type: FacingType,
    headers: Headers,
    heating_appliance: HeatingAppliance,
    inputs_data: String,
    local_conditions: LocalConditions,
    local_regulations: LocalRegulations,
    no: String,
    panels: Panels,
    pipe_location: PipeLocation,
    pipe_type: PipeType,
    pollutant_names: PolluantNames,
    heat_output_reduced: HeatOutputReduced,
    pressure_requirements: String,
    project_description: ProjectDescription,
    set_prop: SetProp,
    stove_params: String,
    subtotal: String,
    technical_specifications: TechnicalSpecficiations,
    terms: Terms,
    total: String,
    type_of_appliance: TypeOfAppliance,
    type_of_load: TypeOfLoad,
    units: Units,
    yes: String,
    area_name: String,
    area_heating_status: AreaHeatingStatus,
    reports: Reports,
    warnings: Warnings
)

object I18nData:

    case class AddElement(
        _self: String,
        add_section_element: String,
        add_direction_change_element: String,
        AddSectionSlopped: String,
        AddSectionHorizontal: String,
        AddSectionVertical: String,
        AddAngleAdjustable: String,
        AddSharpeAngle_0_to_180: String,
        AddSharpeAngle_0_to_90: String,
        AddSharpeAngle_0_to_90_Unsafe: String,
        AddSmoothCurve_90: String,
        AddSmoothCurve_90_Unsafe: String,
        AddCircularArc_60: String,
        AddSmoothCurve_60: String,
        AddSmoothCurve_60_Unsafe: String,
        AddElbows_2x45: String,
        AddElbows_3x30: String,
        AddElbows_4x22p5: String,
        AddPressureDiff: String,
        AddSectionDecrease: String,
        AddSectionIncrease: String,
        AddSectionShapeChange: String,
        AddFlowResistance: String,
        AddFlowResistance_butterfly_damper: String,
        AddFlowResistance_wire_mesh_screen: String,
        AddFlowResistance_solid_steel_grate: String,
        cross_section: String,
        name: String,
        zeta: String,
    )
    
    case class Address(
        header: String,
        num: String,
        street: String,
        zip: String,
        city: String,
        region_state: String,
        country: String
    )

    case class AppendLayerDescr(
        _self: String,
        FromLambda: String,
        FromLambdaUsingThickness: String,
        FromThermalResistanceUsingThickness: String,
        FromThermalResistance: String,
        AirSpaceUsingOuterShape: String,
        AirSpaceUsingThickness: String,
    )
    
    case class Customer(
        first_name: String,
        last_name: String,
        phone: String,
        email: String,
    )

    case class Firebox_15544(
        afpma_prse: Firebox_15544.AFPMA_PRSE,
        dimensions_summary_w_d_h: StringFormat3,
        ref: String,
        typ: String,
        ecolabeled: Firebox_15544.EcoLabeled,
        tested: Firebox_15544.Tested,
        traditional: Firebox_15544.Traditional,
    )

    object Firebox_15544:

        case class AFPMA_PRSE(
            outside_air_location_in_heater: String,
            outside_air_location_from_bottom: String,
            outside_air_conduit_shape: String,
            height_of_first_row_of_air_injectors: String,
            ash_pit_height: String,
            floor_thickness: String,
            combustion_air_manifold_height: String,
            outside_air_inlet_lip: String,
            height_of_air_feed_to_columns: String,
            number_of_air_columns_feeding_firebox: String,
            number_of_air_columns_feeding_door: String,
        )

        case class EcoLabeled(
            version: String,
            version_1_with_airbox: String,
            version_2_without_airbox: String,
            version_2_air_intake_shape: String,
            width: String,
            depth: String,
            height: String,
            door_opening_width: String,
            glass_width: String,
            glass_height: String,
            ash_pit_height_AF: String,
            air_manifold_height_W: String,
            firebox_floor_thickness: String,
            inner_wall_thickness_D1: String,
            outer_wall_thickness_D2: String,
            air_column_thickness_S: String,
            width_between_two_air_columns_sides_E: String,
            width_between_two_air_columns_rear_E: String,
            reinforcement_bars_offset_in_corners: String,
            injector_height_Z: String,
            injector_surface_area: String,
            injector_surface_area_obstructed_max_perc: StringFormat1,
        )

        case class Tested(
            efficiency_nominal: String,
            efficiency_reduced: String,
            heat_output_nominal: String,
            heat_output_reduced: String,
            load_size_nominal: String,
            load_size_reduced: String,
            air_to_fuel_ratio_nominal: String,
            air_to_fuel_ratio_reduced: String,
            co2_perc_by_vol_dry_nominal: String,
            co2_perc_by_vol_dry_reduced: String,
            average_firebox_temperature: String,
            firebox_exit_temperature: String,
        )

        case class Traditional(
            firebox_floor_shape: String,
            depth: String,
            width: String,
            width_to_depth_ratio: String,
            height: String,
            pressure_loss_coefficient_from_door: String,
            total_air_intake_surface_area_on_door: String,
            glass_surface_area: String,
            glass_height: String,
            glass_width: String,
            air_injector_surface_area: String,
            air_manifold_height_W: String,
            firebox_floor_thickness: String,
            ash_pit_height_AF: String,
        )

    case class FireboxNames(
        traditional: String,
        ecolabeled: String,
        ecolabeled_v1: String,
        ecolabeled_v2: String,
        afpma_prse: String,
        certified: String,
        custom_lab_tested: String,
    )

    case class PolluantNames(
        CO: String,
        Dust: String,
        OGC: String,
        NOx: String,
        Dust_OGC: String,
    )

    case class FacingType(
        with_air_gap: String,
        without_air_gap: String,
    )

    case class EmissionsAndEfficiencyValues(
        accredited_or_notified_body: String,
        firebox_name: String,
        min_efficiency_firebox_reduced: String,
        min_efficiency_firebox_nominal: String,
        min_efficiency_full_stove_reduced: String,
        min_efficiency_full_stove_nominal: String,
        xxx_at_13pO2: StringFormat2,
    )

    case class EN13384(
        air_space_detailed: String,
        _air_space_detailed: EN13384.AirSpaceDetailed,
        air_space_ventilated: String,
        air_space_dead: String,
        _ambiant_air_temperatures: EN13384.AmbiantAirTemperatures,
        ambiant_air_temperatures: String,
        _ambiant_air_temperature: EN13384.AmbiantAirTemperature,
        duct_type_non_concentric_high_thermal_resistance : String,
        duct_type_non_concentric_low_thermal_resistance : String,
        duct_type_concentric : String,
        flue_gas_conditions: String,
        flue_gas_conditions_wet: String,
        flue_gas_conditions_dry: String,
        exterior_air_temperature: String,
        materials: EN13384_Materials,
        requirements: EN13384.Requirements,
        reuse_tuo: String,
        terms: EN13384_Terms,
        unheated_area_height: String,
    )

    object EN13384:

        case class AirSpaceDetailed(
            without_air_space: String,
            _ventil_direction: AirSpaceDetailed.VentilDirection,
            ventil_direction: String,
            _ventil_openings: AirSpaceDetailed.VentilOpenings,
            ventil_openings: String,
            with_air_space: String,
        )
        object AirSpaceDetailed:
            case class VentilDirection(
                undefined_dir: String,
                same_dir_as_fluegas: String,
                inverse_dir_as_fluegas: String,
            )
            case class VentilOpenings(
                no_opening: String,
                annular_area_fully_opened: String,
                partially_opened_in_accordance_with_dtu_24_1: String,
            )
        
        case class AmbiantAirTemperature(
            long: String,
            short: String,
        )
        
        case class AmbiantAirTemperatures(
            at_chimney_outlet: String,
            for_boiler_room: String,
            for_heated_areas: String,
            for_exterior_areas: String,
            for_unheated_areas: String,
        )
        case class Requirements(
            no_condensation: String,
        )

    case class EN13384_Materials(
        WeldedSteel: String,
        Glass: String,
        Plastic: String,
        Aluminium: String,
        ClayFlueLiners: String,
        Bricks: String,
        SolderedMetal: String,
        Concrete: String,
        Fibrociment: String,
        Masonry: String,
        CorrugatedMetal: String,
    )

    case class EN13384_Terms(
        P_B: String,
        P_L: String,
        P_Z: String,
        P_Ze: String,
        P_Zmax: String,
        P_Zemax: String,
        P_ZO: String,
        P_ZOmin: String,
        P_ZOe: String,
        P_ZOemin: String,
        P_Zexcess: String,
        P_ZVexcess: String,
        T_ig: String,
        T_ob: String,
        T_iob: String,
        T_sp: String,
    )

    case class EN15544(
        angle_to_original_direction: String,
        materials: EN15544_Materials,
        pressure_requirements: EN15544_PressureRequirements,
        terms: EN15544_Terms,
        terms_xtra: EN15544_Terms_Xtra
    )

    case class EN15544_Materials(
        blocs_de_chamotte: String,
        tuyaux_en_chamotte: String,
    )

    case class EN15544_PressureRequirements(
        sum_of_all_resistances: String,
        sum_of_all_buyoancies: String,
        pressure_difference: String,
    )

    case class EN15544_TermDef(
        name: String,
        descr: String,
    )

    case class EN15544_Terms(
        GlassArea: EN15544_TermDef,
        L_N: EN15544_TermDef,
        A_BR: EN15544_TermDef,
        H_BR: EN15544_TermDef,
        O_BR: EN15544_TermDef,
        m_BU: EN15544_TermDef,
        U_BR: EN15544_TermDef,
        A_GS: EN15544_TermDef,
        L_Z: EN15544_TermDef,
        m_B: EN15544_TermDef,
        m_B_min: EN15544_TermDef,
        P_n: EN15544_TermDef,
        P_n_reduced: EN15544_TermDef,
        t_n: EN15544_TermDef,
    )

    case class EN15544_Terms_Xtra(
        n_min: EN15544_TermDef,
        height_of_the_lowest_opening: EN15544_TermDef,
        Table_1_Factor_a: EN15544_TermDef,
        Table_1_Factor_b: EN15544_TermDef,
        t_ext: EN15544_TermDef,
        m_G: EN15544_TermDef,
        m_L: EN15544_TermDef,
        t_outside_air_mean: EN15544_TermDef,
        t_combustion_air: EN15544_TermDef,
        t_BR: EN15544_TermDef,
        t_burnout: EN15544_TermDef,
        t_fluepipe: EN15544_TermDef,
        t_connector_pipe: EN15544_TermDef,
        t_connector_pipe_mean: EN15544_TermDef,
        c_P: EN15544_TermDef,
        σ_CO2: EN15544_TermDef,
        σ_H2O: EN15544_TermDef,
        η: EN15544_TermDef,
        t_F: EN15544_TermDef,
        t_flue_gas: EN15544_TermDef,
        RequiredDeliveryPressure: EN15544_TermDef,
        t_chimney_wall_top_out: EN15544_TermDef,
        t_chimney_out: EN15544_TermDef,
        t_stove_out: EN15544_TermDef,
        necessary_delivery_pressure: EN15544_TermDef,
        flue_gas_mass_rate: EN15544_TermDef,
    )

    case class EN16510(
        η_s: String
    )

    case class Errors(
        term_should_be_greater_or_eq_than: StringFormat2,
        term_should_be_greater_than: StringFormat2,
        term_should_be_less_or_eq_than: StringFormat2,
        term_should_be_less_than: StringFormat2,
        term_should_be_between_inclusive: StringFormat4,
        term_constraint_min_error: StringFormat2,
        term_constraint_max_error: StringFormat2,
        glass_area_too_large: StringFormat2,
        firebox_height_out_of_range: StringFormat3,
        firebox_base_ratio_invalid: StringFormat3,
        firebox_base_min_width: StringFormat2,
        co2_calculation_only_for_wood_boilers: String,
        injector_velocity_below_minimum: StringFormat2,
        injector_velocity_above_maximum: StringFormat2,
    )

    case class Headers(
        firebox_description: String,
        client_project_data: String,
        constraints_validation: String,
        efficiencies_values: String,
        emissions_and_efficiency_values: String,
        estimated_output_temperatures_15544: String,
        flue_gas_triple_of_variates: String,
        heating_appliance_for_13384: String,
        local_conditions: String,
        pipes_details: String,
        pressure_requirements_13384: String,
        pressure_requirements_15544: String,
        project_description: String,
        reference_temperatures: String,
        technical_specifications: String,
        temperature_requirements_13384: String,
        temperature_requirements_15544: String,
        temperature_at_chimney_outlet: String,
        wind_pressure: String,
    )

    case class HeatingAppliance(
        efficiency_nominal: String,
        efficiency_reduced: String,
        fluegas: HeatingAppliance.FlueGas,
        powers: HeatingAppliance.Powers,
        temperatures: HeatingAppliance.Temperatures,
        massFlows: HeatingAppliance.MassFlows,
        pressures: HeatingAppliance.Pressures,
        volumeFlows: HeatingAppliance.VolumeFlows,
    )

    object HeatingAppliance:
        case class FlueGas(
            co2_dry_perc_nominal: String,
            co2_dry_perc_reduced: String,
            h2o_perc_nominal: String,
            h2o_perc_reduced: String,
        )
        case class Powers(
            heat_output_nominal: String,
            heat_output_reduced: String,
        )
        case class Temperatures(
            flue_gas_temp_nominal: String,
            flue_gas_temp_reduced: String,
        )
        case class MassFlows(
            flue_gas_mass_flow_nominal: String,
            flue_gas_mass_flow_reduced: String,
            combustion_air_mass_flow_nominal: String,
            combustion_air_mass_flow_reduced: String,
        )
        case class Pressures(
            underPressure: String,
            underPressure_negative: String,
            underPressure_positive: String,
            flue_gas_draft_min: String,
            flue_gas_draft_max: String,
            flue_gas_pdiff_min: String,
            flue_gas_pdiff_max: String,
        )
        
        case class VolumeFlows(
            flue_gas_volume_flow_nominal: String,
            flue_gas_volume_flow_reduced: String,
            combustion_air_volume_flow_nominal: String,
            combustion_air_volume_flow_reduced: String,
        )

    case class LocalConditions(
        altitude: String,
        coastal_region: String,
        chimney_termination: LocalConditions.ChimneyTermination
    )

    object LocalConditions:
        case class ChimneyTermination(
            explain: String,
            chimney_location_on_roof: ChimneyTermination.ChimneyLocationOnRoof,
            adjacent_buildings: ChimneyTermination.AdjacentBuildings,
        )
        object ChimneyTermination:
            import ChimneyLocationOnRoof.*
            case class ChimneyLocationOnRoof(
                explain: String,
                chimney_height_above_ridgeline: ChimneyHeightAboveRidgeline,
                horizontal_distance_between_chimney_and_ridgeline: HorizontalDistanceBetweenChimneyAndRidgeline,
                slope: Slope,
                outside_air_intake_and_chimney_locations: OutsideAirIntakeAndChimneyLocations,
                horizontal_distance_between_chimney_and_ridgeline_bis: HorizontalDistanceBetweenChimneyAndRidgelineBis,
            )
            object ChimneyLocationOnRoof:
                case class ChimneyHeightAboveRidgeline(
                    explain: String,
                    more_than_40cm: String,
                    less_than_40cm: String,
                )
                case class HorizontalDistanceBetweenChimneyAndRidgeline(
                    explain: String,
                    less_than_2m30: String,
                    more_than_2m30: String,
                )
                case class Slope(
                    explain: String,
                    less_than_25deg: String,
                    between_25deg_and_40deg: String,
                    more_than_40deg: String,
                )
                case class OutsideAirIntakeAndChimneyLocations(
                    explain: String,
                    on_different_sides_of_the_ridge: String,
                    on_same_side_of_the_ridge: String,
                )
                case class HorizontalDistanceBetweenChimneyAndRidgelineBis(
                    explain: String,
                    less_than_1m: String,
                    more_than_1m: String,
                )
            
            import AdjacentBuildings.*
            case class AdjacentBuildings(
                explain: String,
                horizontal_distance_between_chimney_and_adjacent_buildings: HorizontalDistanceBetweenChimneyAndAdjacentBuildings,
                horizontal_angle_between_chimney_and_adjacent_buildings: HorizontalAngleBetweenChimneyAndAdjacentBuildings,
                vertical_angle_between_chimney_and_adjacent_buildings: VerticalAngleBetweenChimneyAndAdjacentBuildings,
            )
            object AdjacentBuildings:
                case class HorizontalDistanceBetweenChimneyAndAdjacentBuildings(
                    explain: String,
                    less_than_15m: String,
                    more_than_15m: String,
                )
                case class HorizontalAngleBetweenChimneyAndAdjacentBuildings(
                    explain: String,
                    more_than_30deg: String,
                    less_than_30deg: String,
                )
                case class VerticalAngleBetweenChimneyAndAdjacentBuildings(
                    explain: String,
                    more_than_10deg_above_horizon: String,
                    less_than_10deg_above_horizon: String,
                )
    
    case class LocalRegulations(
        regulation_ref: String,
        country: String,
    )

    case class Panels(
        air_intake: String,
        chimney_pipe: String,
        client_project: String,
        firebox: String,
        connector_pipe: String,
        channel_pipe: String,
        geographical_location_and_external_factors: String,
        output_and_other_parameters: String,
        total: String,
    )

    case class PipeShape(
        _self: String,
        circle: String,
        rectangle: String,
        square: String,
    )

    case class PipeLocation(
        _column_header: String,
        short: String,
        boiler_room: String,
        heated_area: String,
        unheated_inside: String,
        outside_or_exterior: String,
        custom_area: String,
    )

    case class PipeType(
        air_intake: String,
        combustion_air: String,
        firebox: String,
        connector: String,
        channel: String,
        chimney: String,
    )

    case class HeatOutputReduced(
        not_defined: String,
        defined_as_default: StringFormat1,
        defined_as_half_of_nominal: String,
        defined_when_tested: StringFormat1
    )

    case class ProjectDescription(
        reference: String,
        date: String,
        country: String
    )

    case class SetProp(
        _self: String,
        _geometric_properties: String,
        define_layers: String,
        SetInnerShape: String,
        SetOuterShape: String,
        SetThickness: String,
        SetRoughness: String,
        SetMaterial: String,
        SetLayer: String,
        SetLayers: String,
        SetAirSpaceAfterLayers: String,
        SetPipeLocation: String,
        SetDuctType: String,
        SetNumberOfFlows: String,
        SetNumberOfFlows_fieldName: String,
        SetNumberOfFlows_NumberOfChannels: String,
        SetNumberOfFlows_Join: String,
    )

    case class TechnicalSpecficiations(
        sizing_method: String,
        nominal_heat_output: String,
        nominal_heat_output_short: String,
        heating_cycle: String,
        maximum_load: String,
        maximum_load_short: String,
        minimum_load: String,
        facing_type: String,
        facing_type_sentence: StringFormat1,
        inner_construction_material: String,
        inner_construction_material_within_specs: String,
    )

    case class Terms(
        name: String,
        angle: String,
        area: String,
        curvature_radius: String,
        diameter: String,
        height: String,
        horizontal_length: String,
        inner_shape: String,
        length: String,
        number_of_parallel_flows: String,
        number_of_segments: String,
        outer_shape: String,
        pipe_shape: PipeShape,
        pressure_difference: String,
        radius: String,
        roughness: String,
        side: String,
        speed: String,
        temperature: String,
        temperature_celsius: String,
        temperature_farenheit: String,
        temperature_kelvin: String,
        thermal_conductivity_λ: String,
        thermal_resistance_Rth: String,
        thickness: String,
        undefined: String,
        elevation_gain: String,
        width: String,
        width_to_height_ratio: String,
        zeta: String,
        zeta_ζ: String,
    )

    case class TypeOfAppliance(
        descr: String,
        pellets: String,
        woodlogs: String,
    )

    case class TypeOfLoad(
        descr: String,
        nominal: String,
        reduced: String,
    )

    final case class Units(
        btu_per_hour: String,
        celsius   : String,
        centimeter: String,
        degree    : String,
        foot      : String,
        hour      : String,
        inch      : String,
        kelvin    : String,
        kilogram  : String,
        kilowatt  : String,
        meter     : String,
        millimeter: String,
        pascal    : String,
        percent   : String,
        pound     : String,
        square_centimeter: String,
        square_inch: String,
        square_meter: String,
        square_meter_kelvin_per_watt: String,
        unitless  : String,
        watt_per_meter_kelvin: String,
      )

    case class AreaHeatingStatus(
        _self: String,
        heated: String,
        not_heated: String,
    )

    case class Reports(
        headings: Reports.Headings,
        document: Reports.Document
    )

    object Reports:
        case class Headings(
            input_data: String,
            compliance_en15544: String,
            compliance_en13384: String
        )
        
        case class Document(
            title: String,
            software_label: String,
            software_name: String,
            versions_label: String,
            reports_module_version: StringFormat1,
            engine_module_version: StringFormat1,
            certification_text_with_source_and_date: String,
            no_certification_text: String
        )

    case class Warnings(
        firebox_afpma_prse_not_validated: String
    )
