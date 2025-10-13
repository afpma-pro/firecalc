/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import cats.data.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.Standard
import afpma.firecalc.engine.models.en13384.std.ThermalResistance.CoefficientOfForm
import afpma.firecalc.engine.models.gtypedefs.ThermalConductivity
import afpma.firecalc.engine.standard.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*
import coulomb.syntax.*
import coulomb.ops.standard.all.given

trait `EN13384_1_A1_2019_Formulas_Alg` extends Standard:
    
    import EN13384_1_A1_2019_Formulas_Alg.*
    
    // type definitions
    import afpma.firecalc.engine.models.en13384.typedefs.*

    // Section "5.2"
    // "Pressure Requirements"

    // Section "5.2.1"
    // "Conduits de fumée fonctionnant sous pression négative"

    /**
      * tirage minimal au niveau de l’admission des fumées dans le conduit (voir 5.10), en Pa
      *
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_L pression de la vitesse du vent, en Pa ;
      * @return
      */
    def P_Z(
        P_H: Pressure,
        P_R: Pressure,
        P_L: Pressure,
    ): Pressure

    /**
      * tirage minimal requis au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_W tirage minimal de l'appareil à combustion, en Pa ;
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air (voir 5.11.3), en Pa ;
      * @return
      */
    def P_Ze(
        P_W: Pressure,
        P_FV: Pressure,
        P_B: Pressure,
    ): Pressure

    // Section "5.2.2"
    // "Conduits de fumée fonctionnant sous pression positive"

    /**
      * pression positive maximale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_L pression de la vitesse du vent, en Pa ;
      * @return
      */
    def P_ZO(
        P_R: Pressure,
        P_H: Pressure,
        P_L: Pressure,
    ): Pressure
    /**
      * pression positive minimale au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @return
      */
    def P_ZOmin(
        P_R: Pressure,
        P_H: Pressure,
    ): Pressure

    // Section "5.3"
    // Exigence relative à la température

    /**
     * limite de température, en K 
     *
     * @param fgCondition flue gas condition (dry or wet)
     * @param T_sp limite de température de condensation des fumées
     * @return
     */
    def T_ig_calc(fgCondition: FlueGasCondition, T_sp: TKelvin): TKelvin

    // Section "5.5.2"
    // "Débit massique des fumées et débit massique de l'air de combustion"

    // m_dot
    // m_B_dot
    // connu ou à l'aide de B.1 B.2 ou B.3

    /**
     * Calcule le débit massique des fumées ṁ, en g/s
     *
     * @param f_m1 coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s)
     * @param f_m2 coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s)
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @param Q_F débit calorifique de l'appareil à combustion, en kW
     * @return débit massique des fumées, en g/s
     */
    def m_dot_calc(
        f_m1: Double,
        f_m2: Double,
        σ_CO2: QtyD[Percent],
        Q_F: QtyD[Kilo * Watt]
    ): QtyD[Gram / Second]

    /**
     * Calcule le débit massique de l'air de combustion ṁB, en g/s
     *
     * @param f_m1 coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s)
     * @param f_m3 coefficient de calcul du débit massique de l'air de combustion, en g/(kW ⋅ s) ;
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @param Q_F débit calorifique de l'appareil à combustion, en kW
     * @return débit massique des fumées, en g/s
     */
    def mB_dot_calc(
        f_m1: Double,
        f_m3: Double,
        σ_CO2: QtyD[Percent],
        Q_F: QtyD[Kilo * Watt]
    ): QtyD[Gram / Second]

    /**
     * Calcule le débit calorifique de l'appareil à combustion Q_F, en kW
     *
     * @param η_W rendement de l'appareil à combustion, en %
     * @param Q puissance utile de l'appareil à combustion, en kW
     * @return débit calorifique de l'appareil à combustion, en kW
     */
    def Q_F_calc(η_W: QtyD[Percent], Q: QtyD[Kilo * Watt]): QtyD[Kilo * Watt]

    /**
     * Calcule la teneur en dioxyde de carbone des fumées sèches, en %
     *
     * @param combustible type de combustible
     * @return teneur en dioxyde de carbone des fumées sèches, en %
     */
    def σ_CO2_calc(
        combustible: FuelType
    ): Either[Throwable, Dimensionless]

    // Section "5.5.3", "Température des fumées"

    /**
      * Température des fumées à la puissance utile nominale (T_WN)
      *
      * @param twn Température des fumées à la puissance utile nominale (T_WN)
      * @return Température des fumées à la puissance utile nominale (T_WN)
      */
    def T_WN(twn: TCelsius): TCelsius

    /**
      * Température des fumées à la puissance utile la plus faible possible (T_Wmin)
      *
      * @param twmin (optional) Température des fumées à la puissance utile la plus faible possible
      * @param twn Température des fumées à la puissance utile nominale (T_WN)
      * @return Température des fumées à la puissance utile nominale (T_WN)
      */
    def T_Wmin(twmin: Option[TCelsius], twn: TCelsius): TCelsius

    // Section "5.5.4", 
    // Tirage minimal de l'appareil à combustion (P_W) 
    // pour les conduits de fumée fonctionnant sous pression négative

    /**
      * Tirage minimal de l'appareil à combustion (P_W) 
      * pour les conduits de fumée fonctionnant sous pression négative
      * 
      * @param pw valeur donnée par le fabricant
      * @return
      */
    def P_W_calc(pw: Pressure): Pressure

    /**
      * Tirage maximal de l'appareil à combustion (P_Wmax) 
      * pour les conduits de fumée fonctionnant sous pression négative
      * 
      * @param pwmax valeur donnée par le fabricant
      * @return
      */
    def P_Wmax_calc(pwmax: Pressure): Pressure

    // Section "5.6.3", "Résistance Thermique (1 / Λ)"

    def thermal_resistance_for_layer_calc(
        cof: CoefficientOfForm, 
        dhi: Length, 
        dho: Length, 
        lambda: ThermalConductivity
    ): SquareMeterKelvinPerWatt

    def thermal_resistance_for_layers_calc(
        mean_gas_temp: TCelsius, 
        startGeom: PipeShape, 
        layers: List[AppendLayerDescr]
    ): Either[ThermalResistance_Error, SquareMeterKelvinPerWatt]

    // Section "5.7.1.2", "Température de l'air extérieur (T_L)"

    /**
     * Calcule la température de l’air extérieur T_L, en K
     *
     * @param pReq pressure requirements (min or max draft)
     * @param T_L_override température de l’air extérieur T_L, en K (si spécification nationale)
     * @return température de l’air extérieur, en K
     */
    def T_L_calc(
        pReq: DraftCondition,
        T_L_override: Map[DraftCondition, T_L]
    ): T_L

    // Section "5.7.1.3", "Température du l'air ambiant (T_u)"

    /**
     * Calcule la température de l'air ambiant T_u (conditions humides ou sèches), en K
     *
     * @param T_L température de l’air extérieur T_L, en K
     * @param airSpace air space
     * @param pReq pressure requirements (min or max draft)
     * @param flueGasCond flue gas condition (dry or wet)
     * @param unheatedHeightInsideAndOutside hauteur de la zone non chauffée à l'intérieur et à l'extérieur du bâtiment, en m
     * @param T_uo_wet_override température de l'air ambiant (conditions humides) à la sortie du conduit de fumée, en K (si spécification nationale)
     * @param T_uo_dry_override température de l'air ambiant (conditions humides) à la sortie du conduit de fumée, en K (si spécification nationale)
     * @param T_u_custom_area température de l'air ambiant dans la zone spécifique définie (labo)
     * @param A_ub surface extérieure du conduit de fumée dans la salle des chaudières, in m2
     * @param A_uh surface extérieure du conduit de fumée dans les zones chauffées, en m2
     * @param A_uu surface extérieure du conduit de fumée dans les zones non chauffées à l'intérieur du bâtiment, en m2
     * @param A_ul surface extérieure du conduit de fumée à l'extérieur du bâtiment, en m2
     * @param A_u_custom_area surface extérieure du conduit de fumée dans la zone spécifique (labo), en m2
     * @return Température de l'air ambiant, en K
     */
    def T_u_calc(
        T_L: T_L,
        airSpace: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
        unheatedHeightInsideAndOutside: QtyD[Meter],
        T_uo_override: T_uo_temperature_override,
        T_u_custom_area: Option[TKelvin],
    )(
        A_ub: QtyD[(Meter ^ 2)],
        A_uh: QtyD[(Meter ^ 2)],
        A_uu: QtyD[(Meter ^ 2)],
        A_ul: QtyD[(Meter ^ 2)],
        A_u_custom_area: QtyD[(Meter ^ 2)],
    ): Either[NoOutsideSurfaceFound, TempD[Kelvin]]

    def T_u_temp_helper(
        T_L: T_L,
        airSpace: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
        unheatedHeightInsideAndOutside: QtyD[Meter],
        T_uo_override: T_uo_temperature_override,
    )(
        ambAirTempSet: AmbiantAirTemperatureSet
    ): TKelvin

    val T_uo_default: T_uo_temperature
    protected val T_ux_defaults: TuTemperatures

    def T_uo(using T_uo_override: T_uo_temperature_override): T_uo_temperature

    def T_uo_calc(
        T_L: T_L,
        unheatedHeightInsideAndOutside: Length,
        airSpace: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
    )(using
        T_uo_override: T_uo_temperature_override
    ): TKelvin

    /**
      * Interpretation of 5.7.1.3
      * 
      * Define how we consider a detailed definition of air gaps in relation to 5.7.1.3
      * Issues : 
      *     - no minimum width is defined for a ventilated air gap
      *     - no explicit mention when we have a mix of sections : some without and some with air gap defined (ventilated or not)
      *
      * @param airGap
      * @return TypeLameAir
      */
    // def interpretTypeLameAirFromAirGap(airGap: AirGap): TypeLameAir

    /**
      * Interpretation of 5.7.1.3
      * 
      * @param airSpaceDetailed
      * @return AirSpace
      */
    def interpretAirSpaceFromAirSpaceDetailed(airSpaceDetailed: AirSpaceDetailed): AirSpace

    // Section "5.7.2"
    // Pression de l'air extérieur (p_L)

    /**
      * Pression de l'air extérieur (p_L)
      *
      * @param T_L température de l'air extérieur, en K
      * @param z hauteur au-dessus du niveau de la mer, en m
      * @return Pression de l'air extérieur (p_L)
      */
    def p_L_calc(
        T_L: TempD[Kelvin],
        z: QtyD[Meter]
    ): QtyD[Pascal]

    // Section "5.7.3.1"
    // Constante des gaz de l'air (R_L)

    /** Constante des gaz de l'air */
    def R_L: JoulesPerKilogramKelvin

    // Section "5.7.3.2"
    // Constante des gaz des fumées (R), en J/(kg.K) 

    /**
     * Constante des gaz des fumées, en J/(kg.K) 
     *
     * @param comb type de combustible
     * @param σ_H2O teneur en vapeur d'eau des fumées, en %
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     */
    def R_calc(
        comb: FuelType,
        σ_H2O: QtyD[Percent],
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin

    // Section 5.7.4, Masse volumique de l'air extérieur (ρ_L)

    /**
      * Masse volumique de l'air extérieur (ρ_L)
      *
      * @param p_L pression de l’air extérieur, en Pa
      * @param T_L température de l’air extérieur, en K
      * @return
      */
    def ρ_L_calc(
        p_L: Pressure,
        T_L: TKelvin,
    ): Density

    /**
      * Masse volumique de l'air de combustion (ρ_B)
      *
      * @param T_B pression de l’air de combustion, en Pa
      * @param z hauteur au-dessus du niveau de la mer, en m
      * @return
      */
    def ρ_B_calc(
        T_B: TKelvin,
        z: Length
    ): Density
    
    // Section "5.7.5", "Capacité calorifique spécifique des fumées (c_p)"

    /**
     * Calcule la capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     *
     * @param t_m température moyenne des fumées, en °C
     * @param f_c0 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%)
     * @param f_c1 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%)
     * @param f_c2 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%)
     * @param f_c3 coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/%
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @return
     */
    def c_p_calc(
        t_m: TempD[Celsius],
        f_c0: Double,
        f_c1: Double,
        f_c2: Double,
        f_c3: Double,
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin

    /**
     * Calcule la capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     * (depuis le type de combustible)
     *
     * @param t_m température moyenne des fumées, en °C
     * @param comb type de combustible utilisé
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @return capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     */
    def c_p_calc(
        t_m: TempD[Celsius],
        comb: FuelType,
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin

    // Section 5.7.6
    // Température de condensation (T_sp)

    /**
      * Température de condensation (T_sp)
      *
      * @param comb type de combustible
      * @param T_p point de rosée de l'eau des fumées pour différents combustibles et différentes concentrations envolume de CO 2 dans les fumées doit être calculé à l'aide des Formules (B.5), (B.6) et (B.7).
      * @param T_sp température de condensation des fumées est le point de rosée acide
      * @return
      */
    def T_sp(
        comb: FuelType,
        T_p: TKelvin,
        ΔT_sp: Option[TKelvin],
    ): TKelvin

    // Section "5.7.7", Facteur de correction de l'instabilité de la température (S_H)

    /**
      * Calcule le facteur de correction de l'instabilité de la température (S_H)
      *
      * @param pReq pressure requirements (min or max draft)
      * @return S_H
      */
    def S_H_calc(
        pReq: DraftCondition
    ): Dimensionless

    // Section "5.7.8", "Coefficient de sécurité du débit (S_E)"

    def S_E_calc(pReq: DraftCondition): Dimensionless

    // Section "5.8", "Détermination des températures"

    // Section "5.8.1", "Généralités"

    /**
     * Calcule la température moyenne des fumées T_m
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_e température des fumées au niveau de l'orifice d'admission du conduit, en K
     * @param K coefficient de refroidissement du conduit de fumée (voir 5.8.2)
     * @return température moyenne des fumées T_m, en K
     */
    def T_m_calc(
        T_u: TempD[Kelvin],
        T_e: TempD[Kelvin],
        K: Dimensionless
    ): TempD[Kelvin]

    /**
     * Calcule la température des fumées à la sortie du conduit de fumée T_o
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_e température des fumées au niveau de l'orifice d'admission du conduit, en K
     * @param K coefficient de refroidissement du conduit de fumée (voir 5.8.2)
     * @return température des fumées à la sortie du conduit de fumée, en K
     */
    def T_o_calc(
        T_u: TempD[Kelvin],
        T_e: TempD[Kelvin],
        K: Dimensionless
    ): TempD[Kelvin]


    /**
     * Calcule la température moyenne des fumées dans le conduit de raccordement T_mV
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_W température des fumées de l'appareil à combustion", en K.
     * @param K_V coefficient de refroidissement du conduit de raccordement (voir 5.8.2)
     * @return température moyenne des fumées dans le conduit de raccordement, en K
     */
    def T_mV_calc(
        T_u: TempD[Kelvin],
        T_W: TempD[Kelvin],
        K_V: Dimensionless
    ): TempD[Kelvin]

    /**
     * Calcule la température des fumées au niveau de l'orifice d'admission du conduit de fumée T_e
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_w température des fumées de l'appareil à combustion", en K.
     * @param K_V coefficient de refroidissement du conduit de raccordement (voir 5.8.2)
     * @return température des fumées au niveau de l'orifice d'admission du conduit de fumée, en K
     */
    def T_e_calc(
        T_u: TempD[Kelvin],
        T_w: TempD[Kelvin],
        K_V: Dimensionless
    ): TempD[Kelvin]

    // Section "5.8.2", "Calcul du coefficient de refroidissement (K)"

    /**
     * Calcule le coefficient de refroidissement du conduit de fumée K
     *
     * @param c_p capacité calorifique spécifique des fumées (voir 5.7.5), en J/(kg ⋅ K)
     * @param k coefficient de transfert thermique du conduit de fumée (voir 5.8.3), en W/(m 2 ⋅ K)
     * @param L longueur du conduit de fumée, en m
     * @param m_dot débit massique des fumées (voir 5.5.2), en kg/s
     * @param U circonférence intérieure du conduit de fumée, en m
     * @return Coefficient de refroidissement du conduit de fumée
     */
    def K_calc(
        c_p: JoulesPerKilogramKelvin,
        k: WattsPerSquareMeterKelvin,
        L: QtyD[Meter],
        m_dot: QtyD[Kilogram / Second],
        U: QtyD[Meter]
    ): Dimensionless

    /**
     * Calcule le coefficient de refroidissement du conduit de raccordement K_V
     *
     * @param c_p capacité calorifique spécifique des fumées (voir 5.7.5), en J/(kg ⋅ K)
     * @param k_V coefficient de transfert thermique du conduit de raccordement (voir 5.8.3), en W/(m 2 ⋅ K)
     * @param L_V longueur du conduit de raccordement, en m
     * @param m_dot débit massique des fumées (voir 5.5.2), en kg/s
     * @param U_V circonférence intérieure du conduit de raccordement, en m
     * @return Coefficient de refroidissement du conduit de raccordement
     */
    def K_V_calc(
        c_p: JoulesPerKilogramKelvin,
        k_V: WattsPerSquareMeterKelvin,
        L_V: QtyD[Meter],
        m_dot: QtyD[Kilogram / Second],
        U_V: QtyD[Meter]
    ): K_V

    // Section "5.8.3", "Coefficient de transfert thermique (k_b)"

    // Section "5.8.3.1", "Généralités"

    /**
     * Calcule le coefficient de transfert thermique du conduit de fumée à la température d'équilibre K_b
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param D_ha diamètre hydraulique extérieur, en m
     * @param α_a coefficient externe de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param α_i coefficient interne de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param Λinverse résistance thermique (voir 5.6.3), en m 2 · K/W
     * @return coefficient de transfert thermique du conduit de fumée à la température d'équilibre K_b, en W/(m 2 ⋅ K)
     */
    def k_b_calc(
        D_h: QtyD[Meter],
        D_ha: QtyD[Meter],
        α_a: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        Λinverse: SquareMeterKelvinPerWatt
    ): WattsPerSquareMeterKelvin

    /**
     * Calcule le coefficient de transfert thermique du conduit de fumée à une température de non-équilibre k
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param D_ha diamètre hydraulique extérieur, en m
     * @param S_H coefficient de correction de l'instabilité de température (voir 5.7.7)
     * @param α_a coefficient externe de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param α_i coefficient interne de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param Λinverse résistance thermique (voir 5.6.3), en m 2 · K/W
     * @return coefficient de transfert thermique du conduit de fumée à une température de non-équilibre k, en W/(m 2 ⋅ K)
     */
    def k_calc(
        D_h: QtyD[Meter],
        D_ha: QtyD[Meter],
        S_H: Dimensionless,
        α_a: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        Λinverse: SquareMeterKelvinPerWatt
    ): WattsPerSquareMeterKelvin

    // Section "5.8.3.2", "Coefficient interne de transfert thermique (α_i)"

    /**
     * Calcule le coefficient de transfert thermique dans le conduit de fumée α_i
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param N_u nombre de Nusselt
     * @param λ_A coefficient de conductivité thermique des fumées, en W/(m . K)
     * @return coefficient de transfert thermique dans le conduit de fumée α_i, en W/(m2 ⋅ K)
     */
    def α_i_calc(
        D_h: QtyD[Meter],
        N_u: Dimensionless,
        λ_A: WattsPerMeterKelvin
    ): WattsPerSquareMeterKelvin

    /**
     * Calcule le nombre de Nusselt moyen sur la hauteur du conduit de fumée
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param L_tot longueur totale entre l'orifice d'admission des fumées dans le conduit et la sortie du conduit, en m
     * @param P_r nombre de Prandtl
     * @param R_e nombre de Reynolds
     * @param Ψ coefficient de résistance due au frottement pour un écoulement hydraulique brut (voir 5.10.3.3)
     * @param Ψ_smooth coefficient de résistance due au frottement pour un écoulement hydraulique régulier (voir 5.10.3.3 pour r = 0)
     * @return nombre de Nusselt moyen sur la hauteur du conduit de fumée
     */
    def N_u_calc(
        D_h: QtyD[Meter],
        L_tot: QtyD[Meter],
        P_r: Dimensionless,
        R_e: Dimensionless,
        Ψ: Dimensionless,
        Ψ_smooth: Dimensionless
    ): Op[Dimensionless]

    /**
     * Calcule le nombre de Prandtl
     *
     * @param c_p capacité calorifique spécifique des fumées, en J/(kg . K)
     * @param η_A viscosité dynamique des fumées, en N . s/m 2
     * @param λ_A coefficient de conductivité thermique des fumées, en W/(m . K)
     * @return nombre de Prandtl
     */
    def P_r_calc(
        c_p: JoulesPerKilogramKelvin,
        η_A: NewtonSecondsPerSquareMeter,
        λ_A: WattsPerMeterKelvin
    ): Dimensionless

    /**
     * Calcule le nombre de Reynolds R_e
     *
     * @param w_m vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
     * @param D_h diamètre hydraulique intérieur des fumées, en m
     * @param ρ_m masse volumique moyenne des fumées, en kg/m3
     * @param η_A viscosité dynamique des fumées, en N . s/m 2
     * @return nombre de Reynolds R_e
     */
    def R_e_calc(
        w_m_notCorrected: QtyD[Meter / Second],
        D_h: QtyD[Meter],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        η_A: NewtonSecondsPerSquareMeter
    ): Dimensionless

    /**
     * Calcule le coefficient de conductivité thermique des fumées λ_A, en W/(m . K)
     *
     * @param t_m température moyenne des fumées, en °C
     * @return coefficient de conductivité thermique des fumées λ_A, en W/(m . K)
     */
    def λ_A_calc(t_m: TempD[Celsius]): WattsPerMeterKelvin

    /**
     * Calcule la viscosité dynamique des fumées η_A, en N . s/m2
     *
     * @param t_m température moyenne des fumées, en °C
     * @return viscosité dynamique des fumées, en N . s/m2
     */
    def η_A_calc(t_m: TempD[Celsius]): NewtonSecondsPerSquareMeter

    // Section "5.8.3.3", "Coefficient externe de transfert thermique (α_a)"

    /**
     * Calcule le coefficient externe de transfert thermique α_a du conduit
     *
     * @param pipeLoc localisation du conduit
     * @param airSpaceDetailed type de lame d'air
     * @return coefficient externe de transfert thermique dans le conduit de fumée α_a, en W/(m2 ⋅ K)
     */
    def α_a_calc(
        pipeLoc: PipeLocation,
        airSpaceDetailed: AirSpaceDetailed,
    ): WattsPerSquareMeterKelvin
    
    /**
     * Calcule le coefficient externe de transfert thermique α_a du conduit
     *
     * Ici, le diamètre hydraulique utilisé est bien celui de l'intérieur du conduit
     * et non de l'extérieur
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param pipeCtx environnement du conduit (intérieur ou extérieur, avec ou sans lame d'air ventilée)
     * @return coefficient externe de transfert thermique dans le conduit de fumée α_a, en W/(m2 ⋅ K)
     */
    // def α_a_calc(
    //     // pipeT: ConnectorPipeT | ChimneyPipeT,
    //     pipeCtx: PipeContext,
    //     Dh: QtyD[Meter]
    // ): WattsPerSquareMeterKelvin

    // Section "5.9.1", "Masse volumique des fumées (ρ_m)"

    /**
      * Calcule la masse volumique moyenne des fumées (formule 27)
      *
      * @param p_L pression atmosphérique extérieure, en Pa
      * @param R constante des gaz des fumées, en J/(kg.K)
      * @param T_m température moyenne des fumées, en K
      * @return ρ_m masse volumique moyenne des fumées, en kg/m3
      */
    def ρ_m_calc(
        p_L: QtyD[Pascal],
        R: JoulesPerKilogramKelvin,
        T_m: TempD[Kelvin]
    ): QtyD[Kilogram / (Meter ^ 3)]

    // Section "5.9.2", Vitesse des fumées (w_m)

    /**
      * Calcule la vitesse moyenne des fumées w_m, en m/s
      *
      * @param A section transversale interne du conduit de fumée, en m2
      * @param m_dot débit massique des fumées (voir 5.5.1), en kg/s
      * @param ρ_m masse volumique moyenne des fumées, en kg/m 3
      * @return vitesse moyenne des fumées, en m/s
      */
    def w_m_calc(
        A: Area,
        m_dot: QtyD[Kilogram / Second],
        ρ_m: Density,
    ): QtyD[Meter / Second]

    // Section "5.10", Détermination des pressions

    // Section "5.10.1.1"
    // Tirage au niveau de l'admission des fumées dans le conduit fonctionnant sous pression
    // négative (P_Z et P_Zmax )

    /**
      * tirage maximal au niveau de l’admission des fumées, en Pa.
      *
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @return
      */
    def P_Zmax(
        P_H: Pressure,
        P_R: Pressure,
    ): Pressure

    // Section "5.10.2"
    // "Tirage théorique disponible dû à l'effet de cheminée (P_H)"

    /**
      * tirage théorique disponible dû à l'effet de cheminée
      *
      * @param H hauteur utile du conduit de fumée, en m ;
      * @param ρ_L masse volumique de l'air extérieur (voir 5.7.4), en kg/m 3 ;
      * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 .
      * @return
      */
    def P_H_calc(
        H: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure

    // Section "5.10.3", "Perte de charge du conduit de fumée (P_R)"

    /**
     * Calcule la perte de charge du conduit de fumée P_R
     *
     * @param P_G variation de pression due au changement de vitesse du flux dans le conduit de fumée, en Pa
     * @param Ψ coefficient de frottement du conduit de fumée
     * @param L longueur du conduit de fumée
     * @param D_h diamètre hydraulique du conduit de fumée
     * @param Σ_ζ somme des coefficients de perte de charge dans le conduit de fumée
     * @param ρ_m masse volumique moyenne de l'air de combustion sur la longueur du conduit de fumée', en kg/m3
     * @param w_m vitesse moyenne de l'air de combustion sur la longueur du conduit de fumée, en m/s
     * @param S_E coefficient de sécurité du débit pour le conduit de fumée
     * @return perte de charge du conduit de fumée, en Pa
     */
    final def P_R(
        P_G: QtyD[Pascal],
        Ψ: Dimensionless,
        L: QtyD[Meter],
        D_h: QtyD[Meter],
        Σ_ζ: Dimensionless,
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
        S_E: Dimensionless
    ): Pressure = 
        P_R_sum_calc(
            P_R_staticFriction_calc(Ψ,L,D_h,ρ_m,w_m,S_E),
            P_R_dynamicFriction_calc(Σ_ζ,ρ_m,w_m,S_E),
            P_R_velocityChange_calc(S_E, P_G)
        )

    final def P_R_sum_calc(
        P_R_staticFriction: Pressure,
        P_R_dynamicFriction: Pressure,
        P_R_velocityChange: Pressure,
    ): Pressure = 
          P_R_staticFriction
        + P_R_dynamicFriction
        + P_R_velocityChange

    def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity): Pressure

    def P_R_staticFriction_calc(
        Ψ: Dimensionless,
        L: QtyD[Meter],
        D_h: QtyD[Meter],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
        S_E: Dimensionless
    ): Pressure

    def P_R_dynamicFriction_calc(
        Σ_ζ: Dimensionless,
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
        S_E: Dimensionless
    ): Pressure

    /**
      * pression (de sécurité ?) due au changement de vitesse des fumées
      *
      * @param S_E coefficient de sécurité du débit (voir 5.7.8) ;
      * @param P_G différence de pression due au changement de vitesse des fumées dans le conduit, en Pa
      * @return
      */
    def P_R_velocityChange_calc(
        S_E: Dimensionless, 
        P_G: QtyD[Pascal]
    ): Pressure

    // Section 5.10.3.2
    // Différence de pression due au changement de vitesse des fumées dans le conduit, P_G

    /**
     * Calcule la variation de pression due au changement de vitesse des fumées dans le conduit, en Pa
     *
     * Pour w1 et w2 , ainsi que pour ρ1 et ρ2 , les valeurs moyennes de la section avant et après le changement de
     * vitesse peuvent être utilisées
     *
     * @param ρ1 masse volumique des fumées avant le changement de vitesse, en kg/m3
     * @param w1 vitesse des fumées avant le changement de vitesse, en m/s
     * @param ρ2 masse volumique des fumées après le changement de vitesse, en kg/m3
     * @param w2 vitesse des fumées après le changement de vitesse, en m/s
     * @return variation de pression due au changement de vitesse des fumées dans le conduit, en Pa
     */
    def P_G_calc(
        ρ1: QtyD[Kilogram / (Meter ^ 3)],
        w1: QtyD[Meter / Second],
        ρ2: QtyD[Kilogram / (Meter ^ 3)],
        w2: QtyD[Meter / Second]
    ): Pressure

    // Section 5.10.4
    // Pression de la vitesse du vent (P_L)

    /**
      * Pression de la vitesse du vent (P_L)
      */
    def P_L_calc(
        coastal_region: Boolean,
        chimney_termination: ChimneyTermination,
    ): P_L

    // Section "5.11.1"
    // Tirage minimal exigé au niveau de l’admission des fumées et tirage maximal admis
    // (P_Ze et P_Zemax), pression différentielle maximale et minimale au niveau de l’admission
    // des fumées dans le conduit (P_ZOe et P_ZOemin)

    /**
      * tirage maximal admis au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_Wmax tirage maximal de l'appareil à combustion, en Pa ;
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air (voir 5.11.3), en Pa ;
      * @return
      */
    def P_Zemax(
        P_Wmax: Pressure,
        P_FV: Pressure,
        P_B: Pressure,
    ): Pressure

    /**
      * pression différentielle maximale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_WO pression différentielle maximale au niveau de la sortie de l'appareil à combustion, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air, en Pa.
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @return
      */
    def P_ZOe(
        P_WO: Pressure,
        P_B: Pressure,
        P_FV: Pressure,
    ): Pressure 

    /**
      * pression différentielle minimale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_WOmin pression différentielle minimale à la sortie de l'appareil à combustion, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air, en Pa.
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @return
      */
    def P_ZOemin(
        P_WOmin: Pressure,
        P_B: Pressure,
        P_FV: Pressure,
    ): Pressure

    // Section "5.11.3.2"
    // "Tirage théorique disponible dû à l'effet de cheminée du conduit de raccordement (P_HV)"

    def P_HV_calc(
        H_V: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_mV: QtyD[Kilogram / (Meter ^ 3)]
    ): QtyD[Pascal]

    // Section "5.11.4"
    // "Perte de charge de l'alimentation en air (P_B)"

    def P_B_without_ventilation_openings: Pressure
    
    /**
      * Coefficient de sécurité du débit pour l'alimentation en air (la valeur de S EB est généralement 1,2)
      * @param pReq pressure requirements (min or max draft)
      * @return
      */
    def S_EB_calc(pReq: DraftCondition): Dimensionless

    /**
     * Calcule la perte de charge de l'alimentation en air (P_B)
     *
     * @param ΨB coefficient de perte de charge due au frottement des ouvertures d'aération ou du conduit d'air de combustion ;
     * @param LB longueur des ouvertures d'aération ou du conduit d'air de combustion, en m ;
     * @param DhB diamètre hydraulique intérieur des ouvertures d'aération ou du conduit d'air de combustion, en m ;
     * @param ΣζB somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les ouvertures d'aération ou le conduit d'air de combustion.
     * @param ρB masse volumique de l'air de combustion, en kg/m 3 ;
     * @param wB vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s ;
     * @return Perte de charge de l'alimentation en air (P_B)
     */
    final def P_B(
        ΨB: Dimensionless,
        LB: QtyD[Meter],
        DhB: QtyD[Meter],
        ΣζB: Dimensionless,
        ρB: QtyD[Kilogram / (Meter ^ 3)],
        wB: QtyD[Meter / Second],
        S_EB: Dimensionless
    ): Pressure = 
        P_R_sum_calc(
            P_R_staticFriction_calc(ΨB,LB,DhB,ρB,wB,S_EB),
            P_R_dynamicFriction_calc(ΣζB,ρB,wB,S_EB),
            P_R_velocityChange = 0.pascals,
        )

    // Section "5.12"
    // "Température de la paroi intérieure à la sortie du conduit de fumée (T_iob)"

    /**
     * Calcule de la température de la paroi intérieure à la sortie du conduit de fumée (T_iob)
     *
     * @param T_ob température des fumées à la sortie du conduit à une température d'équilibre, en K
     * @param k_ob coefficient de transfert thermique à la sortie du conduit de fumée à la température d'équilibre, en W/(m2.K)
     * @param α_i coefficient interne de transfert thermique, en W/(m2.K)
     * @param T_uo température de l'air ambiant à la sortie du conduit de fumée, en K
     * @return température de la paroi intérieure à la sortie du conduit de fumée, en K
     */
    def T_iob(
        T_ob: TempD[Kelvin],
        k_ob: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        T_uo: TempD[Kelvin]
    ): TempD[Kelvin]

    /**
      * Calcule le coefficient de transfert thermique à la sortie du conduit de fumée k_ob à la température d'équilibre
      *
      * @param α_i coefficient interne de transfert thermique à la sortie du conduit de fumée, en W/(m2⋅K)
      * @param _1_Λ résistance thermique, en m2·K/W
      * @param _1_Λ_o résistance thermique de toute isolation supplémentaire de la partie du conduit de fumée au-dessus du toit relative au diamètre hydraulique interne par rapport au conduit, en m2·K/W
      * @param D_h diamètre hydraulique intérieur, en m
      * @param D_hao diamètre hydraulique extérieur à la sortie du conduit de fumée, en m
      * @param α_ao coefficient externe de transfert thermique à la sortie du conduit de fumée, en W/(m2⋅K)
      * @return coefficient de transfert thermique à la sortie du conduit de fumée k_ob à la température d'équilibre, en W/(m2⋅K)
      */
    def k_ob_calc(
        α_i: WattsPerSquareMeterKelvin,
        _1_Λ: SquareMeterKelvinPerWatt,
        _1_Λ_o: SquareMeterKelvinPerWatt,
        D_h: QtyD[Meter],
        D_hao: QtyD[Meter],
        α_ao: WattsPerSquareMeterKelvin
    ): WattsPerSquareMeterKelvin

    // Section "7.7", "Valeurs de base de calcul"
    // Section "7.7.1", "Températures de l'air"
    // Section "7.7.1.2", "Températures de l'air extérieur (T_L)"

    // same as 5.7.1.2 (see implementation there)

    // Section "7.8", "Détermination des températures"

    // IMPLEMENTED only for non-concentric ducts with high thermal resistance
    // Section "7.8.1","Détermination des températures"

    /** mean temperature of the combustion air (in K) */
    def T_mB_calc(ductType: DuctType, tL: T_L): Op[T_mB]

    // Section "7.8.4", "Températures moyennes pour le calcul des pressions"
    
    /**
     * température moyenne des fumées sur la longueur du conduit des fumées, en K ;
     *
     * @param Tms températures moyennes des fumées sur la longueur des segments de conduit des fumées, en K ;
     * @return
     */
    def T_m_calc(Tms: Seq[TKelvin]): TKelvin

    /**
      * température moyenne des fumées sur la longueur du conduit de raccordement des fumées, en K ;
      *
      * @param TmVs températures moyennes des fumées sur la longueur des segments de conduit de raccordement des fumées, en K ;
      * @return
      */
    def T_mV_calc(TmVs: Seq[TKelvin]): TKelvin

    /**
      * température moyenne des fumées sur la longueur du conduit d'air comburant, en K ;
      *
      * @param TmBs températures moyennes des fumées sur la longueur des segments de conduit d'air comburant, en K ;
      * @return
      */
    def T_mB_calc(TmBs: Seq[TKelvin]): TKelvin

    // NOT IMPLEMENTED
    // Section "7.8.x","..."

    // Section "7.11.4", "Perte de charge de l'alimentation en air"

    // Section "7.11.4.1"
    // "Tirage dû à l'effet de cheminée du conduit d'air de combustion"

    /**
     * Calcule le tirage dû à l'effet de cheminée du conduit d'air de combustion P_HB
     *
     * @param H_B hauteur du conduit d'air comburant, en m
     * @param ρ_L masse volumique de l'air ambiant, en kg/m3
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en kg/m3
     * @return tirage dû à l'effet de cheminée du conduit d'air de combustion, en Pa
     */
    def P_HB_calc(
        H_B: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure

    // Section "7.11.4.2"
    // "Tirage dû à l'effet de cheminée du conduit de raccordement côté air"

    /**
     * Calcule le tirage dû à l'effet de cheminée du conduit de raccordement côté air, P_HBV
     *
     * @param H_BV hauteur du conduit de raccordement côté air, en m
     * @param ρ_L masse volumique de l'air ambiant, en kg/m3
     * @param ρ_mBV masse volumique moyenne de l'air de combustion sur la longueur du conduit de raccordement côté air, en kg/m3
     * @return tirage dû à l'effet de cheminée du conduit de raccordement côté air, en Pa
     */
    def P_HBV_calc(
        H_BV: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_mBV: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure

    // Section "7.11.4.3", "Perte de charge du conduit d'air comburant P_RB"

    /**
     * Calcule la perte de charge du conduit d'air comburant P_RB
     *
     * @param P_GB variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     * @param Ψ_B coefficient de frottement du conduit d'air comburant
     * @param L longueur du conduit d'air comburant
     * @param D_hB diamètre hydraulique du conduit d'air comburant
     * @param Σ_ζ_B somme des coefficients de perte de charge dans le conduit d'air comburant
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant', en kg/m3
     * @param w_mB vitesse moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en m/s
     * @param S_EB coefficient de sécurité du débit pour le conduit d'air comburant
     * @return perte de charge du conduit d'air comburant, en Pa
     */
    def P_RB(
        P_GB: QtyD[Pascal],
        Ψ_B: Dimensionless,
        L: QtyD[Meter],
        D_hB: QtyD[Meter],
        Σ_ζ_B: Dimensionless,
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)],
        w_mB: QtyD[Meter / Second],
        S_EB: Dimensionless
    ): Pressure

    /**
     * Résolution itérative du coefficient de perte de charge due au frottement dans le conduit
     *
     * Solve equation (135) in Ψ
     *
     * @param D_h diamètre hydraulique du conduit, en m
     * @param r valeur moyenne de rugosité de la paroi intérieure du conduit, en m
     * @param Re nombre de Reynolds dans le conduit
     * @param approxPrecision precision de l'approximation (iteration stops when two consecutive values differs less than this value)
     * @return coefficient de perte de charge due au frottement dans le conduit
     */
    def solvepsi(
        D_h: QtyD[Meter],
        r: QtyD[Meter],
        Re: Dimensionless,
        approxPrecision: Double = 1e-5
    ): Dimensionless

    /** solvepsi for r = 0 */
    def solvepsi_smooth(
        D_h: QtyD[Meter],
        Re: Dimensionless,
        approxPrecision: Double = 1e-5
    ): Dimensionless

    /**
     * Calcule la variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     *
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en kg/m3
     * @param w_mB vitesse moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en m/s
     * @return variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     */
    def P_GB(
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)],
        w_mB: QtyD[Meter / Second]
    ): Pressure

    // Section "Annexe A - Calcul de la résistance thermique"

    val COEFFICIENT_OF_FORM_MAX_SIDES_RATIO: Double

    def coefficient_of_form(forShape: PipeShape): Either[ThermalResistance_Error.SideRatioTooHighForRectangularForm, CoefficientOfForm]

    // def Λinverse(
    //     layers: ThermalResistance.Layers
    // ): SquareMeterKelvinPerWatt

    // Section "Tableau B.1"

    /** coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s) */
    def f_m1_calc(comb: FuelType): Double

    /** coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s) */
    def f_m2_calc(comb: FuelType): Double

    /** coefficient de calcul du débit massique de l'air de combustion, en g/(kW ⋅ s) */
    def f_m3_calc(comb: FuelType): Double

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R1_calc(comb: FuelType): Double

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R2_calc(comb: FuelType): Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%) */
    def f_c0_calc(comb: FuelType): Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%) */
    def f_c1_calc(comb: FuelType): Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%) */
    def f_c2_calc(comb: FuelType): Double

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/% */
    def f_c3_calc(comb: FuelType): Double

    /** coefficient de calcul de la teneur en vapeur d'eau des fumées, en % */
    def f_w_calc(comb: FuelType): QtyD[Percent]

    /**
     * teneur en vapeur d'eau des fumées, en %
     *
     * formule (B.5)
     * 
     * @param comb type de combustible
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches
     * @return
     */
    def σ_H2O_from_σ_CO2_calc(
        comb: FuelType,
        σ_CO2: QtyD[Percent]
    ): QtyD[Percent]

    /**
     * teneur en dioxyde de carbone des fumées sèches, en %
     *
     * formule (B.5) inversée
     * 
     * @param comb type de combustible
     * @param σ_H2O teneur en en vapeur d'eau des fumées, en %
     * @return
     */
    @deprecated def σ_CO2_from_σ_H2O_calc(
        comb: FuelType,
        σ_H2O: QtyD[Percent]
    ): QtyD[Percent]

    /**
     * pression partielle de la vapeur d'eau, en Pa
     *
     * formule (B.6)
     * 
     * @param σ_H2O teneur en en vapeur d'eau des fumées, en %
     * @param p_L pression de l'air extérieur, en Pa ;
     * @return
     */
    def p_D_from_σ_H2O_calc(σ_H2O: Percentage, p_L: Pressure): Pressure

    /**
      * température du point de rosée, en °C ;
      * 
      * formule (B.7)
      *
      * @param p_D pression partielle de la vapeur d'eau, en Pa ;
      * @return
      */
    def t_p_calc(p_D: Pressure): TCelsius

    /**
     * teneur en vapeur d'eau des fumées, en %
     * 
     * formule (B.12)
     *
     * @param P_D pression partielle de la vapeur d'eau, en Pa ;
     * @param p_L pression de l'air extérieur, en Pa ;
     * @return
     */
    def σ_H2O_from_PD_pL(
        P_D: Pressure,
        p_L: Pressure,
    ): QtyD[Percent]

    /**
     * pression partielle de la vapeur d'eau, en Pa
     *
     * formule (B.13)
     * 
     * @param T_P température du point de rosée, en Kelvin ;
     * @return
     */
    def p_D_from_T_p_calc(T_P: TKelvin): Pressure

    // Section "Tableau B.6"

    def thermalResistanceFromConductivity_forCylindricalLayers(
        y: CoefficientOfForm,
        D_hn: Length,
        λ_n: ThermalConductivity,
        dn: Length,
    ): SquareMeterKelvinPerWatt

    def deadAirSpaceThermalResistance(
        t_emittingSurfaceTemp: TCelsius, 
        dn_airSpaceWidth: Length,
        innerShape: PipeShape,
    ): Either[ThermalResistance_Error.CouldNotComputeThermalResistance, SquareMeterKelvinPerWatt]

end EN13384_1_A1_2019_Formulas_Alg

object EN13384_1_A1_2019_Formulas_Alg:

    export afpma.firecalc.engine.standard.{EN13384_Error as Error}
    export afpma.firecalc.engine.standard.{NuCalcError}
    export afpma.firecalc.engine.standard.{
        ReIsAbove10million,
        PsiRatioIsGreaterThan3,
        PrandtlOutOfBound,
        PrandtlTooSmall,
        PrandtlTooBig
    }

    type Op[A] = ValidatedNel[Error, A]
