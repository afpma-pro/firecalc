/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.v6.ChimneySlot_V3
import afpma.firecalc.dto.v6.ConnectorSlot_V3
import afpma.firecalc.dto.v6.HeadSlot_V3
import afpma.firecalc.dto.v6.PostFireboxChain_V3
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*
import afpma.firecalc.dto.v6.ThermalFlueSlot_V3

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

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
        val (fluePipeResult, flueFinalFrameV) =
            FluePipe_Module_13384.mkPipeFromIncrDescrWithFinalFrame(d.flue)
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

    /** Convert MCE descriptors to descriptor slots for generic topology processing. */
    def toSlots(d: Descriptors): Vector[PostFireboxPipeDescrSlot] =
        Vector(
            ThermalFlueSlot(d.flue     ),
            ConnectorSlot  (d.connector),
            ChimneySlot    (d.chimney  )
        )

    /**
     * Convert MCE descriptors to the structured post-firebox chain (plan issue B2).
     *
     * MCE models the flue as a thermal (EN 13384) pipe, so the head uses
     * [[ThermalFlueSlot_V3]] rather than the flow-only [[afpma.firecalc.dto.v6.FlueSlot_V3]].
     * The legacy single-flue head is preserved byte-identically — non-golden MCE dev
     * fixtures already widen to multi-slot head via their own `toSlots` overrides.
     */
    def toChain(d: Descriptors): PostFireboxChain_V3 =
        PostFireboxChain_V3    (
            head     = Vector[HeadSlot_V3](ThermalFlueSlot_V3(d.flue)),
            terminal = ConnectorSlot_V3(d.connector),
            chimney  = ChimneySlot_V3(d.chimney)
        )

end PipeChain_15544_MCE
