/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.domain.AbsoluteDirection

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Diagnostic test to see what errors EN 15544 returns for split geometry validation. */
class SplitDiagnosticSuite extends AnyFlatSpec with Matchers {

    given FluePipeT = FluePipeT

    val builder = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
    import builder.*

    private val horizontalDir       = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    private val ascendingDir        = PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Up)
    private val horizontalBranchDir = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)

    private val setup = Vector[FlowOnlyPipeDescr_15544](
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Circle(20.cm)),
        SetFlowOnlyPipeProp_15544.SetRoughness(1.mm)
    )

    "Test 1" should "allow split with horizontal reflected branch on horizontal pipe" in {
        val incrDescr = withInitialDirection(horizontalDir).define(
            setup ++ Seq(
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                       ("preSplit", 1.meters ),
                AddFlowOnlyPipeElement_15544.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                       ("postSplit", 1.meters)
            )*
        )
        val result    = incrDescr.toFullDescr()
        result.isValid shouldBe true
    }

    "Test 2" should "allow merge with absDir on ascending pipe" in {
        val incrDescr = withInitialDirection(ascendingDir).define(
            setup ++ Seq(
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                   ("preMerge", 1.meters ),
                AddFlowOnlyPipeElement_15544.MergeTwoFlowsIntoSingleWith90DegTurn(
                    "merge",
                    newInnerShape = PipeShape.Circle(15.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                   ("postMerge", 1.meters)
            )*
        )
        val result    = incrDescr.toFullDescr()
        result.isValid shouldBe true
    }

    "Test 3" should "allow split with horizontal branch on ascending pipe" in {
        val incrDescr = withInitialDirection(ascendingDir).define(
            setup ++ Seq(
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                       ("preSplit", 1.meters ),
                AddFlowOnlyPipeElement_15544.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_15544.AddSectionSlopped                       ("postSplit", 1.meters)
            )*
        )
        val result    = incrDescr.toFullDescr()
        result.isValid shouldBe true
    }
}
