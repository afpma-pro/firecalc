/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.standard.ShapeNotMaterialized
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitMergeMaterializationSuite extends AnyFlatSpec with Matchers:

    private val horizontalDir =
        PipeInitialDirection    (
            azimuth     = AzimuthDirection.Front,
            inclination = InclinationDirection.Horizontal
        )

    // =========================================================================
    // FlowOnlyIncrementalBuilder_13384 — SetInnerShape rejected after split/merge
    // =========================================================================

    "FlowOnlyIncrementalBuilder_13384" should "reject SetInnerShape right after a split (not materialized)" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness                               (3.mm                ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
            AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                "split",
                newInnerShape = PipeShape.Circle(9.cm),
                absDir        = None
            ),
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape right after a merge (not materialized)" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness                               (3.mm                 ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters ),
            AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                "split",
                newInnerShape = PipeShape.Circle(9.cm),
                absDir        = None
            ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters     ),
            AddFlowOnlyPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn    (
                "merge",
                newInnerShape = PipeShape.Circle(18.cm),
                absDir        = None
            ),
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("postMerge", 1.meters)
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "accept SetInnerShape after split + length-bearing section" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness                               (3.mm                ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
            AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                "split",
                newInnerShape = PipeShape.Circle(9.cm),
                absDir        = None
            ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    ),
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual2", 1.meters   )
        )
        descr.toFullDescr().isValid shouldBe true
    }

    it should "accept SetInnerShape after merge + length-bearing section" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness                               (3.mm                  ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters  ),
            AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                "split",
                newInnerShape = PipeShape.Circle(9.cm),
                absDir        = None
            ),
            AddFlowOnlyPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn    (
                "merge",
                newInnerShape = PipeShape.Circle(18.cm),
                absDir        = None
            ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("postMerge", 1.meters ),
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("postMerge2", 1.meters)
        )
        descr.toFullDescr().isValid shouldBe true
    }

end SplitMergeMaterializationSuite
