/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.catsSyntaxValidatedId
import cats.syntax.all.toTraverseOps
import cats.syntax.all.catsSyntaxTuple3Semigroupal

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
// EN13384_1_A1_2019_Common_Application no longer imported — decoupled from engine-13384-strict
import afpma.firecalc.engine.alg.en15544
import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.StoveConstraintContext
import afpma.firecalc.engine.impl.en16510.EN16510_1_2022_Formulas
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok
import afpma.firecalc.engine.models.en16510.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.en13384.Pressures_13384.given
import afpma.firecalc.engine.ops.en13384.forThermal13384
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, PipeSlot, UpstreamState}
import afpma.firecalc.engine.standard.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import algebra.instances.all.given

import coulomb.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}
import coulomb.ops.algebra.all.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import scala.annotation.nowarn

// import standard.dsl.CalculationF.compute

object EN15544_V_2023_Common_Application:
    type Error   = EN15544_V_2023_Application_Alg.ErrorGen
    type VNel[X] = ValidatedNel[Error, X]

abstract class EN15544_V_2023_Common_Application
    extends en15544.EN15544_V_2023_Application_Alg
    with FireboxOps
    with EN15544_Common_Application_Formulas
    with EN15544_Common_HeatingAppliance
    with EN15544_Common_Constraints {
    en15544 =>

    // ─── EN13384ForApp: abstract type + mixin trait ──────────────────────

    // EN13384ForApp is left abstract (from the algebra trait).
    // Leaf modules (strict/MCE) provide a concrete type that extends
    // EN13384_1_A1_2019_Common_Application with this mixin.

    /**
     * Mixin trait providing EN15544-specific overrides for the EN13384 bridge.
     * Leaf modules create their concrete bridge class as:
     * {{{
     *   new EN13384_1_A1_2019_Common_Application(formulas)
     *       with EN13384_For_15544_Overrides { ... }
     * }}}
     */
    trait EN13384_For_15544_Overrides:
        self: EN13384_1_A1_2019_Application_Alg =>

        override type AirIntakePipe_Module_T = en15544.AirIntakePipe_Module_T
        override val AirIntakePipe_Module = en15544.AirIntakePipe_Module

        override type Pipes_13384 = en15544.Pipes_13384

        override type Inputs_13384 = en15544.Inputs_13384

        override lazy val inputs = en13384_inputs

        override lazy val last_known_density_before_connector_pipe: WithParams_13384[Option[Density]] =
            val pr = atParamsFor(summon[Params_13384]).conceptualFluePipeResult.toOption
            computeAt match
                case ComputeAt.Mean   => pr.flatMap(_.last_density_mean).orElse(pr.flatMap(_.last_density_middle))
                case ComputeAt.Middle => pr.flatMap(_.last_density_middle)

        override lazy val last_known_velocity_before_connector_pipe: WithParams_13384[Option[FlowVelocity]] =
            val pr = atParamsFor(summon[Params_13384]).conceptualFluePipeResult.toOption
            computeAt match
                case ComputeAt.Mean   => pr.flatMap(_.last_velocity_mean).orElse(pr.flatMap(_.last_velocity_middle))
                case ComputeAt.Middle => pr.flatMap(_.last_velocity_middle)

        /**
         * Forward 13384 connector/chimney reads through the 15544 N-pipe chain.
         *
         * The base class reads legacy `inputs.pipes.connector` / `.chimney` via a hardcoded
         * 2-slot `postFireboxChainResults`, which is blind to extra flue slots and seeds the
         * chain incorrectly. The 15544 `AtParams.connector_PipeResult` /
         * `chimney_PipeResult` already read the tagged N-pipe `postFireboxPipeResults`
         * with proper upstream-state threading from Stage 1.
         *
         * `def` (not `lazy val`) to defer evaluation and avoid a lazy-val cycle
         * between Stage 2 HA resolution and Stage 1 flue-region results.
         *
         * Error conversion: collapse the accumulated NEL into a single MecaFlu_Error via
         * `UnexpectedThrowable` carrying the first error's string form. Downstream
         * `.toValidatedNel.andThen(...)` in `en13384_common_application.scala` handles
         * the resulting `Either` structurally.
         */
        override def connector_PipeResult: PipeResultOp[WithParams_13384[PipeResultE]] =
            atParamsFor(summon[Params_13384]).connector_PipeResult.toEither.left.map { nel =>
                MecaFlu_Error.UnexpectedThrowable(
                    new RuntimeException(nel.head.toString),
                    sectionTyp = ConnectorPipeT
                )
            }

        override def chimney_PipeResult: PipeResultOp[WithParams_13384[PipeResultE]] =
            atParamsFor(summon[Params_13384]).chimney_PipeResult.toEither.left.map { nel =>
                MecaFlu_Error.UnexpectedThrowable(
                    new RuntimeException(nel.head.toString),
                    sectionTyp = ChimneyPipeT
                )
            }

        // 7.8.4
        // Températures moyennes pour le calcul de pression

        /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
        override def T_mB
            : (DraftCondition) ?=> Validated[NonEmptyList[EN13384_Error], CombustionAirMeanTemperature.Type] =
            // flow only pipes are necessarily considered non concentric
            // otherwise we would have to compute thermal variations
            val dt = DuctType.NonConcentricDuctsHighThermalResistance
            val tl: afpma.firecalc.engine.models.en13384.typedefs.T_L = T_L
            self.formulas.T_mB_calc(dt, tl).withSectionTyp(AirIntakePipeT)

        /** Lookup the pre-built AtParams instance matching the given EN13384 params */
        def atParamsFor(p: Params_13384): AtParams

    val formulas: EN15544_V_2023_Formulas_Alg

    // aliases
    // private val en15544_inputs = inputs
    final lazy val firebox: Firebox_15544 = inputs.design.firebox

    /** Type-safe constraints access via the resolver (dispatches to subtype instance). */
    protected lazy val fc: FireboxConstraints[firebox.Self] =
        FireboxConstraintsResolver.resolve(firebox)

    /** Build the stove-level constraint context. */
    lazy val stoveConstraintContext: StoveConstraintContext =
        StoveConstraintContext(t_n = t_n)

    /** Build the constraint context from sizing results. */
    lazy val constraintContext: ConstraintContext =
        ConstraintContext                           (
            m_B                            = m_B,
            O_BR                           = firebox_sizing.O_BR,
            FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = firebox_sizing.FLOOR_DEPTH_TO_WIDTH_MIN_RATIO,
            FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = firebox_sizing.FLOOR_DEPTH_TO_WIDTH_MAX_RATIO,
            A_BR_min                       = firebox_sizing.A_BR_min,
            A_BR_max                       = firebox_sizing.A_BR_max,
            A_BR                           = firebox_sizing.A_BR,
            H_BR_min                       = firebox_sizing.H_BR_min,
            H_BR                           = firebox_sizing.H_BR,
            n_min                          = n_min
        )

    given convertResistanceCoefficientToError: Conversion[PressureLossCoeff.Err, ErrorGen] =
        (err: PressureLossCoeff.Err) => err: ErrorGen
    given Conversion[EN13384_Error, ErrorGen] =
        (err: EN13384_Error) => err: ErrorGen

    extension [A](a     : A                      ) def validNelE: ValidatedNel[ErrorGen, A] = a.validNel[ErrorGen]
    extension [A](vnelsa: ValidatedNel[String, A])
        def validNelE(sectionTyp: PipeType): ValidatedNel[ErrorGen, A] =
            vnelsa.leftMap(nels => nels.map(EN15544_ErrorMessage.apply(_, sectionTyp)))

    // Section "1", "Scope"

    // Section "2", "Normative references"

    // TODO

    // Section "3", "Terms and definitions"

    // Global definitions
    export afpma.firecalc.engine.models.gtypedefs.{V_L as _, T_L as _, T_mB as _, *}
    export afpma.firecalc.engine.models.gtypedefs.given

    given en15544.type                = en15544
    given EN15544_V_2023_Formulas_Alg = formulas

    protected def computeAndRequireEqualityAtDraftMinDraftMax[Ctx, X](f: DraftCondition ?=> Ctx ?=> X)(
        isEqual: (X, X) => Boolean
    )(using ctx: Ctx): X =
        val pReq_min = DraftCondition.DraftMinOrPositivePressureMax
        val pReq_max = DraftCondition.DraftMaxOrPositivePressureMin
        val x1       = f(using pReq_min)(using ctx)
        val x2       = f(using pReq_max)(using ctx)
        require(isEqual(x1, x2), s"dev error: '${x1}' if min draft != '${x2}' if max draft !! Why ?")
        x1

    /**
     * Energy contained in wet wood with given humidity
     *
     * @param wet_wood_mass wet wood mass
     * @param hum wood moisture given as a fraction on dry wood
     * @return
     */
    protected def energy_in_wet_wood(wet_wood_mass: Mass): Energy =
        wet_wood_mass * formulas.net_calorific_value_of_wet_wood

    lazy val energy_in_nominal_load: Energy
    lazy val energy_in_minimal_load: Option[Energy]

    def en13384_Q_F_calc(energy_in_load: Energy): Power = energy_in_load / combustionDuration
    val combustionDuration: Duration

    /** puissance utile nominale (du foyer !) */
    lazy val en13384_Q_N: VNelMcalcErr[Power] =
        en13384_η_WN.map(en13384_η_WN => en13384_η_WN.asRatio * en13384_Q_F_calc(energy_in_nominal_load))

    /** puissance utile (du foyer !) la plus faible */
    lazy val en13384_Q_Nmin: Option[VNelMcalcErr[Power]] =
        energy_in_minimal_load.map: e_min_load =>
            en13384_η_WN.map: en13384_η_WN =>
                en13384_η_WN.asRatio * en13384_Q_F_calc(e_min_load)

    /** rendement de l'appareil à combustion à puissance utile nominale */
    lazy val en13384_η_WN: VNelMcalcErr[Percentage]

    /** rendement de l'appareil à combustion à puissance utile la plus faible */
    lazy val en13384_η_Wmin: Option[VNelMcalcErr[Percentage]]

    lazy val en13384_heatingAppliance_efficiency: VNelMcalcErr[HeatingAppliance.Efficiency] =
        en13384_η_WN.andThen: en13384_η_WN =>
            en13384_η_Wmin match
                case Some(en13384_η_Wmin) =>
                    en13384_η_Wmin.map: en13384_η_Wmin =>
                        HeatingAppliance.Efficiency(
                            perc_nominal = en13384_η_WN,
                            perc_lowest  = en13384_η_Wmin.some
                        )
                case None                 =>
                    HeatingAppliance
                        .Efficiency(
                            perc_nominal = en13384_η_WN,
                            perc_lowest  = None
                        )
                        .validNel

    /** mass flows (if specified by constructor) */
    lazy val en13384_heatingAppliance_massFlows: HeatingAppliance.MassFlows

    lazy val en13384_heatingAppliance_powers: VNelMcalcErr[HeatingAppliance.Powers] =
        en13384_Q_N.andThen: en13384_Q_N =>
            en13384_Q_Nmin match
                case Some(en13384_Q_Nmin) =>
                    en13384_Q_Nmin.map: en13384_Q_Nmin =>
                        HeatingAppliance.Powers(
                            heat_output_nominal = en13384_Q_N,
                            heat_output_reduced = en13384_Q_Nmin.some
                        )
                case None                 =>
                    HeatingAppliance
                        .Powers(
                            heat_output_nominal = en13384_Q_N,
                            heat_output_reduced = None
                        )
                        .validNel

    /** flue gas temperatures (if specified by constructor) */
    lazy val en13384_heatingAppliance_temperatures: VNelMcalcErr[HeatingAppliance.Temperatures]

    final val en13384_T_L_override_default = T_L_override.forTCelsius(
        whenDraftMinOrDraftMax = formulas.t_outside_air_mean
    )

    lazy val en13384_T_L_override: T_L_override

    val en13384_inputs_nationalAcceptedData =
        NationalAcceptedData(
            T_uo_override = inputs.en13384NationalAcceptedData.T_uo_override,
            T_L_override  = en13384_T_L_override
        )

    /** this value will override `p_L_override` in EN13384 */
    lazy val en13384_p_L_override: Option[Pressure] = None

    // given HeatingAppliance.Efficiency    = en13384_heatingAppliance_efficiency
    given HeatingAppliance.FlueGas   = en13384_heatingAppliance_fluegas
    // given HeatingAppliance.Powers        = en13384_heatingAppliance_powers
    // given HeatingAppliance.Temperatures  = ??? // en13384_heatingAppliance_temperatures
    given HeatingAppliance.MassFlows = en13384_heatingAppliance_massFlows

    // TOFIX : multiple imports & instances of en13384 definitions

    given pipeWithGasFlowOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error]

    /** EN 13384 section-geometry-change friction coefficient factory — provided by leaf modules. */
    given dynFrict13384Factory
        : afpma.firecalc.engine.ops.en15544.FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory =
        import afpma.firecalc.engine.ops.en15544.FlowOnlyDynamicFrictionCoeff_15544.*
        new DynFrict13384Factory:
            def make(pt: PipeType): DynFrict13384Like =
                val delegate = afpma.firecalc.engine.ops.en13384.DynamicFrictionCoeff_13384()(using pt)
                new DynFrict13384Like:
                    def thermalSectionGeometryChange = delegate.thermalSectionGeometryChange

    given ssalg: afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg =
        afpma.firecalc.engine.ops.en15544.ShortSectionAlgFactory.make(using formulas, dynFrict13384Factory)

    // ─── CommonAtParams: params-dependent layer implementation ────────────

    /**
     * Abstract inner class implementing `AtParams` for the common application.
     * Subclasses (strict/MCE) must extend this to provide pipe result implementations.
     */
    abstract class CommonAtParams(override val params: Params_15544) extends AtParams:
        // Extract DraftCondition and LoadQty from params via intermediate vals
        // to avoid diverging implicit search through params_15544_to_13384 / params_13384_to_15544
        private val _dc: DraftCondition = params._1
        private val _lq: LoadQty        = params._2
        private given DraftCondition  = _dc
        @scala.annotation.nowarn("msg=unused private member")
        private given LoadQty         = _lq
        private given Option[LoadQty] = Some(_lq)

        // Pipe results — abstract (provided by strict/MCE subclasses)
        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult]
        lazy val firebox_PipeResult      : VNelMcalcErr[PipeResult]
        protected def flueRegionPipeResults: VNelMcalcErr[(Vector[PipeResult], PipeBuildSeed)]

        /**
         * Stage 2 of the two-stage split.
         *
         * Wraps Stage 1 (`flueRegionPipeResults`) then resolves HA givens
         * (`en13384_heatingAppliance_powers`/`_efficiency`/`_temperatures`) safely,
         * because `heatingAppliance_powers` no longer depends transitively on
         * `postFireboxPipeResults` — it bottoms out at `flueRegionPipeResults`, which does not
         * read HA givens.
         *
         * Builds connector + chimney slots with `tcThermal13384`, threading `UpstreamState` from
         * the LAST `PipeResult` of Stage 1 into the Stage 2 chain, and concatenates results.
         */
        lazy val postFireboxPipeResults: VNelMcalcErr[Vector[(PipeType, PipeResult)]] =
            import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot.*
            val pfbSlots = en15544.incrInputs.postFirebox.slots
            // Resolve Stage 1 first, then resolve HA givens for Stage 2.
            flueRegionPipeResults.andThen { case (stage1Results, stage1Seed) =>
                (
                    en15544.en13384_heatingAppliance_powers,
                    en15544.en13384_heatingAppliance_efficiency,
                    en15544.en13384_heatingAppliance_temperatures
                ).mapN((_, _, _))
                    .andThen: (ha_pow, ha_eff, _) =>
                        @nowarn("msg=unused local definition") given HeatingAppliance.Powers     = ha_pow
                        @nowarn("msg=unused local definition") given HeatingAppliance.Efficiency = ha_eff

                        val tcThermal13384 = CanComputePipeResult.forThermal13384(
                            en15544.en13384_application,
                            en15544.en13384_heatingAppliance_fluegas,
                            en15544.en13384_heatingAppliance_massFlows
                        )

                        // Use stage1Seed directly — no re-trace needed.
                        val lastFluePipeSlotIdx = pfbSlots.lastIndexWhere {
                            case FlueSlot(_) | ThermalFlueSlot(_) => true
                            case _                                => false
                        }
                        val stage2Slots         = pfbSlots.drop(lastFluePipeSlotIdx + 1)

                        // Build Stage 2 PipeSlots (connector + chimney; no FluePipeT allowed here).
                        // Thread prevFrame through foldLeft — no mutable state.
                        // Returns VNelMcalcErr so extraction errors propagate instead of being
                        // swallowed by PipeSlot.noop (which would cascade into downstream crashes).
                        val stage2SlotsV: VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)] =
                            stage2Slots.foldLeft[VNelMcalcErr[(Vector[PipeSlot], PipeBuildSeed)]](
                                Validated.validNel((Vector.empty[PipeSlot], stage1Seed))
                            ) { (accV, slot) =>
                                accV.andThen { case (acc, seed) =>
                                    slot match
                                        case FlueSlot(_)            =>
                                            // Defensive: FluePipeT after the last FluePipeT is impossible.
                                            Validated.validNel((acc :+ PipeSlot.noop(FluePipeT, "Flue"), seed))
                                        case ThermalFlueSlot(descr) =>
                                            val (fdResult, nextSeedV) =
                                                FluePipe_Module_13384
                                                    .mkPipeFromIncrDescrWithSeed(descr, seed)
                                            val nextSeed              = nextSeedV.toOption.getOrElse(seed)
                                            FluePipe_Module_13384.FullDescrResult
                                                .extractPipe(fdResult)
                                                .map(pipe =>
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
                                            if descr.isEmpty then
                                                Validated.validNel(
                                                    (acc :+ PipeSlot.noop(ConnectorPipeT, "Connector"), seed)
                                                )
                                            else
                                                val (fdResult, nextSeedV) =
                                                    ConnectorPipe_Module
                                                        .mkPipeFromIncrDescrWithSeed(descr, seed)
                                                val nextSeed              = nextSeedV.toOption.getOrElse(seed)
                                                ConnectorPipe_Module.FullDescrResult
                                                    .extractPipe(fdResult)
                                                    .map(pipe =>
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
                                        case ChimneySlot(descr)     =>
                                            val (fdResult, nextSeedV) = ChimneyPipe_Module
                                                .mkPipeFromIncrDescr(descr, seed)
                                            val nextSeed              = nextSeedV.toOption.getOrElse(seed)
                                            ChimneyPipe_Module.FullDescrResult
                                                .extractPipe(fdResult)
                                                .map(pipe =>
                                                    (
                                                        acc :+ tcThermal13384.mkSlot(
                                                            ChimneyPipeT,
                                                            "Chimney",
                                                            FlueGas,
                                                            ChimneyPipe_Module.unwrap(pipe)
                                                        ),
                                                        nextSeed
                                                    )
                                                )
                                        case NoFlueSlot             =>
                                            Validated.validNel((acc :+ PipeSlot.noop(NoFluePipeT, "NoFlue"), seed))
                                }
                            }

                        // Seed Stage 2 with the UpstreamState derived from the LAST Stage 1 result.
                        // When stage1Results is empty (no flue region), fall back to firebox_PipeResult.
                        val sourcePipeResult: VNelMcalcErr[PipeResult] =
                            if stage1Results.isEmpty then firebox_PipeResult
                            else Validated.validNel(stage1Results.last)
                        sourcePipeResult.andThen { seedPr =>
                            stage2SlotsV.andThen { case (stage2PipeSlots, _) =>
                                val computeAt             = en15544.en13384_application.computeAt
                                val stage2InitialUpstream =
                                    UpstreamState.fromPipeResult(seedPr, computeAt)
                                val folded                =
                                    stage2PipeSlots.foldLeft[Either[
                                        afpma.firecalc.engine.standard.MecaFlu_Error,
                                        (UpstreamState, Vector[PipeResult])
                                    ]](Right((stage2InitialUpstream, Vector.empty))) { case (acc, slot) =>
                                        acc.flatMap { case (upstream, results) =>
                                            slot.compute(upstream, params).map { pr =>
                                                val nextUpstream =
                                                    UpstreamState.fromPipeResult(pr, computeAt)
                                                (nextUpstream, results :+ pr)
                                            }
                                        }
                                    }
                                folded match
                                    case Right((_, stage2Results)) =>
                                        val flueRegionSlots = pfbSlots.take(lastFluePipeSlotIdx + 1).toVector
                                        val stage1Tagged    =
                                            flueRegionSlots.zip(stage1Results).map { (slot, pr) =>
                                                val pipeType: PipeType = slot match
                                                    case ConnectorSlot(_) => ConnectorPipeT
                                                    case _                => FluePipeT
                                                (pipeType, pr)
                                            }
                                        val stage2Tagged    =
                                            stage2PipeSlots.zip(stage2Results).map { (slot, pr) =>
                                                (slot.pipeType, pr)
                                            }
                                        Validated.validNel(stage1Tagged ++ stage2Tagged)
                                    case Left(err)                 => Validated.invalidNel(err)
                            }
                        }
            }

        // Pipe results — concrete (N-pipe-chain-aware, shared by strict and MCE)

        lazy val connector_PipeResult: VNelMcalcErr[PipeResult] =
            postFireboxPipeResults.andThen { pfb =>
                val candidateIdx = pfb.size - 2
                if candidateIdx >= 0 && candidateIdx < pfb.size then
                    val (pt, pr) = pfb(candidateIdx)
                    if pt == ConnectorPipeT then Validated.validNel(pr)
                    else
                        Validated.invalidNel(
                            UnexpectedDevError(
                                s"connector_PipeResult: slot before chimney is $pt, not ConnectorPipeT"
                            )
                        )
                else
                    Validated.invalidNel(
                        UnexpectedDevError("connector_PipeResult: no ConnectorPipeT slot in N-pipe chain")
                    )
            }

        lazy val chimney_PipeResult: VNelMcalcErr[PipeResult] =
            postFireboxPipeResults.andThen { pfb =>
                if pfb.isEmpty then
                    Validated.invalidNel(
                        UnexpectedDevError("chimney_PipeResult: N-pipe chain is empty")
                    )
                else
                    val (pt, pr) = pfb.last
                    if pt == ChimneyPipeT then Validated.validNel(pr)
                    else
                        Validated.invalidNel(
                            UnexpectedDevError(
                                s"chimney_PipeResult: last slot is $pt, not ChimneyPipeT"
                            )
                        )
            }

        /**
         * Chain-aware "conceptual" accessors for the flue region.
         * Bottom out at `flueRegionPipeResults` (HA-power-free Stage 1),
         * decoupling flue-region reads from Stage 2 HA resolution.
         */
        lazy val conceptualFluePipeResult: VNelMcalcErr[PipeResult] =
            flueRegionPipeResults.andThen { case (rs, _) =>
                if rs.isEmpty then firebox_PipeResult
                else Validated.validNel(rs.last)
            }

        lazy val conceptualFlueRegionPipeResults: VNelMcalcErr[Vector[PipeResult]] =
            flueRegionPipeResults.map(_._1)

        // Section "4.8.4", "Flue gas temperature in the connector pipe"
        lazy val t_connector_pipe_mean: VNelMcalcErr[t_connector_pipe_mean] =
            connector_PipeResult.map(_.gas_temp_mean: t_connector_pipe_mean)

        // Section "4.8.5", "Flue gas temperature at chimney entrance, mean flue gas
        // temperature of the chimney and temperature of the chimney wall at the top of the chimney"
        lazy val t_chimney_entrance: VNelMcalcErr[t_chimney_entrance] =
            chimney_PipeResult.map(_.gas_temp_start: t_chimney_entrance)

        lazy val t_chimney_mean: VNelMcalcErr[t_chimney_mean] =
            chimney_PipeResult.map(_.gas_temp_mean: t_chimney_mean)

        lazy val t_chimney_out: VNelMcalcErr[t_chimney_out] =
            chimney_PipeResult.map(_.gas_temp_end: t_chimney_out)

        lazy val t_chimney_wall_top: VNelMcalcErr[t_chimney_wall_top] =
            chimney_PipeResult.map(_.temperature_iob(_1_Λ_o = SquareMeterKelvinPerWatt(0.0)): t_chimney_wall_top)

        // Section "4.9.2", "Calculation of the standing pressure (p_h)"
        lazy val Σ_p_R_and_Σ_p_u: VNelMcalcErr[Pressure] =
            outputs.pipesResult_15544.accumulateErrors.andThen(_.`Σ_pR+Σ_pu`)

        lazy val Σ_p_h: VNelMcalcErr[Pressure] =
            outputs.pipesResult_15544.accumulateErrors.map(_.Σ_ph)

        // Section "4.10.1", "Pressure requirement"
        lazy val pressureRequirement_EN15544: VNelMcalcErr[PressureRequirement] =
            outputs.pipesResult_15544.accumulateErrors.andThen(pr =>
                (pr.`Σ_pR+Σ_pu`).map: `Σ_pR+Σ_pu` =>
                    PressureRequirement          (
                        sum_pr_pu           = `Σ_pR+Σ_pu`,
                        sum_ph              = pr.Σ_ph,
                        sum_ph_min_expected = `Σ_pR+Σ_pu`,
                        sum_ph_max_expected = 1.05 * `Σ_pR+Σ_pu`
                    )
            )

        // Section "4.10.3", "Efficiency of the combustion (η)"
        // TODO: mauvaise traduction allemande ?
        // TODO: Lors du calcul du rendement de la combustion, les hypothèses suivantes sont retenues : XXX
        lazy val η: VNelMcalcErr[η] =
            t_F.map(t_f => formulas.η_calc(t_f))

        /**
         * In the case of ceramic connector pipes (pottery and ceramic pipes)
         * it is the temperature that occurs in these up to 50 cm
         * between the outlet from the fireplace and the chimney pipe.
         */
        lazy val t_F: VNelMcalcErr[t_F] =
            postFireboxPipeResults.andThen { pfb =>
                val lastFluePipeIdx = pfb.lastIndexWhere(_._1 == FluePipeT)
                if lastFluePipeIdx < 0 then firebox_PipeResult.map(_.gas_temp_end                      : t_F)
                else Validated.validNel                           (pfb(lastFluePipeIdx)._2.gas_temp_end: t_F)
            }

        lazy val η_s: VNelMcalcErr[Percentage] =
            η.map(η =>
                EN16510_1_2022_Formulas.η_s(
                    η,
                    f2 =
                        CorrectionFactor_F2.ControleDeLaPuissanceThermiqueAUnPalier_PasDeControleDeLaTemperatureDeLaPiece,
                    f3 = CorrectionFactors_F3.noFactors,
                    f4 = CorrectionFactor_F4.NoAuxilaryElecConsumption
                )
            )

        // Section "4.10.4", "Flue gas triple of variates"
        // Routed via `conceptualFluePipeResult` so that strict / MCE bottom out at
        // `flueRegionPipeResults` (HA-power-free Stage 1), breaking the lazy-val
        // initialisation cycle between flue-region reads and HA resolution.
        private lazy val channel_pipe_last_element_temperature_end: VNelMcalcErr[TCelsius] =
            conceptualFluePipeResult.map(_.gas_temp_end)

        lazy val required_delivery_pressure: VNelMcalcErr[RequiredDeliveryPressure] =
            (
                combustionAir_PipeResult.andThen(_.`en13384_pr_all-ph`),
                firebox_PipeResult.andThen      (_.`en13384_pr_all-ph`),
                conceptualFlueRegionPipeResults.andThen: rs =>
                    rs.toList.traverse(_.`en13384_pr_all-ph`).map(_.foldLeft(0.0.pascals)(_ + _))
            )
                .mapN: (cci, cc, fp) =>
                    cci + cc + fp

        lazy val t_fluepipe_end: VNelMcalcErr[TCelsius] =
            channel_pipe_last_element_temperature_end

        lazy val flue_gas_triple_of_variates: VNelMcalcErr[FlueGasTripleOfVariates] =
            val mg_vnel: VNelMcalcErr[m_G] = m_G match
                case None       =>
                    Validated.invalidNel(
                        UnexpectedDevError("m_G could not be computed, LoadQty not defined / missing ?")
                    )
                case Some(vnel) => vnel
            (
                required_delivery_pressure,
                t_fluepipe_end,
                mg_vnel
            ).mapN: (rdp, t_fluepipe_end, mg) =>
                FlueGasTripleOfVariates(
                    rdp,
                    t_fluepipe_end,
                    mg
                )

        lazy val estimated_output_temperatures: EstimatedOutputTemperatures =
            EstimatedOutputTemperatures             (
                t_firebox              = t_BR,
                t_firebox_outlet       = t_burnout,
                t_stove_out            = t_fluepipe_end,
                t_chimney_out          = t_chimney_out,
                t_chimney_wall_top_out = t_chimney_wall_top
            )

        // Aggregated pipe results — concrete, uses the tagged postFirebox vector
        lazy val pipesResult_15544_VNelS: PipesResult_15544_VNelMcalcErr =
            PipesResult_15544_VNelMcalcErr(
                airIntake_PipeResult(using params),
                combustionAir_PipeResult,
                firebox_PipeResult,
                postFireboxPipeResults
            )
        lazy val outputs                : Outputs                        =
            models.en15544.std.Outputs(
                techSpecs,
                pipesResult_15544_VNelS,
                reference_temperatures,
                efficiencies_values
            )

        // Validations
        lazy val validateVelocitiesInFluePipe: VNelMcalcErr[Unit] =
            conceptualFlueRegionPipeResults.andThen: rs =>
                rs.toList.map(validateVelocitiesIn).sequence.map(_ => ())

        lazy val validateVelocitiesInConnectorPipe: VNelMcalcErr[Unit] =
            connector_PipeResult.andThen(validateVelocitiesIn)

        lazy val validateVelocitiesInChimneyPipe: VNelMcalcErr[Unit] =
            chimney_PipeResult.andThen(validateVelocitiesIn)

        lazy val validateVelocitiesInPipes: VNel[Unit] =
            List(
                validateVelocitiesInFluePipe,
                validateVelocitiesInConnectorPipe,
                validateVelocitiesInChimneyPipe
            ).sequence[[x] =>> VNelMcalcErr[x], Unit].map(_ => ())

        lazy val validatePressureRequirements_EN15544: VNelMcalcErr[Unit] =
            pressureRequirement_EN15544.andThen: preq =>
                preq.isInValidRange match
                    case true  => ().validNel
                    case false => InvalidPressureRequirement(preq).invalidNel

        lazy val validateChimneyWallTempIsAboveCondensationTemp: VNelMcalcErr[Unit] =
            estimated_output_temperatures.t_chimney_wall_top_out.andThen: t =>
                if (t >= formulas.t_chimney_wall_top_min)
                    ().validNel[MecaFlu_Error]
                else
                    MecaFlu_Error.InvalidChimneyWallTemperature(t).invalidNel

        lazy val validateEfficiencyIsAboveMinEfficiency: VNelMcalcErr[Unit] =
            η.andThen: eff =>
                emissions_and_efficiency_values.min_efficiency_full_stove_nominal.map:
                    case Some(min_eff) =>
                        if (eff.value >= min_eff.value)
                            ().validNel
                        else
                            EfficiencyIsTooLow(eff, min_eff).invalidNel
                    case None          =>
                        ().validNel

        override def validateSeasonalEfficiency(countryCode: Country): VNelMcalcErr[Unit] =
            η_s.andThen: seas_eff =>
                val lreg = LocalRegulations.findBy(countryCode, inputs.design.firebox.type_of_appliance)
                lreg.min_seasonal_efficiency match
                    case Some(min_seas_eff) =>
                        if (seas_eff.value >= min_seas_eff.value)
                            ().validNel
                        else
                            EfficiencyIsTooLow(seas_eff, min_seas_eff).invalidNel
                    case None               =>
                        ().validNel // no min defined, so we're good

        lazy val fluePipeLengthBelowMinimumWarning: Option[FluePipeLengthBelowMinimum] =
            (conceptualFlueRegionPipeResults.toOption, L_Z_min.toOption) match
                case (Some(rs), Some(lzMin)) =>
                    val totalLen = rs.foldLeft(0.0.m)(_ + _.lengthSum)
                    if totalLen.value >= lzMin.unwrap.value then None
                    else Some(FluePipeLengthBelowMinimum(totalLen, lzMin.unwrap))
                case _ => None

        lazy val validateCitedConstraints: VNelMcalcErr[Unit] =
            citedConstraints.checkFireboxConstraints

        lazy val validateFireboxSpecificConstraints: ValidatedNel[FireboxError, Unit] =
            val fbCtx = FireboxConstraintContext(
                mB                 = m_B,
                flow_rate          = V_L,
                airIntakePipeShape = {
                    val p = inputs.pipes
                    p.AirIntakePipe_Module.foldPipeCanBe(p.airIntake)(
                        onNoVentilation = None,
                        onFullDescr     = fd => fd.lastInnerGeom
                    )
                }
            )
            Validated
                .fromOption(
                    NonEmptyList.fromList(
                        fc.internal_firebox_custom_constraints(firebox) :::
                            fc.firebox_custom_constraints(firebox, fbCtx)
                    ),
                    ()
                )
                .swap
    end CommonAtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────
    // Abstract — subclasses (strict/MCE) must instantiate with their own
    // CommonAtParams subclass that provides pipe result implementations.

    lazy val atDraftMin_LoadNominal: AtParams
    lazy val atDraftMin_LoadMin    : Option[AtParams]
    lazy val atDraftMax_LoadNominal: AtParams
    lazy val atDraftMax_LoadMin    : Option[AtParams]

    def atParamsFor(p: Params_13384): AtParams = p match
        case Params_13384.DraftMin_LoadNominal => atDraftMin_LoadNominal
        case Params_13384.DraftMin_LoadMin     =>
            atDraftMin_LoadMin.getOrElse(
                throw IllegalStateException("No AtParams for DraftMin_LoadMin: m_B_min is not defined")
            )
        case Params_13384.DraftMax_LoadNominal => atDraftMax_LoadNominal
        case Params_13384.DraftMax_LoadMin     =>
            atDraftMax_LoadMin.getOrElse(
                throw IllegalStateException("No AtParams for DraftMax_LoadMin: m_B_min is not defined")
            )
        case other                             =>
            throw IllegalStateException(s"Unexpected Params_13384 value: $other")

    // Section "3.3"
    // TODO : add constraint on air gap (yes / no) depending on distance between inner & outer shell + more than 50% of surface built this way

    // Section "3.4"
    // TODO : add constraint on air gap (yes / no) depending on distance between inner & outer shell + more than 50% of surface built this way

    // Section "4", "Calculations"
    // Section "4.2", "Load of fuel" through Section "4.9.5" — see EN15544_Common_Application_Formulas
    export en15544_typedefs.*

    def validateFluePipeShape(): ValidatedNel[FluePipeInvalidGeometryRatio, Unit] =
        // Collect all PipeShape configurations from flue-region slots (FlueSlot + ThermalFlueSlot).
        // Both SetInnerShape prop instructions and AddSectionShapeChange elements can introduce a
        // Rectangle shape that must satisfy the 1:4 aspect-ratio constraint.
        val flueRegionShapes: Seq[PipeShape] =
            en15544.incrInputs.postFirebox.slots.flatMap:
                case PostFireboxPipeSlot.FlueSlot(descr)        =>
                    descr.collect:
                        case SetFlowOnlyPipeProp_15544.SetInnerShape(shape)                 => shape
                        case AddFlowOnlyPipeElement_15544.AddSectionShapeChange(_, toShape) => toShape
                case PostFireboxPipeSlot.ThermalFlueSlot(descr) =>
                    descr.collect:
                        case SetThermalPipeProp_13384.SetInnerShape(shape) => shape
                case _                                          => Seq.empty
        val checks =
            flueRegionShapes.zipWithIndex.map: (shape, idx) =>
                shape match
                    case rect @ PipeShape.Rectangle(_, _) =>
                        val (rmin, rmax) = (1.0, 4.0)
                        val ei = rect.validateRatioBetween(rmin, rmax)
                        Validated
                            .fromEither(ei)
                            .leftMap: ratio =>
                                NonEmptyList.one(
                                    FluePipeInvalidGeometryRatio(
                                        idx,
                                        FluePipeT,
                                        "SetInnerShape",
                                        ratio,
                                        rmin,
                                        rmax
                                    )
                                )
                            .map(_ => ())
                    case _                                =>
                        ().validNel[FluePipeInvalidGeometryRatio]
        checks.toList.sequence[[x] =>> ValidatedNel[FluePipeInvalidGeometryRatio, x], Unit].map(_ => ())

    private def outOfFlueGasVelocityRange(fvelocity: v): Boolean =
        (fvelocity < formulas.flueGasVelocityMin) | (fvelocity > formulas.flueGasVelocityMax)

    protected def validateVelocitiesIn(
        pipeResult: PipeResult
    ): ValidatedNel[FlueGasVelocityError, Unit] =
        pipeResult match
            case pr: PipeResult.WithSections    =>
                // Per-section aggregation: at most one error per section.
                // Start-only, end-only, and both-ends failures collapse into a single
                // FlueGasVelocityError whose `position` field tells the user which
                // boundary(ies) fell outside the admissible range.
                // Zero-length cross-section-change elements are skipped: their boundary
                // velocities duplicate the adjacent straight sections (already validated),
                // and under multi-flow (n_flows > 1) the redundant check can flag
                // spurious violations.
                pr.elements
                    .filterNot(_.isSectionGeometryChange)
                    .flatMap: psr =>
                        val startBad = outOfFlueGasVelocityRange(psr.v_start)
                        val endBad   = outOfFlueGasVelocityRange(psr.v_end)
                        def mkErr(pos: VelocityPosition, vs: Option[v], ve: Option[v]): FlueGasVelocityError =
                            FlueGasVelocityError    (
                                sectionId     = psr.section_id.unwrap,
                                sectionTyp    = psr.section_typ,
                                sectionName   = psr.section_name,
                                position      = pos,
                                startVelocity = vs,
                                endVelocity   = ve,
                                minVel        = formulas.flueGasVelocityMin,
                                maxVel        = formulas.flueGasVelocityMax
                            )
                        (startBad, endBad) match
                            case (false, false) => None
                            case (true, false ) => Some(mkErr(VelocityPosition.Start, Some(psr.v_start), None)          )
                            case (false, true ) => Some(mkErr(VelocityPosition.End, None, Some(psr.v_end))              )
                            case (true, true  ) => Some(mkErr(VelocityPosition.Both, Some(psr.v_start), Some(psr.v_end)))
                    .map(_.invalidNel[Unit])
                    .toList
                    .sequence[[x] =>> ValidatedNel[FlueGasVelocityError, x], Unit]
                    .map(_ => ())
            case _ : PipeResult.WithoutSections => ().validNel

    // Heating appliance — see EN15544_Common_HeatingAppliance

    // Constraints & validations — see EN15544_Common_Constraints

}
