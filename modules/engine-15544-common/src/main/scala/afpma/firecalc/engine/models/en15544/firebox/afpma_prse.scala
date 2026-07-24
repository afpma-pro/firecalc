/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.show_Centimeters

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.biblio
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

/** AFPMA (avec briques injecteurs PRSE) Firebox according to EN15544 */
case class AFPMA_PRSE(
    emissions_values                      : EmissionsAndEfficiencyValues = biblio.afpma.firebox_emissions.AFPMA_PRSE,
    pn_reduced                            : HeatOutputReduced,
    air_intake_direction                  : AFPMA_PRSE.AirIntakeDirection,
    actual_air_intake_pipe_shape          : PipeShape,
    firebox_depth_B                       : Length,
    firebox_width_A                       : Length,
    firebox_height_H                      : Length,
    height_of_first_row_of_air_injectors_X: Length,
    glass_width                           : Length,
    glass_height                          : Length,
    ash_pit_height_AF                     : Length,
    firebox_floor_thickness               : Length,
    outside_air_inlet_lip_U               : Length,
    height_of_air_feed_to_columns_W       : Length,
    air_manifold_height_V                 : Length,
    nb_of_air_columns_feeding_firebox     : Int,
    nb_of_air_columns_feeding_door        : Int,
    override val co2_dry_nominal          : σ_CO2                        = 7.05.percent,
    override val co2_dry_lowest           : Option[σ_CO2]                = None
) extends CertifiedDesign {
    type Self = AFPMA_PRSE

    override val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double = 0.5
    override val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double = 2.0

    override val firebox_type: Locale ?=> String = I18N.firebox_names.afpma_prse
    override def min_load                 = MinLoad.HalfOfMaxLoad.makeWithoutValue
    override def nominal_load             = None
    override def max_load                 = None
    override def height_of_lowest_opening = ash_pit_height_AF
    override val reference                = LocalizedString.from(I18N.firebox_names.afpma_prse)
    override val type_of_appliance        = TypeOfAppliance.WoodLogs
    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = firebox_width_A,
            depth = firebox_depth_B
        ),
        height = firebox_height_H
    )
    override val glass_area: GlassArea  = glass_width * glass_height

    final def equiv_geometry_of_air_columns: PipeShape = rectangle(
        a =
            // TOFIX: found in CalculPdM-v0.2.30
            // - why 1.0cm ?? hauteur ?
            5.mm,
        b =
            // TOFIX: found in CalculPdM-v0.2.30
            // - why x4 and /4 ?
            9.3.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
    )

}

object AFPMA_PRSE:
    given showAsTable: io.taig.babel.Locale => ShowAsTable[AFPMA_PRSE] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            val I = I18N.firebox.afpma_prse
            (I18N.firebox.typ                             :: ""   :: I18N.firebox_names.afpma_prse                      :: Nil) ::
                (I18N.firebox.firebox_depth_B             :: "B"  :: firebox_depth_B.to_cm.showP                        :: Nil) ::
                (I18N.firebox.firebox_width_A             :: "A"  :: firebox_width_A.to_cm.showP                        :: Nil) ::
                (I18N.firebox.firebox_height_H            :: "H"  :: firebox_height_H.to_cm.showP                       :: Nil) ::
                (I.air_intake_direction                   :: ""   :: I.air_intake_direction_from_bottom                 :: Nil) ::
                (I.actual_air_intake_pipe_shape           :: ""   :: actual_air_intake_pipe_shape.showP                 :: Nil) ::
                (I18N.firebox.traditional.glass_width     :: "-"  :: glass_width.to_cm.showP                            :: Nil) ::
                (I18N.firebox.traditional.glass_height    :: "-"  :: glass_height.to_cm.showP                           :: Nil) ::
                (I.height_of_first_row_of_air_injectors_X :: "X"  :: height_of_first_row_of_air_injectors_X.to_cm.showP :: Nil) ::
                (I.ash_pit_height_AF                      :: "AF" :: ash_pit_height_AF.to_cm.showP                      :: Nil) ::
                (I.firebox_floor_thickness                :: ""   :: firebox_floor_thickness.to_cm.showP                :: Nil) ::
                (I.outside_air_inlet_lip_U                :: "U"  :: outside_air_inlet_lip_U.to_cm.showP                :: Nil) ::
                (I.height_of_air_feed_to_columns_W        :: "W"  :: height_of_air_feed_to_columns_W.to_cm.showP        :: Nil) ::
                (I.air_manifold_height_V                  :: "V"  :: air_manifold_height_V.to_cm.showP                  :: Nil) ::
                (I.nb_of_air_columns_feeding_firebox      :: ""   :: nb_of_air_columns_feeding_firebox.showP            :: Nil) ::
                (I.nb_of_air_columns_feeding_door         :: ""   :: nb_of_air_columns_feeding_door.showP               :: Nil) ::
                Nil

    enum AirIntakeDirection  :
        case FromBottom
    object AirIntakeDirection:
        type FromBottom = FromBottom.type
