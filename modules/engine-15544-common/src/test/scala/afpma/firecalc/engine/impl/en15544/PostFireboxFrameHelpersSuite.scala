/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn

import afpma.firecalc.engine.impl.en15544.common.PostFireboxFrameHelpers
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Verifies that `splitBranchDirection` extracts the correct horizontal Vec3
 * from a split's azimuth direction — not hardcoded `Vec3.Rear`.
 *
 * Coordinate system: +X=Right, +Y=Rear, +Z=Up.
 * Azimuth 0°=Rear(+Y), 90°=Right(+X), 180°=Front(-Y), -90°=Left(-X).
 */
class PostFireboxFrameHelpersSuite extends AnyFreeSpec with Matchers {

    import PipeShape.*

    private def assertApproxVec3(actual: Vec3, expected: Vec3, label: String)(using
        org.scalactic.source.Position
    ): Unit =
        assert(math.abs(actual.x - expected.x) < 1e-6, s"$label x: expected ${expected.x}, got ${actual.x}")
        assert(math.abs(actual.y - expected.y) < 1e-6, s"$label y: expected ${expected.y}, got ${actual.y}")
        assert(math.abs(actual.z - expected.z) < 1e-6, s"$label z: expected ${expected.z}, got ${actual.z}")

    private def makeSplit(azimuth: AzimuthDirection) =
        SplitSingleFlowIntoTwoFlowsWith90DegTurn               (
            name                = "test-split",
            absDir              = Some(AbsoluteDirection(Some(azimuth), InclinationDirection.Up)),
            newInnerShape       = Circle(100.mm),
            symmetryPlaneAbsDir = None
        )

    "splitBranchDirection" - {

        "should return Vec3.Rear for Rear azimuth (0°)" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq(makeSplit(AzimuthDirection.Rear)))
            val dir  = PostFireboxFrameHelpers.splitBranchDirection(slot)
            assertApproxVec3(dir, Vec3(0.0, 1.0, 0.0), "Rear")
        }

        "should return Vec3.Right for Right azimuth (90°)" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq(makeSplit(AzimuthDirection.Right)))
            val dir  = PostFireboxFrameHelpers.splitBranchDirection(slot)
            assertApproxVec3(dir, Vec3(1.0, 0.0, 0.0), "Right")
        }

        "should return Vec3.Front for Front azimuth (180°)" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq(makeSplit(AzimuthDirection.Front)))
            val dir  = PostFireboxFrameHelpers.splitBranchDirection(slot)
            assertApproxVec3(dir, Vec3(0.0, -1.0, 0.0), "Front")
        }

        "should return Vec3.Left for Left azimuth (-90°)" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq(makeSplit(AzimuthDirection.Left)))
            val dir  = PostFireboxFrameHelpers.splitBranchDirection(slot)
            assertApproxVec3(dir, Vec3(-1.0, 0.0, 0.0), "Left")
        }

        "should return correct diagonal for RearRight azimuth (45°)" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq(makeSplit(AzimuthDirection.RearRight)))
            val dir  = PostFireboxFrameHelpers.splitBranchDirection(slot)
            // sin(45°) ≈ 0.7071, cos(45°) ≈ 0.7071
            assertApproxVec3(dir, Vec3(0.707107, 0.707107, 0.0), "RearRight")
        }

        "should work for ThermalFlueSlot as well" in {
            import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn as ThermalSplit
            val split = ThermalSplit(
                name                = "test-thermal-split",
                absDir              = Some(AbsoluteDirection(Some(AzimuthDirection.Right), InclinationDirection.Up)),
                newInnerShape       = Circle(100.mm),
                symmetryPlaneAbsDir = None
            )
            val slot  = PostFireboxPipeSlot.ThermalFlueSlot(Seq(split))
            val dir   = PostFireboxFrameHelpers.splitBranchDirection(slot)
            assertApproxVec3(dir, Vec3(1.0, 0.0, 0.0), "ThermalFlueSlot Right")
        }

        "should throw when slot has no split descriptor" in {
            val slot = PostFireboxPipeSlot.FlueSlot(Seq.empty)
            assertThrows[RuntimeException](
                PostFireboxFrameHelpers.splitBranchDirection(slot)
            )
        }

        "should throw when slot is not a FlueSlot or ThermalFlueSlot" in {
            val slot = PostFireboxPipeSlot.ConnectorSlot(Seq.empty)
            assertThrows[RuntimeException](
                PostFireboxFrameHelpers.splitBranchDirection(slot)
            )
        }

        "should throw when split has no absDir" in {
            val split = SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                name                = "no-absDir",
                absDir              = None,
                newInnerShape       = Circle(100.mm),
                symmetryPlaneAbsDir = None
            )
            val slot  = PostFireboxPipeSlot.FlueSlot(Seq(split))
            assertThrows[RuntimeException](
                PostFireboxFrameHelpers.splitBranchDirection(slot)
            )
        }
    }
}
