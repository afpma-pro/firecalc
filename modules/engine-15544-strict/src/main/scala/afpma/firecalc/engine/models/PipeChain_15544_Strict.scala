/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

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
        def fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544] =
            FluePipe_Module_15544.FullDescrResult.extractPipe(fluePipeResult)
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  =
            ConnectorPipe_Module.FullDescrResult.extractPipe(connectorPipeResult)
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    =
            ChimneyPipe_Module.FullDescrResult.extractPipe(chimneyPipeResult)

        def fluePipeMappings      = FluePipe_Module_15544.FullDescrResult.extractIdsMapping(fluePipeResult)
        def connectorPipeMappings = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(connectorPipeResult)
        def chimneyPipeMappings   = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(chimneyPipeResult)

    def build(d: Descriptors): Built =
        // Flue pipe → capture final frame
        val (fluePipeResult, flueFinalFrameV) =
            FluePipe_Module_15544.mkPipeFromIncrDescrWithFinalFrame(d.flue)
        val flueFinalFrame                    = flueFinalFrameV.toOption.flatten

        // Connector pipe with flue's final frame → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(d.connector, flueFinalFrame)
        val connectorFinalFrame                         = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame, falling back to flue's frame
        val chimneyExternalFrame = connectorFinalFrame.orElse(flueFinalFrame)
        val chimneyPipeResult    =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, chimneyExternalFrame)

        Built(fluePipeResult, connectorPipeResult, chimneyPipeResult, flueFinalFrame, connectorFinalFrame)

    /** Convert V4 YAML fields to descriptor slots for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeDescrSlot] =
        Vector(
            FlueSlot     (d.flue     ),
            ConnectorSlot(d.connector),
            ChimneySlot  (d.chimney  )
        )

    /** Build from descriptor slots (validates types are in expected positions). */
    def fromSlots(slots: Vector[PostFireboxPipeDescrSlot]): Either[String, Descriptors] =
        slots match
            case Vector(FlueSlot(f), ConnectorSlot(c), ChimneySlot(ch)) =>
                Right(Descriptors(f, c, ch))
            case _                                                      =>
                Left(s"Expected [FlueSlot, ConnectorSlot, ChimneySlot], got ${slots.map(_.getClass.getSimpleName)}")

end PipeChain_15544_Strict
