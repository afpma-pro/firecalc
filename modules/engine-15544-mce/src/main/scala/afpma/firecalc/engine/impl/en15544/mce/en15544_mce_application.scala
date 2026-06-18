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
import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.{Wood => _, *}
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.PipeWithGasFlowOps
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.ops.en13384.forThermal13384
import afpma.firecalc.engine.ops.en13384.mkforEN13384
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, PipeSlot, UpstreamState}
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.impl.en15544.common.PostFireboxFrameHelpers.toPipeFrame

import scala.annotation.nowarn

import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Alg

object EN15544_MCE_Application:
    def make(
        f    : EN15544_MCE_Formulas,
        bs845: BS845_Alg,
        wComb: WoodCombustionAlg
    )(
        i                  : models.en15544.Inputs_15544_MCE,
        pfbSlots           : Seq[afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7] = Seq.empty,
        initialDir         : Option[afpma.firecalc.dto.common.PipeInitialDirection] = None,
        initialPos         : Option[afpma.firecalc.dto.common.Position3D]           = None,
        airIntakeInitialPos: Option[afpma.firecalc.dto.common.Position3D]           = None,
        airIntakeDescr     : Seq[afpma.firecalc.dto.v7.FlowOnlyPipeDescr_13384_V4]  = Seq.empty
    ): EN15544_MCE_Application = new EN15544_MCE_Application(f, bs845, wComb) {
        override lazy val inputs                : models.en15544.Inputs_15544_MCE                        = i
        override lazy val postFireboxPipeSlots  : Seq[afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7] = pfbSlots
        override def postFireboxInitialDirection: Option[afpma.firecalc.dto.common.PipeInitialDirection] = initialDir
        override def postFireboxInitialPosition : Option[afpma.firecalc.dto.common.Position3D]           = initialPos
        override def airIntakeInitialPosition   : Option[afpma.firecalc.dto.common.Position3D]           = airIntakeInitialPos
        override def airIntakeDescriptors       : Seq[afpma.firecalc.dto.v7.FlowOnlyPipeDescr_13384_V4]  = airIntakeDescr
    }

