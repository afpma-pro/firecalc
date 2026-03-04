/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */


package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.show_Centimeters
import afpma.firecalc.units.coulombutils.show_Dimensionless
import afpma.firecalc.units.coulombutils.show_SquareCentimeters

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

case class TraditionalFirebox(
    emissions_values                    : EmissionsAndEfficiencyValues = Standing_Standard_Burning_Firebox,

    h11_profondeurDuFoyer               : Length,
    h12_largeurDuFoyer                  : Length,
    h13_hauteurDuFoyer                  : Length,
    h66_coeffPerteDeChargePorte         : QtyD[1],
    h67_sectionCumuleeEntreeAirPorte    : QtyD[(Meter ^ 2)],
    h71_largeurVitre                    : Length,
    h72_hauteurVitre                    : Length,
    ash_pit_height                      : Length,
    override val co2_dry_nominal        : σ_CO2 = 7.05.percent,
    override val co2_dry_lowest         : Option[σ_CO2] = None
) extends Traditional {
    type Self = TraditionalFirebox
    override val firebox_type: Locale ?=> String = I18N.firebox_names.traditional
    override val min_load          = MinLoad.HalfOfMaxLoad.makeWithoutValue
    override val pn_reduced        = HeatOutputReduced.HalfOfNominal.makeWithoutValue
    override val reference         = LocalizedString.from(I18N.firebox_names.traditional)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    override val height_of_lowest_opening = ash_pit_height
    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = h12_largeurDuFoyer,
            depth = h11_profondeurDuFoyer
        ),
        height = h13_hauteurDuFoyer
    )
    override val glass_area: GlassArea = h71_largeurVitre * h72_hauteurVitre

    override def formulas: FireboxFormulas[Self] =
        import afpma.firecalc.engine.impl.en15544.common.fireboxFormulas_Strict
        fireboxFormulas_Strict

    override def constraints: FireboxConstraints[Self] =
        import afpma.firecalc.engine.impl.en15544.instances.traditionalConstraints
        traditionalConstraints
}

object TraditionalFirebox:
    given showAsTable: Locale => ShowAsTable[TraditionalFirebox] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            (I18N.firebox.typ                          :: ""                                               :: I18N.firebox_names.traditional    :: Nil) ::
                (I18N.type_of_appliance.descr          :: ""                                               :: type_of_appliance.showP           :: Nil) ::
                (I18N.firebox.firebox_depth            :: "h11"                                            :: h11_profondeurDuFoyer.to_cm.showP :: Nil) ::
                (I18N.firebox.firebox_width            :: "h12"                                            :: h12_largeurDuFoyer.to_cm.showP    :: Nil) ::
                (I18N.firebox.firebox_height           :: "h13"                                            :: h13_hauteurDuFoyer.to_cm.showP    :: Nil) ::
                (I18N.firebox.traditional.ash_pit_height_AF
                    :: "AF"                            :: ash_pit_height.to_cm.showP :: Nil) ::
                (I18N.firebox.traditional.pressure_loss_coefficient_from_door
                    :: "h66"                           :: h66_coeffPerteDeChargePorte.showP                :: Nil) ::
                (I18N.firebox.traditional.total_air_intake_surface_area_on_door
                    :: "h67"                           :: h67_sectionCumuleeEntreeAirPorte.to_cm2.showP    :: Nil) ::
                (I18N.firebox.traditional.glass_width  :: "h71"                                            :: h71_largeurVitre.to_cm.showP      :: Nil) ::
                (I18N.firebox.traditional.glass_height :: "h72"                                            :: h72_hauteurVitre.to_cm.showP      :: Nil) ::
                Nil

