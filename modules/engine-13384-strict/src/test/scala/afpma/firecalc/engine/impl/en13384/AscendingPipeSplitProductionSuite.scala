/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.engine.impl.en13384.FlowOnlyIncrementalBuilder_13384
import afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AscendingPipeSplitProductionSuite extends AnyFlatSpec with Matchers:

    // ── EN 13384 Flow-Only ──────────────────────────────────────────────

    "Flow split on ascending pipe (EN 13384 flow-only)" should "be forbidden" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result = descr.toFullDescr()
        result.isValid shouldBe false
        val errors = result.toEither.left.toOption.get
        errors.head shouldBe a[FlowSplitForbiddenOnAscendingPipe]
    }

    it should "be allowed on descending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Down
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "be allowed on horizontal pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Horizontal
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "allow merge on ascending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)           ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "require initial direction for geometry" in {
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384.makeFor[FluePipeT]
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[GeometryWithoutInitialDirection]
    }

    // ── EN 13384 Thermal ───────────────────────────────────────────────

    "Flow split on ascending pipe (EN 13384 thermal)" should "be forbidden" in {
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        val result = descr.toFullDescr()
        result.isValid shouldBe false
        val errors = result.toEither.left.toOption.get
        errors.head shouldBe a[FlowSplitForbiddenOnAscendingPipe]
    }

    it should "be allowed on descending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Down
                )
            )
        val descr = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "be allowed on horizontal pipe" in {
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Horizontal
                )
            )
        val descr = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "allow merge on ascending pipe" in {
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(
                PostFireboxInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Up
                )
            )
        val descr = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)                                         ),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        val result = descr.toFullDescr()
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "require initial direction for geometry" in {
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384.makeFor[FluePipeT]
        val descr       = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[GeometryWithoutInitialDirection]
    }

end AscendingPipeSplitProductionSuite