abstract class EN15544_MCE_Application(
    override val formulas: EN15544_MCE_Formulas,
    val bs845            : BS845_Alg,
    val wComb            : WoodCombustionAlg
) extends impl.en15544.common.EN15544_V_2023_Common_Application
    with HasTypeMembers_15544_MCE {
    en15544_mce =>

    // ─── EN13384ForApp: concrete type provided by MCE ────────────────────
    override type EN13384ForApp = EN13384_1_A1_2019_Common_Application

    override given pipeWithGasFlowOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384_formulas)

    import wComb.*

    override val doc: Document = Document(
        name     = "MCE:2024-09",
        date     = "2024-09",
        version  = "2024-09",
        revision = "a",
        status   = "Draft",
        author   = "AFPMA"
    )

    override lazy val en13384_inputs = Inputs_13384_WithThermalAirIntake_PreFireboxOnly(
        en13384_inputs_pipes,
        en13384_inputs_nationalAcceptedData,
        en13384_inputs_fuelType,
        inputs.localConditions,
        en13384_inputs_flueGasCondition
    )

    override def en13384_inputs_pipes: Pipes_13384 =
        (inputs.pipes: Pipes_13384_WithThermalAirIntake_PreFireboxOnly)

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
                case tstd: SingleTested  => tstd.efficiency_nominal
                case _   : Firebox_15544 => en13384_η_W_calc(inputs.fluegas_co2_dry_nominal)

    lazy val en13384_η_Wmin: Option[VNelMcalcErr[Percentage]] =
        (
            inputs.design.firebox match
                case tstd: SingleTested  => tstd.efficiency_reduced
                case _   : Firebox_15544 => inputs.fluegas_co2_dry_lowest.map(en13384_η_W_calc)
        ).map(Validated.validNel)

    lazy val energy_in_nominal_load = energy_in_wet_wood(m_B)
    lazy val energy_in_minimal_load = m_B_min.map(mb_min => energy_in_wet_wood(mb_min))

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage =
        inputs.design.firebox match
            case tt: SingleTested  => tt.co2_dry_nominal
            case _ : Firebox_15544 => fluegas_σ_CO2_dry_nominal

    lazy val en13384_fluegas_σ_CO2_dry_lowest: Option[Percentage] =
        inputs.design.firebox match
            case tt: SingleTested  => tt.co2_dry_lowest
            case _ : Firebox_15544 => fluegas_σ_CO2_dry_lowest

    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage] = inputs.fluegas_h2o_perc_vol_nominal
    lazy val en13384_fluegas_σ_H2O_lowest : Option[Percentage] = inputs.fluegas_h2o_perc_vol_lowest

    lazy val en13384_heatingAppliance_massFlows = inputs.massFlows_override

    lazy val en13384_heatingAppliance_temperatures: VNelMcalcErr[HeatingAppliance.Temperatures] =
        atDraftMin_LoadNominal.t_fluepipe_end.map: t =>
            HeatingAppliance.Temperatures(
                flue_gas_temp_nominal = t,
                flue_gas_temp_reduced = None
            )

    override lazy val en13384_T_L_override =
        // user can override T_L if MCE by using inputs.nationalAccepetedData
        inputs.en13384NationalAcceptedData.T_L_override
            .opaqueGetOrElse(en13384_T_L_override_default)

    lazy val en13384_application = new EN13384_1_A1_2019_Common_Application(
        en13384_formulas
    ) with EN13384_For_15544_Overrides {
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

        override def airIntake_PipeResult_withVentilationOpenings(
            fd                : en15544_mce.AirIntakePipe_Module.FullDescr
        ): HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]] =
            ops_en13384.ThermalMecaFlu_13384.makePipeResult(
                fd                 = ThermalAirIntakePipe_Module_13384.unwrap(fd),
                // fd.unwrap,
                // AirIntakePipeDefModule.unwrap(fd),
                hafg               = HeatingAppliance.FlueGas.summon,
                hamf               = HeatingAppliance.MassFlows.summon,
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

        override def atParamsFor(p: Params_13384): AtParams = en15544_mce.atParamsFor(p)
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

    // ─── MCEAtParams: concrete inner class for MCE application ──────────

    class MCEAtParams(p: Params_15544) extends CommonAtParams(p):

        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult] =
            CombustionAirPipe_Module_13384.foldPipeCanBe(inputs.pipes.combustionAir)  (
                onWithout   = combustionAir_PipeResult_whenEmpty(using p),
                onFullDescr = fd => combustionAir_PipeResult_whenExists(fd)(using p)
            )

        lazy val firebox_PipeResult: VNelMcalcErr[PipeResult] =
            combustionAir_PipeResult.andThen: cci =>
                (
                    en13384_heatingAppliance_powers,
                    en13384_heatingAppliance_efficiency
                )
                    .mapN_andThen: (ha_pow, ha_eff) =>
                        given Params_13384 = p: Params_13384
                        ops_en13384.ThermalMecaFlu_13384
                            .makePipeResult                (
                                fd                 = FireboxPipe_Module_13384.unwrap(inputs.pipes.firebox),
                                hafg               = en13384_heatingAppliance_fluegas,
                                hamf               = en13384_heatingAppliance_massFlows,
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

        /**
         * Stage 1 — flue region pipe results (HA-temperature-free).
         *
         * Must NOT read `en13384_heatingAppliance_temperatures` — that's the cycle source in MCE
         * (`_temperatures` reads `t_fluepipe_end`, which reads `conceptualFluePipeResult`, which
         * reads Stage 1). Reading `_powers` / `_efficiency` / `_fluegas` / `_massFlows` is safe in
         * MCE (they derive η_WN from `t_burnout` via BS845, not from `t_fluepipe_end`), so Stage 1
         * can use `CanComputePipeResult.forThermal13384` directly — unlike Strict which must
         * restrict Stage 1 to the HA-power-free `forFlowOnly15544` factory.
         *
         * Unlike Strict, MCE's flue pipes are thermal, so the flue region accepts
         * `ThermalFlueSlot` (and interleaved `ConnectorSlot`). `FlueSlot` (flow-only 15544) is
         * rejected — MCE uses thermal flue pipes exclusively. `ChimneySlot` inside the flue region
         * is defensively rejected (unreachable: chimney is always terminal).
         */
        override protected lazy val flueRegionPipeResults: VNelMcalcErr[(Vector[PipeResult], PipeBuildSeed)] =
            import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*
            val pfbSlots            = en15544_mce.postFireboxPipeSlots
            val lastFluePipeSlotIdx = pfbSlots.lastIndexWhere {
                case FlueSlot(_) | ThermalFlueSlot(_) => true
                case _                                => false
            }
            if lastFluePipeSlotIdx < 0 then
                Validated.validNel(
                    (
                        Vector.empty[PipeResult],
                        PipeBuildSeed.fromFrame(en15544_mce.postFireboxInitialDirection.map(toPipeFrame))
                    )
                )
            else
                val flueRegionSlots = pfbSlots.take(lastFluePipeSlotIdx + 1)
                given Params_13384  = p

                // In MCE the Powers/Efficiency/FlueGas/MassFlows givens are safe to summon at
                // chain-seed time — η_WN derives from `t_burnout` via BS845, not from
                // `t_fluepipe_end`, so there is no evaluation cycle (unlike Strict). We only
                // avoid summoning `_temperatures`, which DOES read `t_fluepipe_end`.
                (
                    en15544_mce.en13384_heatingAppliance_powers,
                    en15544_mce.en13384_heatingAppliance_efficiency
                ).mapN((_, _))
                    .andThen: (ha_pow, ha_eff) =>
                        @nowarn("msg=unused local definition") given HeatingAppliance.Powers     = ha_pow
                        @nowarn("msg=unused local definition") given HeatingAppliance.Efficiency = ha_eff

                        val tcThermal13384 = CanComputePipeResult.forThermal13384(
                            en15544_mce.en13384_application,
                            en15544_mce.en13384_heatingAppliance_fluegas,
                            en15544_mce.en13384_heatingAppliance_massFlows
                        )

                        val initialSeed =
                            PipeBuildSeed.fromFrame(en15544_mce.postFireboxInitialDirection.map(toPipeFrame))

                        // Build a PipeSlot for each flue region slot, threading descriptor seed
                        // through the fold accumulator (no mutable state).
                        val slotsV: VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)] =
                            flueRegionSlots.foldLeft[VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)]](
                                Validated.validNel((Vector.empty, initialSeed))
                            ) { (accV, slot) =>
                                accV.andThen { case (acc, seed) =>
                                    slot match
                                        case ThermalFlueSlot(descr) =>
                                            val v4Descr               = descr
                                            val (fdResult, nextSeedV) =
                                                FluePipe_Module_13384
                                                    .mkPipeFromIncrDescrWithSeed(v4Descr, seed)
                                            val nextSeed              = nextSeedV.getOrElse(seed)
                                            val pipeV                 =
                                                FluePipe_Module_13384.FullDescrResult
                                                    .extractPipe(fdResult)
                                            pipeV match
                                                case Validated.Valid(pipe) =>
                                                    Validated.validNel(
                                                        (
                                                            acc :+ tcThermal13384.mkSlot(
                                                                FluePipeT,
                                                                "Flue",
                                                                FlueGas,
                                                                FluePipe_Module_13384.unwrap(pipe)
                                                            ),
                                                            nextSeed
                                                        )
                                                    )
                                                case _                     =>
                                                    Validated.validNel(
                                                        (acc :+ PipeSlot.noop(FluePipeT, "Flue"), nextSeed)
                                                    )
                                        case ConnectorSlot(descr)   =>
                                            val v4Descr = descr
                                            if v4Descr.isEmpty then
                                                Validated.validNel(
                                                    (
                                                        acc :+ PipeSlot
                                                            .noop(ConnectorPipeT, "Connector"),
                                                        seed
                                                    )
                                                )
                                            else
                                                val (fdResult, nextSeedV) =
                                                    ConnectorPipe_Module
                                                        .mkPipeFromIncrDescrWithSeed(v4Descr, seed)
                                                val nextSeed              = nextSeedV.getOrElse(seed)
                                                val pipeV                 =
                                                    ConnectorPipe_Module.FullDescrResult
                                                        .extractPipe(fdResult)
                                                pipeV match
                                                    case Validated.Valid(pipe) =>
                                                        val connectorSlot =
                                                            ConnectorPipe_Module.foldPipeCanBe(
                                                                pipe
                                                            )  (
                                                                onWithout   = PipeSlot.noop(
                                                                    ConnectorPipeT,
                                                                    "Connector"
                                                                ),
                                                                onFullDescr = fd =>
                                                                    tcThermal13384.mkSlot(
                                                                        ConnectorPipeT,
                                                                        "Connector",
                                                                        FlueGas,
                                                                        ConnectorPipe_Module
                                                                            .unwrap(fd)
                                                                    )
                                                            )
                                                        Validated.validNel(
                                                            (acc :+ connectorSlot, nextSeed)
                                                        )
                                                    case _                     =>
                                                        Validated.validNel(
                                                            (
                                                                acc :+ PipeSlot.noop(
                                                                    ConnectorPipeT,
                                                                    "Connector"
                                                                ),
                                                                nextSeed
                                                            )
                                                        )
                                        case FlueSlot(_)            =>
                                            // MCE uses thermal flue pipes exclusively.
                                            Validated.invalidNel(
                                                NotYetSupportedInFlueRegion(
                                                    "FlueSlot (flow-only) in MCE flue region" +
                                                        " — MCE uses ThermalFlueSlot"
                                                )
                                            )
                                        case ChimneySlot(_)         =>
                                            // Unreachable: the chimney is always terminal
                                            // (after the last FluePipeT), so it cannot appear
                                            // inside the flue region. Defensive rejection.
                                            Validated.invalidNel(
                                                NotYetSupportedInFlueRegion(
                                                    "ChimneySlot in flue region (unreachable)"
                                                )
                                            )
                                        case NoFlueSlot             =>
                                            Validated.invalidNel(
                                                NotYetSupportedInFlueRegion(
                                                    "NoFlueSlot in flue region (unreachable)"
                                                )
                                            )
                                }
                            }

                        slotsV.andThen { case (slots, lastSeed) =>
                            // Seed UpstreamState density/velocity for the first head
                            // slot's en13384_pg (pressure-gain) calculation.
                            //
                            // Two seed paths (plan issue E1-MCE — parallel to Strict's E1
                            // in `npipe-topology-connector-first.md`):
                            //
                            //   (a) HEAD_REGION begins with a FluePipe (ThermalFlueSlot) —
                            //       legacy behaviour, preserved byte-identically for the
                            //       MCE dev-fixture goldens. Rebuild the first ThermalFlueSlot
                            //       via `ThermalMecaFlu_13384.makePipeResult`, seeding with
                            //       firebox outlet density/velocity, and take ITS outlet
                            //       density/velocity.
                            //
                            //   (b) HEAD_REGION begins with a Connector — seed UpstreamState
                            //       directly from firebox exit conditions (skipping the
                            //       ThermalMecaFlu recomputation of a non-existent first
                            //       flue). `en15544_mce.t_burnout` is already the firebox
                            //       exit gas temp; `firebox_PipeResult` carries the firebox
                            //       outlet density/velocity.
                            val computeAt = en15544_mce.en13384_application.computeAt

                            val headStartsWithConnector: Boolean =
                                pfbSlots.headOption.exists {
                                    case ConnectorSlot(_)                 => true
                                    case FlueSlot(_) | ThermalFlueSlot(_) => false
                                    case ChimneySlot(_)                   => false
                                    case NoFlueSlot                       => false
                                }

                            val seedPipeResult: VNelMcalcErr[PipeResult] =
                                if headStartsWithConnector then
                                    // Path (b): firebox outlet is the immediate upstream of
                                    // the head connector. No flue recomputation needed.
                                    firebox_PipeResult
                                else
                                    // Path (a): legacy — rebuild first ThermalFlueSlot.
                                    val firstThermalFlueDescrOpt =
                                        pfbSlots.collectFirst { case ThermalFlueSlot(descr) => descr }
                                    firstThermalFlueDescrOpt match
                                        case None        =>
                                            Validated.invalidNel(
                                                UnexpectedDevError(
                                                    "No ThermalFlueSlot descriptor available to seed MCE stage 1"
                                                )
                                            )
                                        case Some(descr) =>
                                            val v4Descr       = descr
                                            val (fdResult, _) =
                                                FluePipe_Module_13384
                                                    .mkPipeFromIncrDescrWithSeed(v4Descr, initialSeed)
                                            FluePipe_Module_13384.FullDescrResult.extractPipe(
                                                fdResult
                                            ) match
                                                case Validated.Valid(pipe)  =>
                                                    firebox_PipeResult.andThen: cc =>
                                                        ops_en13384.ThermalMecaFlu_13384
                                                            .makePipeResult                (
                                                                fd                 = FluePipe_Module_13384.unwrap(pipe),
                                                                hafg               = en15544_mce.en13384_heatingAppliance_fluegas,
                                                                hamf               = en15544_mce.en13384_heatingAppliance_massFlows,
                                                                temp_start         = en15544_mce.t_burnout,
                                                                last_pipe_density  = computeAt match
                                                                    case ComputeAt.Mean   => cc.last_density_mean
                                                                    case ComputeAt.Middle => cc.last_density_middle
                                                                ,
                                                                last_pipe_velocity = computeAt match
                                                                    case ComputeAt.Mean   => cc.last_velocity_mean
                                                                    case ComputeAt.Middle => cc.last_velocity_middle
                                                                ,
                                                                gas                = FlueGas
                                                            )
                                                            .toValidatedNel
                                                case Validated.Invalid(nel) => Validated.Invalid(nel)

                            val seedDensity : Option[Density]      =
                                seedPipeResult.toOption.flatMap { pr =>
                                    computeAt match
                                        case ComputeAt.Mean   =>
                                            pr.last_density_mean.orElse(pr.last_density_middle)
                                        case ComputeAt.Middle => pr.last_density_middle
                                }
                            val seedVelocity: Option[FlowVelocity] =
                                seedPipeResult.toOption.flatMap { pr =>
                                    computeAt match
                                        case ComputeAt.Mean   =>
                                            pr.last_velocity_mean.orElse(pr.last_velocity_middle)
                                        case ComputeAt.Middle => pr.last_velocity_middle
                                }
                            val initialUpstream = UpstreamState(
                                temp_start         = en15544_mce.t_burnout,
                                last_pipe_density  = seedDensity,
                                last_pipe_velocity = seedVelocity
                            )
                            val folded = slots.foldLeft[Either[
                                afpma.firecalc.engine.standard.MecaFlu_Error,
                                (UpstreamState, Vector[PipeResult])
                            ]](Right((initialUpstream, Vector.empty))) { case (acc, slot) =>
                                acc.flatMap { case (upstream, results) =>
                                    slot.compute(upstream, p).map { pr =>
                                        val nextUpstream =
                                            UpstreamState.fromPipeResult(pr, computeAt)
                                        (nextUpstream, results :+ pr)
                                    }
                                }
                            }
                            folded match
                                case Right((_, results)) => Validated.validNel((results, lastSeed))
                                case Left(err)           => Validated.invalidNel(err)
                        }

    end MCEAtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────

    lazy val atDraftMin_LoadNominal: AtParams         = new MCEAtParams(Params_15544.DraftMin_LoadNominal)
    lazy val atDraftMin_LoadMin    : Option[AtParams] = m_B_min.map(_ => new MCEAtParams(Params_15544.DraftMin_LoadMin))
    lazy val atDraftMax_LoadNominal: AtParams         = new MCEAtParams(Params_15544.DraftMax_LoadNominal)
    lazy val atDraftMax_LoadMin    : Option[AtParams] = m_B_min.map(_ => new MCEAtParams(Params_15544.DraftMax_LoadMin))
}
