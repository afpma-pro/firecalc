/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.SlotContext

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

    def build(
        d                    : Descriptors,
        connectorInitialFrame: Option[PipeFrame] = None
    ): Built =
        // Connector pipe → capture final frame
        val (connectorPipeResult, connectorFinalFrameV) =
            // Standalone build — no slot index
            ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(
                d.connector,
                externalInitialFrame = connectorInitialFrame
            )(using SlotContext.unslotted)
        val connectorFinalFrame                         = connectorFinalFrameV.toOption.flatten

        // Chimney pipe with connector's final frame
        val chimneyPipeResult =
            // Standalone build — no slot index
            ChimneyPipe_Module.mkPipeFromIncrDescr(d.chimney, connectorFinalFrame)(using SlotContext.unslotted)

        Built(connectorPipeResult, chimneyPipeResult, connectorFinalFrame)

    /** Convert V4 YAML fields to descriptor slots for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeDescrSlot_V7] =
        Vector(
            ConnectorSlot(d.connector),
            ChimneySlot  (d.chimney  )
        )

    // TODO(Phase4): EN 13384 strict has NO flue pipe (grammar: Connector + Chimney).
    // Under the `PostFireboxChain_V3` grammar this is an empty HEAD_REGION, which the
    // topology validator accepts (empty HEAD_REGION is legal everywhere). Phase 4
    // (physics seed — plan issue E4) must still decide whether to (a) promote the
    // connector to the head region, or (b) keep this builder strictly on the legacy
    // flat `toSlots` path.

end PipeChain_13384
