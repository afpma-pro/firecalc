/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.data.Validated.*
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.toShow
import cats.syntax.all.catsSyntaxValidatedId

import afpma.firecalc.engine.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LoadQty.withLoad
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import algebra.instances.all.given

import coulomb.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}

import io.taig.babel.Locales
import io.taig.babel.Locale

/**
 * Section 4 formula methods extracted from `EN15544_V_2023_Common_Application`.
 *
 * Covers sections 4.2 (load of fuel) through 4.9.4.3 (friction coefficient),
 * including firebox sizing, temperature calculations, and flow mechanics formulas.
 */
trait EN15544_Common_Application_Formulas { en15544: EN15544_V_2023_Common_Application =>

    // Section "4", "Calculations"

    // Section "4.2", "Load of fuel"

    // Section "4.2.1", "Maximum Load"

    def n_min: n_min =
        inputs.stoveParams.min_efficiency

    def P_n: P_n =
        inputs.stoveParams.mB_or_pn match
            case Left(mb)  => formulas.P_n_calc(mb, t_n, n_min)
            case Right(pn) => pn

    def t_n: t_n =
        inputs.stoveParams.heating_cycle

    // If tested fireboxs are used, the maximum load at nominal heat output shall be the maximum
    // fuel mass according to the type test.
    def m_B: m_B =
        inputs.design.firebox match
            case dcc: Firebox_15544.SingleTested => dcc.maximumFuelMass
            case _  : Firebox_15544              =>
                inputs.stoveParams.mB_or_pn match
                    case Left(mb) => mb
                    case Right(_) => formulas.m_B_calc(P_n, t_n, n_min)

    given Conversion[LoadQty, Option[Mass]] = (lq: LoadQty) =>
        lq match
            case _ @LoadQty.Nominal => m_B.some
            case _ @LoadQty.Reduced => m_B_min

    // Section "4.2.2", "Minimum Load"

    // The definition and calculation of the minimum load is only necessary if a reduced heat output is declared
    // by the manufacturer
    def m_B_min: Option[m_B_min] =
        inputs.design.firebox.pn_reduced match
            case _: (HeatOutputReduced.HalfOfNominal | HeatOutputReduced.FromTypeTest) =>
                inputs.design.firebox.min_load match
                    case MinLoad.NotDefined               => None
                    case MinLoad.HalfOfMaxLoad(Some(min)) =>
                        // The minimum load shall be calculated as 50 % of the maximum load
                        require(min == formulas.m_B_min_calc(m_B))
                        (min: m_B_min).some
                    case MinLoad.HalfOfMaxLoad(None)      =>
                        formulas.m_B_min_calc(m_B).some
                    case MinLoad.FromTypeTest(min)        =>
                        // If tested fireboxs are used, the minimum load at reduced heat output shall be the minimum
                        // fuel mass according to the type test.
                        (min: m_B_min).some
            case HeatOutputReduced.NotDefined => None

    // Section "4.3", "Design of the essential dimensions"

    // Section "4.3.1", "Firebox dimensions"

    // Implementation of FireboxSizing_15544_Alg
    type FireboxSizingAlg = FireboxSizing_15544_Common

    override lazy val firebox_sizing: FireboxSizingAlg =
        FireboxSizing_15544_Common(
            firebox,
            m_B
        )

    // Section "4.3.1.1", "General" — constraints now handled by FireboxConstraints typeclass

    // Section "4.3.2", "Calculated flue pipe length"

    def L_Z_calculated: L_N =
        formulas.L_Z_calculated_calc(inputs.stoveParams.facing_type, m_B)

    // Section "4.3.3", "Minimum flue pipe length"

    // Section "4.3.3.1", "Calculation"

    // TODO
    // When using tested fireboxs, the formulas for calculating the minimum draft length
    // (due to deviating burnout temperatures from the firebox) are not to be used.

    // Section "4.3.3.2/3", "Construction with or without air gap"

    def table_1_Factor_a_or_b: Option[Table_1_Factor_a_or_b] =
        inputs.stoveParams.facing_type match
            case FacingType.WithoutAirGap =>
                formulas.Table_1_Factor_a_opt_calc(n_min).map(Left(_))
            case FacingType.WithAirGap    =>
                formulas.Table_1_Factor_b_opt_calc(n_min).map(Right(_))

    def L_Z_min: VNel[L_N] =
        table_1_Factor_a_or_b match
            case Some(aorb) =>
                formulas.L_Z_min_calc(aorb, m_B).validNel
            case None       =>
                // interpolation failed
                EN15544_ErrorMessage(
                    "interpolation failed: could not retrieve 'a' or 'b' factor in 'Table 1'",
                    FireboxPipeT
                ).invalidNel

