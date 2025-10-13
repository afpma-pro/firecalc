/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import cats.data.*
import cats.implicits.toShow

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.std.ThermalResistance.CoefficientOfForm
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.EN13384_Error
import afpma.firecalc.engine.utils.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Locale
import afpma.firecalc.engine.standard.MecaFlu_Error

type Params_13384 = (DraftCondition, LoadQty)
type WithParams_13384[X] = Params_13384 ?=> X
type WithLoadQty[X] = LoadQty ?=> X

object Params_13384:
    
    val DraftMin_LoadNominal   = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
    val DraftMin_LoadMin       = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Reduced)
    val DraftMax_LoadNominal   = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)
    val DraftMax_LoadMin       = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Reduced)

    object givens:
        given DraftMin_LoadNominal: Params_13384 = Params_13384.DraftMin_LoadNominal
        given DraftMin_LoadMin: Params_13384     = Params_13384.DraftMin_LoadMin
        given DraftMax_LoadNominal: Params_13384 = Params_13384.DraftMax_LoadNominal
        given DraftMax_LoadMin: Params_13384     = Params_13384.DraftMax_LoadMin

    given summonmerge: (pReq: DraftCondition, lq: LoadQty) => Params_13384 = (pReq, lq)
    def summon(using p: Params_13384): Params_13384 = p
    given pressReq_from_Params_13384: (p: Params_13384) => DraftCondition = 
        p._1
    given loadQty_from_Params_13384: (p: Params_13384) => LoadQty = 
        p._2

    given show_Params13384: ShowUsingLocale[Params_13384] = showUsingLocale: p =>
        s"""[ ${I18N.pressure_requirements} = ${p._1.show} ; ${I18N.type_of_load.descr} = ${p._2.show} ]"""
    
    def show(using l: Locale, p: Params_13384): String = 
        show_Params13384(using l).show(p)


