/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import cats.*
import cats.syntax.all.*

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.alg.en15544.FireboxEngineRegistry
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.ops.en13384.mkforEN13384

import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.MassFlows
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance.Temperatures
import afpma.firecalc.engine.models.en13384.typedefs.{DraftCondition, T_L_override}
import afpma.firecalc.engine.models.en13384.Inputs_13384_WithFlowOnlyAirIntake_PreFireboxOnly
import afpma.firecalc.engine.models.en15544.IncrementalPipeInputs_15544
import afpma.firecalc.engine.models.en15544.IncrementalPipeInputs_15544_Strict
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.Inputs_15544_Strict
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.PipeWithGasFlowOps
import afpma.firecalc.engine.ops.en15544 as ops_en15544
import afpma.firecalc.engine.standard.SlotContext
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.policy.standard.given
import scala.annotation.nowarn
import afpma.firecalc.engine.standard.VNelMcalcErr
import cats.data.Validated
import afpma.firecalc.engine.standard.UnexpectedDevError
import afpma.firecalc.engine.standard.NotYetSupportedInFlueRegion
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.ops.en13384.forThermal13384
import afpma.firecalc.engine.ops.en15544.forFlowOnly15544
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, PipeSlot, UpstreamState}
import afpma.firecalc.engine.alg.en13384.WithParams_13384
import afpma.firecalc.engine.impl.en15544.common.PostFireboxFrameHelpers.toPipeFrame
import afpma.firecalc.engine.ops.generic.SlotIndexProvider
import afpma.firecalc.domain.FireboxCoordinateSystem

