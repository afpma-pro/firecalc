/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.Vec3
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*
import afpma.firecalc.engine.models.geometry.PipeFrame

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipeChainGenericSuite extends AnyFlatSpec with Matchers:

    private val emptyFlue      = FlueSlot(Seq.empty)
    private val emptyConnector = ConnectorSlot(Seq.empty)
    private val emptyChimney   = ChimneySlot(Seq.empty)

    private def thermalPipeDescr(
        name : String,
        flows: Option[NbOfFlows] = None
    ): Seq[ThermalPipeDescr_13384] =
        flows.map(ThermalChannelTopologyOp_13384.SetNumberOfFlows.apply).toSeq ++ Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical(name, 100.cm                     )
        )

    private val rearUpFrame = Some(
        PipeFrame.initial(
            Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees    (AzimuthDirection.Rear  ),
                InclinationDirection.toDegrees(InclinationDirection.Up)
            )
        )
    )

    private val rearHorizontalFrame = Some(
        PipeFrame.initial(
            Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees    (AzimuthDirection.Rear          ),
                InclinationDirection.toDegrees(InclinationDirection.Horizontal)
            )
        )
    )

    "PipeChainGeneric.build" should "produce 3 SlotBuildResults for standard topology" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, emptyConnector, emptyChimney))
        results.size shouldBe 3
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
        results(1).pipeType shouldBe ConnectorPipeT
        results(1).label shouldBe "Connector"
        results(2).pipeType shouldBe ChimneyPipeT
        results(2).label shouldBe "Chimney"
    }

    it should "produce valid pipe and mappingFn for empty descriptors" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, emptyConnector, emptyChimney))
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "handle single slot" in {
        val results = PipeChainGeneric.build(Seq(emptyChimney))
        results.size shouldBe 1
        results(0).pipeType shouldBe ChimneyPipeT
    }

    it should "handle empty slot list" in {
        val results = PipeChainGeneric.build(Seq.empty)
        results shouldBe empty
    }

    it should "produce idsMappingFn that returns None for unmapped indices" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue))
        val fn      = results(0).idsMappingFn
        fn.isValid shouldBe true
        fn.toOption.get.apply(0) shouldBe None // empty pipe has no mappings
        fn.toOption.get.apply(99) shouldBe None
    }

    it should "handle ThermalFlueSlot (MCE variant)" in {
        val thermalFlue = ThermalFlueSlot(Seq.empty)
        val results     = PipeChainGeneric.build(Seq(thermalFlue, emptyConnector, emptyChimney))
        results.size shouldBe 3
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
    }

    it should "produce non-empty results with actual descriptors" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        val results   = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), emptyConnector, emptyChimney),
            initialFrame = rearUpFrame
        )
        results(0).pipe.isValid shouldBe true
        // With actual descriptors + direction, the idsMappingFn should map index 2 (the AddSection) to a section result index
        results(0).idsMappingFn.isValid shouldBe true
        val fn        = results(0).idsMappingFn.toOption.get
        // Index 2 is the AddSectionVertical → should map to a section result
        fn(2).isDefined shouldBe true
        // Properties (indices 0-2) shouldn't map to section results
        fn(0) shouldBe None
        // Final frame should be present (direction was set)
        results(0).finalFrame.isDefined shouldBe true
    }

    it should "chain frames: flue's final frame feeds connector's build" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // Flue with direction → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // Connector with minimal thermal config — no initial direction, relies on flue's frame
        val connDescr = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec1", 100.cm                   )
        )
        val results   = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ConnectorSlot(connDescr), emptyChimney),
            initialFrame = rearUpFrame
        )

        // Flue produced a final frame
        results(0).finalFrame.isDefined shouldBe true
        // Connector also built successfully (inherited flue's frame)
        results(1).pipe.isValid shouldBe true
        // Connector's IdsMapping should be valid
        results(1).idsMappingFn.isValid shouldBe true
    }

    it should "carry frame through empty intermediate slot (V5 migration regression)" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // Flue with direction → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // Empty connector — simulates V5 project with no connector migrated to V6
        val results   = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), emptyConnector, emptyChimney),
            initialFrame = rearUpFrame
        )

        // Flue produced a final frame
        results(0).finalFrame.isDefined shouldBe true
        // Empty connector passes through the inherited frame (the builder receives prevFrame
        // and returns it as finalFrame even with zero descriptors)
        results(1).finalFrame shouldBe results(0).finalFrame
        // Chimney receives the frame and builds validly
        results(2).pipe.isValid shouldBe true
    }

    // ── V6 multi-slot topology tests ────────────────────────────────────

    it should "produce 4 SlotBuildResults for multi-flue topology (FlueSlot + FlueSlot + ConnectorSlot + ChimneySlot)" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, FlueSlot(Seq.empty), emptyConnector, emptyChimney))
        results.size shouldBe 4
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
        results(1).pipeType shouldBe FluePipeT
        results(1).label shouldBe "Flue"
        results(2).pipeType shouldBe ConnectorPipeT
        results(2).label shouldBe "Connector"
        results(3).pipeType shouldBe ChimneyPipeT
        results(3).label shouldBe "Chimney"
    }

    it should "produce valid pipe/mappingFn for all slots in multi-flue topology" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, FlueSlot(Seq.empty), emptyConnector, emptyChimney))
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "produce 2 SlotBuildResults for flue-only + chimney (no connector)" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, emptyChimney))
        results.size shouldBe 2
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
        results(1).pipeType shouldBe ChimneyPipeT
        results(1).label shouldBe "Chimney"
    }

    it should "produce valid pipe/mappingFn for flue-only + chimney topology" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, emptyChimney))
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "produce 4 SlotBuildResults for mixed topology (FlueSlot + ThermalFlueSlot + ConnectorSlot + ChimneySlot)" in {
        val thermalFlue = ThermalFlueSlot(Seq.empty)
        val results     = PipeChainGeneric.build(Seq(emptyFlue, thermalFlue, emptyConnector, emptyChimney))
        results.size shouldBe 4
        // Both flue slots map to FluePipeT
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
        results(1).pipeType shouldBe FluePipeT
        results(1).label shouldBe "Flue"
        results(2).pipeType shouldBe ConnectorPipeT
        results(3).pipeType shouldBe ChimneyPipeT
    }

    it should "produce valid pipe/mappingFn for mixed FlueSlot + ThermalFlueSlot topology" in {
        val thermalFlue = ThermalFlueSlot(Seq.empty)
        val results     = PipeChainGeneric.build(Seq(emptyFlue, thermalFlue, emptyConnector, emptyChimney))
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "chain frames through 3+ flue slots with actual descriptors" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // First flue: starts from wrapper frame and adds a vertical section
        val flueDescr1 = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // Second flue: no initial direction, relies on frame from first flue
        val flueDescr2 = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm         ),
            AddSectionVertical("sec2", 80.cm)
        )
        // Third flue: also relies on inherited frame
        val flueDescr3 = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm         ),
            AddSectionVertical("sec3", 60.cm)
        )
        val results    = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr1), FlueSlot(flueDescr2), FlueSlot(flueDescr3), emptyChimney),
            initialFrame = rearUpFrame
        )

        results.size shouldBe 4

        // First flue produces a final frame (it set direction)
        results(0).finalFrame.isDefined shouldBe true
        // Second flue inherits the frame and produces its own final frame
        results(1).pipe.isValid shouldBe true
        results(1).finalFrame.isDefined shouldBe true
        // Third flue also inherits and produces a final frame
        results(2).pipe.isValid shouldBe true
        results(2).finalFrame.isDefined shouldBe true
        // Chimney at the end builds validly
        results(3).pipe.isValid shouldBe true
    }

    it should "chain frame from FlueSlot through ThermalFlueSlot to ConnectorSlot" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // EN15544 flue starts from wrapper frame → produces a final frame
        val flueDescr        = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // EN13384 thermal flue — no initial direction, inherits frame from EN15544 flue
        val thermalFlueDescr = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec2", 80.cm                    )
        )
        // Connector — inherits frame from thermal flue
        val connDescr        = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec3", 50.cm                    )
        )
        val results          = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ThermalFlueSlot(thermalFlueDescr), ConnectorSlot(connDescr), emptyChimney),
            initialFrame = rearUpFrame
        )

        results.size shouldBe 4
        // EN15544 flue produces a frame
        results(0).finalFrame.isDefined shouldBe true
        // Thermal flue inherits and builds successfully
        results(1).pipe.isValid shouldBe true
        results(1).idsMappingFn.isValid shouldBe true
        // Connector inherits and builds successfully
        results(2).pipe.isValid shouldBe true
        results(2).idsMappingFn.isValid shouldBe true
        // Chimney terminal slot builds validly
        results(3).pipe.isValid shouldBe true
    }

    it should "propagate frame across empty flue slot in multi-flue chain" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // First flue starts from wrapper frame → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // Second flue is empty (e.g., placeholder in V6 multi-slot topology)
        // Third slot is chimney
        val results   = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), FlueSlot(Seq.empty), emptyChimney),
            initialFrame = rearUpFrame
        )

        results.size shouldBe 3
        // First flue produces a frame
        results(0).finalFrame.isDefined shouldBe true
        // Empty second flue passes through the inherited frame
        results(1).finalFrame shouldBe results(0).finalFrame
        // Chimney still builds validly — frame carries through
        results(2).pipe.isValid shouldBe true
    }

    // ── V6 thermal-only and connector-only topologies ──────────────────

    it should "produce 3 SlotBuildResults for ThermalFlueSlot-only topology (no EN15544 flue)" in {
        val thermalFlue = ThermalFlueSlot(Seq.empty)
        val results     = PipeChainGeneric.build(Seq(thermalFlue, emptyConnector, emptyChimney))
        results.size shouldBe 3
        results(0).pipeType shouldBe FluePipeT
        results(0).label shouldBe "Flue"
        results(1).pipeType shouldBe ConnectorPipeT
        results(2).pipeType shouldBe ChimneyPipeT
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "produce 3 SlotBuildResults for multiple ThermalFlueSlots + chimney" in {
        val tf1     = ThermalFlueSlot(Seq.empty)
        val tf2     = ThermalFlueSlot(Seq.empty)
        val results = PipeChainGeneric.build(Seq(tf1, tf2, emptyChimney))
        results.size shouldBe 3
        results(0).pipeType shouldBe FluePipeT
        results(1).pipeType shouldBe FluePipeT
        results(2).pipeType shouldBe ChimneyPipeT
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    it should "produce 2 SlotBuildResults for connector-only + chimney (no flue)" in {
        val results = PipeChainGeneric.build(Seq(emptyConnector, emptyChimney))
        results.size shouldBe 2
        results(0).pipeType shouldBe ConnectorPipeT
        results(0).label shouldBe "Connector"
        results(1).pipeType shouldBe ChimneyPipeT
        results(1).label shouldBe "Chimney"
        for r <- results do
            r.pipe.isValid shouldBe true
            r.idsMappingFn.isValid shouldBe true
    }

    // ── V6 frame chaining through thermal flue slots with actual descriptors ──

    it should "chain frames through multiple ThermalFlueSlots with actual descriptors" in {

        // First thermal flue starts from wrapper frame and adds a vertical section
        val thermalFlueDescr1 = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec1", 100.cm                   )
        )
        // Second thermal flue inherits frame
        val thermalFlueDescr2 = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec2", 80.cm                    )
        )
        val results           = PipeChainGeneric.build(
            Seq(ThermalFlueSlot(thermalFlueDescr1), ThermalFlueSlot(thermalFlueDescr2), emptyChimney),
            initialFrame = rearUpFrame
        )

        results.size shouldBe 3
        // First thermal flue produces a frame (it set direction)
        results(0).finalFrame.isDefined shouldBe true
        results(0).pipe.isValid shouldBe true
        // Second thermal flue inherits and builds successfully
        results(1).pipe.isValid shouldBe true
        results(1).idsMappingFn.isValid shouldBe true
        // Chimney terminal
        results(2).pipe.isValid shouldBe true
    }

    it should "chain frame across 4 mixed slots: FlueSlot + ThermalFlueSlot + ThermalFlueSlot + ChimneySlot" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // EN15544 flue starts from wrapper frame
        val flueDescr         = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        // First EN13384 thermal flue — inherits frame from EN15544 flue
        val thermalFlueDescr1 = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec2", 80.cm                    )
        )
        // Second EN13384 thermal flue — inherits frame from first thermal flue
        val thermalFlueDescr2 = Seq[ThermalPipeDescr_13384](
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical("sec3", 60.cm                    )
        )
        val results           = PipeChainGeneric.build(
            Seq(
                FlueSlot       (flueDescr        ),
                ThermalFlueSlot(thermalFlueDescr1),
                ThermalFlueSlot(thermalFlueDescr2),
                emptyChimney
            ),
            initialFrame = rearUpFrame
        )

        results.size shouldBe 4
        // EN15544 flue produces a frame
        results(0).finalFrame.isDefined shouldBe true
        // First thermal flue inherits, builds, and produces its own frame
        results(1).pipe.isValid shouldBe true
        results(1).finalFrame.isDefined shouldBe true
        // Second thermal flue inherits from first thermal, builds successfully
        results(2).pipe.isValid shouldBe true
        results(2).finalFrame.isDefined shouldBe true
        // Chimney terminal
        results(3).pipe.isValid shouldBe true
    }

    // ── V6 idsMappingFn independence across slots ──────────────────────

    it should "produce independent idsMappingFn per slot in multi-flue topology" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*

        // Two flues with sections at different indices within their descriptor sequences
        val flueDescr1 = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("sec1", 100.cm)
        )
        val flueDescr2 = Seq[FlowOnlyPipeDescr_15544](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm         ),
            AddSectionVertical("sec2", 80.cm)
        )
        val results    = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr1), FlueSlot(flueDescr2), emptyChimney),
            initialFrame = rearUpFrame
        )

        // Both slots have valid mapping functions
        results(0).idsMappingFn.isValid shouldBe true
        results(1).idsMappingFn.isValid shouldBe true
        val fn0 = results(0).idsMappingFn.toOption.get
        val fn1 = results(1).idsMappingFn.toOption.get

        // Flue 1: index 2 (AddSectionVertical) maps to a section
        fn0(2).isDefined shouldBe true
        // Flue 1: property indices don't map
        fn0(0) shouldBe None
        fn0(1) shouldBe None

        // Flue 2: index 2 (AddSectionVertical) maps to a section — independent from flue 1
        fn1(2).isDefined shouldBe true
        // Flue 2: property indices don't map
        fn1(0) shouldBe None
        fn1(1) shouldBe None
    }

    it should "inherit flow count from flow-only flue into connector and chimney" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Use horizontal pipe so flow split (2 flows) isn't blocked by FlowSplitForbiddenOnAscendingPipe
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows    (2.flows       ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness        (1.mm          ),
            AddSectionHorizontal("flue", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(
                FlueSlot(flueDescr),
                ConnectorSlot(thermalPipeDescr("connector")),
                ChimneySlot  (thermalPipeDescr("chimney")  )
            ),
            initialFrame = rearHorizontalFrame
        )

        results.map(_.finalNFlows) shouldBe Vector(2.flows, 2.flows, 2.flows)
    }

    it should "inherit flow count from thermal flue into connector and chimney" in {
        // Use horizontal pipe so flow split (2 flows) isn't blocked by FlowSplitForbiddenOnAscendingPipe
        val thermalFlueDescr = Seq[ThermalPipeDescr_13384](
            ThermalChannelTopologyOp_13384.SetNumberOfFlows (2.flows                          ),
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer               (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness           (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation        (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionHorizontal("flue", 100.cm                   )
        )

        val results = PipeChainGeneric.build(
            Seq(
                ThermalFlueSlot(thermalFlueDescr),
                ConnectorSlot(thermalPipeDescr("connector")),
                ChimneySlot  (thermalPipeDescr("chimney")  )
            ),
            initialFrame = rearHorizontalFrame
        )

        results.map(_.finalNFlows) shouldBe Vector(2.flows, 2.flows, 2.flows)
    }

    it should "let an explicit next-slot flow count override the inherited value" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Use Horizontal so the connector's split (2→3) isn't blocked by validateSplitNotOnAscending
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows  (2.flows       ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("flue", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(
                FlueSlot(flueDescr),
                ConnectorSlot(thermalPipeDescr("connector", flows = Some(3.flows))),
                ChimneySlot  (thermalPipeDescr("chimney")                         )
            ),
            initialFrame = rearHorizontalFrame
        )

        results.map(_.finalNFlows) shouldBe Vector(2.flows, 3.flows, 3.flows)
    }

    it should "inherit the terminal flow count from a slot, not the first element flow count" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Use Horizontal direction so the split (2→3 flows) isn't blocked by validateSplitNotOnAscending
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows  (2.flows         ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm            ),
            AddSectionVertical("flue-1", 100.cm),
            SetNumberOfFlows  (3.flows         ),
            AddSectionVertical("flue-2", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ConnectorSlot(thermalPipeDescr("connector"))),
            initialFrame = rearHorizontalFrame
        )

        results.map(_.finalNFlows) shouldBe Vector(3.flows, 3.flows)
    }

    it should "preserve inherited flow count through an empty connector" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Use horizontal pipe so flow split (2 flows) isn't blocked by FlowSplitForbiddenOnAscendingPipe
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows    (2.flows       ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness        (1.mm          ),
            AddSectionHorizontal("flue", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ConnectorSlot(Seq.empty), ChimneySlot(thermalPipeDescr("chimney"))),
            initialFrame = rearHorizontalFrame
        )

        results.map(_.finalNFlows) shouldBe Vector(2.flows, 2.flows, 2.flows)
    }

    // ── V7 FlowSplitForbiddenOnAscendingPipe negative tests ──────────────

    it should "reject flow split on ascending pipe in flow-only flue (FlowSplitForbiddenOnAscendingPipe)" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Ascending pipe with SetNumberOfFlows(2.flows) — should be rejected, falling back to 1 flow
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows  (2.flows       ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("flue", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(
                FlueSlot(flueDescr),
                ConnectorSlot(thermalPipeDescr("connector")),
                ChimneySlot  (thermalPipeDescr("chimney")  )
            ),
            initialFrame = rearUpFrame
        )

        // Flow split forbidden on ascending pipe → falls back to 1 flow
        results.map(_.finalNFlows) shouldBe Vector(1.flows, 1.flows, 1.flows)
    }

    it should "reject flow split on ascending pipe in thermal flue (FlowSplitForbiddenOnAscendingPipe)" in {
        // Ascending pipe with SetNumberOfFlows(2.flows) — should be rejected, falling back to 1 flow
        val thermalFlueDescr = Seq[ThermalPipeDescr_13384](
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(2.flows                          ),
            SetThermalPipeProp_13384.SetInnerShape(Circle(150.mm)              ),
            SetThermalPipeProp_13384.SetMaterial  (Material_13384.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer              (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384.SetRoughness          (1.mm                             ),
            SetThermalPipeProp_13384.SetPipeLocation       (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384.AddSectionVertical ("flue", 100.cm                   )
        )

        val results = PipeChainGeneric.build(
            Seq(
                ThermalFlueSlot(thermalFlueDescr),
                ConnectorSlot(thermalPipeDescr("connector")),
                ChimneySlot  (thermalPipeDescr("chimney")  )
            ),
            initialFrame = rearUpFrame
        )

        // Flow split forbidden on ascending pipe → falls back to 1 flow
        results.map(_.finalNFlows) shouldBe Vector(1.flows, 1.flows, 1.flows)
    }

    it should "reject flow split on ascending pipe through empty connector (FlowSplitForbiddenOnAscendingPipe)" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.all.FlowOnlyChannelTopologyOp_15544.*

        // Ascending pipe with SetNumberOfFlows(2.flows) — should be rejected, falling back to 1 flow
        val flueDescr = Seq[FlowOnlyPipeDescr_15544](
            SetNumberOfFlows  (2.flows       ),
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm          ),
            AddSectionVertical("flue", 100.cm)
        )

        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ConnectorSlot(Seq.empty), ChimneySlot(thermalPipeDescr("chimney"))),
            initialFrame = rearUpFrame
        )

        // Flow split forbidden on ascending pipe → falls back to 1 flow
        results.map(_.finalNFlows) shouldBe Vector(1.flows, 1.flows, 1.flows)
    }

    // ── upstreamFailure cascade suppression ────────────────────────────

    it should "set upstreamFailure=true on downstream slots when an upstream slot fails to build" in {
        // Two consecutive SetInnerShape without a section element triggers ShapeNotMaterialized
        val badThermalFlue = ThermalFlueSlot(
            Seq[ThermalPipeDescr_13384](
                SetThermalPipeProp_13384.SetInnerShape(PipeShape.Circle(150.mm)),
                SetThermalPipeProp_13384.SetInnerShape(
                    PipeShape.Circle(200.mm)
                ) // no section between → ShapeNotMaterialized
            )
        )

        val results = PipeChainGeneric.build(Seq(badThermalFlue, emptyChimney))
        results.size shouldBe 2

        // First slot (bad flue) should have an invalid pipe
        results(0).pipe.isValid shouldBe false
        results(0).upstreamFailure shouldBe false // the failing slot itself is not "upstream"

        // Second slot (chimney) should have upstreamFailure=true
        results(1).upstreamFailure shouldBe true
    }

    it should "set upstreamFailure=true on all slots after the first failure" in {
        val badFlue = FlueSlot(
            Seq[FlowOnlyPipeDescr_15544](
                SetFlowOnlyPipeProp_15544.SetInnerShape(Circle(150.mm)),
                SetFlowOnlyPipeProp_15544.SetInnerShape(Circle(200.mm)) // no section → ShapeNotMaterialized
            )
        )

        val results = PipeChainGeneric.build(Seq(badFlue, emptyConnector, emptyChimney))
        results.size shouldBe 3

        results(0).upstreamFailure shouldBe false
        results(0).pipe.isValid shouldBe false
        results(1).upstreamFailure shouldBe true
        results(2).upstreamFailure shouldBe true
    }

    it should "leave upstreamFailure=false for all slots when build succeeds" in {
        val results = PipeChainGeneric.build(Seq(emptyFlue, emptyConnector, emptyChimney))
        for r <- results do r.upstreamFailure shouldBe false
    }

end PipeChainGenericSuite
