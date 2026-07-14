/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry
import afpma.firecalc.units.Vec3

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N

import cats.Show

import afpma.firecalc.domain.AzimuthDirection

/**
 * Configures how the symmetry plane Π is defined.
 *
 * The symmetry plane always contains `incomingDirection`. Its second axis
 * depends on the config case:
 *
 * | Case               | Plane contains                    | Normal =                     |
 * |--------------------|-----------------------------------|------------------------------|
 * | `NonVertical`      | `{incoming, Up}`                  | `incoming × Up` (normalized) |
 * | `VerticalIncoming` | `{Up, horizontalDir(azimuth)}`    | `Up × horizontalDir` (norm.) |
 *
 * `VerticalIncoming` stores the azimuth of the symmetry-plane direction
 * (clockwise from +Y/Rear): 0°=Rear, 90°=Right, 180°=Front, 270°=Left.
 * The internal rotation (counterclockwise from +X) is derived as `azimuth - 90°`.
 */
enum SymmetryPlaneConfig:
    /**
     * Incoming direction has a horizontal component.
     * Symmetry plane = span(incomingDirection, Up).
     * Normal = incomingDirection × Up (normalized).
     */
    case NonVertical

    /**
     * Incoming direction is vertical (Up or Down).
     * Symmetry plane = span(Up, horizontal direction at given azimuth).
     * Normal = Up × horizontalDir(azimuth) (normalized).
     *
     * @param azimuthDirection azimuth of the symmetry-plane direction
     *   (0°=Rear, 90°=Right, 180°=Front, 270°=Left)
     */
    case VerticalIncoming(azimuthDirection: AzimuthDirection)

    /** Threshold for detecting vertical incoming direction. */

object SymmetryPlaneConfig:
    /** Threshold for detecting vertical incoming direction. */
    private val VerticalEpsilon = 1e-6

    def fromIncoming(incoming: Vec3): SymmetryPlaneConfig =
        val crossNorm = incoming.cross(Vec3.Up).norm
        if crossNorm < VerticalEpsilon then VerticalIncoming(AzimuthDirection.Right)
        else NonVertical

    /**
     * Derive config from the incoming flow direction and an optional symmetry-plane azimuth.
     *
     * When the incoming direction is vertical, the symmetry plane is defined by the plane
     * containing `Up` and the horizontal direction at the given `symmetryPlaneAzimuth`.
     * The azimuth is stored directly in `VerticalIncoming`.
     *
     * If `symmetryPlaneAzimuth` is `None` when the incoming direction is vertical,
     * returns `Left(SymmetryPlaneAzimuthRequired)`.
     *
     * For non-vertical incoming, the azimuth is ignored and `Right(NonVertical)` is returned.
     */
    def fromIncomingWithRotation(
        incoming            : Vec3,
        symmetryPlaneAzimuth: Option[AzimuthDirection]
    ): Either[SplitMergeTwoHelperError, SymmetryPlaneConfig] =
        val crossNorm = incoming.cross(Vec3.Up).norm
        if crossNorm < VerticalEpsilon then
            symmetryPlaneAzimuth match
                case Some(az) => Right(VerticalIncoming(az))
                case None     => Left(SplitMergeTwoHelperError.SymmetryPlaneAzimuthRequired)
        else Right(NonVertical)

    /** Compute the unit normal to the symmetry plane for the given config. */
    def normal(incomingDirection: Vec3, config: SymmetryPlaneConfig): Vec3 =
        config match
            case SymmetryPlaneConfig.NonVertical                        =>
                incomingDirection.cross(Vec3.Up).normalized
            case SymmetryPlaneConfig.VerticalIncoming(azimuthDirection) =>
                // rotation (counterclockwise from +X) = azimuth (clockwise from +Y) - 90°
                val rotationDeg   = AzimuthDirection.toDegrees(azimuthDirection) - 90.0
                val angleRad      = rotationDeg * math.Pi / 180.0
                val horizontalDir = Vec3(math.cos(angleRad), -math.sin(angleRad), 0.0)
                Vec3.Up.cross(horizontalDir).normalized

/** Errors that can occur when constructing a `SplitMergeTwoHelper`. */
sealed trait SplitMergeTwoHelperError

