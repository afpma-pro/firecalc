/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

import io.taig.babel.Locale
import magnolia1.Transl

sealed trait Ecolabeled extends CertifiedDesign:

    // TOCHECK
    override val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double = 0.5
    override val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double = 2.0

    override val emissions_values = EcoPlus_Combustion_Firebox
    override val min_load         = MinLoad.HalfOfMaxLoad.makeWithoutValue
    override def nominal_load     = None
    override def max_load         = None

    val pn_reduced: HeatOutputReduced
    val co2_dry_nominal: σ_CO2         = 7.05.percent // TOCHECK
    val co2_dry_lowest : Option[σ_CO2] = None
    val actual_air_intake_pipe_shape_opt: Option[PipeShape]
    val firebox_depth_B                 : Length
    val firebox_width_A                 : Length
    val firebox_height_H                : Length
    val door_opening_width              : Length
    val glass_width                     : Length
    val glass_height                    : Length
    val ash_pit_height_AF               : Length
    val air_manifold_height_W           : Length
    val firebox_floor_thickness         : Length
    val inner_wall_thickness_D1         : Length
    val outer_wall_thickness_D2         : Length
    val air_column_thickness_S          : Length
    private val air_column_thickness_door_wall_St = air_column_thickness_S // TOCHECK
    val width_between_two_air_columns_sides_E  : Length
    val width_between_two_air_columns_rear_E   : Length
    val reinforcement_bars_offset_in_corners_R1: Length
    val reinforcement_bars_offset_in_corners_R2: Length
    val reinforcement_bars_offset_in_corners_R3: Length
    val injector_height_Z                      : Length
    private val injector_height_door_wall_Zt: Length = injector_height_Z // TOCHECK
    val height_of_first_row_of_air_injectors_X: Length
    val version                               : Ecolabeled.Version

    val outputs: Ecolabeled.Outputs = Ecolabeled.Outputs(
        distance_between_air_injectors_Y  = distance_between_air_injectors_Y,
        injector_width_rear_wall_Lr       = injector_width_rear_wall_Lr,
        injector_width_side_wall_Ls       = injector_width_side_wall_Ls,
        injector_width_door_wall_Lt       = injector_width_door_wall_Lt,
        injector_height_door_wall_Zt      = injector_height_door_wall_Zt,
        air_column_thickness_door_wall_St = air_column_thickness_door_wall_St
    )

    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = firebox_width_A,
            depth = firebox_depth_B
        ),
        height = firebox_height_H
    )
    override val glass_area: GlassArea  = glass_width * glass_height

    lazy val distance_between_air_injectors_Y =
        // TOCHECK: check Y formula
        (-0.257142 * height_of_first_row_of_air_injectors_X.toUnit[Centi * Meter].value + 10.585714).cm

    lazy val injector_width_side_wall_Ls =
        firebox_depth_B - 9.cm // TOCHECK: should be `firebox_depth_B - reinforcement_bars_offset_in_corners_R2 - reinforcement_bars_offset_in_corners_R3` ?
    lazy val injector_width_rear_wall_Lr =
        firebox_width_A - 9.cm // TOCHECK: should be `firebox_width_A - 2.0 * reinforcement_bars_offset_in_corners_R1` ?

    lazy val injector_width_door_wall_Lt = door_opening_width - 6.cm // TOCHECK

    lazy val air_columns_total_width_side_wall =
        firebox_depth_B - reinforcement_bars_offset_in_corners_R2 - width_between_two_air_columns_sides_E - reinforcement_bars_offset_in_corners_R3
    lazy val air_columns_total_width_rear_wall =
        firebox_width_A - 2.0 * reinforcement_bars_offset_in_corners_R1 - width_between_two_air_columns_rear_E

end Ecolabeled

sealed trait Ecolabeled_V1 extends Ecolabeled:
    override val reference         = LocalizedString.from(I18N.firebox_names.ecolabeled_v1)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    final val version              = Ecolabeled.Version.V1

sealed trait Ecolabeled_V2 extends Ecolabeled:
    override val reference         = LocalizedString.from(I18N.firebox_names.ecolabeled_v2)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    final val version              = Ecolabeled.Version.V2

