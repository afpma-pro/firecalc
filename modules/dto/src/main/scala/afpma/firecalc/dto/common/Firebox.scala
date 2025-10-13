/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

import magnolia1.Transl

@Transl(I(_.firebox.typ))
sealed trait Firebox:
    def firebox_depth: Length
    def firebox_width: Length
    def firebox_height: Length

object Firebox:

    @Transl(I(_.firebox_names.traditional))
    case class Traditional(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.traditional.depth))
        firebox_depth: Length,
        @Transl(I(_.firebox.traditional.width))
        firebox_width: Length,
        @Transl(I(_.firebox.traditional.height))
        firebox_height: Length,
        @Transl(I(_.firebox.traditional.pressure_loss_coefficient_from_door))
        pressure_loss_coefficient_from_door: QtyD[1],
        @Transl(I(_.firebox.traditional.total_air_intake_surface_area_on_door))
        total_air_intake_surface_area_on_door: QtyD[(Meter ^ 2)],
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width: Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height: Length,
    ) extends Firebox

    @Transl(I(_.firebox_names.ecolabeled))
    case class EcoLabeled(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.ecolabeled.version))
        version: Either["Version 1", "Version 2"],
        @Transl(I(_.firebox.ecolabeled.version_2_air_intake_shape))
        air_intake_shape: Option[PipeShape], // defined only for V2
        @Transl(I(_.firebox.traditional.depth))
        firebox_depth: Length,
        @Transl(I(_.firebox.traditional.width))
        firebox_width: Length,
        @Transl(I(_.firebox.traditional.height))
        firebox_height: Length,
        @Transl(I(_.firebox.ecolabeled.door_opening_width))
        door_opening_width: Length,
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width: Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height: Length,
        @Transl(I(_.firebox.ecolabeled.ash_pit_height_AF))
        ash_pit_height: Length,
        @Transl(I(_.firebox.ecolabeled.air_manifold_height_W))
        air_manifold_height: Length,
        @Transl(I(_.firebox.ecolabeled.firebox_floor_thickness))
        firebox_floor_thickness: Length,
        @Transl(I(_.firebox.ecolabeled.inner_wall_thickness_D1))
        firebox_inner_wall_thickness: Length,
        @Transl(I(_.firebox.ecolabeled.outer_wall_thickness_D2))
        firebox_outer_wall_thickness: Length,
        @Transl(I(_.firebox.ecolabeled.air_column_thickness_S))
        air_column_thickness: Length,
        @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_sides_E))
        width_between_two_air_columns_sides: Length,
        @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_rear_E))
        width_between_two_air_columns_rear: Length,
        @Transl(I(_.firebox.ecolabeled.reinforcement_bars_offset_in_corners))
        reinforcement_bars_offset_in_corners: Length,
        @Transl(I(_.firebox.ecolabeled.injector_height_Z))
        injector_height: Length,
    ) extends Firebox

    @Transl(I(_.firebox_names.afpma_prse))
    case class AFPMA_PRSE(
        @Transl(I(_.firebox.tested.heat_output_reduced))
        heat_output_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        @Transl(I(_.firebox.afpma_prse.outside_air_location_in_heater))
        outside_air_location_in_heater: AFPMA_PRSE.OutsideAirLocationInHeater,
        @Transl(I(_.firebox.afpma_prse.outside_air_conduit_shape))
        outside_air_conduit_shape: PipeShape,
        @Transl(I(_.firebox.traditional.depth))
        firebox_depth: Length,
        @Transl(I(_.firebox.traditional.width))
        firebox_width: Length,
        @Transl(I(_.firebox.traditional.height))
        firebox_height: Length,
        @Transl(I(_.firebox.afpma_prse.height_of_first_row_of_air_injectors))
        height_of_first_row_of_air_injectors: Length,
        @Transl(I(_.firebox.traditional.glass_width))
        glass_width: Length,
        @Transl(I(_.firebox.traditional.glass_height))
        glass_height: Length,
        @Transl(I(_.firebox.afpma_prse.ash_pit_height))
        ash_pit_height: Length,
        @Transl(I(_.firebox.afpma_prse.floor_thickness))
        floor_thickness: Length,
        @Transl(I(_.firebox.afpma_prse.combustion_air_manifold_height))
        combustion_air_manifold_height: Length,
        @Transl(I(_.firebox.afpma_prse.outside_air_inlet_lip))
        outside_air_inlet_lip: Length,
        @Transl(I(_.firebox.afpma_prse.height_of_air_feed_to_columns))
        height_of_air_feed_to_columns: Length,
        @Transl(I(_.firebox.afpma_prse.number_of_air_columns_feeding_firebox))
        number_of_air_columns_feeding_firebox: Int,
        @Transl(I(_.firebox.afpma_prse.number_of_air_columns_feeding_door))
        number_of_air_columns_feeding_door: Int,
    ) extends Firebox

    object AFPMA_PRSE:

        enum OutsideAirLocationInHeater:
            case FromBottom

        object OutsideAirLocationInHeater:
            type FromBottom = FromBottom.type
            given ShowUsingLocale[OutsideAirLocationInHeater] = showUsingLocale(_ =>
                I18N.firebox.afpma_prse.outside_air_location_from_bottom
            )