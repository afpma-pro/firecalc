/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*
import afpma.firecalc.dto.v7.PostFireboxInitialDirection

import afpma.firecalc.engine.impl.en15544.common.PostFireboxFrameHelpers

import cats.data.Validated

/**
 * Builds a Vector[SlotBuildResult] from a sequence of PostFireboxPipeDescrSlot,
 * chaining each slot's final PipeFrame into the next slot's initial frame.
 *
 * This is the generic replacement for PipeChain_15544_Strict.build and
 * PipeChain_15544_MCE.build — it handles any slot topology.
 */
object PipeChainGeneric:

    /**
     * Build all pipe slots with frame chaining.
     *
     * @param slots the ordered post-firebox pipe descriptor slots
     * @return a vector of SlotBuildResult, one per slot, in the same order
     */
    def build(slots: Seq[PostFireboxPipeDescrSlot]): Vector[SlotBuildResult] =
        build(slots, initialDirection = None)

    /**
     * Build all pipe slots with frame chaining, seeding the first slot's
     * initial frame from the wrapper-level initial direction.
     *
     * After V7 migration, post-firebox pipes store initial direction at the
     * `PostFireboxPipes` wrapper level rather than inside descriptor sequences.
     * This overload converts the wrapper-level direction to a PipeFrame and
     * seeds the fold so the first pipe receives it as its external initial frame,
     * preventing spurious `GeometryWithoutInitialDirection` validation errors.
     *
     * @param slots the ordered post-firebox pipe descriptor slots
     * @param initialDirection wrapper-level initial direction (V7 PostFireboxPipes)
     * @return a vector of SlotBuildResult, one per slot, in the same order
     */
    def build(
        slots           : Seq[PostFireboxPipeDescrSlot],
        initialDirection: Option[PostFireboxInitialDirection]
    ): Vector[SlotBuildResult] =
        val initialSeed = PipeBuildSeed.fromFrame(initialDirection.map(PostFireboxFrameHelpers.toPipeFrame))
        slots
            .foldLeft((Vector.empty[SlotBuildResult], initialSeed)):
                case ((results, seed), slot) =>
                    val result = buildSlot(slot, seed)
                    (results :+ result, result.nextSeed)
            ._1

    private def buildSlot(
        slot: PostFireboxPipeDescrSlot,
        seed: PipeBuildSeed
    ): SlotBuildResult =
        slot match
            case FlueSlot(descr)        => buildFlue15544(descr, seed)
            case ThermalFlueSlot(descr) => buildThermalFlue(descr, seed)
            case ConnectorSlot(descr)   => buildThermal(ConnectorPipeT, "Connector", descr, seed)
            case ChimneySlot(descr)     => buildThermal(ChimneyPipeT, "Chimney", descr, seed)
            case NoFlueSlot             =>
                SlotBuildResult(
                    NoFluePipeT,
                    "NoFlue",
                    Validated.validNel(()              ),
                    Validated.validNel((_: Int) => None),
                    seed
                )

    // ── FlowOnly 15544 flue ─────────────────────────────────────────────

    private def buildFlue15544(
        descr: Seq[FluePipe_Module_15544.incremental.IncrDescr],
        seed : PipeBuildSeed
    ): SlotBuildResult =
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
    ): SlotBuildResult =
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
    ): SlotBuildResult =
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
    ): SlotBuildResult =
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
    ): SlotBuildResult =
        val (fullDescrResult, nextSeedV) =
            ChimneyPipe_Module.mkPipeFromIncrDescr(descr, seed)
        val pipe                         = ChimneyPipe_Module.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                    = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                    = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val nextSeed                     = nextSeedV.getOrElse(seed)
        SlotBuildResult(ChimneyPipeT, "Chimney", pipe, mappingFn, nextSeed)

end PipeChainGeneric
