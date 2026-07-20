/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.Vec3

import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

import afpma.firecalc.engine.models.FireboxSplitFrame
import afpma.firecalc.engine.models.PipePositionContext
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot

import cats.data.Validated
import afpma.firecalc.engine.standard.{SlotContext, SlotIndex}

/**
 * Builds a Vector[SlotBuildResult] from a sequence of PostFireboxPipeDescrSlot,
 * chaining each slot's final PipeFrame into the next slot's initial frame.
 *
 * This is the generic replacement for PipeChain_15544_Strict.build and
 * PipeChain_15544_MCE.build — it handles any slot topology.
 */
object PipeChainGeneric:

    def build(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        initialFrame: Option[PipeFrame] = None
    ): Vector[SlotBuildResult] =
        build(slots, initialFrame, None, None)

    def build(
        slots                    : Seq[PostFireboxPipeDescrSlot_V7],
        initialFrame             : Option[PipeFrame],
        startPoint               : Option[Vec3],
        slot0FireboxSplitPosition: Option[Vec3]
    ): Vector[SlotBuildResult] =
        val initialSeed = slots.headOption match
            case Some(firstSlot) =>
                val defaultSeed = PipeBuildSeed(
                    initialFrame,
                    NbOfFlows(1),
                    Some(PipePositionContext(startPoint, slot0FireboxSplitPosition))
                )
                FireboxSplitFrame.resolveInitialSeed(PostFireboxPipeSlot.fromDto(firstSlot), defaultSeed)
            case None            =>
                PipeBuildSeed(
                    initialFrame,
                    NbOfFlows(1),
                    Some(PipePositionContext(startPoint, slot0FireboxSplitPosition))
                )
        // Track whether any prior slot failed — downstream slots get upstreamFailure=true
        // so the UI can show ErrorsInOtherSectionType instead of spurious cascading errors.
        slots.zipWithIndex
            .foldLeft(((Vector.empty[SlotBuildResult], false), initialSeed)):
                case (((results, failedUpstream), seed), (slot, idx)) =>
                    val result         = buildSlot(slot, seed)(using SlotContext.fromOption(SlotIndex.from(idx)))
                    val resultWithFlag =
                        if failedUpstream then result.copy(upstreamFailure = true)
                        else result
                    val newFailed      = failedUpstream || result.pipe.isInvalid
                    ((results :+ resultWithFlag, newFailed), result.nextSeed)
            ._1
            ._1

    private def buildSlot(
        slot: PostFireboxPipeDescrSlot_V7,
        seed: PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        slot match
            case PostFireboxPipeDescrSlot_V7.FlueSlot(descr)        => buildFlue15544(descr, seed)
            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(descr) => buildThermalFlue(descr, seed)
            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(descr)   =>
                buildThermal(ConnectorPipeT, "Connector", descr, seed)
            case PostFireboxPipeDescrSlot_V7.ChimneySlot(descr)     =>
                buildThermal(ChimneyPipeT, "Chimney", descr, seed)
            case PostFireboxPipeDescrSlot_V7.NoFlueSlot             =>
                SlotBuildResult(
                    NoFluePipeT,
                    "NoFlue",
                    Validated.validNel(()              ),
                    Validated.validNel((_: Int) => None),
                    seed
                )

    // ── FlowOnly 15544 flue ─────────────────────────────────────────────

    private def buildFlue15544(
        descr: Seq[afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544],
        seed : PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        import FluePipe_Module_15544.FullDescrResult.given
        import FluePipe_Module_15544.toFullDescrWithSeed
        val flueResult = FluePipe_Module_15544.incremental
            .define(descr*)
            .toFullDescrWithSeed(seed)
        val fullDescrResult: FluePipe_Module_15544.FullDescrResult = flueResult.map((ids, fd, _) => (ids, fd))
        val pipe       = FluePipe_Module_15544.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV  = FluePipe_Module_15544.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn  = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val nextSeed   = flueResult.map(_._3).getOrElse(seed)
        SlotBuildResult(FluePipeT, "Flue", pipe, mappingFn, nextSeed)

    // ── Thermal 13384 flue (MCE variant, accepts external frame) ────────

    private def buildThermalFlue(
        descr: Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        seed : PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        val (fullDescrResult, nextSeedV) =
            FluePipe_Module_13384.mkPipeFromIncrDescrWithSeed(descr, seed)
        val pipe                         = FluePipe_Module_13384.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                    = FluePipe_Module_13384.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                    = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val nextSeed                     = nextSeedV.getOrElse(seed)
        SlotBuildResult(FluePipeT, "Flue", pipe, mappingFn, nextSeed)

    // ── Thermal 13384 (connector / chimney) ─────────────────────────────

    private def buildThermal(
        pipeType: PipeType,
        label   : String,
        descr   : Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        seed    : PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        pipeType match
            case ConnectorPipeT => buildConnector(descr, seed)
            case ChimneyPipeT   => buildChimney(descr, seed)
            case NoFluePipeT    =>
                SlotBuildResult(
                    NoFluePipeT,
                    "NoFlue",
                    Validated.validNel(()              ),
                    Validated.validNel((_: Int) => None),
                    seed
                )
            case other          =>
                throw new IllegalArgumentException(s"Unexpected thermal pipe type: $other")

    private def buildConnector(
        descr: Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        seed : PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        val (fullDescrResult, nextSeedV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithSeed(descr, seed)
        val pipe                         = ConnectorPipe_Module.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                    = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                    = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val nextSeed                     = nextSeedV.getOrElse(seed)
        SlotBuildResult(ConnectorPipeT, "Connector", pipe, mappingFn, nextSeed)

    private def buildChimney(
        descr: Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        seed : PipeBuildSeed
    )(using sc: SlotContext): SlotBuildResult =
        val (fullDescrResult, nextSeedV) =
            ChimneyPipe_Module.mkPipeFromIncrDescr(descr, seed)
        val pipe                         = ChimneyPipe_Module.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                    = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                    = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val nextSeed                     = nextSeedV.getOrElse(seed)
        SlotBuildResult(ChimneyPipeT, "Chimney", pipe, mappingFn, nextSeed)

end PipeChainGeneric
