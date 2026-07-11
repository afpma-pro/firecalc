/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.geometry.SplitMergeTwoHelper
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.MergeBranchTipNotAtMergePosition
import afpma.firecalc.engine.standard.SplitBranchesCollinear
import afpma.firecalc.engine.standard.SplitBranchesNotOpposite
import afpma.firecalc.engine.standard.SplitReflectedBranchAscends
import afpma.firecalc.units.Vec3
import cats.data.ValidatedNel
import cats.syntax.validated.*

/**
 * Validates split/merge geometry for 90° turn elements.
 *
 * This validator is specific to `SplitSingleFlowIntoTwoFlowsWith90DegTurn` and
 * `MergeTwoFlowsIntoSingleWith90DegTurn`. Its rules are coupled to the loss
 * coefficient zeta=1.4, which is only valid when the two outgoing branches are
 * opposite (180° apart) and each at 90° from the incoming direction.
 *
 * Rules (checked in order):
 *  - **Not perpendicular** (incoming not at 90° to branchOne): **forbidden** —
 *    zeta=1.4 assumes each branch is 90° from incoming, which implies the two
 *    branches are opposite.
 *  - **Branches not opposite** (reflected branch not 180° from branchOne):
 *    **forbidden** — zeta=1.4 only valid for opposite branches.
 *  - **Ascending reflected branch**: **forbidden** — split not allowed.
 *  - **Collinear** (no unique split plane): **forbidden** — not a real split.
 *  - **No branch direction** (absDir = None): **forbidden** — defaults to
 *    incoming direction (collinear).
 *
 * For `MergeTwoFlowsIntoSingleWith90DegTurn`, the direction geometry is always
 * allowed (merges don't have the ascending constraint). The merge position is
 * validated separately via [[validateMergePosition]] in the post-build phase:
 * the branch tip must lie on the split plane defined by the paired split element.
 * See `docs/dev/SPLIT_FLOW_REFLECTION_MATH.md` for the reflection derivation.
 */
object SplitMerge90Validator:
    /**
     * Maximum allowable deviation of the dot product from zero for perpendicular branches.
     * |i · o₁| ≤ ε  ⟺  angle between i and o₁ is within ~0.00006° of 90°.
     */
    private val PerpendicularityEpsilon = 1e-6

    /**
     * Validate a flow split.
     *
     * @param incomingDirection direction of the pipe before the split (unit)
     * @param branchOneDirection direction of the first outgoing branch (unit), or None if collinear
     * @param sectionTyp pipe type for error messages
     * @param elementRef human-readable reference (e.g. "#42")
     * @param isSplitElement whether this is a physical split element (false for SetNumberOfFlows without split element)
     * @return valid if the split geometry is acceptable, invalid otherwise
     */
    def validateSplit(
        incomingDirection : Vec3,
        branchOneDirection: Option[Vec3],
        sectionTyp        : PipeType,
        elementRef        : String,
        isSplitElement    : Boolean = true
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        branchOneDirection match
            case None if !isSplitElement =>
                // SetNumberOfFlows without a split element — no geometry to validate.
                ().validNel
            case None                    =>
                // No branch direction — collinear with incoming, not a real split.
                SplitBranchesCollinear(sectionTyp, elementRef).invalidNel
            case Some(o1)                =>
                SplitMergeTwoHelper.safe(incomingDirection, o1, Vec3(0, 0, 0), 0.0) match
                    case Left(_)       =>
                        // Collinear — straight continuation, not a real split.
                        SplitBranchesCollinear(sectionTyp, elementRef).invalidNel
                    case Right(helper) =>
                        // Perpendicularity: branchOne must be perpendicular to incoming (branches opposite).
                        // Uses helper's already-normalized vectors; no re-normalization needed.
                        val dotProduct = helper.incomingDirection.dot(helper.branchOneDirection)
                        if math.abs(dotProduct) > PerpendicularityEpsilon then
                            // Clamp to [-1, 1] to guard against floating-point drift past unit vectors.
                            val cosAngle       =
                                helper.branchOneDirection.dot(helper.reflectedBranchDirection).max(-1.0).min(1.0)
                            val branchAngleDeg = math.acos(cosAngle) * 180.0 / math.Pi
                            SplitBranchesNotOpposite(sectionTyp, elementRef, branchAngleDeg).invalidNel
                        // Ascending: the reflected second branch must not ascend (z > 0 → forbidden).
                        else if helper.reflectedBranchDirection.z > 0 then
                            SplitReflectedBranchAscends(sectionTyp, elementRef).invalidNel
                        else ().validNel

    /**
     * Validate that a merge element's branch tip is at the correct merge position.
     *
     * The merge position is the orthogonal projection of the branch tip onto the split plane.
     * If the branch tip is not at the merge position (within tolerance), the merge is invalid.
     *
     * @param incomingDirection direction of flow before the split (unit)
     * @param branchOneDirection direction of the first branch at split (unit)
     * @param splitPosition position where the split occurs
     * @param branchTipPosition position of the branch tip at merge time
     * @param sectionTyp pipe type for error messages
     * @param elementRef human-readable reference
     * @return valid if branch tip is at merge position (within tolerance), invalid otherwise
     */
    def validateMergePosition(
        incomingDirection: Vec3,
        splitPosition    : Vec3,
        branchTipPosition: Vec3,
        sectionTyp       : PipeType,
        elementRef       : String
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        val mergePos = SplitMergeTwoHelper.computeExpectedMergePosition(
            incomingDirection,
            splitPosition,
            branchTipPosition
        )
        val distance = (branchTipPosition - mergePos).norm
        // ~1 millimeter tolerance (1e-3 meters)
        if distance > 1e-3 then MergeBranchTipNotAtMergePosition(sectionTyp, elementRef, distance * 1000.0).invalidNel
        else ().validNel

end SplitMerge90Validator
