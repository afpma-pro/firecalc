/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.units.Vec3
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitMerge90ValidatorSuite extends AnyFlatSpec with Matchers:

    val pt  = FluePipeT
    val ref = "#42"

    it should "reject split with non-perpendicular branch" in {
        // incoming = up, branch1 = up-right → dot=0.707 → not perpendicular → SplitBranchesNotOpposite
        val incoming = Vec3.Up
        val branch1  = Vec3(1, 0, 1).normalized // up-right
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref)
        result.isValid shouldBe false
    }

    it should "allow split when reflected branch is horizontal" in {
        val incoming = Vec3(0, 1, 0) // rear (horizontal)
        val branch1  = Vec3(1, 0, 0) // right (horizontal, 90°)
        // i·o1 = 0, reflected = -o1 = (-1, 0, 0) → left (horizontal)
        SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref).isValid shouldBe true
    }

    it should "allow descending incoming with horizontal split" in {
        val incoming = Vec3.Down
        val branch1  = Vec3(1, 0, 0) // right (horizontal, 90°)
        SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref).isValid shouldBe true
    }

    it should "reject collinear branches (no absDir)" in {
        val incoming = Vec3.Up
        val result   = SplitMerge90Validator.validateSplit(incoming, None, pt, ref)
        result.isValid shouldBe false
    }

    it should "reject collinear branches (parallel directions)" in {
        val incoming = Vec3.Up
        val branch1  = Vec3.Up // same direction
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref)
        result.isValid shouldBe false
    }

    it should "reject collinear branches (anti-parallel directions)" in {
        val incoming = Vec3.Up
        val branch1  = Vec3.Down // opposite direction
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref)
        result.isValid shouldBe false
    }

    it should "reject perpendicular ascending split when reflected branch ascends" in {
        // Proves the ascending check is reachable: ascending incoming + perpendicular descending branch
        // → reflected branch ascends.
        // i=(0,0.6,0.8), o₁=(0,0.8,-0.6): dot=0 (perpendicular), o₂=(0,-0.8,0.6) → z>0 (ascending).
        val incoming = Vec3(0, 0.6, 0.8)
        val branch1  = Vec3(0, 0.8, -0.6)
        val result   = SplitMerge90Validator.validateSplit(incoming, Some(branch1), pt, ref)
        result.isValid shouldBe false
    }

end SplitMerge90ValidatorSuite
