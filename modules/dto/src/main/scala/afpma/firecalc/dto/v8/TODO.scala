/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.dto.v8

// =============================================================================
// V8 DTO Migration Plan — Firebox Field Alignment (all variants)
// =============================================================================
//
// Goal: Create Firebox_V5 in v8 package whose case class fields match the
// engine model fields 1:1 for all firebox variants (AFPMA_PRSE, Ecolabeled,
// Traditional), eliminating the need for withFieldRenamed shims and manual
// field mapping in FireboxTransformers.
//
// Background:
// - AFPMA_PRSE model fields renamed in 9768f55
// - Ecolabeled model fields renamed in 7e809e2 (French -> English names)
// - Traditional model fields renamed to English names (commit TBD)
//
// =============================================================================
// 1. Enum Rename: OutsideAirLocationInHeater -> AirIntakeDirection
// =============================================================================
//
// Current (dto/common/OutsideAirLocationInHeater.scala):
//   enum OutsideAirLocationInHeater:
//     case FromBottom
//
// Target (dto/common/AirIntakeDirection.scala):
//   enum AirIntakeDirection:
//     case FromBottom
//
// Rationale: Model-side enum is already named AirIntakeDirection (afpma_prse.scala).
// The DTO enum should match for consistency. The ShowUsingLocale i18n key
// already points to air_intake_direction_from_bottom.
//
// Files affected:
//   - dto/common/OutsideAirLocationInHeater.scala -> rename to AirIntakeDirection.scala
//   - dto/all.scala (export)
//   - dto/instances/CommonInstances.scala (Encoder/Decoder givens)
//   - dto/v1-V4 Firebox_*.scala (@Transl annotations reference the enum type)
//   - All test generators referencing OutsideAirLocationInHeater
//   - FireboxTransformers.scala enum transformer (can be removed if types match)
//
// =============================================================================
// 2. Firebox_V5 AFPMA_PRSE Case Class
// =============================================================================
//
// Model commit: 9768f55
//
// --- Field Mapping: Current DTO (V1-V4) -> Firebox_V5 ---
//
// | DTO (V1-V4)                           | -> Firebox_V5 (matches model)              | Change? |
// |---------------------------------------|---------------------------------------------|---------|
// | outside_air_location_in_heater        | -> air_intake_direction                     | RENAME  |
// | outside_air_conduit_shape             | -> actual_air_intake_pipe_shape             | RENAME  |
// | firebox_depth                         | -> firebox_depth_B                          | RENAME  |
// | firebox_width                         | -> firebox_width_A                          | RENAME  |
// | firebox_height                        | -> firebox_height_H                         | RENAME  |
// | height_of_first_row_of_air_injectors  | -> height_of_first_row_of_air_injectors_X   | RENAME  |
// | glass_width                           |  (glass_width)                              | OK      |
// | glass_height                          |  (glass_height)                             | OK      |
// | ash_pit_height                        | -> ash_pit_height_AF                        | RENAME  |
// | floor_thickness                       | -> firebox_floor_thickness                  | RENAME  |
// | combustion_air_manifold_height        | -> air_manifold_height_V                    | RENAME  |
// | outside_air_inlet_lip                 | -> outside_air_inlet_lip_U                  | RENAME  |
// | height_of_air_feed_to_columns         | -> height_of_air_feed_to_columns_W          | RENAME  |
// | number_of_air_columns_feeding_firebox | -> nb_of_air_columns_feeding_firebox        | RENAME  |
// | number_of_air_columns_feeding_door    | -> nb_of_air_columns_feeding_door           | RENAME  |
//
// --- Firebox_V5 AFPMA_PRSE skeleton ---
//
// case class AFPMA_PRSE(
//     @Transl(I(_.firebox.tested.heat_output_reduced))
//     heat_output_reduced                  : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
//     @Transl(I(_.firebox.afpma_prse.air_intake_direction))
//     air_intake_direction                 : AirIntakeDirection,
//     @Transl(I(_.firebox.afpma_prse.actual_air_intake_pipe_shape))
//     actual_air_intake_pipe_shape         : PipeShape,
//     @Transl(I(_.firebox.firebox_depth_B))
//     firebox_depth_B                      : Length,
//     @Transl(I(_.firebox.firebox_width_A))
//     firebox_width_A                      : Length,
//     @Transl(I(_.firebox.firebox_height_H))
//     firebox_height                       : Length,
//     @Transl(I(_.firebox.afpma_prse.height_of_first_row_of_air_injectors_X))
//     height_of_first_row_of_air_injectors_X: Length,
//     @Transl(I(_.firebox.traditional.glass_width))
//     glass_width                          : Length,
//     @Transl(I(_.firebox.traditional.glass_height))
//     glass_height                         : Length,
//     @Transl(I(_.firebox.afpma_prse.ash_pit_height_AF))
//     ash_pit_height_AF                    : Length,
//     @Transl(I(_.firebox.afpma_prse.firebox_floor_thickness))
//     firebox_floor_thickness              : Length,
//     @Transl(I(_.firebox.afpma_prse.air_manifold_height_V))
//     air_manifold_height_V                : Length,
//     @Transl(I(_.firebox.afpma_prse.outside_air_inlet_lip_U))
//     outside_air_inlet_lip_U              : PosLength,
//     @Transl(I(_.firebox.afpma_prse.height_of_air_feed_to_columns_W))
//     height_of_air_feed_to_columns_W      : Length,
//     @Transl(I(_.firebox.afpma_prse.nb_of_air_columns_feeding_firebox))
//     nb_of_air_columns_feeding_firebox    : Int,
//     @Transl(I(_.firebox.afpma_prse.nb_of_air_columns_feeding_door))
//     nb_of_air_columns_feeding_door       : Int
// ) extends Firebox_V5
//
// =============================================================================
// 3. Firebox_V5 Ecolabeled Case Class
// =============================================================================
//
// Model commit: 7e809e2 (French -> English field names)
// The model fields already use English names with dimension suffixes.
// DTO fields need to align for 1:1 matching.
//
// --- Field Mapping: Current DTO (V1-V4) -> Firebox_V5 ---
//
// | DTO (V1-V4)                             | -> Firebox_V5 (matches model)              | Change? |
// |-----------------------------------------|---------------------------------------------|---------|
// | heat_output_reduced                     | -> pn_reduced                               | RENAME  |
// | version                                 |  (version)                                  | OK      |
// | air_intake_shape                        | -> actual_air_intake_pipe_shape_opt         | RENAME  |
// | firebox_depth                           | -> firebox_depth_B                          | RENAME  |
// | firebox_width                           | -> firebox_width_A                          | RENAME  |
// | firebox_height                          | -> firebox_height_H                         | RENAME  |
// | door_opening_width                      |  (door_opening_width)                       | OK      |
// | glass_width                             |  (glass_width)                              | OK      |
// | glass_height                            |  (glass_height)                             | OK      |
// | ash_pit_height                          | -> ash_pit_height_AF                        | RENAME  |
// | air_manifold_height                     | -> air_manifold_height_W                    | RENAME  |
// | firebox_floor_thickness                 |  (firebox_floor_thickness)                  | OK      |
// | firebox_inner_wall_thickness            | -> inner_wall_thickness_D1                  | RENAME  |
// | firebox_outer_wall_thickness            | -> outer_wall_thickness_D2                  | RENAME  |
// | air_column_thickness                    | -> air_column_thickness_S                   | RENAME  |
// | width_between_two_air_columns_sides     | -> width_between_two_air_columns_sides_E    | RENAME  |
// | width_between_two_air_columns_rear      | -> width_between_two_air_columns_rear_E     | RENAME  |
// | reinforcement_bars_offset_in_corners_R1 |  (reinforcement_bars_offset_in_corners_R1)  | OK      |
// | reinforcement_bars_offset_in_corners_R2 |  (reinforcement_bars_offset_in_corners_R2)  | OK      |
// | reinforcement_bars_offset_in_corners_R3 |  (reinforcement_bars_offset_in_corners_R3)  | OK      |
// | injector_height                         | -> injector_height_Z                        | RENAME  |
// | height_of_first_row_of_air_injectors    | -> height_of_first_row_of_air_injectors_X   | RENAME  |
//
// --- Firebox_V5 Ecolabeled skeleton ---
//
// case class Ecolabeled(
//     @Transl(I(_.firebox.tested.heat_output_reduced))
//     pn_reduced                             : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
//     @Transl(I(_.firebox.ecolabeled.version))
//     version                                : Either["Version 1", "Version 2"],
//     @Transl(I(_.firebox.ecolabeled.version_2_air_intake_shape))
//     actual_air_intake_pipe_shape_opt         : Option[PipeShape],
//     @Transl(I(_.firebox.firebox_depth_B))
//     firebox_depth_B                        : Length,
//     @Transl(I(_.firebox.firebox_width_A))
//     firebox_width_A                        : Length,
//     @Transl(I(_.firebox.firebox_height_H))
//     firebox_height_H                       : Length,
//     @Transl(I(_.firebox.ecolabeled.door_opening_width))
//     door_opening_width                     : Length,
//     @Transl(I(_.firebox.traditional.glass_width))
//     glass_width                            : Length,
//     @Transl(I(_.firebox.traditional.glass_height))
//     glass_height                           : Length,
//     @Transl(I(_.firebox.ecolabeled.ash_pit_height_AF))
//     ash_pit_height_AF                      : Length,
//     @Transl(I(_.firebox.ecolabeled.air_manifold_height_W))
//     air_manifold_height_W                  : Length,
//     @Transl(I(_.firebox.ecolabeled.firebox_floor_thickness))
//     firebox_floor_thickness                : Length,
//     @Transl(I(_.firebox.ecolabeled.inner_wall_thickness_D1))
//     inner_wall_thickness_D1                : Length,
//     @Transl(I(_.firebox.ecolabeled.outer_wall_thickness_D2))
//     outer_wall_thickness_D2                : Length,
//     @Transl(I(_.firebox.ecolabeled.air_column_thickness_S))
//     air_column_thickness_S                 : Length,
//     @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_sides_E))
//     width_between_two_air_columns_sides_E  : Length,
//     @Transl(I(_.firebox.ecolabeled.width_between_two_air_columns_rear_E))
//     width_between_two_air_columns_rear_E   : Length,
//     @Transl(I(_.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R1))
//     reinforcement_bars_offset_in_corners_R1: Length,
//     @Transl(I(_.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R2))
//     reinforcement_bars_offset_in_corners_R2: Length,
//     @Transl(I(_.firebox.ecolabeled.reinforcement_bars_offset_in_corners_R3))
//     reinforcement_bars_offset_in_corners_R3: Length,
//     @Transl(I(_.firebox.ecolabeled.injector_height_Z))
//     injector_height_Z                      : Length,
//     @Transl(I(_.firebox.afpma_prse.height_of_first_row_of_air_injectors_X))
//     height_of_first_row_of_air_injectors_X : Length
// ) extends Firebox_V5
//
// =============================================================================
// 4. Firebox_V5 Traditional Case Class
// =============================================================================
//
// DONE: Traditional model fields renamed to English names (commit TBD).
// Model now uses: firebox_depth_B, firebox_width_A, firebox_height_H,
// pressure_loss_coefficient_from_door, total_air_intake_surface_area_on_door,
// glass_width, glass_height, ash_pit_height_AF.
//
// --- Field Mapping: Current DTO (V1-V4) -> Firebox_V5 ---
//
// | DTO (V1-V4)                               | -> Firebox_V5 (matches model)              | Change? |
// |-------------------------------------------|---------------------------------------------|---------|
// | heat_output_reduced                       | -> pn_reduced (or removed — model hardcodes)| REMOVE? |
// | firebox_depth                             | -> firebox_depth_B                          | RENAME  |
// | firebox_width                             | -> firebox_width_A                          | RENAME  |
// | firebox_height                            | -> firebox_height_H                         | RENAME  |
// | height_of_lowest_opening                  | -> ash_pit_height_AF                        | RENAME  |
// | pressure_loss_coefficient_from_door       | -> pressure_loss_coefficient_from_door      | OK      |
// | total_air_intake_surface_area_on_door     | -> total_air_intake_surface_area_on_door    | OK      |
// | glass_width                               | -> glass_width                              | OK      |
// | glass_height                              | -> glass_height                             | OK      |
//
// --- Firebox_V5 Traditional skeleton ---
//
// case class Traditional(
//     @Transl(I(_.firebox.firebox_depth_B))
//     firebox_depth_B                        : Length,
//     @Transl(I(_.firebox.firebox_width_A))
//     firebox_width_A                        : Length,
//     @Transl(I(_.firebox.firebox_height_H))
//     firebox_height_H                       : Length,
//     @Transl(I(_.en15544.terms_xtra.height_of_the_lowest_opening.name))
//     ash_pit_height_AF                      : Length,
//     @Transl(I(_.firebox.traditional.pressure_loss_coefficient_from_door))
//     pressure_loss_coefficient_from_door    : QtyD[1],
//     @Transl(I(_.firebox.traditional.total_air_intake_surface_area_on_door))
//     total_air_intake_surface_area_on_door  : QtyD[(Meter ^ 2)],
//     @Transl(I(_.firebox.traditional.glass_width))
//     glass_width                            : Length,
//     @Transl(I(_.firebox.traditional.glass_height))
//     glass_height                           : Length
// ) extends Firebox_V5
//
// =============================================================================
// 5. FireboxTransformers Impact
// =============================================================================
//
// Once Firebox_V5 is in place, all three transformers (AFPMA_PRSE, Ecolabeled,
// Traditional) can use enableDefaultValues without withFieldRenamed shims
// since fields match 1:1. The old V1-V4 transformers remain unchanged
// (backward compatibility).
//
// For Ecolabeled, the manual field mapping in transformer_Ecolabeled_Ecolabeled
// (lines 78-190) can also be replaced with enableDefaultValues.
//
// =============================================================================
// 6. Files to Create / Modify
// =============================================================================
//
// Create:
//   - dto/v8/Firebox_V5.scala          (new DTO version with aligned fields)
//   - dto/common/AirIntakeDirection.scala (renamed enum, replace OutsideAirLocationInHeater)
//   - dto/.jvm/src/test/.../Firebox_V5_Generators.scala (test generator)
//
// Modify:
//   - dto/all.scala                    (export Firebox_V5, AirIntakeDirection)
//   - dto/instances/CommonInstances.scala (Encoder/Decoder for AirIntakeDirection)
//   - engine-15544-common/.../FireboxTransformers.scala (new transformer for V5)
//   - i18n/en.conf, fr.conf            (no new keys needed — existing keys match)
//   - i18n/I18nData_Firebox.scala      (no changes — keys already aligned)
//
// Deprecate (keep for backward compat, mark @deprecated):
//   - dto/common/OutsideAirLocationInHeater.scala (type alias or deprecation)
// Placeholder to satisfy compiler
private object V8MigrationPlaceholder
