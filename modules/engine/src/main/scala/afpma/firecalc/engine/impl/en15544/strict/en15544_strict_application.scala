/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas

import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.MassFlows
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.Temperatures
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.en15544 as ops_en15544
import afpma.firecalc.units.coulombutils.*


import coulomb.*
import coulomb.policy.standard.given
import scala.annotation.nowarn
import afpma.firecalc.engine.standard.VNelMcalcErr
import cats.data.Validated
import afpma.firecalc.engine.standard.UnexpectedDevError
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, PipeSlot, PostFireboxPipeChain, UpstreamState}
import afpma.firecalc.engine.alg.en13384.WithParams_13384

object EN15544_Strict_Application:
    def make(
        f: EN15544_V_2023_Formulas_Alg
    )(
        i: Inputs_15544_Strict,
        pfbSlots: Seq[afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot] = Seq.empty
    ): EN15544_Strict_Application = new EN15544_Strict_Application(f) {
        override lazy val inputs: Inputs_15544 = i
        override lazy val postFireboxPipeSlots: Seq[afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot] = pfbSlots
    }

sealed abstract class EN15544_Strict_Application(
    override val formulas: EN15544_V_2023_Formulas_Alg
) extends impl.en15544.common.EN15544_V_2023_Common_Application
    with HasTypeMembers_15544_Strict {
    en15544 =>

    override val doc: Document = Document(
        name     = "15544:2023-02",
        date     = "2023-02",
        version  = "2023-02",
        revision = "NA",
        status   = "Approved",
        author   = "AFNOR"
    )

    override lazy val en13384_inputs = Inputs_13384_WithFlowOnlyAirIntake(
        en13384_inputs_pipes,
        en13384_inputs_nationalAcceptedData,
        en13384_inputs_fuelType,
        inputs.localConditions,
        en13384_inputs_flueGasCondition
    )

    override def en13384_inputs_pipes: Pipes_13384 =
        (inputs.pipes: Pipes_13384_WithFlowOnlyAirIntake)

    // algebra as given
    lazy val en13384_formulas: EN13384_1_A1_2019_Formulas = new EN13384_1_A1_2019_Formulas:
        // overrides
        override def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity) =
            en15544.formulas.p_d_calc(ρ_m, w_m)

    lazy val energy_in_nominal_load = energy_in_wet_wood(m_B)
    lazy val energy_in_minimal_load = m_B_min.map(energy_in_wet_wood)

    val combustionDuration: Duration = (1 / 0.78).hours.toUnit[Minute]

    lazy val en13384_η_WN: VNelMcalcErr[Percentage] =
        atDraftMin_LoadNominal.t_fluepipe_end
            .map(t_fp_end => formulas.η_calc(t_fp_end))

    lazy val en13384_η_Wmin: Option[VNelMcalcErr[Percentage]] =
        atDraftMin_LoadMin.map:
            _.t_fluepipe_end
                .map(t_fp_end => formulas.η_calc(t_fp_end))

    lazy val en13384_fluegas_σ_CO2_dry_nominal: Percentage         = fluegas_σ_CO2_dry_nominal // yes, even for tested firebox
    lazy val en13384_fluegas_σ_CO2_dry_lowest : Option[Percentage] =
        fluegas_σ_CO2_dry_lowest // yes, even for tested firebox

    lazy val en13384_fluegas_σ_H2O_nominal: Option[Percentage] = None // no override, will use EN13384 formulas
    lazy val en13384_fluegas_σ_H2O_lowest : Option[Percentage] = None // no override, will use EN13384 formulas

    lazy val en13384_heatingAppliance_massFlows: MassFlows = MassFlows.undefined

    lazy val en13384_heatingAppliance_temperatures: VNelMcalcErr[Temperatures] =
        val isEqualAndValidVNel: (VNelMcalcErr[TCelsius], VNelMcalcErr[TCelsius]) => Boolean =
            case (Validated.Valid(t1), Validated.Valid(t2)) => t1 == t2
            case (Validated.Invalid(_), Validated.Invalid(_)) => true // errors in both cases, ok for this case.
            case _ => false

        val a = atDraftMin_LoadNominal.t_fluepipe_end
        val b = atDraftMax_LoadNominal.t_fluepipe_end
        require(isEqualAndValidVNel(a, b), s"dev error: '$a' if min draft != '$b' if max draft !! Why ?")

        a.map: fg_temp_nominal =>
            HeatingAppliance.Temperatures(
                flue_gas_temp_nominal = fg_temp_nominal,
                flue_gas_temp_reduced = None
            )

    override final lazy val en13384_T_L_override = en13384_T_L_override_default

    val fluegas_σ_CO2_dry_nominal: σ_CO2 =
        inputs.design.firebox.co2_dry_nominal

    val fluegas_σ_CO2_dry_lowest: Option[σ_CO2] =
        inputs.design.firebox.co2_dry_lowest

    val wood_σ_H2O: σ_H2O = formulas.wood_σ_H2O_calc

    lazy val en13384_application = new EN13384_For_15544_Application(
        formulas = en13384_formulas
    ):
        self =>
        override lazy val inputs = en13384_inputs

        override def airIntake_PipeResult_withVentilationOpenings(
            fd                : AirIntakePipe_Module.FullDescr
        ): HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]] =
            ops_en13384.FlowOnlyMecaFlu_13384.makePipeResult(
                fd                 = FlowOnlyAirIntakePipe_Module_13384.unwrap(fd),
                // self.inputs.pipes.AirIntakePipe_Module.unwrap(fd),
                // fd.unwrap,
                hafg               = HeatingAppliance.FlueGas.summon,
                hamf               = HeatingAppliance.MassFlows.summon,
                hapwr              = HeatingAppliance.Powers.summon,
                haeff              = HeatingAppliance.Efficiency.summon,
                temp_start         = T_L,
                last_pipe_velocity = None,
                gas                = CombustionAir
            )

        override def airIntake_PipeResult =
            FlowOnlyAirIntakePipe_Module_13384.foldPipeCanBe(inputs.pipes.airIntake)(
                onNoVentilation = airIntake_PipeResult_withoutVentilationOpenings,
                onFullDescr     = fd => airIntake_PipeResult_withVentilationOpenings(fd)
            )

        override final lazy val computeAt                                      = ComputeAt.Middle
        override final lazy val p_L_override                                   = en13384_p_L_override
        override def atParamsFor(p: Params_13384): AtParams = en15544.atParamsFor(p)
        // overrides
        @nowarn override def ρ_m(T_m: TKelvin)(using HeatingAppliance.FlueGas) =
            en15544.ρ_G(T_m.toUnit[Celsius])

        override def P_H(h: Length, t: TKelvin)(using HeatingAppliance.FlueGas) =
            en15544.formulas.p_h_calc(h, en15544.ρ_L, ρ_m(t))

        override def P_R_staticFriction(
            L                : Length,
            D_h              : Length,
            r                : Roughness,
            w_m_not_corrected: Velocity,
            ρ_m              : Density,
            t_m              : TKelvin
        ): EpOp[Pressure] =
            val λf = en15544.formulas.λ_f_calc(D_h, r)
            val pd = en15544.formulas.p_d_calc(ρ_m, w_m_not_corrected)
            en15544.formulas.p_R_calc(λf, pd, L, D_h)

        override def P_R_velocityChange(P_G: Pressure) = 0.0.pascals

        override def P_R_dynamicFriction(
            Σ_ζ: Dimensionless,
            ρ_m: QtyD[Kilogram / (Meter ^ 3)],
            w_m: QtyD[Meter / Second]
        ): EpOp[Pressure] =
            val pd = en15544.formulas.p_d_calc(ρ_m, w_m)
            en15544.formulas.p_u_calc(Σ_ζ, pd)

        override def ρ_B(T_B: TKelvin): EpOp[Density] =
            en15544.ρ_L

        override def m_dot =
            given Option[LoadQty] = LoadQty.Nominal.some
            getOrThrow_forLoadOp(
                en15544.m_G,
                ifNone = UnexpectedDevError("could not compute en13384.m_dot : en15544.m_G undefined")
            )

        override def m_dot_min =
            given Option[LoadQty] = m_B_min.map(_ => LoadQty.Reduced)
            getOrThrow_forLoadOp(
                en15544.m_G,
                ifNone = UnexpectedDevError("could not determine 'm_dot_min' because 'm_B_min' is not defined")
            )

        override def mB_dot =
            given Option[LoadQty] = LoadQty.Nominal.some
            getOrThrow_forLoadOp(
                en15544.m_L,
                ifNone = UnexpectedDevError("could not compute en13384.mB_dot : en15544.m_L undefined")
            )

        override def mB_dot_min =
            given Option[LoadQty] = m_B_min.map(_ => LoadQty.Reduced)
            getOrThrow_forLoadOp(
                en15544.m_L,
                ifNone = UnexpectedDevError(
                    "could not determine 'mB_dot_min' because 'm_B_min' or 'en15544.m_L' is not defined"
                )
            )

        // CAN BE COMMENTED ? CAN WE LEAVE IT TO DEFAULT IMPL ?
        // override def σ_CO2(using HeatingAppliance.FlueGas): WithLoadQty[Percentage] =
        //     en15544.fluegas_σ_CO2_dry

        // override lazy val last_known_density_before_connector_pipe =
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_density_middle)

        // override lazy val last_known_velocity_before_connector_pipe =
        //     flue_PipeResult(using params_13384_to_15544).toOption.flatMap(_.last_velocity_middle)
    end en13384_application

    override def combustionAir_PipeResult_whenExists(
        fd: CombustionAirPipe_Module_15544.FullDescr
    ) =
        ops_en15544.FlowOnlyMecaFlu_15544
            .makePipeResult                 (
                fd                  = CombustionAirPipe_Module_15544.unwrap(fd),
                gas                 = CombustionAir,
                loadQty             = LoadQty.summon,
                z_geodetical_height = z_geodetical_height,
                params              = pressReq_from_Params_15544
            )(using en15544)
            .toValidatedNel

    // ─── StrictAtParams: concrete inner class for strict application ─────

    class StrictAtParams(p: Params_15544) extends CommonAtParams(p):

        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult] =
            given Params_15544 = p
            CombustionAirPipe_Module_15544.foldPipeCanBe(inputs.pipes.combustionAir)(
                onWithout   = combustionAir_PipeResult_whenEmpty,
                onFullDescr = fd => combustionAir_PipeResult_whenExists(fd)
            )

        lazy val firebox_PipeResult: VNelMcalcErr[PipeResult] =
            ops_en15544.FlowOnlyMecaFlu_15544
                .makePipeResult(
                    fd                  = FireboxPipe_Module_15544.unwrap(inputs.pipes.firebox),
                    gas                 = FlueGas,
                    loadQty             = p._2,
                    z_geodetical_height = z_geodetical_height,
                    params              = p._1
                )(using en15544)
                .toValidatedNel

        lazy val flue_PipeResult: VNelMcalcErr[PipeResult] =
            ops_en15544.FlowOnlyMecaFlu_15544
                .makePipeResult(
                    fd                  = FluePipe_Module_15544.unwrap(inputs.pipes.flue),
                    gas                 = FlueGas,
                    loadQty             = p._2,
                    z_geodetical_height = z_geodetical_height,
                    params              = p._1
                )(using en15544)
                .toValidatedNel

        lazy val pipesResult_15544_VNelS: PipesResult_15544_VNelString =
            PipesResult_15544_VNelString(
                airIntake     = airIntake_PipeResult(using p),
                combustionAir = combustionAir_PipeResult,
                firebox       = firebox_PipeResult,
                flue          = flue_PipeResult,
                connector     = connector_PipeResult,
                chimney       = chimney_PipeResult
            )

        /** Override to compute results for ALL N post-firebox slots, not just the fixed 3.
          *
          * Builds PipeSlots for each PostFireboxPipeDescrSlot using the appropriate typeclass:
          * - FlueSlot → EN15544 flow-only (CanComputePipeResult.forFlowOnly15544)
          * - ThermalFlueSlot/ConnectorSlot/ChimneySlot → EN13384 thermal
          *
          * Then validates the topology and folds through PostFireboxPipeChain.computeAll
          * to produce N results with proper upstream state threading.
          */
        override lazy val postFireboxPipeResults: VNelMcalcErr[Vector[PipeResult]] =
            import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
            val pfbSlots = en15544.postFireboxPipeSlots
            // Fall back to the classic 3-pipe vector when no DTO slots are configured
            if pfbSlots.isEmpty then
                (flue_PipeResult, connector_PipeResult, chimney_PipeResult).mapN(Vector(_, _, _))
            else
                (
                    en15544.en13384_heatingAppliance_powers,
                    en15544.en13384_heatingAppliance_efficiency,
                    en15544.en13384_heatingAppliance_temperatures
                ).mapN((_, _, _)).andThen: (ha_pow, ha_eff, ha_temp) =>
                    @nowarn given HeatingAppliance.Powers       = ha_pow
                    @nowarn given HeatingAppliance.Efficiency   = ha_eff
                    given HeatingAppliance.Temperatures = ha_temp
                    val params13384: Params_13384 = p
                    given Params_13384  = params13384

                    val tcFlowOnly15544 = CanComputePipeResult.forFlowOnly15544(
                        en15544, en15544.z_geodetical_height, en15544.ssalg
                    )
                    val tcThermal13384 = CanComputePipeResult.forThermal13384(
                        en15544.en13384_application,
                        en15544.en13384_heatingAppliance_fluegas,
                        en15544.en13384_heatingAppliance_massFlows,
                        ha_pow,
                        ha_eff
                    )
                    // Build PipeSlots with frame chaining — each slot's external frame
                    // comes from the previous slot's final frame, matching PipeChainGeneric.build.
                    var prevFrame: Option[PipeFrame] = None
                    val pipeSlots: Vector[PipeSlot] = pfbSlots.map:
                        case FlueSlot(descr) =>
                            val (fdResult, ffV) = FluePipe_Module_15544.mkPipeFromIncrDescrWithFinalFrame(descr, prevFrame)
                            prevFrame = ffV.toOption.flatten.orElse(prevFrame)
                            val pipeV = FluePipe_Module_15544.FullDescrResult.extractPipe(fdResult)
                            pipeV match
                                case Validated.Valid(pipe) =>
                                    tcFlowOnly15544.mkSlot(FluePipeT, "Flue", FlueGas,
                                        FluePipe_Module_15544.unwrap(pipe))
                                case _ =>
                                    PipeSlot.noop(FluePipeT, "Flue")
                        case ThermalFlueSlot(descr) =>
                            val (fdResult, ffV) = FluePipe_Module_13384.mkPipeFromIncrDescrWithFinalFrame(descr, prevFrame)
                            prevFrame = ffV.toOption.flatten.orElse(prevFrame)
                            val pipeV = FluePipe_Module_13384.FullDescrResult.extractPipe(fdResult)
                            pipeV match
                                case Validated.Valid(pipe) =>
                                    tcThermal13384.mkSlot(FluePipeT, "Flue", FlueGas,
                                        FluePipe_Module_13384.unwrap(pipe))
                                case _ =>
                                    PipeSlot.noop(FluePipeT, "Flue")
                        case ConnectorSlot(descr) =>
                            if descr.isEmpty then
                                PipeSlot.noop(ConnectorPipeT, "Connector")
                            else
                                val (fdResult, ffV) = ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(descr, prevFrame)
                                prevFrame = ffV.toOption.flatten.orElse(prevFrame)
                                val pipeV = ConnectorPipe_Module.FullDescrResult.extractPipe(fdResult)
                                pipeV match
                                    case Validated.Valid(pipe) =>
                                        ConnectorPipe_Module.foldPipeCanBe(pipe)(
                                            onWithout   = PipeSlot.noop(ConnectorPipeT, "Connector"),
                                            onFullDescr = fd => tcThermal13384.mkSlot(ConnectorPipeT, "Connector", FlueGas,
                                                ConnectorPipe_Module.unwrap(fd))
                                        )
                                    case _ =>
                                        PipeSlot.noop(ConnectorPipeT, "Connector")
                        case ChimneySlot(descr) =>
                            val chimneyFrame = prevFrame
                            val fdResult = ChimneyPipe_Module.mkPipeFromIncrDescr(descr, chimneyFrame)
                            val pipeV    = ChimneyPipe_Module.FullDescrResult.extractPipe(fdResult)
                            pipeV match
                                case Validated.Valid(pipe) =>
                                    tcThermal13384.mkSlot(ChimneyPipeT, "Chimney", FlueGas,
                                        ChimneyPipe_Module.unwrap(pipe))
                                case _ =>
                                    PipeSlot.noop(ChimneyPipeT, "Chimney")
                    .toVector

                    PostFireboxPipeChain.validated(pipeSlots) match
                        case Validated.Invalid(topoErrors) =>
                            Validated.invalidNel(UnexpectedDevError(
                                s"Invalid post-firebox topology: ${topoErrors.toList.mkString(", ")}"
                            ))
                        case Validated.Valid(chain) =>
                            val initialUpstream = UpstreamState(
                                temp_start         = en15544.en13384_application.T_WN,
                                last_pipe_density  = en15544.en13384_application.last_known_density_before_connector_pipe,
                                last_pipe_velocity = en15544.en13384_application.last_known_velocity_before_connector_pipe
                            )
                            chain.computeAll(params13384, initialUpstream, en15544.en13384_application.computeAt) match
                                case Right(results) => Validated.validNel(results)
                                case Left(err)      => Validated.invalidNel(err)

        lazy val outputs: Outputs =
            val pipesResult = pipesResult_15544_VNelS.accumulateErrors
            models.en15544.std.Outputs(
                techSpecs,
                pipesResult,
                reference_temperatures,
                efficiencies_values
            )
    end StrictAtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────

    lazy val atDraftMin_LoadNominal: AtParams = new StrictAtParams(Params_15544.DraftMin_LoadNominal)
    lazy val atDraftMin_LoadMin: Option[AtParams] = m_B_min.map(_ => new StrictAtParams(Params_15544.DraftMin_LoadMin))
    lazy val atDraftMax_LoadNominal: AtParams = new StrictAtParams(Params_15544.DraftMax_LoadNominal)
    lazy val atDraftMax_LoadMin: Option[AtParams] = m_B_min.map(_ => new StrictAtParams(Params_15544.DraftMax_LoadMin))

    def net_calorific_value_of_wet_wood(
        @nowarn ncv_dry: HeatCapacity,
        @nowarn hum    : Percentage,
        @nowarn kind   : KindOfWood
    ): HeatCapacity =
        4.16.kWh_per_kg // See 4.2.1

    def net_calorific_value_of_dry_wood(@nowarn kind: KindOfWood): HeatCapacity =
        18500.kJ_per_kg.toUnit[Kilo * Watt * Hour / Kilogram] // see 4.10.3
}
