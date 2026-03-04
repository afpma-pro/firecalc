/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import cats.*
import cats.data.Validated
import cats.syntax.all.*

import algebra.instances.all.given
import afpma.firecalc.units.coulombutils.*
import coulomb.*
import coulomb.policy.standard.given

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.impl.en13384.*
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.{Wood => _, *}
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.standard.*

import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Alg

object EN15544_MCE_Application:
    def make(
        f    : EN15544_MCE_Formulas,
        bs845: BS845_Alg,
        wComb: WoodCombustionAlg
    )(
        i: models.en15544.std.Inputs_15544_MCE
    ): EN15544_MCE_Application = new EN15544_MCE_Application(f, bs845, wComb) {
        override lazy val inputs = i
    }

abstract class EN15544_MCE_Application(
    override val formulas: EN15544_MCE_Formulas,
    val bs845            : BS845_Alg,
    val wComb            : WoodCombustionAlg
) extends impl.en15544.common.EN15544_V_2023_Common_Application
    with HasTypeMembers_15544_MCE {
    en15544_mce =>

    import wComb.*

    override val doc: Document = Document(
        name     = "MCE:2024-09",
        date     = "2024-09",
        version  = "2024-09",
        revision = "a",
        status   = "Draft",
        author   = "AFPMA"
    )

    override lazy val en13384_inputs = Inputs_13384_WithThermalAirIntake(
        en13384_inputs_pipes,
        en13384_inputs_nationalAcceptedData,
        en13384_inputs_fuelType,
        inputs.localConditions,
        en13384_inputs_flueGasCondition
    )

    override def en13384_inputs_pipes: Pipes_13384 =
        inputs.pipes

    // algebra as given
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas = new EN13384_1_A1_2019_Formulas:
        override val COEFFICIENT_OF_FORM_MAX_SIDES_RATIO                       = 20.0
        // overrides
        override def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity) =
            en15544_mce.formulas.p_d_calc(ρ_m, w_m)

    val combustionDuration = inputs.combustionDuration

    def en13384_η_W_calc(fluegas_co2_dry: Percentage): Percentage =
        // 2. calculé à partir de taux de CO2 ou O2 + tamb + tg + wood compo ?
        bs845.perfect_combustion_efficiency_given_CO2_dry         (
            wood          = inputs.wood,
            t_flue_gas    = t_burnout,
            t_ambiant_air = formulas.t_outside_air_mean,
            co2_dry_perc  = fluegas_co2_dry
        )

    lazy val en13384_η_WN: VNelMcalcErr[Percentage] =
        Validated.validNel:
            inputs.design.firebox match
                case tstd: SingleTested         => tstd.efficiency_nominal
                case _   : Firebox_15544 => en13384_η_W_calc(inputs.fluegas_co2_dry_nominal)

    lazy val en13384_η_Wmin: Option[VNelMcalcErr[Percentage]] =
        (
            inputs.design.firebox match
                case tstd: SingleTested         => tstd.efficiency_reduced
                case _   : Firebox_15544 => inputs.fluegas_co2_dry_lowest.map(en13384_η_W_calc)
        ).map(Validated.validNel)

    lazy val energy_in_nominal_load = energy_in_wet_wood(m_B)
    lazy val energy_in_minimal_load = m_B_min.map(mb_min => energy_in_wet_wood(mb_min))

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage =
        inputs.design.firebox match
            case tt: SingleTested         => tt.co2_dry_nominal
            case _ : Firebox_15544 => fluegas_σ_CO2_dry_nominal

    lazy val en13384_fluegas_σ_CO2_dry_lowest: Option[Percentage] =
        inputs.design.firebox match
            case tt: SingleTested         => tt.co2_dry_lowest
            case _ : Firebox_15544 => fluegas_σ_CO2_dry_lowest

    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage] = inputs.fluegas_h2o_perc_vol_nominal
    lazy val en13384_fluegas_σ_H2O_lowest : Option[Percentage] = inputs.fluegas_h2o_perc_vol_lowest

    lazy val en13384_heatingAppliance_massFlows = inputs.massFlows_override

    lazy val en13384_heatingAppliance_temperatures = t_fluepipe_end(using runValidationAtParams).map: t =>
        HeatingAppliance.Temperatures(
            flue_gas_temp_nominal = t,
            flue_gas_temp_reduced = None
        )

    override lazy val en13384_T_L_override =
        // user can override T_L if MCE by using inputs.nationalAccepetedData
        inputs.en13384NationalAcceptedData.T_L_override
            .opaqueGetOrElse(en13384_T_L_override_default)

    lazy val en13384_application = new EN13384_For_15544_Application(
        formulas = en13384_formulas
    ) {
        self =>
        override lazy val inputs = en13384_inputs

        override final lazy val p_L_override = en13384_p_L_override

        private lazy val rh_L = en15544_mce.inputs.ext_air_rel_hum_default

        private lazy val ext_air =
            given DraftCondition = DraftCondition.DraftMaxOrPositivePressureMin
            afpma.firecalc.engine.wood_combustion.ExteriorAir(T_L, rh_L, p_L)

        private def combustionInputs(whm: Wood.HumidMass)(lq: LoadQty) =
            val σCO2   = self.σ_CO2(using HeatingAppliance.FlueGas.summon)(using lq)
            val lambda = en15544_mce.inputs.wood.lambda_from_co2_dry(σCO2)
            val whmf   = whm.toHumidMassFlow(combustionDuration)
            val mix    = CombustionMix(lambda, ext_air, en15544_mce.inputs.wood)
            CombustionInputs.byMassFlow(whmf)(mix)

        private lazy val combustionInputs_nominal = combustionInputs(Wood.HumidMass(m_B))(LoadQty.Nominal)
        private lazy val combustionInputs_lowest  =
            m_B_min.map(m => combustionInputs(Wood.HumidMass(m))(LoadQty.Reduced))

        override def σ_H2O(using hafg: HeatingAppliance.FlueGas) =
            val lq         = LoadQty.summon
            val h2o_perc_o = lq match
                case LoadQty.Nominal => hafg.h2o_perc_nominal
                case LoadQty.Reduced => hafg.h2o_perc_reduced
            h2o_perc_o.getOrElse:
                val σCO2   = self.σ_CO2(using hafg)(using lq)
                val lambda = en15544_mce.inputs.wood.lambda_from_co2_dry(
                    σCO2
                ) // 2. otherwise compute it using % CO2, exterior air params
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

        override def airIntake_PipeResult_withVentilationOpenings(
            fd                : en15544_mce.AirIntakePipe_Module.FullDescr
        ): HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]] =
            ops_en13384.ThermalMecaFlu_13384.makePipeResult(
                fd                 = ThermalAirIntakePipe_Module_13384.unwrap(fd),
                // fd.unwrap,
                // AirIntakePipeDefModule.unwrap(fd),
                hafg               = HeatingAppliance.FlueGas.summon,
                hamf               = HeatingAppliance.MassFlows.summon,
                hapwr              = HeatingAppliance.Powers.summon,
                haeff              = HeatingAppliance.Efficiency.summon,
                temp_start         = T_L,
                last_pipe_density  = None,
                last_pipe_velocity = None,
                gas                = CombustionAir
            )

        override def airIntake_PipeResult =
            ThermalAirIntakePipe_Module_13384.foldPipeCanBe(inputs.pipes.airIntake)(
                onNoVentilation = airIntake_PipeResult_withoutVentilationOpenings,
                onFullDescr     = fd => airIntake_PipeResult_withVentilationOpenings(fd)
            )

        // 7.8.4
        // Températures moyennes pour le calcul de pression

        /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
        override def T_mB =
            inputs.pipes.airIntake match
                case p: ThermalAirIntakePipe_13384 =>
                    import ThermalAirIntakePipe_Module_13384.*
                    self.formulas.T_mB_calc(p.ductType, T_L).withSectionTyp(AirIntakePipeT)
    }

    given EN13384_1_A1_2019_Application_Alg = en13384_application

    val fluegas_σ_CO2_dry_nominal: σ_CO2         = inputs.fluegas_co2_dry_nominal
    val fluegas_σ_CO2_dry_lowest : Option[σ_CO2] = inputs.fluegas_co2_dry_lowest.map(x => x: σ_CO2)

    val wood_σ_H2O: σ_H2O = inputs.wood.frac_mass_H2O

    override def m_G =
        LoadQty.summonOpt match
            case None     => None
            case Some(lq) =>
                (
                    en13384_heatingAppliance_powers,
                    en13384_heatingAppliance_efficiency
                )
                    .mapN_andThen_impl:
                        given HeatingAppliance.MassFlows = en13384_heatingAppliance_massFlows
                        (
                            lq match
                                case LoadQty.Nominal => (en13384_application.m_dot    : m_G)
                                case LoadQty.Reduced => (en13384_application.m_dot_min: m_G)
                        ).validNel
                    .some

    override def m_L =
        LoadQty.summonOpt match
            case None     => None
            case Some(lq) =>
                (
                    en13384_heatingAppliance_powers,
                    en13384_heatingAppliance_efficiency
                )
                    .mapN_andThen_impl:
                        given HeatingAppliance.MassFlows = en13384_heatingAppliance_massFlows
                        (
                            lq match
                                case LoadQty.Nominal => (en13384_application.mB_dot    : m_L)
                                case LoadQty.Reduced => (en13384_application.mB_dot_min: m_L)
                        ).validNel
                    .some

    override def combustionAir_PipeResult =
        CombustionAirPipe_Module_13384.foldPipeCanBe(inputs.pipes.combustionAir)(
            onWithout   = combustionAir_PipeResult_whenEmpty,
            onFullDescr = fd => combustionAir_PipeResult_whenExists(fd)
        )

    override def combustionAir_PipeResult_whenExists(
        fd: CombustionAirPipe_Module_13384.FullDescr
    ) =
        airIntake_PipeResult.andThen: asp =>
            (
                en13384_heatingAppliance_powers,
                en13384_heatingAppliance_efficiency
            )
                .mapN_andThen: (ha_pow, ha_eff) =>
                    ops_en13384.ThermalMecaFlu_13384
                        .makePipeResult                (
                            fd                 = CombustionAirPipe_Module_13384.unwrap(fd),
                            hafg               = en13384_heatingAppliance_fluegas,
                            hamf               = en13384_heatingAppliance_massFlows,
                            hapwr              = ha_pow,
                            haeff              = ha_eff,
                            temp_start         = asp.gas_temp_end,
                            last_pipe_density  = en13384_application.computeAt match
                                case ComputeAt.Mean   => asp.last_density_mean
                                case ComputeAt.Middle => asp.last_density_middle
                            ,
                            last_pipe_velocity = en13384_application.computeAt match
                                case ComputeAt.Mean   => asp.last_velocity_mean
                                case ComputeAt.Middle => asp.last_velocity_middle
                            ,
                            gas                = CombustionAir
                        )
                        .toValidatedNel

    override def firebox_PipeResult =
        combustionAir_PipeResult.andThen: cci =>
            (
                en13384_heatingAppliance_powers,
                en13384_heatingAppliance_efficiency
            )
                .mapN_andThen: (ha_pow, ha_eff) =>
                    ops_en13384.ThermalMecaFlu_13384
                        .makePipeResult                (
                            fd                 = FireboxPipe_Module_13384.unwrap(inputs.pipes.firebox),
                            hafg               = en13384_heatingAppliance_fluegas,
                            hamf               = en13384_heatingAppliance_massFlows,
                            hapwr              = ha_pow,
                            haeff              = ha_eff,
                            temp_start         = t_BR,
                            last_pipe_density  = en13384_application.computeAt match
                                case ComputeAt.Mean   => cci.last_density_mean
                                case ComputeAt.Middle => cci.last_density_middle
                            ,
                            last_pipe_velocity = en13384_application.computeAt match
                                case ComputeAt.Mean   => cci.last_velocity_mean
                                case ComputeAt.Middle => cci.last_velocity_middle
                            ,
                            gas                = FlueGas
                        )
                        .toValidatedNel

    override def flue_PipeResult =
        firebox_PipeResult.andThen: cc =>
            (
                en13384_heatingAppliance_powers,
                en13384_heatingAppliance_efficiency
            )
                .mapN_andThen: (ha_pow, ha_eff) =>
                    ops_en13384.ThermalMecaFlu_13384
                        .makePipeResult                (
                            fd                 = FluePipe_Module_13384.unwrap(inputs.pipes.flue),
                            hafg               = en13384_heatingAppliance_fluegas,
                            hamf               = en13384_heatingAppliance_massFlows,
                            hapwr              = ha_pow,
                            haeff              = ha_eff,
                            temp_start         = t_burnout,
                            last_pipe_density  = en13384_application.computeAt match
                                case ComputeAt.Mean   => cc.last_density_mean
                                case ComputeAt.Middle => cc.last_density_middle
                            ,
                            last_pipe_velocity = en13384_application.computeAt match
                                case ComputeAt.Mean   => cc.last_velocity_mean
                                case ComputeAt.Middle => cc.last_velocity_middle
                            ,
                            gas                = FlueGas
                        )
                        .toValidatedNel

    override final def pipesResult_15544_VNelS = PipesResult_15544_VNelString(
        airIntake     = airIntake_PipeResult,
        combustionAir = combustionAir_PipeResult,
        firebox       = firebox_PipeResult,
        flue          = flue_PipeResult,
        connector     = connector_PipeResult,
        chimney       = chimney_PipeResult
    )

    override final def outputs =
        models.en15544.std.Outputs(
            techSpecs,
            pipesResult_15544_VNelS.accumulateErrors,
            reference_temperatures,
            efficiencies_values
            // pressureRequirement_EN15544,
            // estimated_output_temperatures,
            // flue_gas_triple_of_variates
        )

    override final val runValidationAtParams = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
}
