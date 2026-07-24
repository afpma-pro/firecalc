/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog.SB
import afpma.firecalc.engine.models.en15544.typedefs.GlassArea
import afpma.firecalc.engine.models.en15544.typedefs.σ_CO2

import io.scalaland.chimney.*
import io.scalaland.chimney.dsl.*

object FireboxTransformers:

    given transformer_Firebox_Firebox_15544: Transformer[Firebox, Firebox_15544] = { fb =>
        fb match
            case x : Firebox.Traditional            =>
                transformer_Standard_TraditionalFirebox.transform(x)
            case x : Firebox.Ecolabeled             =>
                transformer_Ecolabeled_Ecolabeled.transform(x)
            case x : Firebox.AFPMA_PRSE             =>
                transformer_AFPMA_PRSE.transform(x)
            case st: Firebox.SingleTested           =>
                val singleTestedT = afpma.firecalc.engine.models.en15544.firebox.single_tested.transformer_SingleTested
                singleTestedT.transform(st)
                // throw new UnsupportedOperationException(
                //     "SingleTested fireboxes cannot be converted to Firebox_15544 — they use their own test data"
                // )
            case x : Firebox.Door15aFirebox_Catalog =>
                transformer_dto_Door15aFirebox_Catalog_to_Door15aFirebox_Catalog.transform(x)
    }

    // mappings to engine model
    given transformer_Standard_TraditionalFirebox: Transformer[Firebox.Traditional, firebox.TraditionalFirebox] =
        Transformer
            .define[Firebox.Traditional, firebox.TraditionalFirebox]
            .enableDefaultValues
            .withFieldRenamed(_.firebox_depth, _.firebox_depth_B)
            .withFieldRenamed(_.firebox_width, _.firebox_width_A)
            .withFieldRenamed(_.firebox_height, _.firebox_height_H)
            .withFieldRenamed(_.height_of_lowest_opening, _.ash_pit_height_AF)
            .buildTransformer

    given transformer_inv_TraditionalFirebox_Standard: Transformer[firebox.TraditionalFirebox, Firebox.Traditional] =
        import HeatOutputReduced.{NotDefined, HalfOfNominal}
        Transformer
            .define[firebox.TraditionalFirebox, Firebox.Traditional]
            .enableDefaultValues
            .withFieldComputed(
                _.heat_output_reduced,
                trad =>
                    val res: NotDefined | HalfOfNominal =
                        legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(trad.pn_reduced)
                    res
            )
            .withFieldRenamed(_.firebox_depth_B, _.firebox_depth)
            .withFieldRenamed(_.firebox_width_A, _.firebox_width)
            .withFieldRenamed(_.firebox_height_H, _.firebox_height)
            .withFieldRenamed(_.ash_pit_height_AF, _.height_of_lowest_opening)
            .buildTransformer

    given transformer_Ecolabeled_Ecolabeled: Transformer[Firebox.Ecolabeled, firebox.Ecolabeled] = e =>
        import e.*
        e.version match
            case Left("Version 1")  =>
                firebox.Ecolabeled_V1                             (
                    pn_reduced                              = heat_output_reduced,
                    firebox_depth_B                         = firebox_depth,
                    firebox_width_A                         = firebox_width,
                    firebox_height_H                        = firebox_height,
                    door_opening_width                      = door_opening_width,
                    glass_width                             = glass_width,
                    glass_height                            = glass_height,
                    ash_pit_height_AF                       = ash_pit_height,
                    air_manifold_height_W                   = air_manifold_height,
                    firebox_floor_thickness                 = firebox_floor_thickness,
                    inner_wall_thickness_D1                 = firebox_inner_wall_thickness,
                    outer_wall_thickness_D2                 = firebox_outer_wall_thickness,
                    air_column_thickness_S                  = air_column_thickness,
                    width_between_two_air_columns_sides_E   = width_between_two_air_columns_sides,
                    width_between_two_air_columns_rear_E    = width_between_two_air_columns_rear,
                    reinforcement_bars_offset_in_corners_R1 = reinforcement_bars_offset_in_corners_R1,
                    reinforcement_bars_offset_in_corners_R2 = reinforcement_bars_offset_in_corners_R2,
                    reinforcement_bars_offset_in_corners_R3 = reinforcement_bars_offset_in_corners_R3,
                    injector_height_Z                       = injector_height,
                    height_of_first_row_of_air_injectors_X  = height_of_first_row_of_air_injectors
                )
            case Right("Version 2") =>
                firebox.Ecolabeled_V2                             (
                    pn_reduced                              = heat_output_reduced,
                    actual_air_intake_pipe_shape            = air_intake_shape.getOrElse(Circle(200.mm)),
                    firebox_depth_B                         = firebox_depth,
                    firebox_width_A                         = firebox_width,
                    firebox_height_H                        = firebox_height,
                    door_opening_width                      = door_opening_width,
                    glass_width                             = glass_width,
                    glass_height                            = glass_height,
                    ash_pit_height_AF                       = ash_pit_height,
                    air_manifold_height_W                   = air_manifold_height,
                    firebox_floor_thickness                 = firebox_floor_thickness,
                    inner_wall_thickness_D1                 = firebox_inner_wall_thickness,
                    outer_wall_thickness_D2                 = firebox_outer_wall_thickness,
                    air_column_thickness_S                  = air_column_thickness,
                    width_between_two_air_columns_sides_E   = width_between_two_air_columns_sides,
                    width_between_two_air_columns_rear_E    = width_between_two_air_columns_rear,
                    reinforcement_bars_offset_in_corners_R1 = reinforcement_bars_offset_in_corners_R1,
                    reinforcement_bars_offset_in_corners_R2 = reinforcement_bars_offset_in_corners_R2,
                    reinforcement_bars_offset_in_corners_R3 = reinforcement_bars_offset_in_corners_R3,
                    injector_height_Z                       = injector_height,
                    height_of_first_row_of_air_injectors_X  = height_of_first_row_of_air_injectors
                )

    private def legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(
        p: HeatOutputReduced
    ): HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal =
        p match
            case nd: HeatOutputReduced.NotDefined    => nd
            case h : HeatOutputReduced.HalfOfNominal => h
            case HeatOutputReduced.FromTypeTest(v) =>
                HeatOutputReduced.HalfOfNominal.makeFromValue(v) // safe default, prevent throwing

    given transformer_inv_Ecolabeled: Transformer[firebox.Ecolabeled, Firebox.Ecolabeled] = e =>
        import e.*
        e match
            case _: firebox.Ecolabeled_V1 =>
                Firebox.Ecolabeled                    (
                    heat_output_reduced                     = legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(pn_reduced),
                    version                                 = Left("Version 1"),
                    air_intake_shape                        = None,
                    firebox_depth                           = firebox_depth_B,
                    firebox_width                           = firebox_width_A,
                    firebox_height                          = firebox_height_H,
                    door_opening_width                      = door_opening_width,
                    glass_width                             = glass_width,
                    glass_height                            = glass_height,
                    ash_pit_height                          = ash_pit_height_AF,
                    air_manifold_height                     = air_manifold_height_W,
                    firebox_floor_thickness                 = firebox_floor_thickness,
                    firebox_inner_wall_thickness            = inner_wall_thickness_D1,
                    firebox_outer_wall_thickness            = outer_wall_thickness_D2,
                    air_column_thickness                    = air_column_thickness_S,
                    width_between_two_air_columns_sides     = width_between_two_air_columns_sides_E,
                    width_between_two_air_columns_rear      = width_between_two_air_columns_rear_E,
                    reinforcement_bars_offset_in_corners_R1 = reinforcement_bars_offset_in_corners_R1,
                    reinforcement_bars_offset_in_corners_R2 = reinforcement_bars_offset_in_corners_R2,
                    reinforcement_bars_offset_in_corners_R3 = reinforcement_bars_offset_in_corners_R3,
                    injector_height                         = injector_height_Z,
                    height_of_first_row_of_air_injectors    = height_of_first_row_of_air_injectors_X
                )
            case _: firebox.Ecolabeled_V2 =>
                Firebox.Ecolabeled                    (
                    heat_output_reduced                     = legacy_HeatOutputReduced_to_NotDefined_or_HalfOfNominal(pn_reduced),
                    version                                 = Right("Version 2"),
                    air_intake_shape                        = actual_air_intake_pipe_shape_opt,
                    firebox_depth                           = firebox_depth_B,
                    firebox_width                           = firebox_width_A,
                    firebox_height                          = firebox_height_H,
                    door_opening_width                      = door_opening_width,
                    glass_width                             = glass_width,
                    glass_height                            = glass_height,
                    ash_pit_height                          = ash_pit_height_AF,
                    air_manifold_height                     = air_manifold_height_W,
                    firebox_floor_thickness                 = firebox_floor_thickness,
                    firebox_inner_wall_thickness            = inner_wall_thickness_D1,
                    firebox_outer_wall_thickness            = outer_wall_thickness_D2,
                    air_column_thickness                    = air_column_thickness_S,
                    width_between_two_air_columns_sides     = width_between_two_air_columns_sides_E,
                    width_between_two_air_columns_rear      = width_between_two_air_columns_rear_E,
                    reinforcement_bars_offset_in_corners_R1 = reinforcement_bars_offset_in_corners_R1,
                    reinforcement_bars_offset_in_corners_R2 = reinforcement_bars_offset_in_corners_R2,
                    reinforcement_bars_offset_in_corners_R3 = reinforcement_bars_offset_in_corners_R3,
                    injector_height                         = injector_height_Z,
                    height_of_first_row_of_air_injectors    = height_of_first_row_of_air_injectors_X
                )
            case _ => throw new Exception("not implemented")

    given Transformer[OutsideAirLocationInHeater, firebox.AFPMA_PRSE.AirIntakeDirection] =
        x =>
            x match
                case OutsideAirLocationInHeater.FromBottom =>
                    firebox.AFPMA_PRSE.AirIntakeDirection.FromBottom

    given transformer_AFPMA_PRSE: Transformer[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE] =
        Transformer
            .define[Firebox.AFPMA_PRSE, firebox.AFPMA_PRSE]
            .enableDefaultValues
            .withFieldRenamed(_.heat_output_reduced, _.pn_reduced)
            .withFieldRenamed(_.outside_air_location_in_heater, _.air_intake_direction)
            .withFieldRenamed(_.outside_air_conduit_shape, _.actual_air_intake_pipe_shape)
            .withFieldRenamed(_.firebox_depth, _.firebox_depth_B)
            .withFieldRenamed(_.firebox_width, _.firebox_width_A)
            .withFieldRenamed(_.firebox_height, _.firebox_height_H)
            .withFieldRenamed(_.height_of_first_row_of_air_injectors, _.height_of_first_row_of_air_injectors_X)
            .withFieldRenamed(_.glass_width, _.glass_width)
            .withFieldRenamed(_.glass_height, _.glass_height)
            .withFieldRenamed(_.ash_pit_height, _.ash_pit_height_AF)
            .withFieldRenamed(_.floor_thickness, _.firebox_floor_thickness)
            .withFieldRenamed(_.combustion_air_manifold_height, _.outside_air_inlet_lip_U)
            .withFieldRenamed(_.outside_air_inlet_lip, _.height_of_air_feed_to_columns_W)
            .withFieldRenamed(_.height_of_air_feed_to_columns, _.air_manifold_height_V)
            .withFieldRenamed(_.number_of_air_columns_feeding_firebox, _.nb_of_air_columns_feeding_firebox)
            .withFieldRenamed(_.number_of_air_columns_feeding_door, _.nb_of_air_columns_feeding_door)
            .buildTransformer

    given transformer_dto_Door15aFirebox_Catalog_to_Door15aFirebox_Catalog
        : Transformer[Firebox.Door15aFirebox_Catalog, en15544.std.Door15aFirebox_Catalog] = dto_fb =>
        import dto_fb.*
        import cats.data.Validated.valid
        Door15aFirebox_Catalog_DatabaseEntry                    (
            uniq_id                     = reference,
            mb                          = load_size_nominal,
            sb                          = sb,
            dimensions                  = Dimensions(
                base   = Dimensions.Base.Squared(
                    width = firebox_width,
                    depth = firebox_depth
                ),
                height = firebox_height
            ),
            sb_min                      = sb_min.map(v => v: SB),
            sb_max                      = sb_max.map(v => v: SB),
            mb_min                      = mb_min,
            mb_max                      = mb_max,
            pressure_loss_table_raw     = pressure_loss_table_raw,
            expectedAirIntakePipeShapes = expectedAirIntakePipeShapes,
            actualAirIntakePipeShape    = actualAirIntakePipeShape,
            co2_dry_nominal             = co2_dry_nominal: σ_CO2,
            co2_dry_lowest              = co2_dry_lowest.map(v => v: σ_CO2),
            emissions_values            = EmissionsAndEfficiencyValues(
                firebox_name                       = emissions_values.firebox_name,
                accredited_or_notified_body        = emissions_values.accredited_or_notified_body,
                test_reports                       = emissions_values.test_reports,
                min_efficiency_firebox_nominal     = None,
                min_efficiency_full_stove_nominal  = valid(None),
                min_efficiency_firebox_reduced     = None,
                min_efficiency_full_stove_reduced  = valid(None),
                min_seasonal_efficiency_full_stove = valid(None),
                emissions_values                   = EmissionValues(
                    co   = toTestEmissionValue(emissions_values.emissions_values.co),
                    dust = toTestEmissionValue(emissions_values.emissions_values.dust),
                    ogc  = toTestEmissionValue(emissions_values.emissions_values.ogc),
                    nox  = toTestEmissionValue(emissions_values.emissions_values.nox)
                )
            ),
            glass_area                  = glass_area: GlassArea,
            height_of_lowest_opening    = height_of_lowest_opening,
            pn_reduced                  = heat_output_reduced
        )

    private[firebox] def toTestEmissionValue(dto: TestEmissionValue_DTO): TestEmissionValue =
        TestEmissionValue(
            polluant_name = dto.polluant_name,
            valueO        = dto.valueO,
            test_method   = dto.test_method,
            o2ref         = dto.o2ref
        )
