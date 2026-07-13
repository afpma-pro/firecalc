/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.alg.SplitMerge90Validator.*
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.geometry.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.NonEmptyList
import cats.data.ValidatedNel
import cats.syntax.traverse.*

object SplitGeometryValidator:

    /**
     * Validates split geometry using real positions computed by PositionTracker.
     *
     * For each split/merge, derives `branchOneStartPosition` from the segment list:
     * - PositionTracker processes elements sequentially, creating segments only for section commands
     * - Split elements create SplitMergePosition but NO segments
     * - Therefore the first segment after a split has startPoint == split position
     * - If no segment follows the split (shouldn't happen — isForbiddenAddElementAtEnd prevents splits at pipe end),
     *   falls back to the split position itself
     */
    def validateSplitPositions(
        splits        : NonEmptyList[SplitMergePosition],
        positionResult: PipePositionResult,
        sectionTyp    : PipeType
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        // Segments are already sorted by elementIndex (PositionTracker invariant).
        // We can use linear scan via find instead of building a Map.
        val segments = positionResult.segments

        val validations = splits.toList.map { sm =>
            val branchOneStart = segments
                .find(_.elementIndex > sm.elementIndex)
                .map(_.startPoint)
                .getOrElse(sm.position)

            validateSplit     (
                incomingDirection      = sm.frame.direction,
                branchOneDirection     = Some(sm.branchOneDirection),
                splitPosition          = sm.position,
                branchOneStartPosition = branchOneStart,
                sectionTyp             = sectionTyp,
                elementRef             = s"#${sm.elementIndex}",
                isSplitElement         = true
            )
        }

        validations.sequence.map(_ => ())

end SplitGeometryValidator
