/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

class SplitMergeTwoHelperSuite extends AnyFlatSpec with Matchers:

    val eps = 1e-10

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
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val result = SplitMergeTwoHelper.safe(i, o1, pos)
        result.isRight shouldBe true

        val plane = result.toOption.get
        assertVec3Approx(plane.incomingDirection, Vec3.Up   )
        assertVec3Approx(plane.branchOneDirection, Vec3.Rear)
    }

    it should "return CollinearVectors for parallel directions" in {
        val i   = Vec3.Up
        val o1  = Vec3.Up
        val pos = Vec3(0, 0, 0)

        val result = SplitMergeTwoHelper.safe(i, o1, pos)
        result shouldBe Left(SplitMergeTwoHelperError.CollinearVectors)
    }

    it should "return CollinearVectors for anti-parallel directions" in {
        val i   = Vec3.Up
        val o1  = Vec3.Down
        val pos = Vec3(0, 0, 0)

        val result = SplitMergeTwoHelper.safe(i, o1, pos)
        result shouldBe Left(SplitMergeTwoHelperError.CollinearVectors)
    }

    it should "normalize non-unit input directions" in {
        val i   = Vec3(0, 0, 3) // not unit
        val o1  = Vec3(0, 4, 0) // not unit
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        assertVec3Approx(plane.incomingDirection, Vec3.Up   )
        assertVec3Approx(plane.branchOneDirection, Vec3.Rear)
    }

    // ── reflectedBranchDirection ───────────────────────────────────────────────

    "reflectedBranchDirection" should "reflect o1 across the line of i (90° horizontal split)" in {
        // i = Up, o1 = Rear → o2 should be Front (reflection of Rear across Up line)
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        assertVec3Approx(plane.reflectedBranchDirection, Vec3.Front)
    }

    it should "reflect o1 across i (45° ascending split)" in {
        // i = diagonal up-rear, o1 = horizontal right
        // o2 should be the reflection of o1 across the line of i
        val i   = Vec3(0, 1, 1).normalized // 45° up-rear
        val o1  = Vec3.Right               // horizontal right
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val o2    = plane.reflectedBranchDirection

        // i is the angle bisector: angle(i, o1) == angle(i, o2)
        val angle1 = i.angleTo(o1)
        val angle2 = i.angleTo(o2)
        assertApprox(angle1, angle2, "bisector property")

        // o2 should be in the plane spanned by {i, o1}
        val normal = i.cross(o1)
        assertApprox(normal.dot(o2), 0.0, "coplanarity")

        // o2 should be unit length
        assertApprox(o2.norm, 1.0, "unit length")
    }

    it should "preserve angles symmetrically" in {
        val i   = Vec3(1, 0, 1).normalized // 45° up-right
        val o1  = Vec3(0, 1, 0)            // rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val o2    = plane.reflectedBranchDirection

        // Dot products with i should be equal (same angle)
        assertApprox(i.dot(o1), i.dot(o2), "equal dot with bisector")

        // o2 should be unit
        assertApprox(o2.norm, 1.0, "unit length")
    }

    // ── planeNormal ────────────────────────────────────────────────────────────

    "planeNormal" should "be perpendicular to both i and o1" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val n     = plane.planeNormal

        assertApprox(n.dot(plane.incomingDirection), 0.0, "perpendicular to i"  )
        assertApprox(n.dot(plane.branchOneDirection), 0.0, "perpendicular to o1")
        assertApprox(n.norm, 1.0, "unit length"                                 )
    }

    // ── orthonormalBasis ───────────────────────────────────────────────────────

    "orthonormalBasis" should "return two orthonormal vectors spanning the plane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val (u, v) = plane.orthonormalBasis

        // u should be i (normalized)
        assertVec3Approx(u, plane.incomingDirection)

        // v should be perpendicular to u and to planeNormal
        assertApprox(u.dot(v), 0.0, "u perpendicular to v"      )
        assertApprox(plane.planeNormal.dot(v), 0.0, "v in plane")
        assertApprox(v.norm, 1.0, "v is unit"                   )
    }

    // ── projectOntoPlane ───────────────────────────────────────────────────────

    "projectOntoPlane" should "return the point itself when it lies in the plane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get

        // A point in the plane (along i or o1)
        val inPlane   = Vec3(0, 1, 1) // Rear + Up
        val projected = plane.projectOntoPlane(inPlane)
        assertVec3Approx(projected, inPlane)
    }

    it should "remove the out-of-plane component" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get

        // Point off the plane (has a Right component, which is the normal direction)
        val offPlane  = Vec3(1, 1, 1) // Right + Rear + Up
        val projected = plane.projectOntoPlane(offPlane)

        // Projected should have zero component along planeNormal
        assertApprox(projected.dot(plane.planeNormal), 0.0, "no normal component")

        // Should be (0, 1, 1) — Rear + Up
        assertVec3Approx(projected, Vec3(0, 1, 1))
    }

    // ── reflectAcrossPlane ─────────────────────────────────────────────────────

    "reflectAcrossPlane" should "mirror a point across the split plane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get

        // Point off plane: (1, 1, 1) = Right + Rear + Up
        val q1 = Vec3(1, 1, 1)
        val q2 = plane.reflectAcrossPlane(q1)

        // q2 should be (-1, 1, 1) — mirrored across the YZ plane
        assertVec3Approx(q2, Vec3(-1, 1, 1))

        // Midpoint of q1 and q2 should lie in the plane
        val midpoint = Vec3(
            (q1.x + q2.x) / 2.0,
            (q1.y + q2.y) / 2.0,
            (q1.z + q2.z) / 2.0
        )
        assertApprox(midpoint.dot(plane.planeNormal), 0.0, "midpoint in plane")
    }

    it should "return the point itself when it lies in the plane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane     = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val inPlane   = Vec3(0, 1, 1)
        val reflected = plane.reflectAcrossPlane(inPlane)
        assertVec3Approx(reflected, inPlane)
    }

    // ── computeMergePosition ───────────────────────────────────────────────────

    "computeMergePosition" should "equal projectOntoPlane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val tip   = Vec3(2, 3, 4)

        val merge     = plane.computeMergePosition(tip)
        val projected = plane.projectOntoPlane(tip)
        assertVec3Approx(merge, projected)
    }

    // ── computeSecondBranchTip ─────────────────────────────────────────────────

    "computeSecondBranchTip" should "equal reflectAcrossPlane" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get
        val tip   = Vec3(2, 3, 4)

        val reflectedTip = plane.computeReflectedBranchTip(tip)
        val reflected    = plane.reflectAcrossPlane(tip)
        assertVec3Approx(reflectedTip, reflected)
    }

    // ── Integration: symmetric auto-merge ──────────────────────────────────────

    "auto-merge workflow" should "produce symmetric branches converging at merge" in {
        val i   = Vec3.Up
        val o1  = Vec3.Rear
        val pos = Vec3(0, 0, 0)

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get

        // Simulate branch 1 ending at some point off the plane
        val q1Tip = Vec3(0.5, 2.0, 3.0) // slightly right, rear, up

        val q2Tip    = plane.computeReflectedBranchTip(q1Tip)
        val mergePos = plane.computeMergePosition(q1Tip)

        // q1Tip and q2Tip should be symmetric (q2 = reflect(q1))
        assertVec3Approx(q2Tip, Vec3(-0.5, 2.0, 3.0))

        // mergePos should be the projection of q1Tip onto the plane
        assertVec3Approx(mergePos, Vec3(0, 2.0, 3.0))

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

        val plane = SplitMergeTwoHelper.safe(i, o1, pos).toOption.get

        // Point directly above split position (in the plane)
        val inPlane   = Vec3(1, 2, 5) // pos + 2*Up
        val projected = plane.projectOntoPlane(inPlane)
        assertVec3Approx(projected, inPlane)

        // Point offset from plane
        val offPlane = Vec3(2, 2, 5) // inPlane + Right
        val proj     = plane.projectOntoPlane(offPlane)
        // Projection minus splitPos should lie in the plane
        assertApprox((proj - pos).dot(plane.planeNormal), 0.0, "offset in plane")
    }

    // ── Show instance ──────────────────────────────────────────────────────────

    "SplitMergeTwoHelperError.CollinearVectors.show" should "return a descriptive message" in {
        import cats.Show
        val msg = Show[SplitMergeTwoHelperError].show(SplitMergeTwoHelperError.CollinearVectors)
        msg should include("CollinearVectors")
        msg should include("collinear")
    }