object SplitMergeTwoHelperError:

    /**
     * The incoming direction and first-branch direction are collinear.
     * The split plane is geometrically undefined — any plane containing
     * that line is a valid choice, so no unique split can be derived.
     */
    case object CollinearVectors extends SplitMergeTwoHelperError

    /**
     * The incoming direction is vertical but no symmetry-plane azimuth was provided.
     * The symmetry plane is undefined because there is no horizontal axis
     * to span with `Up`.
     */
    case object SymmetryPlaneAzimuthRequired extends SplitMergeTwoHelperError

    given Show[SplitMergeTwoHelperError] = Show.show:
        case CollinearVectors             =>
            "SplitMergeTwoHelperError.CollinearVectors: incoming and first-branch directions are collinear — split plane is undefined"
        case SymmetryPlaneAzimuthRequired =>
            "SplitMergeTwoHelperError.SymmetryPlaneAzimuthRequired: symmetryPlaneAzimuth is required when the incoming direction is vertical"

    given ShowUsingLocale[SplitMergeTwoHelperError] = Show.show:
        case CollinearVectors             =>
            I18N.split_merge.collinearVectors
        case SymmetryPlaneAzimuthRequired =>
            I18N.split_merge.azimuthRequired

/**
 * Geometry helper for symmetric flow splits.
 *
 * Given an incoming direction and a user-defined first-branch direction, this class
 * defines the symmetry plane Π. Within this plane, the second-branch direction is
 * the **plane reflection** of the first across Π.
 *
 * The symmetry plane Π always contains `incomingDirection`. Its full definition
 * depends on `symmetryPlaneConfig`:
 * - `NonVertical`: Π = span(incomingDirection, Up)
 * - `VerticalIncoming(azimuth)`: Π = span(Up, horizontalDir at azimuth)
 *
 * The `splitPosition` is the geometric anchor of the plane Π.  Branch start positions
 * are displaced from `splitPosition` along their respective directions by
 * `branchOneOffset`, so branches do not necessarily begin at the split position itself.
 *
 * All direction vectors are normalized at construction.
 *
 * @param incomingDirection direction of flow before the split (normalized)
 * @param branchOneDirection direction of the first outgoing branch, user-defined (normalized)
 * @param splitPosition 3D anchor position of the symmetry plane Π
 * @param branchOneOffset non-negative distance from `splitPosition` to each branch start along its direction
 * @param symmetryPlaneConfig how to define the symmetry plane when incoming is vertical
 */
