/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32

import cats.data.ValidatedNel
import cats.syntax.validated.catsSyntaxValidatedId
import cats.syntax.option.catsSyntaxOptionId

import afpma.firecalc.engine.biblio.kov.firebox_emissions.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.en15544.ConstraintSlots
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.ShowAsTable

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}
import algebra.instances.all.given
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
    height_of_first_row_of_air_injectors: Length = 5.cm,
) extends From_CalculPdM_V_0_2_32
{
    override val reference = LocalizedString.from(I18N.firebox_names.traditional)
    override val type_of_appliance = TypeOfAppliance.WoodLogs
    val area_calc_method = AreaCalcMethod.AutoIfCubic
    val largeurVitre = h71_largeurVitre
    val hauteurVitre = h72_hauteurVitre

    private def injectors_air_velocity(flow_rate: VolumeFlow): Velocity =
        flow_rate / h67_sectionCumuleeEntreeAirPorte

    def validateSpecificConstraints(m_B: m_B, flow_rate_opt: Option[VolumeFlow]): Locale ?=> ValidatedNel[FireboxError, Unit] = 
        flow_rate_opt match
            case None => MissingFlowRate.invalidNel
            case Some(flow_rate) =>
                val injection_velocity_rate = injectors_air_velocity(flow_rate)
                val injector_velocity_rate_min = 2.m_per_s
                val injector_velocity_rate_max = 4.m_per_s
                if (injection_velocity_rate < injector_velocity_rate_min)
                    InjectorVelocityBelowMinimum(injection_velocity_rate.showP, injector_velocity_rate_min.showP).invalidNel
                else if (injection_velocity_rate > injector_velocity_rate_max)
                    InjectorVelocityAboveMaximum(injection_velocity_rate.showP, injector_velocity_rate_max.showP).invalidNel
                else
                    ().validNel

    override def m_B_constraintSlots: ConstraintSlots.M_B = ConstraintSlots.M_B(
        min = TermConstraint.Min[m_B](10.kg).some,
        max = TermConstraint.Max[m_B](40.kg).some
    )
}

object TraditionalFirebox:
    given showAsTable: Locale => ShowAsTable[TraditionalFirebox] = 
        ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
            import x.*
            (I18N.firebox.typ                            :: ""    :: I18N.firebox_names.traditional                              :: Nil) ::
            (I18N.type_of_appliance.descr                :: ""    :: type_of_appliance.showP                                     :: Nil) ::
            (I18N.firebox.traditional.depth              :: "h11" :: h11_profondeurDuFoyer.to_cm.showP                           :: Nil) ::
            (I18N.firebox.traditional.width              :: "h12" :: h12_largeurDuFoyer.to_cm.showP                              :: Nil) ::
            (I18N.firebox.traditional.height             :: "h13" :: h13_hauteurDuFoyer.to_cm.showP                              :: Nil) ::
            (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name
                                                         :: "X"   :: height_of_first_row_of_air_injectors.to_cm.showP :: Nil)            ::
            (I18N.firebox.traditional.pressure_loss_coefficient_from_door       
                                                         :: "h66" :: h66_coeffPerteDeChargePorte.showP                           :: Nil) ::
            (I18N.firebox.traditional.total_air_intake_surface_area_on_door       
                                                         :: "h67" :: h67_sectionCumuleeEntreeAirPorte.to_cm2.showP               :: Nil) ::
            (I18N.firebox.traditional.glass_width        :: "h71" :: h71_largeurVitre.to_cm.showP                                :: Nil) ::
            (I18N.firebox.traditional.glass_height       :: "h72" :: h72_hauteurVitre.to_cm.showP                                :: Nil) ::
            Nil

object TraditionalFirebox_Module extends From_CalculPdM_V_0_2_32_Module:

    type FB = TraditionalFirebox
    
    extension (firebox: TraditionalFirebox) 
        def toCombustionAirPipe_15544: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_15544.FullDescr] = 
            import CombustionAirPipe_Module_15544.*
            import firebox.*
            CombustionAirPipe_Module_15544.incremental
            .define(
                innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                roughness(3.mm), // TOFIX: 3mm or 2mm ???
                addFlowResistance("porte", h66_coeffPerteDeChargePorte, cross_section = h67_sectionCumuleeEntreeAirPorte),
            )
            .toFullDescr().extractPipe

        def toCombustionAirPipe_13384: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_13384.FullDescr] = 
            import CombustionAirPipe_Module_13384.*
            import firebox.*
            CombustionAirPipe_Module_13384.incremental
            .define(
                pipeLocation(PipeLocation.HeatedArea), // added for EN13384
                innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                layer(e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                roughness(3.mm), // TOFIX: 3mm or 2mm ???
                addFlowResistance_crossSection("porte", h66_coeffPerteDeChargePorte, h67_sectionCumuleeEntreeAirPorte),
            )
            .toFullDescr().extractPipe
end TraditionalFirebox_Module