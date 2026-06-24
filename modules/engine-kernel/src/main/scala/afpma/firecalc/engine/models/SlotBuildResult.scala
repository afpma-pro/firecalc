/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

/**
 * Type-erased build result for a single post-firebox pipe slot.
 *
 * Each pipe module (FluePipe_Module_15544, ConnectorPipe_Module, etc.) has its own opaque IdsMapping type.
 * This case class erases those types so we can collect results from heterogeneous slots into a uniform vector.
 *
 * @param pipeType       the pipe's type tag (FluePipeT, ConnectorPipeT, ChimneyPipeT)
 * @param label          human-readable label for the slot (e.g. "Flue", "Connector", "Chimney")
 * @param pipe           the validated pipe model, type-erased to Any
 * @param idsMappingFn   maps UI element index (idIncr: Int) to section result index (Option[Int]),
 *                       wrapped in ValidatedNel to propagate incremental build errors
 * @param nextSeed       the immutable descriptor-build state to seed the next slot
 */
case class SlotBuildResult(
    pipeType    : PipeType,
    label       : String,
    pipe        : ValidatedNel[IncrementalValidation_Error, Any],
    idsMappingFn: ValidatedNel[IncrementalValidation_Error, Int => Option[Int]],
    nextSeed    : PipeBuildSeed
):
    def finalFrame : Option[PipeFrame] = nextSeed.frame
    def finalNFlows: NbOfFlows         = nextSeed.nFlows