object Ecolabeled:

    enum Version:
        case V1, V2

    @Transl(I(_.firebox.ecolabeled.computed_values))
    case class Outputs(
        @Transl(I(_.firebox.ecolabeled.distance_between_air_injectors_Y))
        distance_between_air_injectors_Y : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_rear_wall_Lr))
        injector_width_rear_wall_Lr      : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_side_wall_Ls))
        injector_width_side_wall_Ls      : Length,
        @Transl(I(_.firebox.ecolabeled.injector_width_door_wall_Lt))
        injector_width_door_wall_Lt      : Length,
        @Transl(I(_.firebox.ecolabeled.injector_height_door_wall_Zt))
        injector_height_door_wall_Zt     : Length,
        @Transl(I(_.firebox.ecolabeled.air_column_thickness_door_wall_St))
        air_column_thickness_door_wall_St: Length
    )

    given showAsTable: Locale => ShowAsTable[Ecolabeled] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            val I               = I18N.firebox.ecolabeled
            // val IV1 = I18N.firebox.ecolobaled_v1
            val version         = x.version match
                case Version.V1 => I.version_1_with_airbox
                case Version.V2 => I.version_2_without_airbox
            val version_details = x.version match
                case Version.V1 => Nil
                case Version.V2 => (
                    I.version_2_air_intake_shape :: "" :: x.actual_air_intake_pipe_shape_opt
                        .map(_.showP)
                        .getOrElse("-")          :: Nil
                )
            val list            =
                (I18N.firebox.typ                              :: ""   :: I18N.firebox_names.ecolabeled_v1                        :: Nil) ::
                    (I.version                                 :: ""   :: version                                                 :: Nil) ::
                    version_details                            ::
                    (I18N.firebox.firebox_width_A              :: "A"  :: firebox_width_A.to_cm.showP                             :: Nil) ::
                    (I18N.firebox.firebox_depth_B              :: "B"  :: firebox_depth_B.to_cm.showP                             :: Nil) ::
                    (I18N.firebox.firebox_height_H             :: "H"  :: firebox_height_H.to_cm.showP                            :: Nil) ::
                    (I.door_opening_width                      :: ""   :: door_opening_width.to_cm.showP                          :: Nil) ::
                    (I.glass_width                             :: ""   :: glass_width.to_cm.showP                                 :: Nil) ::
                    (I.glass_height                            :: ""   :: glass_height.to_cm.showP                                :: Nil) ::
                    (I.ash_pit_height_AF                       :: "AF" :: ash_pit_height_AF.to_cm.showP                           :: Nil) ::
                    (I.air_manifold_height_W                   :: "W"  :: air_manifold_height_W.to_cm.showP                       :: Nil) ::
                    (I.firebox_floor_thickness                 :: ""   :: firebox_floor_thickness.to_cm.showP                     :: Nil) ::
                    (I.inner_wall_thickness_D1                 :: "D1" :: inner_wall_thickness_D1.to_cm.showP                     :: Nil) ::
                    (I.outer_wall_thickness_D2                 :: "D2" :: outer_wall_thickness_D2.to_cm.showP                     :: Nil) ::
                    (I.air_column_thickness_S                  :: "S"  :: air_column_thickness_S.to_cm.showP                      :: Nil) ::
                    (I.width_between_two_air_columns_sides_E   :: "E"  :: width_between_two_air_columns_sides_E.to_cm.showP       :: Nil) ::
                    (I.width_between_two_air_columns_rear_E    :: "E"  :: width_between_two_air_columns_rear_E.to_cm.showP        :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R1 :: "R1" :: reinforcement_bars_offset_in_corners_R1.to_cm.showP     :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R2 :: "R2" :: reinforcement_bars_offset_in_corners_R2.to_cm.showP     :: Nil) ::
                    (I.reinforcement_bars_offset_in_corners_R3 :: "R3" :: reinforcement_bars_offset_in_corners_R3.to_cm.showP     :: Nil) ::
                    (I.injector_height_Z                       :: "Z"  :: injector_height_Z.to_mm.showP                           :: Nil) ::
                    (I.distance_between_air_injectors_Y        :: "Y"  :: x.outputs.distance_between_air_injectors_Y.to_cm.showP  :: Nil) ::
                    (I.injector_width_rear_wall_Lr             :: "Lr" :: x.outputs.injector_width_rear_wall_Lr.to_cm.showP       :: Nil) ::
                    (I.injector_width_side_wall_Ls             :: "Ls" :: x.outputs.injector_width_side_wall_Ls.to_cm.showP       :: Nil) ::
                    (I.injector_width_door_wall_Lt             :: "Lt" :: x.outputs.injector_width_door_wall_Lt.to_cm.showP       :: Nil) ::
                    (I.injector_height_door_wall_Zt            :: "Zt" :: x.outputs.injector_height_door_wall_Zt.to_mm.showP      :: Nil) ::
                    (I.air_column_thickness_door_wall_St       :: "St" :: x.outputs.air_column_thickness_door_wall_St.to_cm.showP :: Nil) ::
                    (I.height_of_first_row_of_air_injectors_X  :: "X"  :: height_of_first_row_of_air_injectors_X.to_cm.showP      :: Nil) ::
                    Nil
            list.filter(_.nonEmpty)

