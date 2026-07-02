/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import cats.Show

/** Errors that can occur when constructing a `SplitMergeTwoHelper`. */
sealed trait SplitMergeTwoHelperError

object SplitMergeTwoHelperError:

    /**
     * The incoming direction and first-branch direction are collinear.
     * The split plane is geometrically undefined — any plane containing
     * that line is a valid choice, so no unique split can be derived.
     */
    case object CollinearVectors extends SplitMergeTwoHelperError

    given Show[SplitMergeTwoHelperError] = Show.show:
        case CollinearVectors =>
            "SplitMergeTwoHelperError.CollinearVectors: incoming and first-branch directions are collinear — split plane is undefined"

/**
 * Geometry helper for symmetric flow splits.
 *
 * Given an incoming direction and a user-defined first-branch direction, this class
 * defines the split plane Π spanned by {incoming, firstBranch} and computes the
 * second-branch direction as the reflection of the first across the line of the
 * incoming direction.
 *
 * The incoming direction acts as the angle bisector between the two outgoing branches.
 *
 * All direction vectors are normalized at construction.
 *
 * @param incomingDirection direction of flow before the split (normalized)
 * @param branchOneDirection direction of the first outgoing branch, user-defined (normalized)
 * @param splitPosition 3D position of the split element tip
 */
case class SplitMergeTwoHelper(
    incomingDirection : Vec3,
    branchOneDirection: Vec3,
    splitPosition     : Vec3
):

    // ── derived geometry ──

    /** Unit normal to the split plane Π = span(incomingDirection, branchOneDirection). */
    val planeNormal: Vec3 = incomingDirection.cross(branchOneDirection).normalized

    /**
     * Orthonormal basis (u, v) spanning the split plane Π.
     *
     *   u = incomingDirection (already unit)
     *   v = planeNormal × u (unit, perpendicular to u, lies in Π)
     *
     * Any point in Π can be expressed as: splitPosition + α·u + β·v
     */
    val orthonormalBasis: (Vec3, Vec3) =
        val u = incomingDirection
        val v = planeNormal.cross(u).normalized
        (u, v)

    /**
     * Reflected branch direction, computed as the reflection of `branchOneDirection`
     * across the line spanned by `incomingDirection`.
     *
     * Formula: o₂ = 2(i·o₁)i - o₁  (Householder reflection, i is unit)
     *
     * See `docs/dev/SPLIT_FLOW_REFLECTION_MATH.md` for full derivation.
     */
    val reflectedBranchDirection: Vec3 =
        val cosAngle = incomingDirection.dot(branchOneDirection)
        (incomingDirection * (2.0 * cosAngle)) - branchOneDirection

    // ── plane operations ──

    /**
     * Orthogonally project a 3D point onto the split plane Π.
     *
     * Removes the component of `(point - splitPosition)` along `planeNormal`.
     *
     * @param point any 3D point
     * @return projection of `point` onto Π
     */
    def projectOntoPlane(point: Vec3): Vec3 =
        val offset    = point - splitPosition
        val distAlong = offset.dot(planeNormal)
        point - planeNormal * distAlong

    /**
     * Reflect a 3D point across the split plane Π.
     *
     * Mirrors `point` to the opposite side of Π at equal distance.
     * Used to compute the implicit second-branch tip from the first-branch tip.
     *
     * @param point any 3D point
     * @return reflection of `point` across Π
     */
    def reflectAcrossPlane(point: Vec3): Vec3 =
        val offset    = point - splitPosition
        val distAlong = offset.dot(planeNormal)
        point - planeNormal * (2.0 * distAlong)

    // ── auto-merge helpers ──

    /**
     * Compute the merge position for an auto-merge element.
     *
     * Given the tip of branch 1, projects it onto Π. Both branches converge
     * to this point with equal-length final segments. The merge lies on the
     * split plane but not necessarily on the incoming-direction axis.
     *
     * @param branchOneTip 3D tip position of the first branch as defined by the user
     * @return merge position on Π where both branches converge
     */
    def computeMergePosition(branchOneTip: Vec3): Vec3 =
        projectOntoPlane(branchOneTip)

    /**
     * Compute the tip position of the implicit second branch.
     *
     * Reflects the first branch tip across Π. If the tip lies in Π, the
     * result equals the input (both branches meet naturally).
     *
     * @param branchOneTip 3D tip position of the first branch as defined by the user
     * @return tip position of the symmetric reflected branch
     */
    def computeReflectedBranchTip(branchOneTip: Vec3): Vec3 =
        reflectAcrossPlane(branchOneTip)

object SplitMergeTwoHelper:

    private val CollinearityEpsilon = 1e-9

    /**
     * Construct a `SplitMergeTwoHelper` from the given directions and position.
     *
     * Directions are normalized at construction. If `incomingDirection` and
     * `branchOneDirection` are collinear (cross product near zero), the
     * split plane is undefined and `Left(CollinearVectors)` is returned.
     *
     * @param incomingDirection direction of flow before the split
     * @param branchOneDirection direction of the first outgoing branch
     * @param splitPosition 3D position of the split element tip
     * @return `Right(SplitMergeTwoHelper)` on success, `Left(CollinearVectors)` if directions are collinear
     */
    def safe(
        incomingDirection : Vec3,
        branchOneDirection: Vec3,
        splitPosition     : Vec3
    ): Either[SplitMergeTwoHelperError, SplitMergeTwoHelper] =
        val iNorm        = incomingDirection.normalized
        val o1Norm       = branchOneDirection.normalized
        val crossProduct = iNorm.cross(o1Norm)

        if crossProduct.norm < CollinearityEpsilon then Left(SplitMergeTwoHelperError.CollinearVectors)
        else Right(SplitMergeTwoHelper(iNorm, o1Norm, splitPosition))
