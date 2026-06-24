/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

// ---------------------------------------------------------------------------
// EN 15544 Strict: FlowOnly flue → Thermal connector → Thermal chimney
// ---------------------------------------------------------------------------
object PipeChain_15544_Strict:

    case class Descriptors(
        flue     : Seq[FlowOnlyPipeDescr_15544],
        connector: Seq[ThermalPipeDescr_13384],
        chimney  : Seq[ThermalPipeDescr_13384]
    )

    case class Built(
        fluePipeResult     : FluePipe_Module_15544.FullDescrResult,
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        flueFinalFrame     : Option[PipeFrame],
        connectorFinalFrame: Option[PipeFrame]
    ):
        def fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544] =
            FluePipe_Module_15544.FullDescrResult.extractPipe(fluePipeResult)
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  =
            ConnectorPipe_Module.FullDescrResult.extractPipe(connectorPipeResult)
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    =
            ChimneyPipe_Module.FullDescrResult.extractPipe(chimneyPipeResult)

        def fluePipeMappings      = FluePipe_Module_15544.FullDescrResult.extractIdsMapping(fluePipeResult)
        def connectorPipeMappings = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(connectorPipeResult)
        def chimneyPipeMappings   = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(chimneyPipeResult)

    def build(
        d               : Descriptors,
        flueInitialFrame: Option[PipeFrame] = None
    ): Built =
        import FluePipe_Module_15544.FullDescrResult.given
        import FluePipe_Module_15544.toFullDescrWithSeed
        // Flue pipe → capture final frame
        val initialSeed    = PipeBuildSeed.fromFrame(flueInitialFrame)
        val flueResult     = FluePipe_Module_15544.incremental
            .define(d.flue*)
            .toFullDescrWithSeed(initialSeed)
        val fluePipeResult: FluePipe_Module_15544.FullDescrResult = flueResult.map((ids, fd, _) => (ids, fd))
        val flueSeed       = flueResult.map(_._3).getOrElse(initialSeed)
        val flueFinalFrame = flueSeed.frame

        // Connector pipe with flue's final frame → capture final frame
        val (connectorPipeResult, connectorSeedV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithSeed(d.connector, flueSeed)
        val connectorSeed                         = connectorSeedV.getOrElse(flueSeed)
        val connectorFinalFrame                   = connectorSeed.frame

        // Chimney pipe with connector's final frame, falling back to flue's frame
        val chimneyPipeResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorSeed)._1

        Built(fluePipeResult, connectorPipeResult, chimneyPipeResult, flueFinalFrame, connectorFinalFrame)

    /** Convert V4 YAML fields to V7 descriptor slots (engine-side enum) for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeSlot] =
        Vector(
            FlueSlot     (d.flue     ),
            ConnectorSlot(d.connector),
            ChimneySlot  (d.chimney  )
        )
end PipeChain_15544_Strict