object Ecolabeled_V1:
    def apply(
        pn_reduced                             : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        firebox_depth_B                        : Length,
        firebox_width_A                        : Length,
        firebox_height_H                       : Length,
        door_opening_width                     : Length,
        glass_width                            : Length,
        glass_height                           : Length,
        ash_pit_height_AF                      : Length,
        air_manifold_height_W                  : Length,
        firebox_floor_thickness                : Length,
        inner_wall_thickness_D1                : Length,
        outer_wall_thickness_D2                : Length,
        air_column_thickness_S                 : Length,
        width_between_two_air_columns_sides_E  : Length,
        width_between_two_air_columns_rear_E   : Length,
        reinforcement_bars_offset_in_corners_R1: Length,
        reinforcement_bars_offset_in_corners_R2: Length,
        reinforcement_bars_offset_in_corners_R3: Length,
        injector_height_Z                      : Length,
        height_of_first_row_of_air_injectors_X : Length
    ): Ecolabeled_V1 =
        new Ecolabeled_V1_or_V2_Impl(
            pn_reduced,
            None,
            firebox_depth_B,
            firebox_width_A,
            firebox_height_H,
            door_opening_width,
            glass_width,
            glass_height,
            ash_pit_height_AF,
            air_manifold_height_W,
            firebox_floor_thickness,
            inner_wall_thickness_D1,
            outer_wall_thickness_D2,
            air_column_thickness_S,
            width_between_two_air_columns_sides_E,
            width_between_two_air_columns_rear_E,
            reinforcement_bars_offset_in_corners_R1,
            reinforcement_bars_offset_in_corners_R2,
            reinforcement_bars_offset_in_corners_R3,
            injector_height_Z,
            height_of_first_row_of_air_injectors_X
        ) with Ecolabeled_V1 {
            override val firebox_type: Locale ?=> String = I18N.firebox_names.ecolabeled_v1
        }

object Ecolabeled_V2:
    def apply(
        pn_reduced                             : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
        actual_air_intake_pipe_shape           : PipeShape,
        firebox_depth_B                        : Length,
        firebox_width_A                        : Length,
        firebox_height_H                       : Length,
        door_opening_width                     : Length,
        glass_width                            : Length,
        glass_height                           : Length,
        ash_pit_height_AF                      : Length,
        air_manifold_height_W                  : Length,
        firebox_floor_thickness                : Length,
        inner_wall_thickness_D1                : Length,
        outer_wall_thickness_D2                : Length,
        air_column_thickness_S                 : Length,
        width_between_two_air_columns_sides_E  : Length,
        width_between_two_air_columns_rear_E   : Length,
        reinforcement_bars_offset_in_corners_R1: Length,
        reinforcement_bars_offset_in_corners_R2: Length,
        reinforcement_bars_offset_in_corners_R3: Length,
        injector_height_Z                      : Length,
        height_of_first_row_of_air_injectors_X : Length
    ): Ecolabeled_V2 =
        new Ecolabeled_V1_or_V2_Impl(
            pn_reduced,
            Some(actual_air_intake_pipe_shape),
            firebox_depth_B,
            firebox_width_A,
            firebox_height_H,
            door_opening_width,
            glass_width,
            glass_height,
            ash_pit_height_AF,
            air_manifold_height_W,
            firebox_floor_thickness,
            inner_wall_thickness_D1,
            outer_wall_thickness_D2,
            air_column_thickness_S,
            width_between_two_air_columns_sides_E,
            width_between_two_air_columns_rear_E,
            reinforcement_bars_offset_in_corners_R1,
            reinforcement_bars_offset_in_corners_R2,
            reinforcement_bars_offset_in_corners_R3,
            injector_height_Z,
            height_of_first_row_of_air_injectors_X
        ) with Ecolabeled_V2 {
            override val firebox_type: Locale ?=> String = I18N.firebox_names.ecolabeled_v2
        }

/** 'Ecolabeled' Firebox according to EN15544 */
private sealed abstract class Ecolabeled_V1_or_V2_Impl(
    val pn_reduced                             : HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
    val actual_air_intake_pipe_shape_opt       : Option[PipeShape], // defined only for V2
    val firebox_depth_B                        : Length,
    val firebox_width_A                        : Length,
    val firebox_height_H                       : Length,
    val door_opening_width                     : Length,
    val glass_width                            : Length,
    val glass_height                           : Length,
    val ash_pit_height_AF                      : Length,
    val air_manifold_height_W                  : Length,
    val firebox_floor_thickness                : Length,
    val inner_wall_thickness_D1                : Length,
    val outer_wall_thickness_D2                : Length,
    val air_column_thickness_S                 : Length,
    val width_between_two_air_columns_sides_E  : Length,
    val width_between_two_air_columns_rear_E   : Length,
    val reinforcement_bars_offset_in_corners_R1: Length,
    val reinforcement_bars_offset_in_corners_R2: Length,
    val reinforcement_bars_offset_in_corners_R3: Length,
    val injector_height_Z                      : Length,
    val height_of_first_row_of_air_injectors_X : Length
) extends Ecolabeled {
    type Self = Ecolabeled
    def height_of_lowest_opening: Length = ash_pit_height_AF
}
