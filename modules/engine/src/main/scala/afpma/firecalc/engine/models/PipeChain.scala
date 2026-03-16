/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

/** Pipe-chain builders: single source of truth for frame inheritance across flue → connector → chimney. */

// ---------------------------------------------------------------------------
// EN 15544 Strict: FlowOnly flue → Thermal connector → Thermal chimney
// ---------------------------------------------------------------------------
object PipeChain_15544_Strict:

    case class Descriptors(
        flue     : Seq[FluePipe_Module_15544.incremental.IncrDescr],
        connector: Seq[ConnectorPipe_Module.incremental.IncrDescr],
        chimney  : Seq[ChimneyPipe_Module.incremental.IncrDescr]
    )

    case class Built(
        fluePipeResult     : FluePipe_Module_15544.FullDescrResult,
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        flueFinalFrame     : Option[PipeFrame],
        connectorFinalFrame: Option[PipeFrame]
    ):
        import FluePipe_Module_15544.FullDescrResult.* // scalafix:ok
        import ConnectorPipe_Module.FullDescrResult.*  // scalafix:ok
        import ChimneyPipe_Module.FullDescrResult.*

        def fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544] = fluePipeResult.extractPipe
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  = connectorPipeResult.extractPipe
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    = chimneyPipeResult.extractPipe

        def fluePipeMappings      = fluePipeResult.extractIdsMapping
        def connectorPipeMappings = connectorPipeResult.extractIdsMapping
        def chimneyPipeMappings   = chimneyPipeResult.extractIdsMapping

    def build(d: Descriptors): Built =
        // Flue pipe → capture final frame
        val (fluePipeResult, flueFinalFrameV) =
            FluePipe_Module_15544.mkPipeFromIncrDescrWithFinalFrame(d.flue)
        val flueFinalFrame = flueFinalFrameV.toOption.flatten

        // Connector pipe with flue's final frame → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(d.connector, flueFinalFrame)
        val connectorFinalFrame = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame
        val chimneyPipeResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorFinalFrame)

        Built(fluePipeResult, connectorPipeResult, chimneyPipeResult, flueFinalFrame, connectorFinalFrame)

end PipeChain_15544_Strict

// ---------------------------------------------------------------------------
// EN 15544 MCE: Thermal flue → Thermal connector → Thermal chimney
// ---------------------------------------------------------------------------
object PipeChain_15544_MCE:

    case class Descriptors(
        flue     : Seq[FluePipe_Module_13384.incremental.IncrDescr],
        connector: Seq[ConnectorPipe_Module.incremental.IncrDescr],
        chimney  : Seq[ChimneyPipe_Module.incremental.IncrDescr]
    )

    case class Built(
        fluePipeResult     : FluePipe_Module_13384.FullDescrResult,
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        flueFinalFrame     : Option[PipeFrame],
        connectorFinalFrame: Option[PipeFrame]
    ):
        import FluePipe_Module_13384.FullDescrResult.*  // scalafix:ok
        import ConnectorPipe_Module.FullDescrResult.*   // scalafix:ok
        import ChimneyPipe_Module.FullDescrResult.*

        def fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_13384] = fluePipeResult.extractPipe
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  = connectorPipeResult.extractPipe
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    = chimneyPipeResult.extractPipe

        def fluePipeMappings      = fluePipeResult.extractIdsMapping
        def connectorPipeMappings = connectorPipeResult.extractIdsMapping
        def chimneyPipeMappings   = chimneyPipeResult.extractIdsMapping

    def build(d: Descriptors): Built =
        // Flue pipe → capture final frame
        val (fluePipeResult, flueFinalFrameV) =
            FluePipe_Module_13384.mkPipeFromIncrDescrWithFinalFrame(d.flue)
        val flueFinalFrame = flueFinalFrameV.toOption.flatten

        // Connector pipe with flue's final frame → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(d.connector, flueFinalFrame)
        val connectorFinalFrame = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame
        val chimneyPipeResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorFinalFrame)

        Built(fluePipeResult, connectorPipeResult, chimneyPipeResult, flueFinalFrame, connectorFinalFrame)

end PipeChain_15544_MCE

// ---------------------------------------------------------------------------
// EN 13384: Thermal connector → Thermal chimney (no flue)
// ---------------------------------------------------------------------------
object PipeChain_13384:

    case class Descriptors(
        connector: Seq[ConnectorPipe_Module.incremental.IncrDescr],
        chimney  : Seq[ChimneyPipe_Module.incremental.IncrDescr]
    )

    case class Built(
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        connectorFinalFrame: Option[PipeFrame]
    ):
        import ConnectorPipe_Module.FullDescrResult.*
        import ChimneyPipe_Module.FullDescrResult.*

        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe] = connectorPipeResult.extractPipe
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]   = chimneyPipeResult.extractPipe

        def connectorPipeMappings = connectorPipeResult.extractIdsMapping
        def chimneyPipeMappings   = chimneyPipeResult.extractIdsMapping

    def build(d: Descriptors): Built =
        // Connector pipe → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(d.connector)
        val connectorFinalFrame = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame
        val chimneyPipeResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorFinalFrame)

        Built(connectorPipeResult, chimneyPipeResult, connectorFinalFrame)

end PipeChain_13384
