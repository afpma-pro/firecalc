/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.WithLoadQty
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.MassFlows
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.Temperatures
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.en15544 as ops_en15544
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import scala.annotation.nowarn

object EN15544_Strict_Application:
    def make(
        f: EN15544_V_2023_Formulas_Alg,
    )(
        i: models.en15544.std.Inputs_EN15544_Strict,
    ): EN15544_Strict_Application = new EN15544_Strict_Application(f)(i)

sealed class EN15544_Strict_Application(
    override val formulas: EN15544_V_2023_Formulas_Alg,
)(
    override val inputs: models.en15544.std.Inputs_EN15544_Strict
) 
    extends impl.en15544.common.EN15544_V_2023_Common_Application[Inputs_EN15544_Strict](inputs)
{
    en15544 =>


    override val doc: Document = Document(
        name = "15544:2023-02",
        date = "2023-02",
        version = "2023-02",
        revision = "NA",
        status = "Approved",
        author = "AFNOR"
    )

    // algebra as given
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas = new EN13384_1_A1_2019_Formulas:
        // overrides        
        override def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity) = 
            en15544.formulas.p_d_calc(ρ_m, w_m)

    lazy val energy_in_nominal_load = energy_in_wet_wood(m_B)
    lazy val energy_in_minimal_load = m_B_min.map(energy_in_wet_wood)

    val combustionDuration: Duration = (1 / 0.78).hours.toUnit[Minute]

    lazy val en13384_η_WN: Percentage = 
        formulas.η_calc(t_fluepipe_end(using Params_15544.DraftMin_LoadNominal)) // en15544.n_min ? mais en enlevant l'accumulateur ?
    
    lazy val en13384_η_Wmin: Option[Percentage] = 
        m_B_min.map: _ =>
            // m_B_min is defined so we can compute using LoadQty.Reduced
            formulas.η_calc(t_fluepipe_end(using Params_15544.DraftMin_LoadMin)) // en15544.n_min ? mais en enlevant l'accumulateur ?

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage = fluegas_σ_CO2_dry_nominal // yes, even for tested firebox
    lazy val en13384_fluegas_σ_CO2_dry_lowest : Option[Percentage] = fluegas_σ_CO2_dry_lowest  // yes, even for tested firebox

    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage] = None // no override, will use EN13384 formulas
    lazy val en13384_fluegas_σ_H2O_lowest : Option[Percentage] = None // no override, will use EN13384 formulas

    lazy val en13384_heatingAppliance_massFlows: MassFlows = MassFlows.undefined

    lazy val en13384_heatingAppliance_temperatures: Temperatures = HeatingAppliance.Temperatures(
        flue_gas_temp_nominal = 
            import LoadQty.givens.nominal
            computeAndRequireEqualityAtDraftMinDraftMax[Unit, TCelsius] { (pReq: DraftCondition) ?=>
                t_fluepipe_end(using (pReq, nominal))
            }(_ == _)(using ()),
        flue_gas_temp_reduced = 
            None,
            // import LoadQty.givens.reduced
            // computeAndRequireEqualityAtDraftMinDraftMax[Unit, TCelsius] { (pReq: PressureRequirements) ?=>
            //     t_fluepipe_end(using (ep, lowest))
            // }(_ == _)(using ()).some,
    )

    override final lazy val en13384_T_L_override = en13384_T_L_override_default
    override final lazy val en13384_p_L_override = None

    def fluegas_σ_CO2_dry: WithLoadQty[Option[σ_CO2]] = 
        val ei: Either[IllegalStateException, Option["OK"]] = inputs.design.firebox match
            case _: OneOff => Right("OK".some)
            case tt: Tested => 
                val afr_opt = LoadQty.summon match
                    case LoadQty.Nominal => tt.airFuelRatio_nominal.some
                    case LoadQty.Reduced  => tt.airFuelRatio_lowest
                afr_opt.fold(Right(None)): afr =>
                    if (afr < 1.95 || afr > 3.95)   // TODO: add constraint instead ? in formulas impl. ?
                        Left(new IllegalStateException(s"ERROR: tested firebox should have air-fuel ratio λ such as 1.95 <= λ <= 3.95, got ${afr.show}"))
                    else
                        Right("OK".some)
        ei.map(o => o.map(_ => formulas.fluegas_σ_CO2_calc)).fold(e => throw e, identity)

    val fluegas_σ_CO2_dry_nominal: σ_CO2 = fluegas_σ_CO2_dry(using LoadQty.Nominal).get // should always be defined
    val fluegas_σ_CO2_dry_lowest: Option[σ_CO2] = fluegas_σ_CO2_dry(using LoadQty.Reduced)

    val wood_σ_H2O: σ_H2O = formulas.wood_σ_H2O_calc

    lazy val en13384_application = new EN13384_For15544_Application(
        formulas = en13384_formulas,
        inputs   = en13384_inputs
    ):
        final override lazy val computeAt = ComputeAt.Middle
        final override lazy val p_L_override = en13384_p_L_override
        // overrides
        @nowarn override def ρ_m(T_m: TKelvin)(using HeatingAppliance.FlueGas) = 
            en15544.ρ_G(T_m.toUnit[Celsius])

        override def P_H(h: Length, t: TKelvin)(using HeatingAppliance.FlueGas) = 
            en15544.formulas.p_h_calc(h, en15544.ρ_L, ρ_m(t))

        override def P_R_staticFriction(
            L: Length, 
            D_h: Length, 
            r: Roughness,
            w_m_not_corrected: Velocity,
            ρ_m: Density,
            t_m: TKelvin,
        ): EpOp[Pressure] =
            val λf = en15544.formulas.λ_f_calc(D_h, r)
            val pd = en15544.formulas.p_d_calc(ρ_m, w_m_not_corrected)
            en15544.formulas.p_R_calc(λf, pd, L, D_h)

        override def P_R_velocityChange(P_G: Pressure) = 0.0.pascals

        override def P_R_dynamicFriction(
            Σ_ζ: Dimensionless,
            ρ_m: QtyD[Kilogram / (Meter ^ 3)],
            w_m: QtyD[Meter / Second],
        ): EpOp[Pressure] =
            val pd = en15544.formulas.p_d_calc(ρ_m, w_m)
            en15544.formulas.p_u_calc(Σ_ζ, pd)

        override def ρ_B(T_B: TKelvin): EpOp[Density] = 
            en15544.ρ_L

        override def m_dot = 
            given Option[LoadQty] = LoadQty.Nominal.some
            en15544.m_G.get
        
        override def m_dot_min = 
            given Option[LoadQty] = m_B_min.map(_ => LoadQty.Reduced)
            en15544.m_G.getOrElse(throw new IllegalStateException(s"could not determine 'm_dot_min' because 'm_B_min' is not defined"))

        override def mB_dot = 
            given Option[LoadQty] = LoadQty.Nominal.some
            en15544.m_L.get

        override def mB_dot_min = 
            given Option[LoadQty] = m_B_min.map(_ => LoadQty.Reduced)
            en15544.m_L.getOrElse(throw new IllegalStateException(s"could not determine 'mB_dot_min' because 'm_B_min' is not defined"))
        
        // CAN BE COMMENTED ? CAN WE LEAVE IT TO DEFAULT IMPL ?
        // override def σ_CO2(using HeatingAppliance.FlueGas): WithLoadQty[Percentage] = 
        //     en15544.fluegas_σ_CO2_dry

        // override lazy val last_known_density_before_connector_pipe = 
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_middle)
    
        // override lazy val last_known_velocity_before_connector_pipe = 
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_middle)

    def combustionAir_PipeResult =
        airIntake_PipeResult.flatMap: _ =>
            ops_en15544.MecaFlu_EN15544_Strict.makePipeResult(
                fd                  = CombustionAirPipe_Module_EN15544.unwrap(inputs.pipes.combustionAir),
                gas                 = CombustionAir,
                loadQty             = LoadQty.summon,
                z_geodetical_height = z_geodetical_height,
                params              = pressReq_from_Params_15544
            )(using en15544)

    def firebox_PipeResult = 
        ops_en15544.MecaFlu_EN15544_Strict.makePipeResult(
            fd                  = FireboxPipe_Module_EN15544.unwrap(inputs.pipes.firebox),
            gas                 = FlueGas,
            loadQty             = LoadQty.summon,
            z_geodetical_height = z_geodetical_height,
            params              = pressReq_from_Params_15544
        )(using en15544)

    // def t_connector_pipe_mean: WithParams_15544[t_connector_pipe_mean] = 
    //     connector_PipeResult
    //         .map: cp =>
    //             val tms = cp.elements.flatMap(_.gas_temp_mean.map(_.toUnit[Kelvin]))
    //             en13384_formulas.T_mV_calc(tms.size, tms).toUnit[Celsius]
    //         .fold(e => throw new Exception(e.msg), identity)

    def flue_PipeResult = 
        ops_en15544.MecaFlu_EN15544_Strict.makePipeResult(
            fd                  = FluePipe_Module_EN15544.unwrap(inputs.pipes.flue),
            gas                 = FlueGas,
            loadQty             = LoadQty.summon,
            z_geodetical_height = z_geodetical_height,
            params              = pressReq_from_Params_15544
        )(using en15544)

    override final def pipesResult_15544_VNelS = 
        PipesResult_15544_VNelString(
            airIntake           = airIntake_PipeResult,
            combustionAir       = combustionAir_PipeResult,
            firebox             = firebox_PipeResult,
            flue                = flue_PipeResult,
            connector           = connector_PipeResult,
            chimney             = chimney_PipeResult,
        )

    override final def outputs =
        val pipesResult = pipesResult_15544_VNelS.accumulateErrors
        models.en15544.std.Outputs(
            techSpecs,
            pipesResult,
            reference_temperatures,
            efficiencies_values,
            // pressureRequirement_EN15544,
            // estimated_output_temperatures,
            // flue_gas_triple_of_variates
        )

    final override val runValidationAtParams: Params_15544 = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
    
    def net_calorific_value_of_wet_wood(
        @nowarn ncv_dry: HeatCapacity, 
        @nowarn hum: Percentage,
        @nowarn kind: KindOfWood
    ): HeatCapacity =
        4.16.kWh_per_kg // See 4.2.1

    def net_calorific_value_of_dry_wood(@nowarn kind: KindOfWood): HeatCapacity =
        18500.kJ_per_kg.toUnit[Kilo * Watt * Hour / Kilogram] // see 4.10.3
}