object EN15544_Strict_Application:
    def make(
        f: EN15544_V_2023_Formulas_Alg
    )(
        i          : Inputs_15544_Strict,
        _incrInputs: IncrementalPipeInputs_15544_Strict
    ): EN15544_Strict_Application = new EN15544_Strict_Application(f) {
        override lazy val inputs    : Inputs_15544_Strict                = i
        override lazy val incrInputs: IncrementalPipeInputs_15544_Strict = _incrInputs
    }

    /**
     * Factory for the standalone [[FireboxCalculation]] path: like [[make]]
     * but with the envelope's `T_L_override` MERGED OVER this application's
     * own default (`en13384_T_L_override_default`), per draft condition.
     *
     * Absent overrides reproduce the full strict application member-for-member
     * (the standalone is layered out of it); a per-condition override wins for
     * its condition while the other condition keeps the strict default — never
     * the EN 13384 standard default.
     */
    private[strict] def makeWithEnvelope(
        f: EN15544_V_2023_Formulas_Alg
    )(
        i          : Inputs_15544_Strict,
        _incrInputs: IncrementalPipeInputs_15544_Strict,
        tLOverride : afpma.firecalc.engine.models.en13384.typedefs.T_L_override
    ): EN15544_Strict_Application = new EN15544_Strict_Application(f) {
        override lazy val inputs    : Inputs_15544_Strict                = i
        override lazy val incrInputs: IncrementalPipeInputs_15544_Strict = _incrInputs
        override lazy val en13384_T_L_override =
            tLOverride.mergeOver(en13384_T_L_override_default)
    }

    /**
     * Wrap an existing application so that its lazy `fc` (firebox engine)
     * resolves from a remote-backed registry instead of the default local one.
     *
     * The wrapper delegates all behaviour to `delegate` except for
     * `fireboxEngineRegistry`, which returns a six-entry registry (one per
     * closed kind, in `FireboxEngineRegistry_Strict` order) where the actual
     * kind is served by a `RemoteBackedFireboxEngine` and the rest keep their
     * local engines; `checkTotality()` is run on construction. Because `fc` is
     * `lazy`, it has not yet been evaluated when this wrapper is created — the
     * algebra chain only calls `make`, and `fc` is accessed later by panels.
     *
     * Per spec §5.4, this is the single code path for the remote engine:
     * `fireboxEngineSource match { case Local => localRegistry;
     * case Remote(_) => remoteRegistryWithData }`.
     */
    private[engine] def withRemoteData(
        delegate  : EN15544_Strict_Application,
        remoteData: RemoteFireboxData[
            FireboxPipe_Module_15544.FullDescr,
            CombustionAirPipe_Module_15544.FullDescr
        ]
    ): EN15544_Strict_Application =
        new RemoteBackedWrapper(delegate, remoteData)

    /**
     * Wrapper that overrides `fireboxEngineRegistry` to return a remote-backed
     * registry. All other members delegate to the original application.
     *
     * Defined inside the companion object so it can extend the sealed class.
     */
    private final class RemoteBackedWrapper(
        delegate  : EN15544_Strict_Application,
        remoteData: RemoteFireboxData[
            FireboxPipe_Module_15544.FullDescr,
            CombustionAirPipe_Module_15544.FullDescr
        ]
    ) extends EN15544_Strict_Application(delegate.formulas):

        override lazy val inputs    : Inputs_15544_Strict         = delegate.inputs
        override lazy val incrInputs: IncrementalPipeInputs_15544 =
            delegate.incrInputs

        // Capture the firebox reference for narrowing (TypeTest supplied per entry).
        private val designFirebox: afpma.firecalc.engine.models.en15544.std.Firebox_15544 =
            delegate.inputs.design.firebox

        // ── Remote-backed registry (ticket 11: generic over the base registry) ──
        // Built by mapping the base `FireboxEngineRegistry_Strict` entry list:
        // the entry whose `narrow` accepts this project's firebox — the kind
        // `remoteData` was prefetched for — is swapped for a remote-backed
        // engine (`toRemoteBacked`); every other entry keeps its local engine.
        // Order and kind coverage are inherited from the base registry
        // (invariant 7), so `checkTotality()` holds by construction.
        //
        // This is generic in the model type: it uses each entry's own
        // `TypeTest`-backed `narrow` to identify the served kind, so the wrapper
        // never names a concrete firebox model. The base registry carries
        // generic remote-only carriers for the proprietary kinds (ticket 16:
        // shared, Austrian-free), and this mapping swaps the served entry for
        // the remote-backed engine.
        override protected lazy val fireboxEngineRegistry: FireboxEngineRegistry[
            FireboxPipe_Module_15544.FullDescr,
            CombustionAirPipe_Module_15544.FullDescr
        ] =
            new FireboxEngineRegistry[
                FireboxPipe_Module_15544.FullDescr,
                CombustionAirPipe_Module_15544.FullDescr
            ]:
                val name = "RemoteBackedFireboxEngineRegistry"
                val all  = FireboxEngineRegistry_Strict.all.map: entry =>
                    if entry.narrow(designFirebox).isDefined then entry.toRemoteBacked(remoteData)
                    else entry
                locally { checkTotality() }

        // AtParams (atDraftMin/Max_LoadNominal/LoadMin) are NOT delegated
        // across instances: they are inherited from the abstract class and
        // recomputed against THIS wrapper's `fc` (the remote-backed registry
        // above). Delegating the delegate's AtParams would need a runtime cast
        // to bridge the path-dependent inner-trait types `delegate.AtParams`
        // -> `this.AtParams` (invariant 9), and would compute the outputs
        // with the LOCAL engine despite the remote registry. Inheriting is
        // cast-free and uses the remote engine. (fix-plan Phase 3, dev. 4.)

