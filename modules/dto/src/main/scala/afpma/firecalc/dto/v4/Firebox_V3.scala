/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.FireboxI

import afpma.firecalc.i18n.*

import coulomb.*

import cats.Show

import io.circe.Decoder
import io.circe.Encoder
import magnolia1.Transl

sealed trait Firebox_V3 extends FireboxI

object Firebox_V3:

    enum TestStandard:
        case EN_15250
        case EN_13229
        case National(name: String)

    object TestStandard:
        // No-arg cases are encoded as plain strings to avoid the YAML null bug
        // where `{}` (empty object) is emitted as `null` by the YAML printer.
        given Decoder[TestStandard] = Decoder.instance { cursor =>
            cursor.as[String] match
                case Right("EN_15250") => Right(EN_15250)
                case Right("EN_13229") => Right(EN_13229)
                case _                 =>
                    cursor.downField("National").as[String].map(National(_))
            }

        given Encoder[TestStandard] = Encoder.instance {
            case EN_15250       => io.circe.Json.fromString("EN_15250")
            case EN_13229       => io.circe.Json.fromString("EN_13229")
            case National(name) => io.circe.Json.obj("National" -> io.circe.Json.fromString(name))
        }
        given Show[TestStandard] = Show.show:
            case EN_15250       => "EN 15250"
            case EN_13229       => "EN 13229"
            case National(name) => name
    end TestStandard

    @Transl(I(_.firebox_names.traditional))
    case class Traditional(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced                  : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.firebox_depth))
        firebox_depth                        : Length,
        @Transl(I(_.firebox.firebox_width))
        firebox_width                        : Length,
        @Transl(I(_.firebox.firebox_height))
        firebox_height                       : Length,
        @Transl(I(_.en15544.terms_xtra.height_of_the_lowest_opening.name))
        height_of_lowest_opening         : Length,
        @Transl(I(_.firebox.traditional.pressure_loss_coefficient_from_door))
        pressure_loss_coefficient_from_door  : QtyD[1],
        @Transl(I(_.firebox.traditional.total_air_intake_surface_area_on_door))
        total_air_intake_surface_area_on_door: QtyD[(Meter ^ 2)],
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width                          : Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height                         : Length
    ) extends Firebox_V3

    @Transl(I(_.firebox_names.ecolabeled))
    case class Ecolabeled(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced                 : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.ecolabeled.version))
        version                             : Either["Version 1", "Version 2"],
        @Transl(I(_.firebox.ecolabeled.version_2_air_intake_shape))
        air_intake_shape                    : Option[PipeShape], // defined only for V2
        @Transl(I(_.firebox.firebox_depth))
        firebox_depth                       : Length,
        @Transl(I(_.firebox.firebox_width))
        firebox_width                       : Length,
        @Transl(I(_.firebox.firebox_height))
        firebox_height                      : Length,
        @Transl(I(_.firebox.afpma_prse.height_of_first_row_of_air_injectors))
        height_of_first_row_of_air_injectors: Length,
        @Transl(I(_.firebox.ecolabeled.door_opening_width))
        door_opening_width                  : Length,
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width                         : Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height                        : Length,
        @Transl(I(_.firebox.ecolabeled.ash_pit_height_AF))
        ash_pit_height                      : Length,
        @Transl(I(_.firebox.ecolabeled.air_manifold_height_W))
        air_manifold_height                 : Length,
        @Transl(I(_.firebox.ecolabeled.firebox_floor_thickness))
        firebox_floor_thickness             : Length,
        @Transl(I(_.firebox.ecolabeled.inner_wall_thickness_D1))
        firebox_inner_wall_thickness        : Length,
        @Transl(I(_.firebox.ecolabeled.outer_wall_thickness_D2))
        firebox_outer_wall_thickness        : Length,
        @Transl(I(_.firebox.ecolabeled.air_column_thickness_S))
        air_column_thickness                : Length,
        @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_sides_E))
        width_between_two_air_columns_sides : Length,
        @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_rear_E))
        width_between_two_air_columns_rear  : Length,
        @Transl(I(_.firebox.ecolabeled.reinforcement_bars_offset_in_corners))
        reinforcement_bars_offset_in_corners: Length,
        @Transl(I(_.firebox.ecolabeled.injector_height_Z))
        injector_height                     : Length
    ) extends Firebox_V3

    @Transl(I(_.firebox_names.afpma_prse))
    case class AFPMA_PRSE(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced                  : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.afpma_prse.outside_air_location_in_heater))
        outside_air_location_in_heater       : OutsideAirLocationInHeater,
        @Transl(I(_.firebox.afpma_prse.outside_air_conduit_shape))
        outside_air_conduit_shape            : PipeShape,
        @Transl(I(_.firebox.firebox_depth))
        firebox_depth                        : Length,
        @Transl(I(_.firebox.firebox_width))
        firebox_width                        : Length,
        @Transl(I(_.firebox.firebox_height))
        firebox_height                       : Length,
        @Transl(I(_.firebox.afpma_prse.height_of_first_row_of_air_injectors))
        height_of_first_row_of_air_injectors : Length,
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width                          : Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height                         : Length,
        @Transl(I(_.firebox.afpma_prse.ash_pit_height))
        ash_pit_height                       : Length,
        @Transl(I(_.firebox.afpma_prse.floor_thickness))
        floor_thickness                      : Length,
        @Transl(I(_.firebox.afpma_prse.combustion_air_manifold_height))
        combustion_air_manifold_height       : Length,
        @Transl(I(_.firebox.afpma_prse.outside_air_inlet_lip))
        outside_air_inlet_lip                : Length,
        @Transl(I(_.firebox.afpma_prse.height_of_air_feed_to_columns))
        height_of_air_feed_to_columns        : Length,
        @Transl(I(_.firebox.afpma_prse.number_of_air_columns_feeding_firebox))
        number_of_air_columns_feeding_firebox: Int,
        @Transl(I(_.firebox.afpma_prse.number_of_air_columns_feeding_door))
        number_of_air_columns_feeding_door   : Int
    ) extends Firebox_V3

    @Transl(I(_.firebox_names.single_tested))
    case class SingleTested(
        @Transl(I(_.firebox.single_tested.reference))
        reference                             : String,
        @Transl(I(_.type_of_appliance.descr))
        type_of_appliance                     : TypeOfAppliance,
        @Transl(I(_.firebox.single_tested.test_standard))
        test_standard                         : TestStandard,
        @Transl(I(_.firebox.firebox_depth))
        firebox_depth                         : Length,
        @Transl(I(_.firebox.firebox_width))
        firebox_width                         : Length,
        @Transl(I(_.firebox.firebox_height))
        firebox_height                        : Length,
        @Transl(I(_.firebox.ash_pit_height))
        ash_pit_height                        : Length,
        @Transl(I(_.firebox.single_tested.is_glass_surface_ratio_below_one_fifth))
        is_glass_surface_ratio_below_one_fifth: Boolean,
        @Transl(I(_.firebox.glass_area))
        glass_area                            : Area,
        @Transl(I(_.firebox.single_tested.mean_firebox_temperature))
        mean_firebox_temperature              : Option[TCelsius],
        @Transl(I(_.firebox.single_tested.t_burnout))
        t_burnout                             : TCelsius,
        @Transl(I(_.firebox.single_tested.efficiency_nominal))
        efficiency_nominal                    : Percentage,
        @Transl(I(_.firebox.single_tested.efficiency_reduced))
        efficiency_reduced                    : Option[Percentage],
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced                   : HeatOutputReduced.NotDefined_Or_Tested,
        @Transl(I(_.firebox.single_tested.minimum_fuel_mass))
        minimum_fuel_mass                     : Option[Mass],
        @Transl(I(_.firebox.single_tested.maximum_fuel_mass))
        maximum_fuel_mass                     : Mass,
        @Transl(I(_.firebox.single_tested.air_fuel_ratio_nominal))
        air_fuel_ratio_nominal                : Dimensionless,
        @Transl(I(_.firebox.single_tested.air_fuel_ratio_lowest))
        air_fuel_ratio_lowest                 : Option[Dimensionless],
        @Transl(I(_.firebox.single_tested.co2_dry_nominal))
        co2_dry_nominal                       : Percentage,
        @Transl(I(_.firebox.single_tested.co2_dry_lowest))
        co2_dry_lowest                        : Option[Percentage],
        @Transl(I(_.firebox.single_tested.pellets_load_burn_duration))
        pellets_load_burn_duration            : Option[QtyD[Minute]],
        @Transl(I(_.firebox.single_tested.emissions_firebox_name))
        emissions_firebox_name                : String,
        @Transl(I(_.firebox.single_tested.emissions_accredited_body))
        emissions_accredited_body             : String,
        @Transl(I(_.firebox.single_tested.emissions_co))
        emissions_co                          : EmissionValueU,
        @Transl(I(_.firebox.single_tested.emissions_dust))
        emissions_dust                        : EmissionValueU,
        @Transl(I(_.firebox.single_tested.emissions_ogc))
        emissions_ogc                         : EmissionValueU,
        @Transl(I(_.firebox.single_tested.emissions_nox))
        emissions_nox                         : EmissionValueU
    ) extends Firebox_V3

    @Transl(I(_.firebox_names.door_15a_firebox))
    case class Door15aFirebox_Catalog(
        @Transl(I(_.firebox.single_tested.reference))
        reference                             : String,
        @Transl(I(_.firebox.firebox_depth))
        firebox_depth                         : Length,
        @Transl(I(_.firebox.firebox_width))
        firebox_width                         : Length,
        @Transl(I(_.firebox.firebox_height))
        firebox_height                        : Length,
        @Transl(I(_.firebox.door_15a_firebox.load_size_nominal))
        load_size_nominal                     : Option[Mass] = None,
        @Transl(I(_.firebox.door_15a_firebox.sb))
        sb                                    : QtyD[Centimeter],
        @Transl(I(_.firebox.door_15a_firebox.sb_min))
        sb_min                                : Option[QtyD[Centimeter]] = None,
        @Transl(I(_.firebox.door_15a_firebox.sb_max))
        sb_max                                : Option[QtyD[Centimeter]] = None,
        @Transl(I(_.firebox.door_15a_firebox.mb_min))
        mb_min                                : Option[Mass] = None,
        @Transl(I(_.firebox.door_15a_firebox.mb_max))
        mb_max                                : Option[Mass] = None,
        @Transl(I(_.firebox.door_15a_firebox.pressure_loss_table))
        pressure_loss_table_raw               : String = "",
        @Transl(I(_.firebox.door_15a_firebox.expected_air_intake_pipe_shape))
        expectedAirIntakePipeShape            : PipeShape,
        @Transl(I(_.firebox.single_tested.co2_dry_nominal))
        co2_dry_nominal                       : Percentage,
        @Transl(I(_.firebox.single_tested.co2_dry_lowest))
        co2_dry_lowest                        : Option[Percentage] = None,
        @Transl(I(_.emissions_and_efficiency_values._self))
        emissions_values                      : EmissionsAndEfficiencyValues_DTO,
        @Transl(I(_.firebox.glass_area))
        glass_area                            : Area,
        @Transl(I(_.en15544.terms_xtra.height_of_the_lowest_opening.name))
        height_of_lowest_opening              : Length,
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced                   : HeatOutputReduced = HeatOutputReduced.NotDefined,
    ) extends Firebox_V3