    // Section "4.3.4", "Gas groove profile"

    def A_GS: A_GS = formulas.A_GS_calc(m_B)

    // Section "4.4", "Calculation of the burning rate"

    def m_BU: m_BU = formulas.m_BU_calc(m_B)

    // Section "4.5", "Fixing of the air ratio"
    val λ: λ = formulas.λ_calc

    // Section "4.6", "Combustion air flue gas"

    // Section "4.6.2", "Combustion air flow rate"

    def V_L: EpOp[LoadOp[V_L]] = withLoad: mb =>
        val ft = f_t(t_combustion_air)
        formulas.V_L_calc(mb, ft, f_s)

    // Section "4.6.2.2", "Temperature correction"

    def f_t(t: TempD[Celsius]): f_t =
        formulas.f_t_calc(t)

    // Section "4.6.2.3", "Altitude correction"

    def z_geodetical_height: z_geodetical_height =
        inputs.localConditions.altitude

    def f_s: f_s =
        formulas.f_s_calc(z_geodetical_height)

    // Section "4.6.3", "Flue gas flow rate"

    def V_G(t: TempD[Celsius]): LoadOp[V_G] = withLoad: mb =>
        val ft = f_t(t)
        formulas.V_G_calc(mb, ft, f_s)

    // Section "4.6.4", "Flue gas mass flow rate"

    def m_G: LoadOp[VNelMcalcErr[m_G]] = withLoad(mb => formulas.m_G_calc(mb).validNel)

    def m_L: LoadOp[VNelMcalcErr[m_L]] = withLoad(mb => formulas.m_L_calc(mb).validNel)

    // Section "4.7.1", "Combustion air density"

    def ρ_L: EpOp[ρ_L] =
        val ft = f_t(t_combustion_air)
        formulas.ρ_L_calc(ft, f_s)

    // Section "4.7.2", "Flue gas density"

    def ρ_G(t: TempD[Celsius]): ρ_G =
        val ft = f_t(t)
        formulas.ρ_G_calc(ft, f_s)

    // Section "4.8.1",
    // "Mean outside air temperature and combustion air temperature"

    override lazy val t_combustion_air: EpOp[t_combustion_air] =
        en13384_application.T_mB match
            case Valid(tmb)    => tmb.unwrap.toUnit[Celsius]
            case Invalid(errs) =>
                given Locale = Locales.en
                throw new Exception(errs.head.show)

    // Section "4.8.2", "Mean firebox temperature"

    // import Firebox.ccDesignShow

    def t_BR: t_BR =
        formulas.t_BR_calc(inputs.design.firebox)

    // Section "4.8.3", "Flue gas temperature in the flue pipe"

    def t_burnout: t_burnout =
        formulas.t_burnout_calc(inputs.design.firebox)

    def t_fluepipe(L_Z: QtyD[Meter]): t_fluepipe =
        formulas.t_fluepipe_calc(t_burnout, L_Z, L_Z_calculated)

    /**
     * Compute mean temperature of the gas in the fluepipe, between two points given their distances from the firebox outlet
     *
     * Computed using the integral of formula (22) between lz1 and lz2:
     *   t_sortie * formulas.L_Z_calculated / 0.83
     *   *
     *   (
     *     math.exp(0.83 * lz1 / formulas.L_Z_calculated)
     *     -
     *     math.exp(0.83 * lz2 / formulas.L_Z_calculated)
     *   )
     *
     * @param lz1 distance from firebox to point 1
     * @param lz2 distance from firebox to point 2
     * @return
     */
    def t_fluepipe_mean(lz1: QtyD[Meter], lz2: QtyD[Meter]): VNel[t_fluepipe] =
        val tb = t_burnout
        val ln = L_Z_calculated
        (
            1.0 / (lz2 - lz1).value
                *
                (tb.toUnit[Celsius].value * ln.to_m.value) / 0.83
                *
                (
                    math.exp(0.83 * (lz1 / ln).value)
                        -
                            math.exp(0.83 * (lz2 / ln).value)
                )
        ).degreesCelsius.validNelE

    // Section "4.9", "Calculation of flow mechanics"

    // Section "4.9.4.1", "Static fricition (p_R)"

    // Section "4.9.4.2", "Dynamic Pressure (p_d)"

    // Section "4.9.4.3", "Friction coefficient (ƛ_f)"

    enum k_f_values(val h: QtyD[Meter]):
        case ChamottePipes extends k_f_values(0.002.meters)
        case ChamotteSlabs extends k_f_values(0.003.meters)

    // Section "4.9.5",
    // "Calculation of the resistance due to direction change (p_u)"

}
