/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.PipeFrame

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
        slots
            .foldLeft((Vector.empty[SlotBuildResult], Option.empty[PipeFrame])):
                case ((results, prevFrame), slot) =>
                    val result = buildSlot(slot, prevFrame)
                    (results :+ result, result.finalFrame.orElse(prevFrame))
            ._1

    private def buildSlot(
        slot     : PostFireboxPipeDescrSlot,
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        slot match
            case FlueSlot(descr)        => buildFlue15544(descr, prevFrame)
            case ThermalFlueSlot(descr) => buildThermalFlue(descr, prevFrame)
            case ConnectorSlot(descr)   => buildThermal(ConnectorPipeT, "Connector", descr, prevFrame)
            case ChimneySlot(descr)     => buildThermal(ChimneyPipeT, "Chimney", descr, prevFrame)

    // ── FlowOnly 15544 flue ─────────────────────────────────────────────

    private def buildFlue15544(
        descr    : Seq[FluePipe_Module_15544.incremental.IncrDescr],
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        import FluePipe_Module_15544.FullDescrResult.given
        import FluePipe_Module_15544.toFullDescrWithExternalInitialFrame
        val flueResult = FluePipe_Module_15544.incremental
            .define(descr*)
            .toFullDescrWithExternalInitialFrame(prevFrame)
        val fullDescrResult: FluePipe_Module_15544.FullDescrResult = flueResult.map((ids, fd, _) => (ids, fd))
        val pipe       = FluePipe_Module_15544.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV  = FluePipe_Module_15544.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn  = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val finalFrame = flueResult.map(_._3).toOption.flatten
        SlotBuildResult(FluePipeT, "Flue", pipe, mappingFn, finalFrame)

    // ── Thermal 13384 flue (MCE variant, accepts external frame) ────────

    private def buildThermalFlue(
        descr    : Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        val (fullDescrResult, finalFrameV) =
            FluePipe_Module_13384.mkPipeFromIncrDescrWithFinalFrame(descr, prevFrame)
        val pipe                           = FluePipe_Module_13384.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                      = FluePipe_Module_13384.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                      = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val finalFrame                     = finalFrameV.toOption.flatten
        SlotBuildResult(FluePipeT, "Flue", pipe, mappingFn, finalFrame)

    // ── Thermal 13384 (connector / chimney) ─────────────────────────────

    private def buildThermal(
        pipeType : PipeType,
        label    : String,
        descr    : Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        pipeType match
            case ConnectorPipeT => buildConnector(descr, prevFrame)
            case ChimneyPipeT   => buildChimney(descr, prevFrame)
            case other          =>
                throw new IllegalArgumentException(s"Unexpected thermal pipe type: $other")

    private def buildConnector(
        descr    : Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        val (fullDescrResult, finalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(descr, prevFrame)
        val pipe                           = ConnectorPipe_Module.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV                      = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn                      = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        val finalFrame                     = finalFrameV.toOption.flatten
        SlotBuildResult(ConnectorPipeT, "Connector", pipe, mappingFn, finalFrame)

    private def buildChimney(
        descr    : Seq[afpma.firecalc.dto.all.ThermalPipeDescr_13384],
        prevFrame: Option[PipeFrame]
    ): SlotBuildResult =
        // ChimneyPipe_Module doesn't have mkPipeFromIncrDescrWithFinalFrame,
        // but it does have mkPipeFromIncrDescr with externalInitialFrame.
        // We build and extract, but no final frame is needed (chimney is terminal).
        val fullDescrResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(descr, prevFrame)
        val pipe            = ChimneyPipe_Module.FullDescrResult.extractPipe(fullDescrResult)
        val mappingsV       = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(fullDescrResult)
        val mappingFn       = mappingsV.map(m => (i: Int) => m.getUnsafe(i).map(_.unwrap.unwrap))
        // No final frame for chimney (it's terminal in the chain)
        SlotBuildResult(ChimneyPipeT, "Chimney", pipe, mappingFn, None)

end PipeChainGeneric
