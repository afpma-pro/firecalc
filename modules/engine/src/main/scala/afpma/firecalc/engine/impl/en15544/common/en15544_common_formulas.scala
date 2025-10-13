/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import algebra.instances.all.given

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}


trait EN15544_V_2023_Common_Formulas extends EN15544_V_2023_Formulas_Alg:

    // Calculation using inputs
    override lazy val m_B_calc = 
        (pn, tn, nmin) =>
            pn * tn 
            / 
            (
                nmin / 100.0.percent 
                * 
                net_calorific_value_of_wet_wood
            )

    override lazy val P_n_calc: (m_B, t_n, n_min) => P_n = 
        (m_B, tn, nmin) =>
            ( m_B / tn )
            *
            (
                nmin / 100.0.percent 
                * 
                4.16.kWh_per_kg
            )

    // Section "4.2.2", "Minimum Load"

    override lazy val m_B_min_calc = 
        (mb) => 0.5 * mb

    // Section "4.3", "Design of the essential dimensions"

    // Section "4.3.1", "Firebox dimensions"

    // Section "4.3.1.1", "General"

    override lazy val O_BR_calc = 
        (mb) => (900 * mb.value).squareCentimeters

    // Section "4.3.1.3", "Firebox base"

    // A_BR_min
    override lazy val A_BR_min_calc = 
        mb => (100 * mb.value).squareCentimeters

    // A_BR_max
    override lazy val A_BR_max_calc = 
        (mb, ubr) =>
            (((900 * mb.value) - (25 + mb.value) * ubr.toUnit[Centimeter].value) / 2.0).squareCentimeters

    // Section "4.3.1.4", "Firebox height"

    override lazy val H_BR_min_calc = 
        mb => (25.0 + mb.value).cm


    override lazy val H_BR_calc = 
        (mb, abr, ubr) =>
            (
                (900.0 * mb.toUnit[Kilogram].value - 2.0 * abr.toUnit[(Centimeter ^ 2)].value) 
                / 
                ubr.toUnit[Centimeter].value
            ).cm

    // Section "4.3.2", "Calculated flue pipe length"

    override lazy val L_Z_calculated_calc = 
        (facingType, mb) =>
            facingType match
                case FacingType.WithoutAirGap =>
                    (1.3 * Math.sqrt(mb.value)).meters
                case FacingType.WithAirGap =>
                    (1.5 * Math.sqrt(mb.value)).meters

    // Section "4.3.3", "Minimum flue pipe length"

    // Section "4.3.3.1", "Calculation"

    // TODO
    // When using tested fireboxs, the formulas for calculating the minimum draft length
    // (due to deviating burnout temperatures from the firebox) are not to be used.

    // Section "4.3.3.2/3", "Construction with or without air gap"

    val EN15544_2023_TABLE_1_RAW_STRING = 
         """|Efficiency	Factor a	Factor b
            |70	0,84	0,97
            |71	0,89	1,02
            |72	0,94	1,08
            |73	0,99	1,14
            |74	1,05	1,21
            |75	1,11	1,28
            |76	1,17	1,35
            |77	1,23	1,42
            |78	1,30	1,50
            |79	1,36	1,57
            |80	1,43	1,65
            |81	1,51	1,74
            |82	1,58	1,83
            |83	1,67	1,92
            |84	1,76	2,03
            |85	1,85	2,13
            |86	1,95	2,25
            |87	2,06	2,37
            |88	2,17	2,50
            |89	2,30	2,65
            |90	2,43	2,80""".stripMargin

    override lazy val Table_1_Factor_a_opt_calc =
        nmin =>
            TSVTableString
                .fromString(EN15544_2023_TABLE_1_RAW_STRING)
                .getUsingLinearInterpolation(
                    xHeader = "Efficiency",
                    yHeader = "Factor a"
                )(xi = nmin.value)
                .map(_.unitless)

    override lazy val Table_1_Factor_b_opt_calc =
        nmin =>
            TSVTableString
                .fromString(EN15544_2023_TABLE_1_RAW_STRING)
                .getUsingLinearInterpolation(
                    xHeader = "Efficiency",
                    yHeader = "Factor b"
                )(xi = nmin.value)
                .map(_.unitless)

    override lazy val L_Z_min_calc = (ab: Table_1_Factor_a_or_b, mb: m_B) =>
        val a_or_b_value = ab.fold(_.value, _.value)
        (a_or_b_value * Math.sqrt(mb.value)).meters

    // Section "4.3.4", "Gas groove profile"

    override lazy val A_GS_calc = 
        mb => (1 * mb.value).squareCentimeters

    // Section "4.4", "Calculation of the burning rate"

    override lazy val m_BU_calc = 
        (mb) => (0.78 * mb.value).withUnit[Kilogram / Hour]


    // Section "4.5", "Fixing of the air ratio"
    override lazy val λ_calc = 2.95.unitless

    // Section "4.6", "Combustion air flue gas"

    // Section "4.6.2", "Combustion air flow rate"

    override lazy val V_L_calc = 
        (mb, ft, fs) =>
            (0.00256 * mb.value * ft.value * fs.value).withUnit[(Meter ^ 3) / Second]

    // Section "4.6.2.2", "Temperature correction"

    override lazy val f_t_calc = 
        (t) => ((273.0 + t.value) / 273.0).ea

    // Section "4.6.2.3", "Altitude correction"

    override lazy val f_s_calc = 
        (z) => math.exp((9.81 * z.toUnit[Meter].value) / 78624.0).unitless

    // Section "4.6.3", "Flue gas flow rate"

    override lazy val V_G_calc = 
        (mb, ft, fs) =>
            (0.00273 * mb.value * ft.value * fs.value).withUnit[(Meter ^ 3) / Second]

    // Section "4.6.4", "Flue gas mass flow rate"

    override lazy val m_G_calc = 
        (mb) => (0.0035 * mb.value).withUnit[Kilogram / Second]

    override lazy val m_L_calc = 
        (mb) => 
            // set ft and fs as any double because they cancel out
            val ft: f_t = 1.0.unitless
            val fs: f_s = 1.0.unitless
            ρ_L_calc(ft, fs) * V_L_calc(mb, ft, fs) // approx: 0.00331 * mb

    // Section "4.7.1", "Combustion air density"

    override lazy val ρ_L_calc = 
        (ft, fs) =>
            (1.293 / (ft.value * fs.value)).withUnit[Kilogram / (Meter ^ 3)]

    // Section "4.7.2", "Flue gas density"

    override lazy val ρ_G_calc = 
        (ft, fs) =>
            (1.282 / (ft.value * fs.value)).withUnit[Kilogram / (Meter ^ 3)]

    // Section "4.8.1",
    // "Mean outside air temperature and combustion air temperature"

    /** Deviating from EN 13384-1 the outside air temperature is set at 0 °C for the calculation. */
    final override def t_outside_air_mean = 0.degreesCelsius

    // Section "4.8.2", "Mean firebox temperature"

    // import Firebox.ccDesignShow

    lazy val t_BR_default: TCelsius = t_BR_default_in_standard

    final lazy val t_BR_default_in_standard: TCelsius = 700.degreesCelsius

    override lazy val t_BR_calc = 
        case t: Firebox_15544.Tested =>
            t.meanFireboxTemperature
                .map(_.toUnit[Celsius])
                .getOrElse(t_BR_default)
        case _: Firebox_15544.OneOff =>
            t_BR_default

    // Section "4.8.3", "Flue gas temperature in the flue pipe"

    lazy val t_burnout_default: TCelsius = t_burnout_in_standard

    final lazy val t_burnout_in_standard = 550.degreesCelsius

    override lazy val t_burnout_calc = 
        case t: Firebox_15544.Tested              => t.tBurnout.toUnit[Celsius]
        case _: Firebox_15544.OneOff  => t_burnout_default

    override lazy val t_fluepipe_calc = 
        (tburnout, lz, lzCalculated) =>
            (
                tburnout.toUnit[Celsius].value 
                * math.exp(
                    (-0.83 * lz.toUnit[Meter].value)
                    / 
                    lzCalculated.toUnit[Meter].value
                )
            ).degreesCelsius

    // Section "4.8.4", "Flue gas temperature in the connector pipe"

    // Section "4.8.5",
    // "Flue gas temperature at chimney entrance mean flue gas " +
    //     "temperature of the chimney and temperature of the chimney wall " +
    //     "at the top of the chimney"

    // Section "4.9", "Calculation of flow mechanics"

    // Section "4.9.2", "Calculation of the standing pressure (p_h)"

    override lazy val p_h_calc = 
        (h, ρL, ρG) =>
            (G.value * h.value * (ρL.value - ρG.value)).pascals

    // Section "4.9.3", "Calculation of the flow velocity"

    override lazy val v_calc = 
        (vdot, a) => vdot / a

    // Section "4.9.4.1", "Static fricition (p_R)"

    override lazy val p_R_calc = 
        (λf, pd, l, dh) => 
            (λf.value * pd.value * (l / dh).value).pascals

    // Section "4.9.4.2", "Dynamic Pressure (p_d)"

    override lazy val p_d_calc = (ρ, v) =>
        (ρ.value * math.pow(v.value, 2) / 2.0).pascals

    // Section "4.9.4.3", "Friction coefficient (ƛ_f)"

    override lazy val λ_f_calc = 
        (dh, kf) => 
            (
                1.0 
                / 
                math.pow(
                    1.14 + 2.0 * math.log10( (dh / kf).value ),
                    2
                )
            ).ea

    // Section "4.9.5",
    // "Calculation of the resistance due to direction change (p_u)"

    override lazy val p_u_calc = 
        (ζ, pd) => ζ * pd

    // Section "4.10", "Operation control"

    // Section "4.10.1", "Pressure requirement"

    // Section "4.10.3", "Efficiency of the combustion (η)"
    // TODO: mauvaise traduction allemande ?
    // TODO: Lors du calcul du rendement de la combustion, les hypothèses suivantes sont retenues : XXX

    /** concentration de dioxyde carbone (% en volume) : humide ou sec ??? */
    final override val fluegas_σ_CO2_calc: σ_CO2 = 7.05.percent
    
    /** teneur en eau du bois (% en masse) */
    final override val wood_σ_H2O_calc: σ_H2O = 17.0.percent

    final override val η_calc = 
        (tf) =>
            val tfDegrees = tf.toUnit[Celsius].value
            (
                101.09 
                - 0.0941 * tfDegrees 
                - 6.275e-6 * math.pow(tfDegrees, 2)
                - 3.173e-9 * math.pow(tfDegrees, 3)
            ).percent

    // 4.9.5
    final override def ζ1_modified_calc(ζα1: ζ, ζα2: ζ, ζα3: ζ, α1: QtyD[Degree], α2: QtyD[Degree], lz: QtyD[Meter], dh: D_h): ζ =
        val out = ζα1 + (α1 / (α1 + α2)) * (ζα3 - ζα2 - ζα1) * (1 - (lz / dh).value)
        // println(s"""| ζ1_modified
        //             | -----------
        //             |
        //             | ζα1 = $ζα1
        //             | ζα2 = $ζα2
        //             | ζα3 = $ζα3
        //             |
        //             | α1 = $α1
        //             | α2 = $α2
        //             |
        //             | lz = $lz
        //             | dh = $dh
        //             |
        //             | => out = $out 
        //             |""".stripMargin)
        out
        

    final override def ζ2_modified_calc(ζα1: ζ, ζα2: ζ, ζα3: ζ, α1: QtyD[Degree], α2: QtyD[Degree], lz: QtyD[Meter], dh: D_h): ζ =
        val out = ζα2 + (α2 / (α1 + α2)) * (ζα3 - ζα2 - ζα1) * (1 - (lz / dh).value)
        // println(s"""| ζ2_modified
        //             | -----------
        //             | 
        //             | ζα1 = $ζα1
        //             | ζα2 = $ζα2
        //             | ζα3 = $ζα3
        //             |
        //             | α1 = $α1
        //             | α2 = $α2
        //             |
        //             | lz = $lz
        //             | dh = $dh 
        //             |
        //             | => out = $out
        //             |""".stripMargin)
        out