/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.{
    SplitSingleFlowIntoTwoFlowsWith90DegTurn,
    MergeTwoFlowsIntoSingleWith90DegTurn
}
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.models.FluePipe_Module_15544
import afpma.firecalc.engine.models.PipeBuildSeed
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.MergeBranchTipNotAtMergePosition
import afpma.firecalc.units.Vec3

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Verifies that `MergeBranchTipNotAtMergePosition` is surfaced when a merge
 * element's branch tip is not at the correct merge position.
 *
 * Reproduces the geometry from `merge-warning-should-trigger.fcalc`:
 *   - Split at origin, branch goes Rear (+X)
 *   - 0.3m section, 90° bend Down, 1m descent, 90° bend Front, 0.7m horizontal
 *   - Merge at (-0.4, 0, -1) — 1m away from the split plane (Z=0)
 *   - Expected: MergeBranchTipNotAtMergePosition because branch tip is
 *     not on the split plane defined by the split element.
 */
class MergePositionValidationSuite extends AnyFreeSpec with Matchers {

    import PipeShape.*

    val builder = FluePipe_Module_15544.incremental

    "merge position validation" - {

        "should reject merge whose branch tip is far from the split plane" in {
            // Reproduce the geometry from merge-warning-should-trigger.fcalc:
            //   Slot 0 (FlueSlot):
            //     0: SplitSingleFlowIntoTwoFlowsWith90DegTurn (absDir=Rear/Horizontal)
            //     1: SetRoughness(3mm)
            //     2: AddSectionSlopped("sortie de foyer", 0.3m)
            //     3: AddSharpeAngle_0_to_180("vers descente", 90°, absDir=Down)
            //     4: AddSectionSlopped("descente", 1m)
            //     5: AddSharpeAngle_0_to_180("vers section horizontale", 90°, absDir=Front/Horizontal)
            //     6: AddSectionSlopped("section horizontale", 0.7m)
            //     7: MergeTwoFlowsIntoSingleWith90DegTurn (absDir=Left/Horizontal)
            //
            // Geometry trace (builder initial direction = Right/Horizontal, but split overrides to Up):
            //   Split at (0,0,0), frame direction = Up (0,0,1), branch goes Rear (0,1,0)
            //   0.3m section along Rear → (0, 0.3, 0)
            //   90° Down → frame becomes (0,0,-1)
            //   1m section → (0, 0.3, -1)
            //   90° Front → frame becomes (0,-1,0)
            //   0.7m section → (0, -0.4, -1)
            //   Merge at (0, -0.4, -1) — 1m below the split plane (Z=0)
            //
            // Split plane: Z=0 (perpendicular to incoming=Up)
            // Branch tip Z = -1, split plane Z = 0 → distance = 1m >> 1mm tolerance

            // The file load path (PipeChainGeneric.build) calls FireboxSplitFrame.resolveInitialSeed
            // which overrides the frame to Up when the first element is a Split.
            // We replicate this by seeding with an Up frame directly.
            val upFrame = PipeFrame.initial(Vec3.Up)
            val seed    = PipeBuildSeed.fromFrame(Some(upFrame))

            val p = builder.define(
                // 0: Split — branch goes Rear, 90° from incoming Up
                SplitSingleFlowIntoTwoFlowsWith90DegTurn            (
                    "Diviser en 2 flux avec virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Rear),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                // 1: Set roughness
                SetFlowOnlyPipeProp_15544.SetRoughness              (0.003.meters                 ),
                // 2: 0.3m section (goes Rear after split)
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("sortie de foyer", 0.3.meters),
                // 3: 90° bend Down
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers descente",
                    90.0.degrees,
                    Some(AbsoluteDirection(azimuth = None, inclination = InclinationDirection.Down))
                ),
                // 4: 1m descent
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("descente", 1.0.meters           ),
                // 5: 90° bend Front/Horizontal
                AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(
                    "vers section horizontale",
                    90.0.degrees,
                    Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Front),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                // 6: 0.7m horizontal (goes Front)
                AddFlowOnlyPipeElement_15544.AddSectionSlopped      ("section horizontale", 0.7.meters),
                // 7: Merge — branch tip is at (0, -0.4, -1), far from split plane
                MergeTwoFlowsIntoSingleWith90DegTurn                (
                    "Fusionner en 1 flux après virage à 90°",
                    newInnerShape = Circle(0.18.meters),
                    absDir        = Some(
                        AbsoluteDirection    (
                            azimuth     = Some(AzimuthDirection.Left),
                            inclination = InclinationDirection.Horizontal
                        )
                    )
                ),
                // 8: Section after merge (required so merge isn't last element)
                AddFlowOnlyPipeElement_15544.AddSectionSlopped("section horizontale", 0.5.meters)
            )

            val result = p.toFullDescrWithSeed(seed)

            // Print all errors for debugging
            println(s"Valid: ${result.isValid}")
            result match
                case cats.data.Validated.Invalid(errs) =>
                    errs.toList.foreach(e => println(s"  Error: ${e.getClass.getSimpleName} — $e"))
                case _                                 =>

            // The build should fail because the merge branch tip is not at the merge position
            result.isValid shouldBe false

            val errors = result.toEither.left.toOption.get
            // At least one error should be MergeBranchTipNotAtMergePosition
            errors.exists(_.isInstanceOf[MergeBranchTipNotAtMergePosition]) shouldBe true
        }
    }
}
