/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

/**
  * Pure Algebra only contains pure formulas.
  * You should not implement it using inputs to your class or object.
  */
trait EN15544_V_2023_Formulas_Alg:

    // 4.2.1
    
    def m_B_calc: (P_n, t_n, n_min) => m_B

    /**
     * net calorific value of wet wood
     *
     * @param ncv_dry net calorific value of dry wood
     * @param hum wood moisture in % as fraction on dry wood
     * @param kind kind of wood (hard or soft)
     * @return
     */
    def net_calorific_value_of_wet_wood: HeatCapacity

    def m_B_min_calc: (m_B) => m_B_min

    // type K_V <: Dimensionless
    // def K_V_calc: (c_P, PipeSection, Temperature, Temperature, m_G, σ_CO2) => K_V

    def p_d_calc: (Density, Velocity) => p_d

    def p_h_calc: (H, ρ_L, ρ_G) => p_h

    def P_n_calc: (m_B, t_n, n_min) => P_n

    // TODO better (added so we don't forget later on)

    def v_calc: (VolumeFlow, QtyD[(Meter ^ 2)]) => v

    def O_BR_calc: m_B => O_BR

    // Section "4.3.1.3", "Firebox base"
    def A_BR_min_calc: m_B => A_BR
    def A_BR_max_calc: (m_B, U_BR) => A_BR

    // Section "4.3.1.4", "Firebox height"
    def H_BR_min_calc: m_B => H_BR
    def H_BR_calc: (m_B, A_BR, U_BR) => H_BR

    // Section "4.3.2", "Calculated flue pipe length"
    def L_Z_calculated_calc: (FacingType, m_B) => L_N

    // Section "4.3.3", "Minimum flue pipe length"
    def Table_1_Factor_a_opt_calc: n_min => Option[Table_1_Factor_a]
    def Table_1_Factor_b_opt_calc: n_min => Option[Table_1_Factor_b]
    def L_Z_min_calc: (Table_1_Factor_a_or_b, m_B) => L_N

    // Section "4.3.4", "Gas groove profile"
    def A_GS_calc: m_B => A_GS

    // Section "4.4", "Calculation of the burning rate"
    def m_BU_calc: m_B => m_BU

    // Section "4.5", "Fixing of the air ratio"
    def λ_calc: λ

    // Section "4.6", "Combustion air flue gas"
    // Section "4.6.2", "Combustion air flow rate"
    def V_L_calc: (m_B, f_t, f_s) => V_L

    // Section "4.6.2.2", "Temperature correction"
    def f_t_calc: TempD[Celsius] => f_t

    // Section "4.6.2.3", "Altitude correction"
    def f_s_calc: z_geodetical_height => f_s

    // Section "4.6.3", "Flue gas flow rate"
    def V_G_calc: (m_B, f_t, f_s) => V_G

    // Section "4.6.4", "Flue gas mass flow rate"
    def m_G_calc: m_B => m_G
    def m_L_calc: m_B => m_L

    // Section "4.7.1", "Combustion air density"
    def ρ_L_calc: (f_t, f_s) => ρ_L

    // Section "4.7.2", "Flue gas density"
    def ρ_G_calc: (f_t, f_s) => ρ_G

    // Section "4.8.1",
    // "Mean outside air temperature and combustion air temperature"
    def t_outside_air_mean: t_outside_air_mean

    // Section "4.8.2", "Mean firebox temperature"
    lazy val t_BR_default: TCelsius
    lazy val t_BR_default_in_standard: TCelsius
    def t_BR_calc: (Firebox_15544) => t_BR

    lazy val t_burnout_default: TCelsius
    lazy val t_burnout_in_standard: TCelsius
    def t_burnout_calc: (Firebox_15544) => t_burnout

    // Section "4.8.3", "Flue gas temperature in the flue pipe"
    def t_fluepipe_calc: (t_burnout, L_Z, L_N) => t_fluepipe

    // Section "4.8.4", "Flue gas temperature in the connector pipe"

    // Section "4.8.5",

    // Flue gas temperature at chimney entrance mean flue gas
    // temperature of the chimney and temperature of the chimney wall
    // at the top of the chimney

    // Section "4.9.4", Calculation of static friction

    def p_R_calc: (λ_f, p_d, L, D_h) => p_R

    // Section "4.9.4.3", Friction coefficient

    def λ_f_calc: (D_h, k_f) => λ_f

    // Section "4.9.5, Calculation of the resistance due to direction change (p_u)

    def p_u_calc: (ζ, p_d) => p_u

    /** concentration de dioxyde carbone (% en volume) : humide ou sec ??? (supposée humide) */
    def fluegas_σ_CO2_calc: σ_CO2
    
    /** teneur en eau du bois (% en masse) */
    def wood_σ_H2O_calc: σ_H2O

    def η_calc: t_F => η

    // 4.9.5
    def ζ1_modified_calc(ζα1: ζ, ζα2: ζ, ζα3: ζ, α1: QtyD[Degree], α2: QtyD[Degree], lz: QtyD[Meter], dh: D_h): ζ
    def ζ2_modified_calc(ζα1: ζ, ζα2: ζ, ζα3: ζ, α1: QtyD[Degree], α2: QtyD[Degree], lz: QtyD[Meter], dh: D_h): ζ

    // 4.10.3
    def net_calorific_value_of_dry_wood: HeatCapacity