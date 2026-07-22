/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.ThermalPipeDescr_13384

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.SlotContext

import cats.data.ValidatedNel

// ---------------------------------------------------------------------------
// EN 15544 MCE: Thermal flue → Thermal connector → Thermal chimney
// ---------------------------------------------------------------------------
object PipeChain_15544_MCE:

    case class Descriptors(
        flue     : Seq[ThermalPipeDescr_13384],
        connector: Seq[ThermalPipeDescr_13384],
        chimney  : Seq[ThermalPipeDescr_13384]
    )

    case class Built(
        fluePipeResult     : FluePipe_Module_13384.FullDescrResult,
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        flueFinalFrame     : Option[PipeFrame],
        connectorFinalFrame: Option[PipeFrame]
    ):
        def fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_13384] =
            FluePipe_Module_13384.FullDescrResult.extractPipe(fluePipeResult)
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  =
            ConnectorPipe_Module.FullDescrResult.extractPipe(connectorPipeResult)
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    =
            ChimneyPipe_Module.FullDescrResult.extractPipe(chimneyPipeResult)

        def fluePipeMappings      = FluePipe_Module_13384.FullDescrResult.extractIdsMapping(fluePipeResult)
        def connectorPipeMappings = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(connectorPipeResult)
        def chimneyPipeMappings   = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(chimneyPipeResult)

    def build(d: Descriptors): Built =
        // Flue pipe → capture final frame
        // Fixed test position
        val initialSeed                 = PipeBuildSeed.default
        val (fluePipeResult, flueSeedV) =
            FluePipe_Module_13384.mkPipeFromIncrDescrWithSeed(d.flue, initialSeed)(using
                SlotContext.forSlotUnsafe(0)
            )
        val flueSeed = flueSeedV.getOrElse(initialSeed)
        val flueFinalFrame = flueSeed.frame

        // Connector pipe with flue's final frame → capture final frame
        // Fixed test position
        val (connectorPipeResult, connectorSeedV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithSeed(d.connector, flueSeed)(using
                SlotContext.forSlotUnsafe(1)
            )
        val connectorSeed = connectorSeedV.getOrElse(flueSeed)
        val connectorFinalFrame = connectorSeed.frame

        // Chimney pipe with connector's final frame, falling back to flue's frame
        // Fixed test position
        val chimneyPipeResult =
            ChimneyPipe_Module
                .mkPipeFromIncrDescr(d.chimney, connectorSeed)(using SlotContext.forSlotUnsafe(2))
                ._1

        Built(fluePipeResult, connectorPipeResult, chimneyPipeResult, flueFinalFrame, connectorFinalFrame)

    /** Convert MCE descriptors to engine-side descriptor slots for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeSlot] =
        Vector(
            ThermalFlueSlot(d.flue     ),
            ConnectorSlot  (d.connector),
            ChimneySlot    (d.chimney  )
        )
end PipeChain_15544_MCE