trait EN13384_1_A1_2019_Application_Alg extends Standard:
    
    type VNel[A] = ValidatedNel[EN13384_Error, A]
    
    val formulas: EN13384_1_A1_2019_Formulas_Alg
    export formulas.{
        P_R_velocityChange_calc as _, // make sure we use P_R_velocityChange only
        ρ_m_calc as _,
        *
    }

    val inputs: Inputs

    def heatingAppliance_final(using ha_input: HeatingAppliance): HeatingAppliance

    // aliases

    /** exterior air temperature */
    def ext_air_temp: EpOp[T_L]

    /** exterior air pressure */
    def ext_air_press: EpOp[p_L]

    /** exterior air relative humidity */
    // def ext_air_rel_hum: Percentage

    /** exterior air */
    // def exteriorAir: EpOp[afpma.wood_combustion.ExteriorAir]

    /** exterior air model */
    def exteriorAirModel: EpOp[ExteriorAir]

    def airIntake_PipeResult: HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]]

    type PipeResultOp[X] = HeatingAppliance.CtxOp5_EFPoTM[X]
    
    lazy val last_known_density_before_connector_pipe     : WithParams_13384[Option[Density]]
    lazy val last_known_velocity_before_connector_pipe    : WithParams_13384[Option[FlowVelocity]]

    def connector_PipeResult   : PipeResultOp[WithParams_13384[PipeResultE]]
    def chimney_PipeResult      : PipeResultOp[WithParams_13384[PipeResultE]]
    def pipesResult_13384_VNelS : PipeResultOp[WithParams_13384[PipesResult_13384_VNelString]]
    def pipesResult_13384       : PipeResultOp[WithParams_13384[VNelString[PipesResult_13384]]]
    
    // chimney temperatures
    def t_chimney_out           : PipeResultOp[WithParams_13384[t_chimney_out]]
    def t_chimney_wall_top      : PipeResultOp[WithParams_13384[t_chimney_wall_top]]

    // 3.1

    /** puissance utile nominale */
    def Q_N(using HeatingAppliance.Powers): Power

    /** puissance utile la plus faible possible */
    def Q_Nmin(using HeatingAppliance.Powers): Option[Power]

    // 5.2
    // Pressure Requirements

    type PressureRequirementsOp[X] = HeatingAppliance.CtxOp6_EFPoTMPr[X]

    def pressureRequirements: PressureRequirementsOp[WithLoadQty[ValidatedNel[MecaFlu_Error, PressureRequirements_13384]]]

    def makePressureRequirements_UnderNegPress(
        atLoadQty       : LoadQty,
        // min draught
        pb_min_draught  : Pressure, 
        pfv_min_draught : Pressure, 
        ph_min_draught  : Pressure, 
        phv_min_draught : Pressure, 
        pr_min_draught  : Pressure, 
        // max draught
        pb_max_draught  : Pressure, 
        pfv_max_draught : Pressure, 
        ph_max_draught  : Pressure, 
        phv_max_draught : Pressure, 
        pr_max_draught  : Pressure, 
    )(using HeatingAppliance.Pressures): PressureRequirements_13384.UnderNegPress

    def makePressureRequirements_UnderPosPress(
        atLoadQty       : LoadQty,
        // min draught
        pb_min_draught  : Pressure, 
        pfv_min_draught : Pressure, 
        ph_min_draught  : Pressure, 
        phv_min_draught : Pressure, 
        pr_min_draught  : Pressure, 
        // max draught
        pb_max_draught  : Pressure, 
        pfv_max_draught : Pressure, 
        ph_max_draught  : Pressure, 
        phv_max_draught : Pressure, 
        pr_max_draught  : Pressure, 
    )(using HeatingAppliance.Pressures): PressureRequirements_13384.UnderPosPress

    // 5.3
    // Temperature Requirements
    
    /** temperature limit */
    def T_ig(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin]
    def temperatureRequirements: PipeResultOp[WithLoadQty[TemperatureRequirements_EN13384]]

    // Section "5.5.2"

    type MassFlowOp = HeatingAppliance.CtxOp4_EFPoM[MassFlow]

    /** Débit massique des fumées */
    def m_dot: MassFlowOp

    /** Débit massique des fumées à puissance utile admissible la plus faible */
    def m_dot_min: MassFlowOp

    /** Débit massique de l'air de combustion */
    def mB_dot: MassFlowOp

    /** Débit massique de l'air de combustion à puissance utile admissible la plus faible */
    def mB_dot_min: MassFlowOp

    def massFlows_final: HeatingAppliance.CtxOp4_EFPoM[HeatingAppliance.MassFlows]

    type VolumeFlowOpGeneric[F[_], A] = EpOp[HeatingAppliance.CtxOp5_EFPoTM[F[A]]]
    type VolumeFlowOp[F[_]] = VolumeFlowOpGeneric[F, VolumeFlow]

    /** Débit volumique des fumées */
    def vf: VolumeFlowOp[cats.Id]

    /** Débit volumique des fumées à puissance utile réduite*/
    def vf_min: VolumeFlowOp[cats.Id]

    /** Débit volumique de l'air de combustion */
    def vfB:  VolumeFlowOp[VNel]
    
    /** Débit volumique de l'air de combustion à puissance utile réduite */
    def vfB_min:  VolumeFlowOp[VNel]

    def volumeFlows_final: VolumeFlowOpGeneric[VNel, HeatingAppliance.VolumeFlows]

    def η_WN(using HeatingAppliance.Efficiency): Percentage
    def η_Wmin(using HeatingAppliance.Efficiency): Option[Percentage]

    def efficiency_final(using haeff: HeatingAppliance.Efficiency): HeatingAppliance.Efficiency

    /** débit calorifique de l'appareil à combustion Q_FN */
    def Q_FN(using
        HeatingAppliance.Efficiency, 
        HeatingAppliance.Powers
    ): Power

    /** débit calorifique de l'appareil à combustion Q_Fmin */
    def Q_Fmin(using
        HeatingAppliance.Efficiency, 
        HeatingAppliance.Powers
    ): Option[Power]

    // Section 5.5.3

    /** Température des fumées à la puissance utile nominale */
    def T_WN(using HeatingAppliance.Temperatures): TKelvin

    /** Température des fumées à la puissance utile la plus faible possible */
    def T_Wmin(using HeatingAppliance.Temperatures): TKelvin

    def temperatures_final(using ha_input_t: HeatingAppliance.Temperatures): HeatingAppliance.Temperatures
    
    // Section "5.5.4", 

    /**
     * Tirage minimal de l'appareil à combustion (P_W) 
     * pour les conduits de fumée fonctionnant sous pression négative
     * 
     */
    def P_W(using hap: HeatingAppliance.Pressures): Pressure

    /**
     * Tirage maximal de l'appareil à combustion (P_W) 
     * pour les conduits de fumée fonctionnant sous pression négative
     * 
     */
    def P_Wmax(using hap: HeatingAppliance.Pressures): Pressure

    /**
     * Pression différentielle maximale 
     * pour les conduits de fumée fonctionnant sous pression positive
     * 
     */
    def P_WO(using hap: HeatingAppliance.Pressures): Pressure

    /**
     * Pression différentielle minimale
     * pour les conduits de fumée fonctionnant sous pression positive
     * 
     */
    def P_WOmin(using hap: HeatingAppliance.Pressures): Pressure

    def pressures_final(using hap: HeatingAppliance.Pressures): HeatingAppliance.Pressures
    
    // Section 5.7.1
    
    def reference_temperatures: ReferenceTemperatures

    // Section "5.7.1.2"
    
    /** température de l'air extérieur */
    def T_L: EpOp[T_L]
    def T_L_map: T_L_override

    // Section "5.7.1.3"

    /** override température de l'air à la sortie du conduit de fumée */
    def T_uo_override: T_uo_temperature_override

    /** température de l'air à la sortie du conduit de fumée */
    def T_uo: T_uo_temperature

    // Section "5.7.2"

    /** pression de l'air extérieur */
    def p_L: EpOp[Pressure]

    // Section "5.7.3.2"

    /** Constante des gaz des fumées, en J/(kg.K)  */
    def R(using HeatingAppliance.FlueGas): WithLoadQty[JoulesPerKilogramKelvin]

    // Section 5.7.4

    /** masse volumique de l'air extérieur */
    def ρ_L: EpOp[Density]

    /** masse volumique de l'air de combustion */
    def ρ_B(T_B: TKelvin): EpOp[Density]

    // Section 5.7.6

    /** point de rosée de l'eau T_p des fumées */
    def T_p(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin]

    /** Température de condensation T_sp des fumées */
    def T_sp(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin]

    /** coefficient de correction de l'instabilité de température */
    def S_H: EpOp[Dimensionless]

    // Section "5.9.1", "Masse volumique des fumées (ρ_m)"

    /** masse volumique moyenne des fumées */
    def ρ_m(T_m: TKelvin)(using HeatingAppliance.FlueGas): WithParams_13384[Density]

    // Section "5.10.2"

    /** tirage théorique disponible dû à l'effet de cheminée */
    def P_H(h: Length, t: TKelvin)(using HeatingAppliance.FlueGas): WithParams_13384[Pressure]

    // Section "5.10.3"

    /**
      * static friction, in Pa
      *
      * @param L longueur du conduit de fumée, en m ;
      * @param D_h diamètre hydraulique intérieur, en m ;
      * @param r valeur moyenne de rugosité de la paroi intérieure, en m ;
      * @param w_m_not_corrected vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
      * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 ;
      * @param t_m température moyenne des fumées, en °C
      * @return
      */
    def P_R_staticFriction(
        L: Length, 
        D_h: Length, 
        r: Roughness,
        w_m_not_corrected: Velocity,
        ρ_m: Density,
        t_m: TKelvin,
    ): EpOp[Pressure]

    /**
      * pressure through velocity change, in Pa
      *
      * @param P_G difference in pressure caused by change of velocity of the flue gas
      * @return
      */
    def P_R_velocityChange(P_G: Pressure): EpOp[Pressure]

    /**
     * dynamic friction, in Pa
     *
     * @param Σ_ζ somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les fumées
     * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 ;
     * @param w_m vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
     * @return
     */
    def P_R_dynamicFriction(
        Σ_ζ: Dimensionless,
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
    ): EpOp[Pressure]

    // 5.10.4

    /** Wind velocity pressure (P_L), in Pa */ 
    def P_L: P_L

    // 5.11.4
    // Perte de charge de l'alimentation en air (P_B)

    /**
     * static friction, in Pa
     *
     * @param LB longueur du conduit d'air de combustion', en m ;
     * @param DhB diamètre hydraulique intérieur, en m ;
     * @param rB valeur moyenne de rugosité de la paroi intérieure, en m ;
     * @param wB  vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s
     * @param ρB masse volumique moyenne de l'air de combustion, en kg/m3 ;
     * @param tB température moyenne de l'air de combustion, en K
     * @return
     */
    def P_B_staticFriction(
        LB: Length, 
        DhB: Length, 
        rB: Roughness,
        wB: Velocity,
        ρB: Density,
        tmB: TKelvin,
    ): EpOp[Pressure]

    /**
     * dynamic friction, in Pa
     *
     * @param ΣζB somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les ouvertures d'aération ou le conduit d'air de combustion.
     * @param ρB masse volumique moyenne de l'air de combustion, en kg/m3 ;
     * @param wB vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s
     * @return
     */
    def P_B_dynamicFriction(
        ΣζB: Dimensionless,
        ρB: Density,
        wB: Velocity,
    ): EpOp[Pressure]
    
    // 7.8.4
    // Températures moyennes pour le calcul de pression

    /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
    def T_mB: EpOp[VNel[T_mB]]

    // Tableau B.1

    // Section "Tableau B.1"

    /** coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s) */
    def f_m1: Double

    /** coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s) */
    def f_m2: Double

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R1: Double

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R2: Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%) */
    def f_c0: Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%) */
    def f_c1: Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%) */
    def f_c2: Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/% */
    def f_c3: Double

    /** coefficient de calcul de la teneur en vapeur d'eau des fumées, en % */
    def f_w: QtyD[Percent]
    
    /** teneur en dioxyde de carbone des fumées sèches, en % */
    def σ_CO2(using HeatingAppliance.FlueGas): WithLoadQty[Percentage]

    /** teneur en vapeur d'eau des fumées, en % */
    def σ_H2O(using HeatingAppliance.FlueGas): WithLoadQty[Percentage]


    def fluegas_final(using hafg: HeatingAppliance.FlueGas): HeatingAppliance.FlueGas

end EN13384_1_A1_2019_Application_Alg