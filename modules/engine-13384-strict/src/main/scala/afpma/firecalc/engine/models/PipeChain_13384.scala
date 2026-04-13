/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

// ---------------------------------------------------------------------------
// EN 13384: Thermal connector → Thermal chimney (no flue)
// ---------------------------------------------------------------------------
object PipeChain_13384:

    case class Descriptors(
        connector: Seq[ThermalPipeDescr_13384],
        chimney  : Seq[ThermalPipeDescr_13384]
    )

    case class Built(
        connectorPipeResult: ConnectorPipe_Module.FullDescrResult,
        chimneyPipeResult  : ChimneyPipe_Module.FullDescrResult,
        connectorFinalFrame: Option[PipeFrame]
    ):
        def connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe] =
            ConnectorPipe_Module.FullDescrResult.extractPipe(connectorPipeResult)
        def chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]   =
            ChimneyPipe_Module.FullDescrResult.extractPipe(chimneyPipeResult)

        def connectorPipeMappings = ConnectorPipe_Module.FullDescrResult.extractIdsMapping(connectorPipeResult)
        def chimneyPipeMappings   = ChimneyPipe_Module.FullDescrResult.extractIdsMapping(chimneyPipeResult)

    def build(d: Descriptors): Built =
        // Connector pipe → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(d.connector)
        val connectorFinalFrame                         = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame
        val chimneyPipeResult =
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorFinalFrame)

        Built(connectorPipeResult, chimneyPipeResult, connectorFinalFrame)

    /** Convert V4 YAML fields to descriptor slots for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeDescrSlot] =
        Vector(
            ConnectorSlot(d.connector),
            ChimneySlot  (d.chimney  )
        )

end PipeChain_13384
