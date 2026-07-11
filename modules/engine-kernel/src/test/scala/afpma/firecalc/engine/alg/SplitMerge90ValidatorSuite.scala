/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.units.Vec3

import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.geometry.SymmetryPlaneConfig

import afpma.firecalc.domain.AzimuthDirection
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitMerge90ValidatorSuite extends AnyFlatSpec with Matchers:

    val pt  = FluePipeT
    val ref = "#42"

    it should "reject split with non-perpendicular branch" in {
        // incoming = up, branch1 = up-right → dot=0.707 → not perpendicular → SplitBranchesNotOpposite
        val incoming = Vec3.Up
        val branch1  = Vec3(1, 0, 1).normalized // up-right
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
        result.isValid shouldBe false
    }

    it should "allow split when reflected branch is horizontal" in {
        val incoming = Vec3(0, 1, 0) // rear (horizontal)
        val branch1  = Vec3(1, 0, 0) // right (horizontal, 90°)
        // i·o1 = 0, reflected = -o1 = (-1, 0, 0) → left (horizontal)
        SplitMerge90Validator
            .validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
            .isValid shouldBe true
    }

    it should "allow descending incoming with horizontal split" in {
        val incoming = Vec3.Down
        val branch1  = Vec3(1, 0, 0) // right (horizontal, 90°)
        SplitMerge90Validator
            .validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
            .isValid shouldBe true
    }

    it should "reject collinear branches (no absDir)" in {
        val incoming = Vec3.Up
        val result   = SplitMerge90Validator.validateSplit(incoming, None, Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
        result.isValid shouldBe false
    }

    it should "reject collinear branches (parallel directions)" in {
        val incoming = Vec3.Up
        val branch1  = Vec3.Up // same direction
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
        result.isValid shouldBe false
    }

    it should "reject collinear branches (anti-parallel directions)" in {
        val incoming = Vec3.Up
        val branch1  = Vec3.Down // opposite direction
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
        result.isValid shouldBe false
    }

    it should "allow perpendicular descending split when reflected branch descends" in {
        // With plane reflection: incoming=(0,0.6,0.8), branch1=(0,0.8,-0.6)
        // NonVertical, normal = incoming × Up = (0.8, 0, 0) = Right
        // branchTwo = branch1 - 2(branch1·Right)×Right = branch1 - 0 = (0,0.8,-0.6)
        // branchTwo.z = -0.6 < 0 → descending → allowed
        val incoming = Vec3(0, 0.6, 0.8)
        val branch1  = Vec3(0, 0.8, -0.6)
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), Vec3(0, 0, 0), Vec3(0, 0, 0), pt, ref)
        result.isValid shouldBe true
    }

    // ── validateMergePosition tests ──

    it should "pass merge when branch tip is on the split plane" in {
        // Split: incoming = Up, branch1 = Right
        // VerticalIncoming(0°): normal = Up × Right = Rear
        // Symmetry plane = XZ plane (Y=0)
        // Branch tip at (1, 0, 0) — on XZ plane → distance = 0
        val incoming  = Vec3.Up
        val splitPos  = Vec3(0, 0, 0)
        val branchTip = Vec3(1, 0, 0)
        val symConfig = SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
        val result    = SplitMerge90Validator.validateMergePosition(
            incoming,
            splitPos,
            branchTip,
            symConfig,
            pt,
            "#merge"
        )
        result.isValid shouldBe true
    }

    it should "fail merge when branch tip is off the split plane" in {
        // Split: incoming = Up, branch1 = Right
        // VerticalIncoming(0°): normal = Up × Right = Rear
        // Symmetry plane = XZ plane (Y=0)
        // Branch tip at (1, 0.5, 0.5) — 0.5m off XZ along Y → 500mm
        val incoming  = Vec3.Up
        val splitPos  = Vec3(0, 0, 0)
        val branchTip = Vec3(1, 0.5, 0.5)
        val symConfig = SymmetryPlaneConfig.VerticalIncoming(AzimuthDirection.Right)
        val result    = SplitMerge90Validator.validateMergePosition(
            incoming,
            splitPos,
            branchTip,
            symConfig,
            pt,
            "#merge"
        )
        result.isValid shouldBe false
    }

    it should "pass merge when branch tip is on the split plane (non-trivial)" in {
        // Split: incoming = Rear, branch1 = Up, split at (0, 1, 0)
        // NonVertical: normal = Rear × Up = Right
        // Symmetry plane = YZ plane (X=0)
        // Branch tip at (0, 1, 0) — on YZ plane → distance = 0
        val incoming  = Vec3(0, 1, 0)
        val splitPos  = Vec3(0, 1, 0)
        val branchTip = Vec3(0, 1, 0)
        val symConfig = SymmetryPlaneConfig.NonVertical
        val result    = SplitMerge90Validator.validateMergePosition(
            incoming,
            splitPos,
            branchTip,
            symConfig,
            pt,
            "#merge"
        )
        result.isValid shouldBe true
    }

    it should "pass merge when branch tip is on the split plane (different geometry)" in {
        // Split: incoming = Right, branch1 = Up, split at origin
        // NonVertical: normal = Right × Up = Front
        // Symmetry plane = XZ plane (Y=0)
        // Branch tip at (1, 0, 0) — on XZ plane → distance = 0
        val incoming  = Vec3.Right
        val splitPos  = Vec3(0, 0, 0)
        val branchTip = Vec3(1, 0, 0)
        val symConfig = SymmetryPlaneConfig.NonVertical
        val result    = SplitMerge90Validator.validateMergePosition(
            incoming,
            splitPos,
            branchTip,
            symConfig,
            pt,
            "#merge"
        )
        result.isValid shouldBe true
    }

end SplitMerge90ValidatorSuite
