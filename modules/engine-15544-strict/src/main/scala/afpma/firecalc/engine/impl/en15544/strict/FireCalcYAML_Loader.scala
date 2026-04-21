/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.impl.en15544.strict.{*, given}
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.SingleTested
import afpma.firecalc.engine.standard.*

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel

import scala.util.*

case class FireCalcYAML_Loader(fcProj: FireCalcYAML):
    self =>

    // DO BETTER
    require(
        fcProj.standard_or_computation_method == StandardOrComputationMethod.EN_15544_2023,
        s"Only '${StandardOrComputationMethod.EN_15544_2023.reference}' is allowed for now."
    )

    val airIntakePipeResult: FlowOnlyAirIntakePipe_Module_13384.FullDescrResult =
        FlowOnlyAirIntakePipe_Module_13384.mkPipeFromIncrDescr(fcProj.air_intake_descr)

    val airIntakePipe: ValidatedNel[IncrementalValidation_Error, FlowOnlyAirIntakePipe_13384] =
        FlowOnlyAirIntakePipe_Module_13384.extractPipe(airIntakePipeResult)

    val airIntakePipeMappings: Validated[NonEmptyList[
        IncrementalValidation_Error
    ], FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping] =
        FlowOnlyAirIntakePipe_Module_13384.extractIdsMapping(airIntakePipeResult)

    // ── Post-firebox topology ────────────────────────────────────────────
    // Slot-indexed build results from PipeChainGeneric. Used by the UI for
    // position tracking, per-slot IdsMapping, and final PipeFrame extraction.
    val slotBuildResults: Vector[SlotBuildResult] =
        PipeChainGeneric.build(fcProj.post_firebox_pipes)

    import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*
    import afpma.firecalc.engine.ops.generic.{PipeSlot, PostFireboxPipeChain}

    /**
     * Normalize post-firebox slots for the new grammar (plan issue Y1).
     *
     * Behaviour:
     *   - Empty head region is LEGAL under the grammar-level validator
     *     across all pipelines. This normaliser NEVER inserts a flue to
     *     plug an empty head.
     *   - If the YAML omits the terminal connector slot but the last
     *     pre-chimney slot is a Flue/ThermalFlue (i.e. the only issue is
     *     the missing terminal), insert an empty `ConnectorSlot(Seq.empty)`
     *     between it and the chimney. This preserves the backward-compat
     *     behaviour for V6 YAML files emitted before Phase 3.
     *   - Never insert or rearrange otherwise. Grammar violations
     *     (consecutive same-type in head, head ending with Connector,
     *     missing chimney, etc.) must surface through the topology
     *     validator — not be silently rewritten.
     */
    private def normalizePostFireboxSlots(
        slots: Seq[afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot]
    ): Seq[afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot] =
        if slots.isEmpty then slots
        else if slots.last match { case ChimneySlot(_) => false; case _ => true } then
            // No chimney as the last slot — leave the grammar validator to report
            // `MissingChimney`. Any insertion here would paper over the error.
            slots
        else
            val lastIdx    = slots.size - 1
            val preChimney = slots.take(lastIdx) // everything before the chimney
            preChimney.lastOption match
                case Some(FlueSlot(_)) | Some(ThermalFlueSlot(_)) =>
                    // Head ends with a Flue and chimney follows directly → the
                    // YAML simply omitted the terminal connector. Insert empty.
                    preChimney ++ Seq(ConnectorSlot(Seq.empty)) ++ Seq(slots.last)
                case _                                            =>
                    // Either the terminal slot is already a ConnectorSlot
                    // (correctly shaped), or the pre-chimney is something else
                    // (e.g. empty, ends with connector, etc.) — surface via the
                    // topology validator.
                    slots

    private lazy val normalizedPostFireboxSlots = normalizePostFireboxSlots(fcProj.post_firebox_pipes)

    // TODO(Phase4/Phase5): Surface a `PostFireboxChain_V3` projection alongside
    // the flat `normalizedPostFireboxSlots` once downstream engine consumers
    // (15544 common/strict/mce application seed) and UI panels have migrated.
    // For now, builders `PipeChain_15544_{Strict,MCE}.toChain` exist for
    // engine-internal consumption; YAML → domain conversion continues to
    // publish the flat seq via `postFireboxPipeSlots` to avoid a Phase-5-scoped
    // UI rewrite leaking into Phase 3.

    // Topology grammar validation (permissive — errors are exposed, not thrown).
    // HEAD_REGION may be empty (legal across all pipelines).
    val topologyValidation
        : Validated[NonEmptyList[afpma.firecalc.engine.ops.generic.TopologyError], PostFireboxPipeChain] =
        PostFireboxPipeChain.validated(
            normalizedPostFireboxSlots.map { slot =>
                slot match
                    case FlueSlot(_)        => PipeSlot.noop(FluePipeT, "Flue")
                    case ThermalFlueSlot(_) => PipeSlot.noop(FluePipeT, "Flue")
                    case ConnectorSlot(_)   => PipeSlot.noop(ConnectorPipeT, "Connector")
                    case ChimneySlot(_)     => PipeSlot.noop(ChimneyPipeT, "Chimney")
            }.toVector
        )

    // EN15544 Strict

    private val fb: Firebox_15544 =
        import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
        summon[io.scalaland.chimney.Transformer[Firebox, Firebox_15544]].transform(fcProj.firebox)

    private def mkStrictAlg[F <: Firebox_15544](
        fb: F
    )(using
        cap: FireboxToCombustionAirPipe_15544_Strict[F],
        fbp: FireboxToFireboxPipe_15544_Strict[F]
    ): StoveProjectDescr_15544_Strict_Alg =
        new v0_2024_10_strict.Firebox_15544_Strict_Alg with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg:
            type FB = F
            val firebox                         = fb
            protected val toCombustionAirPipeTC = cap
            protected val toFireboxPipeTC       = fbp
            val language                        = fcProj.locale.language
            override val project                = fcProj.project_description
            val localConditions                 = fcProj.local_conditions
            val stoveParams                     = fcProj.stove_params
            val airIntakePipe                   = self.airIntakePipe
            override def postFireboxPipeSlots   = normalizedPostFireboxSlots

    val stoveProjectDescr_EN15544_Strict: StoveProjectDescr_15544_Strict_Alg =
        fb match
            case f: TraditionalFirebox     => mkStrictAlg(f)
            case f: AFPMA_PRSE             => mkStrictAlg(f)
            case f: Ecolabeled             => mkStrictAlg(f)
            case f: SingleTested           => mkStrictAlg(f)
            case f: Door15aFirebox_Catalog => mkStrictAlg(f)
            case f =>
                throw new IllegalStateException("Unknow firebox type")

    def make_en15544_Strict_Application: ValidatedNel[MCalc_Error, EN15544_Strict_Application] =
        stoveProjectDescr_EN15544_Strict.en15544_Alg

end FireCalcYAML_Loader
