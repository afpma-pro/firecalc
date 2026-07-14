/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.engine.models.geometry.SymmetryPlaneConfig
import afpma.firecalc.units.Vec3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

class SplitMergeTwoHelperSuite extends AnyFlatSpec with Matchers:

    val eps    = 1e-10
    val origin = Vec3(0, 0, 0)

    def assertApprox(a: Double, b: Double, label: String = ""): Unit =
        val diff = math.abs(a - b)
        withClue(s"$label (got $a, expected $b, diff=$diff)") {
            diff should be < eps
        }

    def assertVec3Approx(a: Vec3, b: Vec3, tolerance: Double = eps): Unit =
        assertApprox(a.x, b.x, s"x component")
        assertApprox(a.y, b.y, s"y component")
        assertApprox(a.z, b.z, s"z component")

    // ── Construction ───────────────────────────────────────────────────────────

    "SplitMergeTwoHelper.safe" should "succeed for non-collinear directions" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val result =
            SplitMergeTwoHelper.safe(
                i,
                o1,
                origin,
                origin,
                SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
            )
        result.isRight shouldBe true

        val plane = result.toOption.get
        assertVec3Approx(plane.incomingDirection, Vec3.Up   )
        assertVec3Approx(plane.branchOneDirection, Vec3.Rear)
    }

    it should "return CollinearVectors for parallel directions" in {
        val i  = Vec3.Up
        val o1 = Vec3.Up

        val result =
            SplitMergeTwoHelper.safe(
                i,
                o1,
                origin,
                origin,
                SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
            )
        result shouldBe Left(SplitMergeTwoHelperError.CollinearVectors)
    }

    it should "return CollinearVectors for anti-parallel directions" in {
        val i  = Vec3.Up
        val o1 = Vec3.Down

        val result =
            SplitMergeTwoHelper.safe(
                i,
                o1,
                origin,
                origin,
                SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
            )
        result shouldBe Left(SplitMergeTwoHelperError.CollinearVectors)
    }

    it should "normalize non-unit input directions" in {
        val i  = Vec3(0, 0, 3) // not unit
        val o1 = Vec3(0, 4, 0) // not unit

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.incomingDirection, Vec3.Up)
        assertVec3Approx(plane.branchOneDirection, Vec3.Rear)
    }

    // ── branchTwoDirection ─────────────────────────────────────────────────────

    "branchTwoDirection" should "reflect o1 across the symmetry plane (90° horizontal split)" in {
        // i = Up, o1 = Rear, rotation=0° → symmetry plane normal = Rear (XZ plane)
        // o2 = Rear - 2(Rear·Rear) × Rear = Rear - 2×Rear = -Rear = Front
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.branchTwoDirection, Vec3.Front)
    }

    it should "reflect o1 across the symmetry plane (45° ascending split)" in {
        // i = (0,1,1)/√2 (diagonal up-rear), o1 = Right, NonVertical
        // symmetryPlaneNormal = i × Up = (0,1,1)/√2 × (0,0,1) = (1,0,0) = Right
        // o2 = Right - 2(Right·Right) × Right = Right - 2×Right = -Right = Left
        val i  = Vec3(0, 1, 1).normalized
        val o1 = Vec3.Right

        val plane = SplitMergeTwoHelper.safe(i, o1, origin, origin, SymmetryPlaneConfig.NonVertical).toOption.get
        assertVec3Approx(plane.branchTwoDirection, Vec3.Left)
    }

    it should "satisfy the plane reflection property" in {
        // branchTwo = branchOne - 2(branchOne·n) × n
        val i  = Vec3(1, 0, 1).normalized
        val o1 = Vec3(0, 1, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, origin, origin, SymmetryPlaneConfig.NonVertical).toOption.get
        val n     = plane.symmetryPlaneNormal
        val o2    = plane.branchTwoDirection

        val expected = o1 - n * (2.0 * o1.dot(n))
        assertVec3Approx(o2, expected.normalized)
    }

    it should "always produce a unit-length direction" in {
        val i  = Vec3(0, 1, 1).normalized
        val o1 = Vec3.Right

        val plane = SplitMergeTwoHelper.safe(i, o1, origin, origin, SymmetryPlaneConfig.NonVertical).toOption.get
        assertApprox(plane.branchTwoDirection.norm, 1.0, "unit length")
    }

    // ── symmetryPlaneNormal ────────────────────────────────────────────────────

    "symmetryPlaneNormal" should "be computed from VerticalIncoming config" in {
        // i = Up, o1 = Rear, rotation=0° → horizontalDir = Right
        // normal = Up × Right = Rear
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val n     = plane.symmetryPlaneNormal

        assertApprox    (n.dot(plane.incomingDirection), 0.0, "perpendicular to i")
        assertApprox    (n.norm, 1.0, "unit length"                               )
        // Cardinal check: Up × Right = Rear
        assertVec3Approx(n, Vec3.Rear                                             )
    }

    // ── orthonormalBasis ───────────────────────────────────────────────────────

    "orthonormalBasis" should "return two orthonormal vectors spanning the plane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val (u, v) = plane.orthonormalBasis

        // u should be i (normalized)
        assertVec3Approx(u, plane.incomingDirection)

        // v should be perpendicular to u and to symmetryPlaneNormal
        assertApprox(u.dot(v), 0.0, "u perpendicular to v"              )
        assertApprox(plane.symmetryPlaneNormal.dot(v), 0.0, "v in plane")
        assertApprox(v.norm, 1.0, "v is unit"                           )
    }

    // ── projectOntoPlane ───────────────────────────────────────────────────────

    "projectOntoPlane" should "return the point itself when it lies in the plane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get

        // Symmetry plane is XZ (Y=0). A point in the plane has Y=0.
        val inPlane   = Vec3(1, 0, 1) // Right + Up
        val projected = plane.projectOntoPlane(inPlane)
        assertVec3Approx(projected, inPlane)
    }

    it should "remove the out-of-plane component" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get

        // Symmetry plane normal = Rear (Y=0 plane). Point with Y component is off the plane.
        val offPlane  = Vec3(1, 1, 1) // Right + Rear + Up
        val projected = plane.projectOntoPlane(offPlane)

        // Projected should have zero component along symmetryPlaneNormal (Rear)
        assertApprox(projected.dot(plane.symmetryPlaneNormal), 0.0, "no normal component")

        // Should be (1, 0, 1) — Right + Up (Y component removed)
        assertVec3Approx(projected, Vec3(1, 0, 1))
    }

    // ── reflectAcrossPlane ─────────────────────────────────────────────────────

    "reflectAcrossPlane" should "mirror a point across the split plane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get

        // Symmetry plane normal = Rear (XZ plane, Y=0)
        // Point off plane: (1, 1, 1) = Right + Rear + Up
        val q1 = Vec3(1, 1, 1)
        val q2 = plane.reflectAcrossPlane(q1)

        // q2 should be (1, -1, 1) — Y component negated (mirrored across XZ plane)
        assertVec3Approx(q2, Vec3(1, -1, 1))

        // Midpoint of q1 and q2 should lie in the plane
        val midpoint = Vec3(
            (q1.x + q2.x) / 2.0,
            (q1.y + q2.y) / 2.0,
            (q1.z + q2.z) / 2.0
        )
        assertApprox(midpoint.dot(plane.symmetryPlaneNormal), 0.0, "midpoint in plane")
    }

    it should "return the point itself when it lies in the plane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane     =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val inPlane   = Vec3(1, 0, 1) // in XZ plane (Y=0)
        val reflected = plane.reflectAcrossPlane(inPlane)
        assertVec3Approx(reflected, inPlane)
    }

    // ── computeMergePosition ───────────────────────────────────────────────────

    "computeMergePosition" should "equal projectOntoPlane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val tip   = Vec3(2, 3, 4)

        val merge     = plane.computeMergePosition(tip)
        val projected = plane.projectOntoPlane(tip)
        assertVec3Approx(merge, projected)
    }

    // ── computeSecondBranchTip ─────────────────────────────────────────────────

    "computeSecondBranchTip" should "equal reflectAcrossPlane" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val tip   = Vec3(2, 3, 4)

        val reflectedTip = plane.computeReflectedBranchTip(tip)
        val reflected    = plane.reflectAcrossPlane(tip)
        assertVec3Approx(reflectedTip, reflected)
    }

    // ── Integration: symmetric auto-merge ──────────────────────────────────────

    "auto-merge workflow" should "produce symmetric branches converging at merge" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get

        // Simulate branch 1 ending at some point off the plane
        val q1Tip = Vec3(0.5, 2.0, 3.0) // slightly right, rear, up

        val q2Tip    = plane.computeReflectedBranchTip(q1Tip)
        val mergePos = plane.computeMergePosition(q1Tip)

        // q1Tip and q2Tip should be symmetric (q2 = reflect(q1) across XZ plane)
        assertVec3Approx(q2Tip, Vec3(0.5, -2.0, 3.0))

        // mergePos should be the projection of q1Tip onto the XZ plane (Y=0)
        assertVec3Approx(mergePos, Vec3(0.5, 0, 3.0))

        // Distance from q1 to merge should equal distance from q2 to merge
        val dist1 = (q1Tip - mergePos).norm
        val dist2 = (q2Tip - mergePos).norm
        assertApprox(dist1, dist2, "equal branch lengths to merge")
    }

    // ── Edge: non-origin split position ────────────────────────────────────────

    "projectOntoPlane" should "work correctly when splitPosition is not origin" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(1, 2, 3) // split at non-origin

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, pos, pos, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get

        // Point directly above split position (in the plane)
        val inPlane   = Vec3(1, 2, 5) // pos + 2*Up
        val projected = plane.projectOntoPlane(inPlane)
        assertVec3Approx(projected, inPlane)

        // Point offset from plane
        val offPlane = Vec3(2, 2, 5) // inPlane + Right
        val proj     = plane.projectOntoPlane(offPlane)
        // Projection minus splitPos should lie in the plane
        assertApprox((proj - pos).dot(plane.symmetryPlaneNormal), 0.0, "offset in plane")
    }

    // ── Branch start positions ────────────────────────────────────────────────

    "branchOneStartPosition" should "equal splitPosition when offset is zero" in {
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.branchOneStartPosition, origin)
    }

    "branchOneStartPosition" should "be displaced along branchOneDirection" in {
        val i              = Vec3.Up
        val o1             = Vec3.Rear
        val branchOneStart = Vec3(0, 2.5, 0)

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, branchOneStart, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.branchOneStartPosition, Vec3(0, 2.5, 0))
    }

    "branchTwoStartPosition" should "be displaced along branchTwoDirection" in {
        val i              = Vec3.Up
        val o1             = Vec3.Rear
        val branchOneStart = Vec3(0, 2.5, 0)

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, branchOneStart, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.branchTwoStartPosition, Vec3(0, -2.5, 0))
    }

    "branchTwoStartPosition" should "be symmetric to branchOneStartPosition across the plane" in {
        val i              = Vec3.Up
        val o1             = Vec3.Rear
        val branchOneStart = Vec3(0, 2.5, 0)

        val plane    =
            SplitMergeTwoHelper
                .safe(i, o1, origin, branchOneStart, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        val midpoint = Vec3(
            (plane.branchOneStartPosition.x + plane.branchTwoStartPosition.x) / 2.0,
            (plane.branchOneStartPosition.y + plane.branchTwoStartPosition.y) / 2.0,
            (plane.branchOneStartPosition.z + plane.branchTwoStartPosition.z) / 2.0
        )
        assertApprox(midpoint.dot(plane.symmetryPlaneNormal), 0.0, "midpoint in plane")
    }

    // ── SymmetryPlaneConfig.VerticalIncoming with rotation ────────────────────

    "VerticalIncoming with rotation=90°" should "compute correct normal and branchTwo" in {
        // i = Up, o1 = Rear, rotation=90°
        // horizontalDir = (cos(90°), -sin(90°), 0) = (0, -1, 0) = Front
        // normal = Up × Front = (0,0,1) × (0,-1,0) = (1,0,0) = Right
        // branchTwo = Rear - 2(Rear·Right) × Right = Rear - 0 = Rear
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Front))
                .toOption
                .get
        assertVec3Approx(plane.symmetryPlaneNormal, Vec3.Right)
        assertVec3Approx(plane.branchTwoDirection, Vec3.Rear)
    }

    "VerticalIncoming with rotation=0°" should "give normal=Rear for firebox example" in {
        // Standard firebox: i = Up, o1 = Rear, rotation=0°
        // horizontalDir = Right, normal = Up × Right = Rear
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right))
                .toOption
                .get
        assertVec3Approx(plane.symmetryPlaneNormal, Vec3.Rear)
        assertVec3Approx(plane.branchTwoDirection, Vec3.Front)
    }

    "NonVertical" should "compute normal from incoming × Up" in {
        // i = (1,0,1)/√2, o1 = Rear
        // normal = i × Up = (1,0,1)/√2 × (0,0,1) = (0,-1,0)/√2 → (0,-1,0) = Front
        val i  = Vec3(1, 0, 1).normalized
        val o1 = Vec3.Rear

        val plane = SplitMergeTwoHelper.safe(i, o1, origin, origin, SymmetryPlaneConfig.NonVertical).toOption.get
        assertVec3Approx(plane.symmetryPlaneNormal, Vec3.Front)
        // branchTwo = Rear - 2(Rear·Front) × Front = Rear - 2(-1) × Front = Rear + 2×Front = Front
        assertVec3Approx(plane.branchTwoDirection, Vec3.Front )
    }

    // ── SymmetryPlaneConfig.fromIncomingWithRotation ─────────────────────────

    "fromIncomingWithRotation" should "use provided azimuth for vertical incoming" in {
        val config = SymmetryPlaneConfig.fromIncomingWithRotation(Vec3.Up, Some(AzimuthDirection.Front))
        config shouldBe Right(SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Front))
    }

    it should "return Left when azimuth missing for vertical incoming" in {
        val result = SymmetryPlaneConfig.fromIncomingWithRotation(Vec3.Up, None)
        result shouldBe Left(SplitMergeTwoHelperError.SymmetryPlaneAzimuthRequired)
    }

    it should "ignore azimuth for non-vertical incoming" in {
        val config = SymmetryPlaneConfig.fromIncomingWithRotation(Vec3.Rear, Some(AzimuthDirection.Front))
        config shouldBe Right(SymmetryPlaneConfig.NonVertical)
    }

    it should "return Left when azimuth missing for vertical incoming (Down)" in {
        val result = SymmetryPlaneConfig.fromIncomingWithRotation(Vec3.Down, None)
        result shouldBe Left(SplitMergeTwoHelperError.SymmetryPlaneAzimuthRequired)
    }

    "VerticalIncoming with rotation=90°" should "reflect Rear to Left" in {
        // rotation=90°: normal = Up × Front = Right
        // Symmetry plane = YZ plane (X=0)
        // o1 = Rear, o2 = Rear - 2(Rear·Right) × Right = Rear - 0 = Rear... wait
        // Actually: normal = (sin(90°), cos(90°), 0) = (1, 0, 0) = Right
        // o2 = Rear - 2(Rear·Right) × Right = Rear - 2(0) × Right = Rear
        // Hmm, that's not right. Let me re-derive.
        // o1 = Rear = (0, 1, 0), normal = Right = (1, 0, 0)
        // o1 · n = 0, so o2 = o1 - 0 = o1 = Rear
        // This means Rear is in the symmetry plane (YZ), so it reflects to itself.
        // That's correct — Rear is perpendicular to Right, so it lies in the YZ plane.
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Front))
                .toOption
                .get
        // Rear is in the symmetry plane (YZ), so it reflects to itself
        assertVec3Approx(plane.branchTwoDirection, Vec3.Rear)
    }

    it should "reflect Right to Left when rotation=90°" in {
        // rotation=90°: normal = Right = (1, 0, 0)
        // o1 = Right = (1, 0, 0)
        // o1 · n = 1, so o2 = Right - 2(1) × Right = Right - 2×Right = -Right = Left
        val i  = Vec3.Up
        val o1 = Vec3.Right

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Front))
                .toOption
                .get
        assertVec3Approx(plane.branchTwoDirection, Vec3.Left)
    }

    "VerticalIncoming with rotation=270°" should "keep Rear in the symmetry plane" in {
        // rotation=270°: normal = Up × Left = Front
        // Wait: normal = (sin(270°), cos(270°), 0) = (-1, 0, 0) = Left
        // o1 = Rear = (0, 1, 0), o1 · n = 0
        // o2 = Rear - 0 = Rear (Rear is in the symmetry plane)
        // Hmm, let me check: at rot=270°, horizontalDir = (cos(270°), -sin(270°), 0) = (0, 1, 0) = Rear
        // normal = Up × Rear = Left = (-1, 0, 0)
        // o1 = Rear = (0, 1, 0), o1 · n = 0
        // So Rear is in the symmetry plane (YZ), reflecting to itself.
        // But we want it to reflect to Front. Let me check rotation=0° again.
        // At rot=0°: horizontalDir = (1, 0, 0) = Right
        // normal = Up × Right = Rear = (0, 1, 0)
        // o1 = Rear = (0, 1, 0), o1 · n = 1
        // o2 = Rear - 2(1) × Rear = Rear - 2×Rear = -Rear = Front ✓
        // So rotation=0° gives normal=Rear, which reflects Rear to Front.
        // rotation=270° gives normal=Left, which keeps Rear in the plane.
        // Let me test what we actually get:
        val i  = Vec3.Up
        val o1 = Vec3.Rear

        val plane =
            SplitMergeTwoHelper
                .safe(i, o1, origin, origin, SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Rear))
                .toOption
                .get
        // At rot=270°: normal = Left, Rear is in the symmetry plane (YZ with normal=Left)
        assertVec3Approx(plane.branchTwoDirection, Vec3.Rear)
    }

    // ── Show instance ──────────────────────────────────────────────────────────

    "SplitMergeTwoHelperError.CollinearVectors.show" should "return a descriptive message" in {
        import cats.Show
        val msg = Show[SplitMergeTwoHelperError].show(SplitMergeTwoHelperError.CollinearVectors)
        msg should include("CollinearVectors")
        msg should include("collinear")
    }
