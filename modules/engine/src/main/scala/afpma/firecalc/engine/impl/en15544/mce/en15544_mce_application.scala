/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import cats.*
import cats.syntax.all.*


import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.impl.en13384.*
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.units.coulombutils.*
import algebra.instances.all.given
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}
import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Alg

object EN15544_MCE_Application:
    def make(
        f: EN15544_MCE_Formulas,
        bs845: BS845_Alg,
        wComb: WoodCombustionAlg,
    )(
        i: models.en15544.std.Inputs_EN15544_MCE,
    ): EN15544_MCE_Application = new EN15544_MCE_Application(f, bs845, wComb)(i)

open class EN15544_MCE_Application(
    override val formulas: EN15544_MCE_Formulas,
    val bs845: BS845_Alg,
    val wComb: WoodCombustionAlg,
)(
    override val inputs: models.en15544.std.Inputs_EN15544_MCE
) 
    extends impl.en15544.common.EN15544_V_2023_Common_Application[Inputs_EN15544_MCE](inputs)
{
    en15544_mce =>
    
    import wComb.*

    override val doc: Document = Document(
        name = "MCE:2024-09",
        date = "2024-09",
        version = "2024-09",
        revision = "a",
        status = "Draft",
        author = "AFPMA"
    )

    // algebra as given
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas = new EN13384_1_A1_2019_Formulas:
        override val COEFFICIENT_OF_FORM_MAX_SIDES_RATIO = 20.0
        // overrides        
        override def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity) = 
            en15544_mce.formulas.p_d_calc(ρ_m, w_m)

    val combustionDuration = inputs.combustionDuration

    def en13384_η_W_calc(fluegas_co2_dry: Percentage): Percentage = 
        // 2. calculé à partir de taux de CO2 ou O2 + tamb + tg + wood compo ?
        bs845.perfect_combustion_efficiency_given_CO2_dry(
            wood            = inputs.wood,
            t_flue_gas      = t_burnout,
            t_ambiant_air   = formulas.t_outside_air_mean,
            co2_dry_perc    = fluegas_co2_dry
        )

    lazy val en13384_η_WN: Percentage = inputs.design.firebox match
        case _: OneOff  => en13384_η_W_calc(inputs.fluegas_co2_dry_nominal)
        case tstd: Tested => tstd.efficiency_nominal

    lazy val en13384_η_Wmin: Option[Percentage] = inputs.design.firebox match
        case _: OneOff  => inputs.fluegas_co2_dry_lowest.map(en13384_η_W_calc)
        case tstd: Tested => tstd.efficiency_reduced

    lazy val energy_in_nominal_load = energy_in_wet_wood(m_B)
    lazy val energy_in_minimal_load = m_B_min.map(mb_min => energy_in_wet_wood(mb_min))

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage =
        inputs.design.firebox match
            case _: OneOff => fluegas_σ_CO2_dry_nominal
            case tt: Tested => tt.co2_dry_nominal
    
    lazy val en13384_fluegas_σ_CO2_dry_lowest: Option[Percentage] =
        inputs.design.firebox match
            case _: OneOff => fluegas_σ_CO2_dry_lowest
            case tt: Tested => tt.co2_dry_lowest

    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage] = inputs.fluegas_h2o_perc_vol_nominal
    lazy val en13384_fluegas_σ_H2O_lowest : Option[Percentage] = inputs.fluegas_h2o_perc_vol_lowest

    lazy val en13384_heatingAppliance_massFlows = inputs.massFlows_override

    lazy val en13384_heatingAppliance_temperatures = HeatingAppliance.Temperatures(
        flue_gas_temp_nominal = t_fluepipe_end(using runValidationAtParams),
        flue_gas_temp_reduced  = None,
    )

    override lazy val en13384_T_L_override = 
        // user can override T_L if MCE by using inputs.nationalAccepetedData
        inputs.en13384NationalAcceptedData.T_L_override
            .opaqueGetOrElse(en13384_T_L_override_default)
    
    override lazy val en13384_p_L_override = None

    lazy val en13384_application = new EN13384_For15544_Application(
        formulas = en13384_formulas,
        inputs   = en13384_inputs,
    ) {
        self =>
        
        final override lazy val computeAt = ComputeAt.Mean
        final override lazy val p_L_override = en13384_p_L_override

        private lazy val rh_L = en15544_mce.inputs.ext_air_rel_hum_default

        private lazy val ext_air = 
            given DraftCondition = DraftCondition.DraftMaxOrPositivePressureMin
            afpma.firecalc.engine.wood_combustion.ExteriorAir(T_L, rh_L, p_L)
        
        private def combustionInputs(whm: Wood.HumidMass)(lq: LoadQty) = 
            val σCO2 = self.σ_CO2(using HeatingAppliance.FlueGas.summon)(using lq)
            val lambda = en15544_mce.inputs.wood.lambda_from_co2_dry(σCO2)
            val whmf = whm.toHumidMassFlow(combustionDuration)
            val mix = CombustionMix(lambda, ext_air, en15544_mce.inputs.wood)
            CombustionInputs.byMassFlow(whmf)(mix)

        private lazy val combustionInputs_nominal = combustionInputs(Wood.HumidMass(m_B))(LoadQty.Nominal)
        private lazy val combustionInputs_lowest  = m_B_min.map(m => combustionInputs(Wood.HumidMass(m))(LoadQty.Reduced))

        override def σ_H2O(using hafg: HeatingAppliance.FlueGas) = 
            val lq = LoadQty.summon
            val h2o_perc_o = lq match
                case LoadQty.Nominal => hafg.h2o_perc_nominal
                case LoadQty.Reduced  => hafg.h2o_perc_reduced
            h2o_perc_o.getOrElse:
                val σCO2 = self.σ_CO2(using hafg)(using lq)
                val lambda = en15544_mce.inputs.wood.lambda_from_co2_dry(σCO2) // 2. otherwise compute it using % CO2, exterior air params
                en15544_mce.inputs.wood.fluegas_h2o_for_lambda(lambda)
                // val mix = CombustionMix(
                //     lambda = en13384_inputs.wood.lambda_from_co2_dry(en13384_fluegas_σ_CO2_dry),
                //     ext_air = ext_air,
                //     en13384_inputs.wood,
                // )
                // val comb = CombustionInputs.byMass(
                //     afpma.wood_combustion.Wood.HumidMass(m_B)
                // )(mix)
                // comb.output_perfect_percbyvol_humid_H2O

        /** Débit massique des fumées */
        override def m_dot = 
            HeatingAppliance.MassFlows.summon.flue_gas_mass_flow_nominal
                .getOrElse:
                    combustionInputs_nominal.output_perfect_massflow_tot

        override def m_dot_min = 
            HeatingAppliance.MassFlows.summon.flue_gas_mass_flow_reduced
                .orElse:
                    combustionInputs_lowest.map(_.output_perfect_massflow_tot)
                .getOrElse:
                    m_dot / 3.0

        /** Débit massique de l'air de combustion */
        override def mB_dot = 
            HeatingAppliance.MassFlows.summon.combustion_air_mass_flow_nominal.getOrElse:
                combustionInputs_nominal.input_air_mass_flow

        override def mB_dot_min = 
            HeatingAppliance.MassFlows.summon.combustion_air_mass_flow_reduced
                .orElse:
                    combustionInputs_lowest.map(_.input_air_mass_flow)
                .getOrElse:
                    mB_dot / 3.0

        // override lazy val last_known_density_before_connector_pipe = 
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_mean)
    
        // override lazy val last_known_velocity_before_connector_pipe = 
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_mean)
    }

    given EN13384_1_A1_2019_Application_Alg = en13384_application

    val fluegas_σ_CO2_dry_nominal: σ_CO2 = inputs.fluegas_co2_dry_nominal
    val fluegas_σ_CO2_dry_lowest: Option[σ_CO2] = inputs.fluegas_co2_dry_lowest.map(x => x: σ_CO2)
    
    val wood_σ_H2O: σ_H2O = inputs.wood.frac_mass_H2O

    override def m_G = 
        LoadQty.summonOpt match
            case Some(LoadQty.Nominal) => (en13384_application.m_dot: m_G).some
            case Some(LoadQty.Reduced)  => (en13384_application.m_dot_min: m_G).some
            case None                  => None

    override def m_L = 
        LoadQty.summonOpt match
            case Some(LoadQty.Nominal) => (en13384_application.mB_dot: m_L).some
            case Some(LoadQty.Reduced)  => (en13384_application.mB_dot_min: m_L).some
            case None                  => None

    override def combustionAir_PipeResult =
        airIntake_PipeResult.flatMap: asp =>
            ops_en13384.MecaFlu_EN13384.makePipeResult(
                fd                          = CombustionAirPipe_Module_EN13384.unwrap(inputs.pipes.combustionAir),
                hafg                        = en13384_heatingAppliance_fluegas,
                hamf                        = en13384_heatingAppliance_massFlows,
                hapwr                       = en13384_heatingAppliance_powers,
                haeff                       = en13384_heatingAppliance_efficiency,
                temp_start                  = asp.gas_temp_end,
                last_pipe_density           = en13384_application.computeAt match
                    case ComputeAt.Mean         => asp.last_density_mean
                    case ComputeAt.Middle       => asp.last_density_middle
                ,
                last_pipe_velocity          = en13384_application.computeAt match
                    case ComputeAt.Mean         => asp.last_velocity_mean
                    case ComputeAt.Middle       => asp.last_velocity_middle
                ,
                gas                         = CombustionAir,
            )

    override def firebox_PipeResult = 
        combustionAir_PipeResult.flatMap: cci =>
            ops_en13384.MecaFlu_EN13384.makePipeResult(
                fd                          = FireboxPipe_Module_EN13384.unwrap(inputs.pipes.firebox),
                hafg                        = en13384_heatingAppliance_fluegas,
                hamf                        = en13384_heatingAppliance_massFlows,
                hapwr                       = en13384_heatingAppliance_powers,
                haeff                       = en13384_heatingAppliance_efficiency,
                temp_start                  = t_BR,
                last_pipe_density           = en13384_application.computeAt match
                    case ComputeAt.Mean         => cci.last_density_mean
                    case ComputeAt.Middle       => cci.last_density_middle
                ,
                last_pipe_velocity          = en13384_application.computeAt match
                    case ComputeAt.Mean         => cci.last_velocity_mean
                    case ComputeAt.Middle       => cci.last_velocity_middle
                ,
                gas                         = FlueGas,
            )

    override def flue_PipeResult = 
        firebox_PipeResult.flatMap: cc =>
            ops_en13384.MecaFlu_EN13384.makePipeResult(
                fd                          = FluePipe_Module_EN13384.unwrap(inputs.pipes.flue),
                hafg                        = en13384_heatingAppliance_fluegas,
                hamf                        = en13384_heatingAppliance_massFlows,
                hapwr                       = en13384_heatingAppliance_powers,
                haeff                       = en13384_heatingAppliance_efficiency,
                temp_start                  = t_burnout,
                last_pipe_density           = en13384_application.computeAt match
                    case ComputeAt.Mean         => cc.last_density_mean
                    case ComputeAt.Middle       => cc.last_density_middle
                ,
                last_pipe_velocity          = en13384_application.computeAt match
                    case ComputeAt.Mean         => cc.last_velocity_mean
                    case ComputeAt.Middle       => cc.last_velocity_middle
                ,
                gas                         = FlueGas,
            )

    override final def pipesResult_15544_VNelS = PipesResult_15544_VNelString(
        airIntake               = airIntake_PipeResult,
        combustionAir  = combustionAir_PipeResult,
        firebox             = firebox_PipeResult,
        flue                    = flue_PipeResult,
        connector              = connector_PipeResult,
        chimney                 = chimney_PipeResult,
    )

    override final def outputs =
        models.en15544.std.Outputs(
            techSpecs,
            pipesResult_15544_VNelS.accumulateErrors,
            reference_temperatures,
            efficiencies_values,
            // pressureRequirement_EN15544,
            // estimated_output_temperatures,
            // flue_gas_triple_of_variates
        )

    final override val runValidationAtParams = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
}