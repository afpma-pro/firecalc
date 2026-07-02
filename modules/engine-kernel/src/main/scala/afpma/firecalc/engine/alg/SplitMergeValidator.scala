/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.geometry.SplitMergeTwoHelper
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.SplitBranchesCollinear
import afpma.firecalc.engine.standard.SplitReflectedBranchAscends
import afpma.firecalc.units.Vec3
import cats.data.ValidatedNel
import cats.syntax.validated.*

/**
 * Validates split/merge geometry based on reflected branch direction.
 *
 * Rules:
 *  - **Split**: the reflected second branch must not ascend (z > 0 → forbidden).
 *    The incoming direction itself can be anything — only the reflected branch matters.
 *  - **Merge**: always allowed (any direction).
 *  - **Collinear** (no unique split plane): **forbidden** — not a real split.
 *  - **No branch direction** (absDir = None): **forbidden** — defaults to incoming direction (collinear).
 *
 * See `docs/dev/SPLIT_FLOW_REFLECTION_MATH.md` for the reflection derivation.
 */
object SplitMergeValidator:

    /**
     * Validate a flow split.
     *
     * @param incomingDirection direction of the pipe before the split (unit)
     * @param branchOneDirection direction of the first outgoing branch (unit), or None if collinear
     * @param sectionTyp pipe type for error messages
     * @param elementRef human-readable reference (e.g. "#42")
     * @return valid if the split geometry is acceptable, invalid otherwise
     */
    def validateSplit(
        incomingDirection : Vec3,
        branchOneDirection: Option[Vec3],
        sectionTyp        : PipeType,
        elementRef        : String
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        branchOneDirection match
            case None     =>
                // No branch direction — collinear with incoming, not a real split.
                SplitBranchesCollinear(sectionTyp, elementRef).invalidNel
            case Some(o1) =>
                SplitMergeTwoHelper.safe(incomingDirection, o1, Vec3(0, 0, 0)) match
                    case Left(_)       =>
                        // Collinear — straight continuation, not a real split.
                        SplitBranchesCollinear(sectionTyp, elementRef).invalidNel
                    case Right(helper) =>
                        if helper.reflectedBranchDirection.z > 0 then
                            SplitReflectedBranchAscends(sectionTyp, elementRef).invalidNel
                        else ().validNel

end SplitMergeValidator
