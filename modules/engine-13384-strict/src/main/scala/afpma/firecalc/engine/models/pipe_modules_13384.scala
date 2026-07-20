/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.IsZeroLengthPipeElement

import afpma.firecalc.engine.impl.en13384.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.FrameReplay
import afpma.firecalc.engine.ops.HasOutsideSurfaceInLocation
import afpma.firecalc.engine.ops.HasUnheatedHeightInsideAndOutside
import afpma.firecalc.engine.standard.{IncrementalValidation_Error, SlotContext}

import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

// type ThermalAirIntakePipe = ThermalAirIntakePipe_Module.PipeCanBe
trait ThermalAirIntakePipe_Module extends AirIntakePipe_Common_Module:
    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PT = AirIntakePipeT
    }
    type PipeElDescr0        = ThermalPipeDescr_13384.PipeElDescr
    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[AirIntakePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    // IncrDescr resolves to ThermalPipeDescr_13384 (V4) here; summon the existing V4 extractor.
    def airIntakeElemExtractors: FrameReplay.ElemExtractors[IncrDescr] =
        import afpma.firecalc.engine.models.geometry.PipeDescrExtractors.given
        summon[FrameReplay.ElemExtractors[IncrDescr]]

// type FlowOnlyAirIntakePipe = FlowOnlyAirIntakePipe_Module.PipeCanBe
trait FlowOnlyAirIntakePipe_Module extends AirIntakePipe_Common_Module:
    type _IncrementalBuilder = FlowOnlyIncrementalBuilder_13384 {
        type PT = AirIntakePipeT
    }
    type PipeElDescr0        = FlowOnlyPipeDescr_13384.PipeElDescr
    val incremental = afpma.firecalc.engine.impl.en13384.FlowOnlyIncrementalBuilder_13384.makeFor[AirIntakePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    // IncrDescr resolves to FlowOnlyPipeDescr_13384 (V4) here; summon the existing V4 extractor.
    def airIntakeElemExtractors: FrameReplay.ElemExtractors[IncrDescr] =
        import afpma.firecalc.engine.models.geometry.PipeDescrExtractors.given
        summon[FrameReplay.ElemExtractors[IncrDescr]]

type ConnectorPipe = ConnectorPipe_Module.PipeCanBe

/**
 * EN 13384 connector pipe module.
 *
 * Plan issue E4 (npipe-topology-connector-first): verified that a ConnectorPipe
 * with no upstream pipe composes correctly when driven via `PostFireboxPipeChain`.
 * The EN 13384 standalone entry point (`en13384_common_application.postFireboxChainResults`)
 * seeds the initial `UpstreamState` with `temp_start = T_WN / T_Wmin` (firebox exit)
 * and `last_pipe_density = last_pipe_velocity = None` — the thermal pipe calculation
 * only requires the start temperature; it does not read the last_pipe_* fields when
 * computing its own density/velocity. No "seed" alternative is needed here: the
 * existing path already feeds firebox exit directly as the connector inlet.
 */
object ConnectorPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ConnectorPipeT]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[ConnectorPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[ThermalPipeDescr_13384])(using sc: SlotContext): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, externalInitialFrame = None)(using sc)

    def mkPipeFromIncrDescr(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame]
    )(using sc: SlotContext): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, PipeBuildSeed(externalInitialFrame, NbOfFlows(1), None))(using sc)

    def mkPipeFromIncrDescr(
        incrSeq: Seq[ThermalPipeDescr_13384],
        seed   : PipeBuildSeed
    )(using sc: SlotContext): FullDescrResult =
        if (incrSeq.isEmpty) (IdsMapping.empty, Without).validNel[IncrementalValidation_Error]
        else
            incremental
                .define(incrSeq*)
                .toFullDescrWithSeed(seed)
                .map((ids, fd, _) => (ids, fd))

    def mkPipeFromIncrDescrWithFinalFrame(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame] = None
    )(using
        sc: SlotContext
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        val (fdResult, seedV) =
            mkPipeFromIncrDescrWithSeed(incrSeq, PipeBuildSeed(externalInitialFrame, NbOfFlows(1), None))(using
                sc
            )
        (fdResult, seedV.map(_.frame))

    def mkPipeFromIncrDescrWithSeed(
        incrSeq: Seq[ThermalPipeDescr_13384],
        seed   : PipeBuildSeed
    )(using sc: SlotContext): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, PipeBuildSeed]) =
        if (incrSeq.isEmpty) ((IdsMapping.empty, Without).validNel[IncrementalValidation_Error], seed.validNel)
        else
            val result = incremental.define(incrSeq*).toFullDescrWithSeed(seed)
            (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    type PipeCanBe = FullDescr | Without

    /** Safely fold over PipeCanBe without exposing abstract type matching */
    def foldPipeCanBe[A](pipe: PipeCanBe)(
        onWithout  : => A,
        onFullDescr: FullDescr => A
    ): A =
        pipe match
            case Without => onWithout
            case fd: FullDescr => onFullDescr(fd)

    case object Without
    type Without = Without.type
    val without: ConnectorPipe = Without

type ChimneyPipe = ChimneyPipe_Module.PipeCanBe
object ChimneyPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ChimneyPipeT]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[ChimneyPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[ThermalPipeDescr_13384])(using sc: SlotContext): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, externalInitialFrame = None)(using sc)

    def mkPipeFromIncrDescr(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame]
    )(using sc: SlotContext): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, PipeBuildSeed(externalInitialFrame, NbOfFlows(1), None))(using sc)._1

    def mkPipeFromIncrDescr(
        incrSeq: Seq[ThermalPipeDescr_13384],
        seed   : PipeBuildSeed
    )(using sc: SlotContext): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, PipeBuildSeed]) =
        val result = incremental.define(incrSeq*).toFullDescrWithSeed(seed)
        (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    /**
     * Inner cross-section at the chimney's terminal end.
     *
     * Builds via the shared incremental builder, so every shape-affecting
     * element type is folded exhaustively through `HasInnerShapeAtPos[PipeElDescr]`
     * (declared `compiletime.deferred` in `PipeDescrAlg` — new element types must
     * opt in or fail to compile). Honours `SetInnerShape`, nested batches, and
     * mid-pipe `SectionGeometryChange` elements.
     *
     * `externalInitialFrame` is the upstream slot's exit frame (typically the
     * connector's `finalFrame`). Chimneys do not declare their own initial
     * direction, so this argument is required for validation to succeed.
     */
    def lastInnerShape(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame]
    )(using sc: SlotContext): Option[PipeShape] =
        mkPipeFromIncrDescr(incrSeq, externalInitialFrame)(using sc).extractPipe.toOption
            .flatMap(_.lastInnerGeom)

    type PipeCanBe = FullDescr

    given HasOutsideSurfaceInLocation[ChimneyPipe]      :
        extension (ch: ChimneyPipe)
            def outsideSurfaceIn[PLoc <: PipeLocation](
                inLoc: PLoc
            ): QtyD[(Meter ^ 2)] =
                ch.elems
                    .map(_.el)
                    .map:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection =>
                            if (sec.pipeLoc == inLoc)
                                sec.outer_shape.area
                            else 0.m2
                        case _ => 0.m2
                    .map(_.toUnit[(Meter ^ 2)].value)
                    .sum
                    .m2
    given HasUnheatedHeightInsideAndOutside[ChimneyPipe]:
        extension (ch: ChimneyPipe)
            def unheatedHeightInsideAndOutside: UnheatedHeightInsideAndOutside =
                ch.elems
                    .map(_.el)
                    // keep only unheated locations
                    .filter:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection =>
                            sec.pipeLoc.areaHeatingStatus == AreaHeatingStatus.NotHeated
                        case _  : IsZeroLengthPipeElement                        =>
                            false
                    .map:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection => sec.elevation_gain
                        case _ =>
                            throw new IllegalStateException(
                                "dev error: only straight section expected because of previous filtering op."
                            )
                    .map: elev_gain =>
                        require(
                            elev_gain >= 0.meters,
                            s"unexpected negative vertical elevation found in chimney pipe : $ch"
                        )
                        val absValue_in_m = math.abs(elev_gain.toUnit[Meter].value)
                        absValue_in_m
                    .sum
                    .meters

    // extension (ch: ChimneyPipe)
    //     def extractAirGap: AirGap =
    //         // get air gaps for each section
    //         val airSpaceParts =
    //             ch.elems
    //                 .map(_.el)
    //                 .map:
    //                     case s: en13384.pipedescr.StraightSection   =>
    //                         Some(AirGap.AirGapPart(s.length, s.airSpace))
    //                     case _                      =>
    //                         None
    //                 .flatten
    //                 .toList
    //         AirGap.fromParts(airSpaceParts)

end ChimneyPipe_Module

// EN13384 — Air intake pipe modules (sealed hierarchy: AirIntakePipe_Module_Generic)

sealed trait AirIntakePipe_Module_Generic[Params0] extends AirIntakePipe_Common_Module:
    override final type Params = Params0

type AirIntakePipe_13384 = ThermalAirIntakePipe_13384 | FlowOnlyAirIntakePipe_13384

type FlowOnlyAirIntakePipe_13384 = FlowOnlyAirIntakePipe_Module_13384.PipeCanBe
object FlowOnlyAirIntakePipe_Module_13384
    extends FlowOnlyAirIntakePipe_Module
    with AirIntakePipe_Module_Generic[DraftCondition]

type ThermalAirIntakePipe_13384 = ThermalAirIntakePipe_Module_13384.PipeCanBe
object ThermalAirIntakePipe_Module_13384
    extends ThermalAirIntakePipe_Module
    with AirIntakePipe_Module_Generic[DraftCondition]
