/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32

import cats.data.ValidatedNel
import cats.syntax.validated.catsSyntaxValidatedId

import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.ShowAsTable

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import io.taig.babel.Locale

case class TraditionalFirebox(
    emissions_values: EmissionsAndEfficiencyValues = Standing_Standard_Burning_Firebox,
    pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
    h11_profondeurDuFoyer: Length,
    h12_largeurDuFoyer: Length,
    h13_hauteurDuFoyer: Length,
    h66_coeffPerteDeChargePorte: QtyD[1],
    h67_sectionCumuleeEntreeAirPorte: QtyD[(Meter ^ 2)],
    h71_largeurVitre: Length,
    h72_hauteurVitre: Length,
) extends From_CalculPdM_V_0_2_32 
{
    override val reference = LocalizedString.from(I18N.firebox_names.traditional)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    val area_calc_method = AreaCalcMethod.AutoIfCubic
    val largeurVitre = h71_largeurVitre
    val hauteurVitre = h72_hauteurVitre

    def air_injector_surface_area = h67_sectionCumuleeEntreeAirPorte

    def validate(m_B: m_B): Locale ?=> ValidatedNel[FireboxError, Unit] = ().validNel
}

object TraditionalFirebox:
    given showAsTable: Locale => ShowAsTable[TraditionalFirebox] = 
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            (I18N.firebox.typ                           :: ""    :: I18N.firebox_names.traditional                   :: Nil) ::
            (I18N.type_of_appliance.descr                           :: ""    :: type_of_appliance.showP                                     :: Nil) ::
            (I18N.firebox.traditional.depth              :: "h11" :: h11_profondeurDuFoyer.to_cm.showP                           :: Nil) ::
            (I18N.firebox.traditional.width              :: "h12" :: h12_largeurDuFoyer.to_cm.showP                              :: Nil) ::
            (I18N.firebox.traditional.height             :: "h13" :: h13_hauteurDuFoyer.to_cm.showP                              :: Nil) ::
            (I18N.firebox.traditional.pressure_loss_coefficient_from_door       :: "h66" :: h66_coeffPerteDeChargePorte.showP                           :: Nil) ::
            (I18N.firebox.traditional.total_air_intake_surface_area_on_door       :: "h67" :: h67_sectionCumuleeEntreeAirPorte.to_cm2.showP               :: Nil) ::
            (I18N.firebox.traditional.glass_width        :: "h71" :: h71_largeurVitre.to_cm.showP                                :: Nil) ::
            (I18N.firebox.traditional.glass_height       :: "h72" :: h72_hauteurVitre.to_cm.showP                                :: Nil) ::
            Nil

object TraditionalFirebox_Module extends From_CalculPdM_V_0_2_32_Module:

    type FB = TraditionalFirebox
    
    extension (firebox: TraditionalFirebox) 
        def toCombustionAirPipe_EN15544: ValidatedNel[String, CombustionAirPipe_Module_EN15544.FullDescr] = 
            import CombustionAirPipe_Module_EN15544.*
            import firebox.*
            CombustionAirPipe_Module_EN15544.incremental
            .define(
                innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                roughness(3.mm), // TOFIX: 3mm or 2mm ???
                addFlowResistance("porte", h66_coeffPerteDeChargePorte, cross_section = h67_sectionCumuleeEntreeAirPorte),
            )
            .toFullDescr().extractPipe

        def toCombustionAirPipe_EN13384: ValidatedNel[String, CombustionAirPipe_Module_EN13384.FullDescr] = 
            import CombustionAirPipe_Module_EN13384.*
            import firebox.*
            CombustionAirPipe_Module_EN13384.incremental
            .define(
                pipeLocation(PipeLocation.HeatedArea), // added for EN13384
                innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                layer(e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                roughness(3.mm), // TOFIX: 3mm or 2mm ???
                addFlowResistance_crossSection("porte", h66_coeffPerteDeChargePorte, h67_sectionCumuleeEntreeAirPorte),
            )
            .toFullDescr().extractPipe
end TraditionalFirebox_Module