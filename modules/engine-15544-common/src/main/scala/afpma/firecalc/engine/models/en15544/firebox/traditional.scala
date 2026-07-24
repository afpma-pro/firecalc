/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.show_Centimeters
import afpma.firecalc.units.coulombutils.show_Dimensionless
import afpma.firecalc.units.coulombutils.show_SquareCentimeters

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

case class TraditionalFirebox(
    emissions_values                     : EmissionsAndEfficiencyValues = Standing_Standard_Burning_Firebox,
    firebox_depth_B                      : Length,
    firebox_width_A                      : Length,
    firebox_height_H                     : Length,
    pressure_loss_coefficient_from_door  : QtyD[1],
    total_air_intake_surface_area_on_door: QtyD[(Meter ^ 2)],
    glass_width                          : Length,
    glass_height                         : Length,
    ash_pit_height_AF                    : Length,
    override val co2_dry_nominal         : σ_CO2                        = 7.05.percent,
    override val co2_dry_lowest          : Option[σ_CO2]                = None
) extends Traditional {
    type Self = TraditionalFirebox
    override val firebox_type: Locale ?=> String = I18N.firebox_names.traditional
    override val min_load                 = MinLoad.HalfOfMaxLoad.makeWithoutValue
    override def nominal_load             = None
    override def max_load                 = None
    override val pn_reduced               = HeatOutputReduced.HalfOfNominal.makeWithoutValue
    override val reference                = LocalizedString.from(I18N.firebox_names.traditional)
    override val type_of_appliance        = TypeOfAppliance.WoodLogs
    override val height_of_lowest_opening = ash_pit_height_AF
    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = firebox_width_A,
            depth = firebox_depth_B
        ),
        height = firebox_height_H
    )
    override val glass_area: GlassArea  = glass_width * glass_height

}

object TraditionalFirebox:
    given showAsTable: Locale => ShowAsTable[TraditionalFirebox] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            (I18N.firebox.typ                          :: ""                                                 :: I18N.firebox_names.traditional :: Nil) ::
                (I18N.type_of_appliance.descr          :: ""                                                 :: type_of_appliance.showP        :: Nil) ::
                (I18N.firebox.firebox_depth_B          :: "B"                                                :: firebox_depth_B.to_cm.showP    :: Nil) ::
                (I18N.firebox.firebox_width_A          :: "A"                                                :: firebox_width_A.to_cm.showP    :: Nil) ::
                (I18N.firebox.firebox_height_H         :: "H"                                                :: firebox_height_H.to_cm.showP   :: Nil) ::
                (I18N.firebox.traditional.ash_pit_height_AF
                    :: "AF"                            :: ash_pit_height_AF.to_cm.showP                      :: Nil) ::
                (I18N.firebox.traditional.pressure_loss_coefficient_from_door
                    :: ""                              :: pressure_loss_coefficient_from_door.showP          :: Nil) ::
                (I18N.firebox.traditional.total_air_intake_surface_area_on_door
                    :: ""                              :: total_air_intake_surface_area_on_door.to_cm2.showP :: Nil) ::
                (I18N.firebox.traditional.glass_width  :: ""                                                 :: glass_width.to_cm.showP        :: Nil) ::
                (I18N.firebox.traditional.glass_height :: ""                                                 :: glass_height.to_cm.showP       :: Nil) ::
                Nil
