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
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384.toFullDescr
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.SingleTested
import afpma.firecalc.engine.models.geometry.AirDistributionBox
import afpma.firecalc.engine.models.geometry.AirIntakePositionMode
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PipePositionComputer
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.geometry.PostFireboxStartPositionMode
import afpma.firecalc.engine.standard.*

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

import scala.util.*

case class FireCalcYAML_Loader(fcProj: FireCalcYAML):
    self =>

    // DO BETTER
    require(
        fcProj.standard_or_computation_method == StandardOrComputationMethod.EN_15544_2023,
        s"Only '${StandardOrComputationMethod.EN_15544_2023.reference}' is allowed for now."
    )

    val airIntakePipeResult: FlowOnlyAirIntakePipe_Module_13384.FullDescrResult =
        if (fcProj.air_intake_pipes.descr.isEmpty)
            (
                FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping.empty,
                FlowOnlyAirIntakePipe_Module_13384.noVentilationOpenings
            )
                .validNel[IncrementalValidation_Error]
        else
            FlowOnlyAirIntakePipe_Module_13384.incremental
                .fromFramedSequence(fcProj.air_intake_pipes)
                .define(fcProj.air_intake_pipes.descr*)
                .toFullDescr()

    val airIntakePipe: ValidatedNel[IncrementalValidation_Error, FlowOnlyAirIntakePipe_13384] =
        FlowOnlyAirIntakePipe_Module_13384.extractPipe(airIntakePipeResult)

    val airIntakePipeMappings: Validated[NonEmptyList[
        IncrementalValidation_Error
    ], FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping] =
        FlowOnlyAirIntakePipe_Module_13384.extractIdsMapping(airIntakePipeResult)

    // ── Post-firebox topology ────────────────────────────────────────────
    // Defensive sanitization: ensure V7 wrapper fields are the only source of truth
    // even when called with directly-constructed V7 values that bypass decode.
    private val cleanFramedPostFireboxPipes =
        afpma.firecalc.dto.v7.FramedPostFireboxPipes.sanitize(fcProj.post_firebox_pipes)

    // Slot-indexed build results from PipeChainGeneric. Used by the UI for
    // position tracking, per-slot IdsMapping, and final PipeFrame extraction.
    private val cleanInitialPipeFrame: PipeFrame =
        val dir = cleanFramedPostFireboxPipes.initialDirection
        PipeFrame.initial(
            afpma.firecalc.units.Vec3.fromAzimuthElevation(
                dir.azimuth.map(afpma.firecalc.dto.v4.AzimuthDirection.toDegrees).getOrElse(0.0            ),
                afpma.firecalc.dto.v4.InclinationDirection.toDegrees                       (dir.inclination)
            )
        )

    val slotBuildResults: Vector[SlotBuildResult] =
        PipeChainGeneric.build(cleanFramedPostFireboxPipes.slots, Some(cleanInitialPipeFrame))

    import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*
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
        slots: Seq[afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7]
    ): Seq[afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7] =
        if slots.isEmpty then slots
        else
            val slots0 = slots match
                case ConnectorSlot(_) +: ChimneySlot(_) +: Nil =>
                    PostFireboxPipeDescrSlot_V7.NoFlueSlot +: slots
                case _                                         => slots
            if slots0.last match { case ChimneySlot(_) => false; case _ => true } then slots0
            else
                val lastIdx    = slots0.size - 1
                val preChimney = slots0.take(lastIdx)
                preChimney.lastOption match
                    case Some(FlueSlot(_)) | Some(ThermalFlueSlot(_)) =>
                        preChimney ++ Seq(ConnectorSlot(Seq.empty)) ++ Seq(slots0.last)
                    case _                                            =>
                        slots0

    private lazy val normalizedPostFireboxSlots = normalizePostFireboxSlots(cleanFramedPostFireboxPipes.slots)

    // TODO(Phase4/Phase5): Surface a `PostFireboxChain_V3` projection alongside
    // the flat `normalizedPostFireboxSlots` once downstream engine consumers
    // (15544 common/strict/mce application seed) and UI panels have migrated.
    // YAML → domain conversion continues to publish the flat seq via
    // `postFireboxPipeSlots` to avoid a Phase-5-scoped UI rewrite leaking into
    // Phase 3.

    // Topology grammar validation (permissive — errors are exposed, not thrown).
    // HEAD_REGION may be empty (legal across all pipelines).
    val topologyValidation
        : Validated[NonEmptyList[afpma.firecalc.engine.ops.generic.TopologyError], PostFireboxPipeChain] =
        PostFireboxPipeChain.validated(
            normalizedPostFireboxSlots.map { slot =>
                slot match
                    case NoFlueSlot         => PipeSlot.noop(NoFluePipeT, "NoFlue")
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

    /** Resolve Auto/Manual post-firebox position to a concrete Position3D. */
    private def resolvePostFireboxPosition: Option[Position3D] =
        cleanFramedPostFireboxPipes.initialPosition match
            case PostFireboxStartPosition.Manual(pos) => Some(pos)
            case PostFireboxStartPosition.Auto        =>
                val firstShape = PipePositionComputer
                    .firstInnerShapeIn(cleanFramedPostFireboxPipes.slots)
                    .getOrElse(PipePositionComputer.DefaultPipeShape)

                PipePositionComputer.findFirstSplitDir(cleanFramedPostFireboxPipes.slots) match
                    case Some(absDir) =>
                        fb.dimensions.base match
                            case afpma.firecalc.engine.models.en15544.std.Dimensions.Base.Squared(width, depth) =>
                                Some(
                                    PipePositionComputer.computeBranchStartAfterSplit    (
                                        absDir     = absDir,
                                        boxXWidth  = width.value,
                                        boxYDepth  = depth.value,
                                        boxZBottom = 0.0,
                                        boxZHeight = fb.dimensions.height.value,
                                        innerShape = firstShape
                                    )
                                )
                    case None         =>
                        fb.dimensions.base match
                            case afpma.firecalc.engine.models.en15544.std.Dimensions.Base.Squared(width, depth) =>
                                Some(
                                    PipePositionComputer.computePostFireboxStart (
                                        direction  = cleanFramedPostFireboxPipes.initialDirection,
                                        boxXWidth  = width.value,
                                        boxYDepth  = depth.value,
                                        boxZBottom = 0.0,
                                        boxZHeight = fb.dimensions.height.value,
                                        innerShape = firstShape
                                    )
                                )

    /** Resolve Auto/Manual air intake position to a concrete Position3D. */
    private def resolveAirIntakePosition: Option[Position3D] =
        import afpma.firecalc.dto.v7.AirIntakePosition
        if fcProj.air_intake_pipes.descr.isEmpty then None
        else
            fcProj.air_intake_pipes.position match
                case AirIntakePosition.InitialManual(pos) => Some(pos)
                case AirIntakePosition.FinalManual(pos)   => Some(pos)
                case AirIntakePosition.InitialAuto        =>
                    fb.dimensions.base match
                        case afpma.firecalc.engine.models.en15544.std.Dimensions.Base.Squared(width, depth) =>
                            Some(
                                PipePositionComputer.computeAirIntakeFinalAuto     (
                                    descr      = fcProj.air_intake_pipes.descr,
                                    initialDir = fcProj.air_intake_pipes.initialDir,
                                    boxXWidth  = width.value,
                                    boxYDepth  = depth.value,
                                    boxZBottom = AirDistributionBox.Z_BOTTOM,
                                    boxZHeight = AirDistributionBox.Z_HEIGHT
                                )
                            )
                case AirIntakePosition.FinalAuto          =>
                    fb.dimensions.base match
                        case afpma.firecalc.engine.models.en15544.std.Dimensions.Base.Squared(width, depth) =>
                            Some(
                                PipePositionComputer.computeAirIntakeFinalAuto     (
                                    descr      = fcProj.air_intake_pipes.descr,
                                    initialDir = fcProj.air_intake_pipes.initialDir,
                                    boxXWidth  = width.value,
                                    boxYDepth  = depth.value,
                                    boxZBottom = AirDistributionBox.Z_BOTTOM,
                                    boxZHeight = AirDistributionBox.Z_HEIGHT
                                )
                            )

    private def mkStrictAlg[F <: Firebox_15544](
        fb: F
    )(using
        cap: FireboxToCombustionAirPipe_15544_Strict[F],
        fbp: FireboxToFireboxPipe_15544_Strict[F]
    ): StoveProjectDescr_15544_Strict_Alg =
        new v0_2024_10_strict.Firebox_15544_Strict_Alg with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg:
            type FB = F
            val firebox                              = fb
            protected val toCombustionAirPipeTC      = cap
            protected val toFireboxPipeTC            = fbp
            val language                             = fcProj.locale.language
            override val project                     = fcProj.project_description
            val localConditions                      = fcProj.local_conditions
            val stoveParams                          = fcProj.stove_params
            val airIntakePipe                        = self.airIntakePipe
            override def postFireboxPipeSlots        = PostFireboxPipeSlot.fromDto(normalizedPostFireboxSlots)
            override def postFireboxInitialDirection = Some(cleanFramedPostFireboxPipes.initialDirection)
            override def postFireboxPositionMode     =
                PostFireboxStartPositionMode.fromDto(cleanFramedPostFireboxPipes.initialPosition)
            override def postFireboxInitialPosition  = resolvePostFireboxPosition
            override def airIntakePositionMode       = AirIntakePositionMode.fromDto(fcProj.air_intake_pipes.position)
            override def airIntakeInitialPosition    = resolveAirIntakePosition
            override def airIntakeDescriptors        = fcProj.air_intake_pipes.descr

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
