/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AscendingPipeSplitProductionSuite extends AnyFlatSpec with Matchers:

    "Flow split on ascending pipe (EN 15544)" should "be forbidden" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_15544
            .makeFor[FluePipeT]
            .withInitialDirection(
                PipeInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_15544.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_15544.SetRoughness           (1.mm         ),
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal("-", 0.cm    ),
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_15544.AddSectionSlopped   ("s", 1.meters)
        )
        val result = descr.toFullDescr()
        result.isValid shouldBe false
        val errors = result.toEither.left.toOption.get
        errors.head shouldBe a[FlowSplitForbiddenOnAscendingPipe]
    }

    it should "be allowed on descending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_15544
            .makeFor[FluePipeT]
            .withInitialDirection(
                PipeInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Down
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_15544.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_15544.SetRoughness           (1.mm         ),
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal("-", 0.cm    ),
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_15544.AddSectionSlopped   ("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "be allowed on horizontal pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_15544
            .makeFor[FluePipeT]
            .withInitialDirection(
                PipeInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Horizontal
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_15544.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_15544.SetRoughness           (1.mm         ),
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal("-", 0.cm    ),
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_15544.AddSectionSlopped   ("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "allow merge on ascending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_15544
            .makeFor[FluePipeT]
            .withInitialDirection(
                PipeInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_15544.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_15544.SetRoughness           (1.mm         ),
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal("-", 0.cm    ),
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(1)           ),
            AddFlowOnlyPipeElement_15544.AddSectionSlopped   ("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "require initial direction for geometry" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
        val descr       = builder.define(
            SetFlowOnlyPipeProp_15544.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_15544.SetRoughness           (1.mm         ),
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal("-", 0.cm    ),
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_15544.AddSectionSlopped   ("s", 1.meters)
        )
        val result      = descr.toFullDescr()
        // Should be invalid because geometry requires an initial direction
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[GeometryWithoutInitialDirection]
    }

end AscendingPipeSplitProductionSuite