case class SplitMergeTwoHelper private (
    incomingDirection  : Vec3,
    branchOneDirection : Vec3,
    splitPosition      : Vec3,
    branchOneOffset    : Double,
    symmetryPlaneConfig: SymmetryPlaneConfig
):

    require(branchOneOffset >= 0.0, "branchOneOffset must be non-negative")

    // ── derived geometry ──

    /** Unit normal to the symmetry plane Π, computed from `symmetryPlaneConfig`. */
    val symmetryPlaneNormal: Vec3 = SymmetryPlaneConfig.normal(incomingDirection, symmetryPlaneConfig)

    /**
     * Orthonormal basis (u, v) spanning the symmetry plane Π.
     *
     *   u = incomingDirection (already unit)
     *   v = symmetryPlaneNormal × u (unit, perpendicular to u, lies in Π)
     *
     * Any point in Π can be expressed as: splitPosition + α·u + β·v
     */
    val orthonormalBasis: (Vec3, Vec3) =
        val u = incomingDirection
        val v = symmetryPlaneNormal.cross(u).normalized
        (u, v)

    /**
     * Direction of the second branch, computed as the plane reflection of
     * `branchOneDirection` across the symmetry plane Π.
     *
     * Formula: o₂ = o₁ - 2(o₁ · n)n  where n = symmetryPlaneNormal (unit)
     *
     * See `docs/dev/SPLIT_FLOW_REFLECTION_MATH.md` for full derivation.
     */
    val branchTwoDirection: Vec3 =
        branchOneDirection - (symmetryPlaneNormal * (2.0 * branchOneDirection.dot(symmetryPlaneNormal)))

    /** Start position of branch one: displaced from splitPosition along branchOneDirection. */
    val branchOneStartPosition: Vec3 = splitPosition + (branchOneDirection * branchOneOffset)

    /** Start position of branch two: displaced from splitPosition along branchTwoDirection. */
    val branchTwoStartPosition: Vec3 = splitPosition + (branchTwoDirection * branchOneOffset)

    // ── plane operations ──

    /**
     * Orthogonally project a 3D point onto the symmetry plane Π.
     *
     * Removes the component of `(point - splitPosition)` along `symmetryPlaneNormal`.
     *
     * @param point any 3D point
     * @return projection of `point` onto Π
     */
    def projectOntoPlane(point: Vec3): Vec3 =
        val offset    = point - splitPosition
        val distAlong = offset.dot(symmetryPlaneNormal)
        point - symmetryPlaneNormal * distAlong

    /**
     * Reflect a 3D point across the symmetry plane Π.
     *
     * Mirrors `point` to the opposite side of Π at equal distance.
     * Used to compute the implicit second-branch tip from the first-branch tip.
     *
     * @param point any 3D point
     * @return reflection of `point` across Π
     */
    def reflectAcrossPlane(point: Vec3): Vec3 =
        val offset    = point - splitPosition
        val distAlong = offset.dot(symmetryPlaneNormal)
        point - symmetryPlaneNormal * (2.0 * distAlong)

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
    /** Compute the branch offset from a branch start position and split anchor. */
    def computeBranchOneOffset(branchOneStartPosition: Vec3, splitPosition: Vec3): Double =
        (branchOneStartPosition - splitPosition).norm

    private val CollinearityEpsilon = 1e-9

    /**
     * Construct a `SplitMergeTwoHelper` from the given directions, position, and branch one start position.
     *
     * Directions are normalized at construction. If `incomingDirection` and
     * `branchOneDirection` are collinear (cross product near zero), the
     * split plane is undefined and `Left(CollinearVectors)` is returned.
     *
     * `branchOneOffset` is derived as `(branchOneStartPosition - splitPosition).norm`.
     *
     * @param incomingDirection direction of flow before the split
     * @param branchOneDirection direction of the first outgoing branch
     * @param splitPosition 3D anchor position of the split plane Π
     * @param branchOneStartPosition 3D start position of branch one (offset from splitPosition along branchOneDirection)
     * @param symmetryPlaneConfig how to define the symmetry plane when incoming is vertical
     * @return `Right(SplitMergeTwoHelper)` on success, `Left(CollinearVectors)` if directions are collinear
     */
    def safe(
        incomingDirection     : Vec3,
        branchOneDirection    : Vec3,
        splitPosition         : Vec3,
        branchOneStartPosition: Vec3,
        symmetryPlaneConfig   : SymmetryPlaneConfig
    ): Either[SplitMergeTwoHelperError, SplitMergeTwoHelper] =
        val iNorm           = incomingDirection.normalized
        val o1Norm          = branchOneDirection.normalized
        val branchOneOffset = computeBranchOneOffset(branchOneStartPosition, splitPosition)
        val crossProduct    = iNorm.cross(o1Norm)

        if crossProduct.norm < CollinearityEpsilon then Left(SplitMergeTwoHelperError.CollinearVectors)
        else Right(SplitMergeTwoHelper(iNorm, o1Norm, splitPosition, branchOneOffset, symmetryPlaneConfig))

    /**
     * Compute the expected merge position for merge-position validation.
     *
     * The symmetry plane Π is defined by `symmetryPlaneConfig`.
     * The expected merge position is the orthogonal projection of the branch tip
     * onto this plane — both branches should converge to a point on Π.
     *
     * @param incomingDirection  direction of flow before the split (unit)
     * @param splitPosition      position where the split occurs
     * @param branchTipPosition  position of the branch tip at merge time
     * @param symmetryPlaneConfig how to define the symmetry plane when incoming is vertical
     * @return expected merge position (projection of branch tip onto the split plane Π)
     */
    def computeExpectedMergePosition(
        incomingDirection  : Vec3,
        splitPosition      : Vec3,
        branchTipPosition  : Vec3,
        symmetryPlaneConfig: SymmetryPlaneConfig
    ): Vec3 =
        val symmetryPlaneNormal = SymmetryPlaneConfig.normal(incomingDirection, symmetryPlaneConfig)
        val offset              = branchTipPosition - splitPosition
        val distAlong           = offset.dot(symmetryPlaneNormal)
        branchTipPosition - symmetryPlaneNormal * distAlong
