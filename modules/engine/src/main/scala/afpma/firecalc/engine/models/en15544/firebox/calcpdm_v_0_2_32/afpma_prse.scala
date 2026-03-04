/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.show_Centimeters

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.biblio
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.utils.ShowAsTable

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

/** AFPMA (avec briques injecteurs PRSE) Firebox according to EN15544 */
case class AFPMA_PRSE(
    emissions_values                       : EmissionsAndEfficiencyValues = biblio.afpma.firebox_emissions.AFPMA_PRSE,
    pn_reduced                             : HeatOutputReduced,
    origineArriveeAir                      : AFPMA_PRSE.OutsideAirLocationInHeater,
    arriveeAirGeometry                     : PipeShape,
    h11_profondeurDuFoyer                  : QtyD[Meter],
    h12_largeurDuFoyer                     : QtyD[Meter],
    h13_hauteurDuFoyer                     : QtyD[Meter],
    h83_hauteurEntreSoleEt1erInjecteur_X   : Length,
    h88_largeurVitre                       : Length,
    h89_hauteurVitre                       : Length,
    h91_hauteurDuCendrier_AF               : QtyD[Meter],
    h92_epaisseurSole_S                    : QtyD[Meter],
    h93_hauteurEmbaseDessousSoleFoyer_V    : QtyD[Meter],
    h94_hauteurDepassementArriveeAirFoyer_U: QtyD[Meter],
    h95_hauteurPassageVersColonneAir_W     : QtyD[Meter],
    h96_nbColonnesAirFoyer                 : Int,
    h97_nbColonnesAirPorte                 : Int,
    override val co2_dry_nominal           : σ_CO2 = 7.05.percent,
    override val co2_dry_lowest            : Option[σ_CO2] = None
) extends CertifiedDesign {
    type Self = AFPMA_PRSE
    override val firebox_type: Locale ?=> String = I18N.firebox_names.afpma_prse
    override def min_load = MinLoad.HalfOfMaxLoad.makeWithoutValue
    override def height_of_lowest_opening = h91_hauteurDuCendrier_AF
    override val reference         = LocalizedString.from(I18N.firebox_names.afpma_prse)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    override val dimensions: Dimensions = Dimensions(
        base   = Dimensions.Base.Squared(
            width = h12_largeurDuFoyer,
            depth = h11_profondeurDuFoyer
        ),
        height = h13_hauteurDuFoyer
    )
    override val glass_area: GlassArea = h88_largeurVitre * h89_hauteurVitre

    final def geometrieEquivalenteDesInjecteursAir: PipeShape = rectangle(
        a =
            // TOFIX: found in CalculPdM-v0.2.30
            // - why 1.0cm ?? hauteur ?
            5.mm,
        b =
            // TOFIX: found in CalculPdM-v0.2.30
            // - why x4 and /4 ?
            9.3.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0)
    )

    override def formulas: FireboxFormulas[Self] =
        import afpma.firecalc.engine.impl.en15544.common.fireboxFormulas_Strict
        fireboxFormulas_Strict

    override def constraints: FireboxConstraints[Self] =
        import afpma.firecalc.engine.impl.en15544.instances.afpmaPrseConstraints
        afpmaPrseConstraints
}

object AFPMA_PRSE:
    given showAsTable: io.taig.babel.Locale => ShowAsTable[AFPMA_PRSE] =
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            (I18N.firebox.typ                                                 :: "" :: I18N.firebox_names.afpma_prse                                    :: Nil) ::
                (I18N.firebox.firebox_depth                                   :: "h11" :: h11_profondeurDuFoyer.to_cm.showP                             :: Nil) ::
                (I18N.firebox.firebox_width                                   :: "h12" :: h12_largeurDuFoyer.to_cm.showP                                :: Nil) ::
                (I18N.firebox.firebox_height                                  :: "h13" :: h13_hauteurDuFoyer.to_cm.showP                                :: Nil) ::
                (I18N.firebox.afpma_prse.height_of_first_row_of_air_injectors :: "h83" :: h83_hauteurEntreSoleEt1erInjecteur_X.to_cm.showP              :: Nil) ::
                (I18N.firebox.traditional.glass_width                         :: "h88" :: h88_largeurVitre.to_cm.showP                                  :: Nil) ::
                (I18N.firebox.traditional.glass_height                        :: "h89" :: h89_hauteurVitre.to_cm.showP                                  :: Nil) ::
                Nil

    enum OutsideAirLocationInHeater  :
        case FromBottom
    object OutsideAirLocationInHeater:
        type FromBottom = FromBottom.type

