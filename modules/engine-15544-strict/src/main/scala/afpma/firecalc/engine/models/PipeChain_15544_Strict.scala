/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.v6.ChimneySlot_V3
import afpma.firecalc.dto.v6.ConnectorSlot_V3
import afpma.firecalc.dto.v6.FlueSlot_V3
import afpma.firecalc.dto.v6.HeadSlot_V3
import afpma.firecalc.dto.v6.NoFlueSlot_V3
import afpma.firecalc.dto.v6.PostFireboxChain_V3
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.PipeFrame
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

    def build(d: Descriptors): Built =
        import FluePipe_Module_15544.FullDescrResult.given
        import FluePipe_Module_15544.toFullDescrWithExternalInitialFrame
        // Flue pipe → capture final frame
        val flueResult     = FluePipe_Module_15544.incremental
            .define(d.flue*)
            .toFullDescrWithExternalInitialFrame(None)
        val fluePipeResult: FluePipe_Module_15544.FullDescrResult = flueResult.map((ids, fd, _) => (ids, fd))
        val flueFinalFrame = flueResult.map(_._3).toOption.flatten

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

    /**
     * Convert descriptors to the structured post-firebox chain (plan issue B1).
     *
     * EN 15544 Strict's legacy descriptor shape has a single flue + terminal
     * connector + chimney, so the emitted chain has a single-slot HEAD_REGION
     * (`[FlueSlot_V3(d.flue)]`), the descriptor's `connector` as terminal
     * connector, and `chimney` as the chimney slot. This preserves byte-identical
     * semantics for all 6 golden `CasType_*` fixtures, which all use a
     * single-Flue head.
     */
    def toChain(d: Descriptors): PostFireboxChain_V3 =
        PostFireboxChain_V3    (
            head     =
                if d.flue.isEmpty then Vector[HeadSlot_V3](NoFlueSlot_V3)
                else Vector[HeadSlot_V3](FlueSlot_V3(d.flue)),
            terminal = ConnectorSlot_V3(d.connector),
            chimney  = ChimneySlot_V3(d.chimney)
        )

end PipeChain_15544_Strict
