/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.FramedPostFireboxPipes
import afpma.firecalc.engine.standard.IncompatibleDirectionInPipe
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import afpma.firecalc.units.Vec3

/**
 * DirectionReachability parity tests for V7 migration.
 *
 * Verifies that sanitized V7 slots + wrapper initial frame produce the expected
 * reachability result without descriptor-level tracking operations.
 */
class DirectionReachabilityV7IntegrationSuite extends AnyFreeSpec with Matchers:

    private def cleanFlueDescr: Seq[afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4] = Seq(
        SetFlowOnlyPipeProp_15544_V4.SetRoughness              (3.mm         ),
        AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal   ("test", 50.cm),
        AddFlowOnlyPipeElement_15544_V4.AddSharpeAngle_0_to_180(
            "virage",
            90.degrees,
            None
        )
    )

    private def cleanSlots = Seq(
        PostFireboxPipeDescrSlot_V7.FlueSlot     (cleanFlueDescr),
        PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty     ),
        PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty     )
    )

    private def wrapperInitialFrame: PipeFrame =
        PipeFrame.initial(
            Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees    (AzimuthDirection.Right         ),
                InclinationDirection.toDegrees(InclinationDirection.Horizontal)
            )
        )

    "DirectionReachability V7" - {

        "works with sanitized slots + wrapper initialFrame" in {
            val sanitized = cleanSlots.map(FramedPostFireboxPipes.sanitizeSlot)
            val result    = DirectionReachability.checkPostFireboxChain(
                PostFireboxPipeSlot.fromDto(sanitized          ),
                Some                       (wrapperInitialFrame)
            )
            result.isEmpty.shouldBe(true)
        }

        "fails when direction is unreachable" in {
            val failingSlots = Seq(
                PostFireboxPipeDescrSlot_V7.FlueSlot(
                    Seq(
                        AddFlowOnlyPipeElement_15544_V4.AddSharpeAngle_0_to_180(
                            "unreachable",
                            90.degrees,
                            Some(AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal))
                        )
                    )
                )
            )
            val sanitized    = failingSlots.map(FramedPostFireboxPipes.sanitizeSlot)
            val result       = DirectionReachability.checkPostFireboxChain(
                PostFireboxPipeSlot.fromDto(sanitized          ),
                Some                       (wrapperInitialFrame)
            )
            result.isEmpty.shouldBe(false)
        }

        "frame threads across multiple real PostFireboxPipeDescrSlot values" in {
            val sanitized = cleanSlots.map(FramedPostFireboxPipes.sanitizeSlot)
            val extraFlue = PostFireboxPipeDescrSlot_V7.FlueSlot(
                Seq(
                    AddFlowOnlyPipeElement_15544_V4.AddSharpeAngle_0_to_180("virage", 90.degrees, None)
                )
            )
            val result    = DirectionReachability.checkPostFireboxChain(
                PostFireboxPipeSlot.fromDto(sanitized :+ FramedPostFireboxPipes.sanitizeSlot(extraFlue)),
                Some                       (wrapperInitialFrame                                        )
            )
            result.isEmpty.shouldBe(true)
        }
    }
end DirectionReachabilityV7IntegrationSuite