sealed abstract class EN15544_Strict_Application(
    override val formulas: EN15544_V_2023_Formulas_Alg
) extends impl.en15544.common.EN15544_V_2023_Common_Application
    with HasTypeMembers_15544_Strict
    with FireboxScopeCore_Strict {
    en15544 =>

    override protected def fireboxEngineRegistry: FireboxEngineRegistry[?, ?] =
        FireboxEngineRegistry_Strict

    /** Test-only accessor for the active registry (fix-plan Phase 1 acceptance). */
    private[strict] def activeRegistry: FireboxEngineRegistry[?, ?] =
        fireboxEngineRegistry

    /**
     * Package-private forwarders for the protected per-params contributed-
     * result lookups, so [[FireboxScopeCore_Strict]] (a mixin trait that
     * cannot access protected members of the common application base) can
     * reach them. Each indexes the engine's contributed grid by the requested
     * operating point.
     */
    private[strict] def fireboxContributedResultOpt(dc: DraftCondition, lq: LoadQty): VNelMcalcErr[Option[PipeResult]] =
        fireboxContributedResultAt(dc, lq)
    private[strict] def combustionAirContributedResultOpt(
        dc: DraftCondition,
        lq: LoadQty
    )                                                                               : VNelMcalcErr[Option[PipeResult]] =
        combustionAirContributedResultAt(dc, lq)

    // ─── EN13384ForApp: concrete type provided by strict ─────────────────
    override type EN13384ForApp = EN13384_1_A1_2019_Common_Application

    override given pipeWithGasFlowOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384_formulas)

    override val doc: Document = Document(
        name     = "15544:2023-02",
        date     = "2023-02",
        version  = "2023-02",
        revision = "NA",
        status   = "Approved",
        author   = "AFNOR"
    )

    override lazy val en13384_inputs = Inputs_13384_WithFlowOnlyAirIntake_PreFireboxOnly(
        en13384_inputs_pipes,
        en13384_inputs_nationalAcceptedData,
        en13384_inputs_fuelType,
        inputs.localConditions,
        en13384_inputs_flueGasCondition
    )

    override def en13384_inputs_pipes: Pipes_13384 =
        (inputs.pipes: Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly)

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
        // Conservative choice: use min-draft temperature (lower temp → less buoyancy).
        //
        // When the flue region contains only EN 15544 flow-only pipes, t_fluepipe_end
        // is identical at min and max draft for a given load pass (temperature is
        // draft-independent). However, interleaved ConnectorSlots in the flue region
        // are computed using Thermal 13384 (heat-transfer formulas where temperature
        // loss depends on gas velocity, which varies with draft). This makes
        // t_fluepipe_end legitimately draft-dependent for topologies like
        // [Flue, Connector, Flue, Connector, Chimney].
        //
        // It is NOT load-independent: the reduced pass decays with a pass-selected
        // L_N (see `OpPointLoads.effective_load_mass`, PRD D2), so its flue-gas
        // temperatures (and ηWmin) differ from the nominal ones on reduced-capable
        // appliances.
        // This value stays the nominal-pass temperature by design.
        atDraftMin_LoadNominal.t_fluepipe_end.map: fg_temp_nominal =>
            HeatingAppliance.Temperatures(
                flue_gas_temp_nominal = fg_temp_nominal,
                flue_gas_temp_reduced = None
            )

    override lazy val en13384_T_L_override = en13384_T_L_override_default

    val fluegas_σ_CO2_dry_nominal: σ_CO2 =
        inputs.design.firebox.co2_dry_nominal

    val fluegas_σ_CO2_dry_lowest: Option[σ_CO2] =
        inputs.design.firebox.co2_dry_lowest

    val wood_σ_H2O: σ_H2O = formulas.wood_σ_H2O_calc

    lazy val en13384_application = new EN13384_1_A1_2019_Common_Application(
        en13384_formulas
    ) with EN13384_For_15544_Overrides:
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
                temp_start         = T_L,
                last_pipe_velocity = None,
                gas                = CombustionAir,
                sc                 = SlotContext.unslotted
            )

        override def airIntake_PipeResult =
            FlowOnlyAirIntakePipe_Module_13384.foldPipeCanBe(inputs.pipes.airIntake)(
                onNoVentilation = airIntake_PipeResult_withoutVentilationOpenings,
                onFullDescr     = fd => airIntake_PipeResult_withVentilationOpenings(fd)
            )

        override final lazy val computeAt    = ComputeAt.Middle
        override final lazy val p_L_override = en13384_p_L_override
        override def atParamsFor(p: Params_13384): AtParams = en15544.atParamsFor(p)
        // overrides
        override def ρ_m(T_m: TKelvin)(using HeatingAppliance.FlueGas) =
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

    end en13384_application

    override def combustionAir_PipeResult_whenExists(
        fd: CombustionAirPipe_Module_15544.FullDescr
    ) =
        val p = summon[Params_15544]
        solveCombustionAirDescriptor(fd, p._1, p._2)

    // ─── StrictAtParams: concrete inner class for strict application ─────

    class StrictAtParams(p: Params_15544) extends CommonAtParams(p):

        /**
         * The combustion-air gas-path result.
         *
         * An engine that returned `FireboxContribution.AsResult` has dictated
         * the result outright (it owes the full continuation state); the solver
         * is then not invoked. Every engine registered today returns `AsDescr`,
         * so the fallback below is the normal path.
         */
        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult] =
            solveCombustionAirPipeAt(p._1, p._2).andThen:
                case Some(pr) => pr.validNel
                case None     =>
                    given Params_15544 = p
                    combustionAir_PipeResult_whenEmpty

        /**
         * The firebox gas-path result.
         *
         * An engine that returned `FireboxContribution.AsResult` has dictated
         * the result outright (it owes the full continuation state); the solver
         * is then not invoked. Every engine registered today returns `AsDescr`,
         * so the fallback below is the normal path.
         */
        lazy val firebox_PipeResult: VNelMcalcErr[PipeResult] =
            solveFireboxPipeAt(p._1, p._2)

        /**
         * Stage 1 — flue region pipe results (HA-power-free).
         *
         * Computes the flue region slots (up to and including the last `FluePipeT`) using
         * HA-power-FREE typeclasses only. This lazy val must NOT read
         * `en13384_heatingAppliance_powers` / `_efficiency` / `_temperatures` — doing so would
         * re-introduce an initialization cycle (Stage 2 depends on Stage 1; HA givens depend
         * on Stage 2).
         *
         * `ThermalFlueSlot` is supported in the flue region because `ThermalMecaFlu_13384`
         * is HA-power-free; `tcThermal13384` is built from `en13384_heatingAppliance_fluegas`
         * and `en13384_heatingAppliance_massFlows`, neither of which reads the HA-power givens.
         * Interleaved `ConnectorSlot` in the flue region is computed using Thermal 13384.
         */
        override protected lazy val flueRegionPipeResults: VNelMcalcErr[(Vector[PipeResult], PipeBuildSeed)] =
            import PostFireboxPipeSlot.*
            val pfbSlots            = en15544.incrInputs.postFirebox.slots
            // Locate the last FluePipeT slot; the flue region is slots up to and including it.
            val lastFluePipeSlotIdx = pfbSlots.lastIndexWhere {
                case FlueSlot(_) | ThermalFlueSlot(_) => true
                case _                                => false
            }
            if lastFluePipeSlotIdx < 0 then
                Validated.validNel(
                    (
                        Vector.empty[PipeResult],
                        PipeBuildSeed.fromFrame(en15544.incrInputs.postFirebox.initialDirection.map(toPipeFrame))
                    )
                )
            else
                val flueRegionSlots = pfbSlots.take(lastFluePipeSlotIdx + 1)

                // HA-power-FREE typeclasses — this is the whole point of Stage 1.
                val tcFlowOnly15544 = CanComputePipeResult.forFlowOnly15544(
                    en15544,
                    en15544.z_geodetical_height,
                    en15544.ssalg
                )
                // forThermal13384 is also HA-power-free (ThermalMecaFlu_13384 no longer
                // reads Powers/Efficiency/Temperatures). Safe to instantiate here in Stage 1.
                val tcThermal13384  = CanComputePipeResult.forThermal13384(
                    en15544.en13384_application,
                    en15544.en13384_heatingAppliance_fluegas,
                    en15544.en13384_heatingAppliance_massFlows
                )

                val dims = en15544.firebox.dimensions.base match
                    case afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions.Base.Squared(width, depth) =>
                        (width.value, depth.value, en15544.firebox.dimensions.height.value)

                val seed = PipeBuildSeed.withPositions(
                    slots        = flueRegionSlots,
                    initialFrame = en15544.incrInputs.postFirebox.initialDirection.map(toPipeFrame),
                    fbWidth      = dims._1,
                    fbDepth      = dims._2,
                    fbHeight     = dims._3,
                    fbBottomZ    = FireboxCoordinateSystem.FireboxBaseCenterZ
                )

                // Build PipeSlot for each flue region slot, threading descriptor seed through
                // the fold accumulator (no mutable state).
                val slotsV: VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)] =
                    val provider = SlotIndexProvider.prefix
                    flueRegionSlots.zipWithIndex.foldLeft[VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)]](
                        Validated.validNel((Vector.empty, seed))
                    ) { case (accV, (slot, idx)) =>
                        accV.andThen { case (acc, seed) =>
                            slot match
                                case FlueSlot(descr)        =>
                                    import FluePipe_Module_15544.FullDescrResult.given
                                    import FluePipe_Module_15544.toFullDescrWithSeed
                                    val flueResult = FluePipe_Module_15544.incremental
                                        .define(descr*)
                                        .toFullDescrWithSeed(seed)(using SlotContext.forSlot(provider(idx)))
                                    val fdResult: FluePipe_Module_15544.FullDescrResult =
                                        flueResult.map((ids, fd, _) => (ids, fd))
                                    val nextSeed = flueResult.map(_._3).toOption.getOrElse(seed)
                                    FluePipe_Module_15544.FullDescrResult
                                        .extractPipe(fdResult)
                                        .map(pipe =>
                                            (
                                                acc :+ tcFlowOnly15544.mkSlot(
                                                    FluePipeT,
                                                    "Flue",
                                                    FlueGas,
                                                    FluePipe_Module_15544.unwrap(pipe)
                                                ),
                                                nextSeed
                                            )
                                        )
                                case ThermalFlueSlot(descr) =>
                                    val v4Descr               = descr
                                    val (fdResult, nextSeedV) =
                                        FluePipe_Module_13384
                                            .mkPipeFromIncrDescrWithSeed(v4Descr, seed)(using
                                                SlotContext.forSlot(provider(idx))
                                            )
                                    val nextSeed = nextSeedV.toOption.getOrElse(seed)
                                    FluePipe_Module_13384.FullDescrResult
                                        .extractPipe(fdResult)
                                        .map                                           (pipe =>
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
                                case ConnectorSlot(descr)   =>
                                    val v4Descr = descr
                                    if v4Descr.isEmpty then
                                        Validated.validNel(
                                            (acc :+ PipeSlot.noop(ConnectorPipeT, "Connector"), seed)
                                        )
                                    else
                                        val (fdResult, nextSeedV) =
                                            ConnectorPipe_Module
                                                .mkPipeFromIncrDescrWithSeed(v4Descr, seed)(using
                                                    SlotContext.forSlot(provider(idx))
                                                )
                                        val nextSeed = nextSeedV.toOption.getOrElse(seed)
                                        ConnectorPipe_Module.FullDescrResult
                                            .extractPipe(fdResult)
                                            .map                                           (pipe =>
                                                (
                                                    acc :+ ConnectorPipe_Module.foldPipeCanBe(pipe)  (
                                                        onWithout   = PipeSlot.noop(ConnectorPipeT, "Connector"),
                                                        onFullDescr = fd =>
                                                            tcThermal13384.mkSlot(
                                                                ConnectorPipeT,
                                                                "Connector",
                                                                FlueGas,
                                                                ConnectorPipe_Module.unwrap(fd)
                                                            )
                                                    ),
                                                    nextSeed
                                                )
                                            )
                                case ChimneySlot(_)         =>
                                    // Unreachable: the chimney is always terminal (after the last
                                    // FluePipeT), so it cannot appear inside the flue region.
                                    // Defensive: report as NotYetSupportedInFlueRegion.
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
                    // Seed UpstreamState density/velocity for the first head slot's
                    // en13384_pg (pressure-gain) calculation.
                    //
                    // Two seed paths (plan issue E1 — npipe-topology-connector-first):
                    //
                    //   (a) HEAD_REGION begins with FluePipe — legacy behaviour, preserved
                    //       byte-identically for the 6 golden `CasType_*` fixtures.
                    //       Rebuild the first FluePipe via the standalone single-flue-pipe
                    //       computation (`FlowOnlyMecaFlu_15544.makePipeResult` on the first
                    //       `FlueSlot` descriptor, no predecessor frame) and take its outlet
                    //       density/velocity.
                    //
                    //   (b) HEAD_REGION begins with a Connector (new grammar allows
                    //       Connector-first chains) — seed UpstreamState directly from
                    //       firebox exit conditions. `en15544.t_burnout` is already the
                    //       firebox exit gas temperature; `firebox_PipeResult` carries the
                    //       firebox outlet density/velocity at the requested computeAt
                    //       sampling point.
                    //
                    // The legacy seed cannot route through
                    // `last_known_density_before_connector_pipe` because that hook reads
                    // `conceptualFluePipeResult` (the N-pipe chain terminal), which would
                    // create a lazy-val cycle through `flueRegionPipeResults`.
                    val computeAt = en15544.en13384_application.computeAt

                    val headStartsWithConnector: Boolean =
                        pfbSlots.headOption.exists {
                            case ConnectorSlot(_)                 => true
                            case FlueSlot(_) | ThermalFlueSlot(_) => false
                            case ChimneySlot(_)                   => false
                            case NoFlueSlot                       => false
                        }

                    // Source `PipeResult` whose outlet density/velocity seeds the
                    // N-pipe chain's UpstreamState:
                    //   - Connector-first head → firebox outlet (path (b)).
                    //   - Flue-first head      → legacy single-flue-pipe recomputation
                    //                            on the first `FlueSlot` (path (a)).
                    val seedPipeResult: VNelMcalcErr[PipeResult] =
                        if headStartsWithConnector then firebox_PipeResult
                        else
                            import FluePipe_Module_15544.FullDescrResult.given
                            import FluePipe_Module_15544.toFullDescrWithSeed
                            val firstFlueSlotDescrOpt = pfbSlots.collectFirst { case FlueSlot(descr) => descr }
                            firstFlueSlotDescrOpt match
                                case None        =>
                                    Validated.invalidNel(
                                        UnexpectedDevError("No FlueSlot descriptor available to seed stage 1")
                                    )
                                case Some(descr) =>
                                    val flueResult = FluePipe_Module_15544.incremental
                                        .define(descr*)
                                        .toFullDescrWithSeed(seed)(using SlotContext.forSlotUnsafe(0))
                                    val fdResult: FluePipe_Module_15544.FullDescrResult =
                                        flueResult.map((ids, fd, _) => (ids, fd))
                                    FluePipe_Module_15544.FullDescrResult.extractPipe(fdResult) match
                                        case Validated.Valid(pipe)  =>
                                            ops_en15544.FlowOnlyMecaFlu_15544
                                                .makePipeResult                 (
                                                    fd                  = FluePipe_Module_15544.unwrap(pipe),
                                                    gas                 = FlueGas,
                                                    loadQty             = p._2,
                                                    z_geodetical_height = z_geodetical_height,
                                                    params              = p._1,
                                                    tempStartOverride   = None,
                                                    sc                  = SlotContext.forSlotUnsafe(0)
                                                )(using
                                                    en15544,
                                                    en15544.ssalg(SlotContext.forSlotUnsafe(0))
                                                )
                                                .toValidatedNel
                                        case Validated.Invalid(nel) => Validated.Invalid(nel)

                    val seedDensity : Option[Density]      = seedPipeResult.toOption.flatMap { pr =>
                        computeAt match
                            case ComputeAt.Mean   => pr.last_density_mean.orElse(pr.last_density_middle)
                            case ComputeAt.Middle => pr.last_density_middle
                    }
                    val seedVelocity: Option[FlowVelocity] = seedPipeResult.toOption.flatMap { pr =>
                        computeAt match
                            case ComputeAt.Mean   => pr.last_velocity_mean.orElse(pr.last_velocity_middle)
                            case ComputeAt.Middle => pr.last_velocity_middle
                    }
                    en15544.t_burnout.andThen: tBurnout =>
                        val initialUpstream = UpstreamState(
                            temp_start         = tBurnout,
                            last_pipe_density  = seedDensity,
                            last_pipe_velocity = seedVelocity
                        )
                        val folded          = slots.zipWithIndex.foldLeft[Either[
                            afpma.firecalc.engine.standard.MecaFlu_Error,
                            (UpstreamState, Vector[PipeResult])
                        ]](Right((initialUpstream, Vector.empty))) { case (acc, (slot, idx)) =>
                            acc.flatMap { case (upstream, results) =>
                                slot.compute(upstream, p, SlotIndexProvider.prefix(idx)).map { pr =>
                                    val nextUpstream = UpstreamState.fromPipeResult(pr, computeAt)
                                    (nextUpstream, results :+ pr)
                                }
                            }
                        }
                        folded match
                            case Right((_, results)) => Validated.validNel((results, lastSeed))
                            case Left(err)           => Validated.invalidNel(err)
                }

    end StrictAtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────

    lazy val atDraftMin_LoadNominal: AtParams         = new StrictAtParams(Params_15544.DraftMin_LoadNominal)
    lazy val atDraftMin_LoadMin    : Option[AtParams] = m_B_min.map(_ => new StrictAtParams(Params_15544.DraftMin_LoadMin))
    lazy val atDraftMax_LoadNominal: AtParams         = new StrictAtParams(Params_15544.DraftMax_LoadNominal)
    lazy val atDraftMax_LoadMin    : Option[AtParams] = m_B_min.map(_ => new StrictAtParams(Params_15544.DraftMax_LoadMin))

    def net_calorific_value_of_wet_wood(
        @nowarn("msg=unused") ncv_dry: HeatCapacity,
        @nowarn("msg=unused") hum    : Percentage,
        @nowarn("msg=unused") kind   : KindOfWood
    ): HeatCapacity =
        4.16.kWh_per_kg // See 4.2.1

    def net_calorific_value_of_dry_wood(@nowarn("msg=unused") kind: KindOfWood): HeatCapacity =
        18500.kJ_per_kg.toUnit[Kilo * Watt * Hour / Kilogram] // see 4.10.3
}
