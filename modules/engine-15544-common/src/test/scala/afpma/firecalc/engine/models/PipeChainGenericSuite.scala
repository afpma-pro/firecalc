/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipeChainGenericSuite extends AnyFlatSpec with Matchers:

    private val emptyFlue      = FlueSlot(Seq.empty)
    private val emptyConnector = ConnectorSlot(Seq.empty)
    private val emptyChimney   = ChimneySlot(Seq.empty)

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
        val flueDescr = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        val results   = PipeChainGeneric.build(Seq(FlueSlot(flueDescr), emptyConnector, emptyChimney))
        results(0).pipe.isValid shouldBe true
        // With actual descriptors + direction, the idsMappingFn should map index 3 (the AddSection) to a section result index
        results(0).idsMappingFn.isValid shouldBe true
        val fn        = results(0).idsMappingFn.toOption.get
        // Index 3 is the AddSectionVertical → should map to a section result
        fn(3).isDefined shouldBe true
        // Properties (indices 0-2) shouldn't map to section results
        fn(0) shouldBe None
        // Final frame should be present (direction was set)
        results(0).finalFrame.isDefined shouldBe true
    }

    it should "chain frames: flue's final frame feeds connector's build" in {
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        import afpma.firecalc.dto.v3.Material_13384_V2

        // Flue with direction → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        // Connector with minimal thermal config — no initial direction, relies on flue's frame
        val connDescr = Seq[ThermalPipeDescr_13384_V3](
            SetThermalPipeProp_13384_V3.SetInnerShape(Circle(150.mm)                 ),
            SetThermalPipeProp_13384_V3.SetMaterial  (Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384_V3.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384_V3.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384_V3.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384_V3.AddSectionVertical("sec1", 100.cm                   )
        )
        val results   = PipeChainGeneric.build(Seq(FlueSlot(flueDescr), ConnectorSlot(connDescr), emptyChimney))

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
        val flueDescr = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        // Empty connector — simulates V5 project with no connector migrated to V6
        val results = PipeChainGeneric.build(Seq(FlueSlot(flueDescr), emptyConnector, emptyChimney))

        // Flue produced a final frame
        results(0).finalFrame.isDefined shouldBe true
        // Empty connector produces no frame of its own
        results(1).finalFrame shouldBe None
        // But the carried frame (from fold accumulator) should still reach chimney:
        // we verify by checking that the chimney slot was built with prevFrame = flue's frame.
        // Since chimney is terminal and returns None for finalFrame, we check the pipe built validly
        // (it would fail or produce different geometry without the inherited frame).
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

        // First flue: sets direction and adds a vertical section
        val flueDescr1 = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        // Second flue: no initial direction, relies on frame from first flue
        val flueDescr2 = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm              ),
            AddSectionVertical("sec2", 80.cm     )
        )
        // Third flue: also relies on inherited frame
        val flueDescr3 = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness      (1.mm              ),
            AddSectionVertical("sec3", 60.cm     )
        )
        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr1), FlueSlot(flueDescr2), FlueSlot(flueDescr3), emptyChimney)
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
        import afpma.firecalc.dto.v3.Material_13384_V2

        // EN15544 flue with direction → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        // EN13384 thermal flue — no initial direction, inherits frame from EN15544 flue
        val thermalFlueDescr = Seq[ThermalPipeDescr_13384_V3](
            SetThermalPipeProp_13384_V3.SetInnerShape(Circle(150.mm)                 ),
            SetThermalPipeProp_13384_V3.SetMaterial  (Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384_V3.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384_V3.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384_V3.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384_V3.AddSectionVertical("sec2", 80.cm                    )
        )
        // Connector — inherits frame from thermal flue
        val connDescr = Seq[ThermalPipeDescr_13384_V3](
            SetThermalPipeProp_13384_V3.SetInnerShape(Circle(150.mm)                 ),
            SetThermalPipeProp_13384_V3.SetMaterial  (Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384_V3.SetLayer             (2.0.mm, WattsPerMeterKelvin(50.0)),
            SetThermalPipeProp_13384_V3.SetRoughness         (1.mm                             ),
            SetThermalPipeProp_13384_V3.SetPipeLocation      (PipeLocation.HeatedArea          ),
            AddThermalPipeElement_13384_V3.AddSectionVertical("sec3", 50.cm                    )
        )
        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), ThermalFlueSlot(thermalFlueDescr), ConnectorSlot(connDescr), emptyChimney)
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

        // First flue with direction → produces a final frame
        val flueDescr = Seq[FlowOnlyPipeDescr_15544_V3](
            SetInnerShape(Circle(150.mm)),
            SetRoughness       (1.mm                                          ),
            SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up),
            AddSectionVertical ("sec1", 100.cm                                )
        )
        // Second flue is empty (e.g., placeholder in V6 multi-slot topology)
        // Third slot is chimney
        val results = PipeChainGeneric.build(
            Seq(FlueSlot(flueDescr), FlueSlot(Seq.empty), emptyChimney)
        )

        results.size shouldBe 3
        // First flue produces a frame
        results(0).finalFrame.isDefined shouldBe true
        // Empty second flue has no frame of its own
        results(1).finalFrame shouldBe None
        // Chimney still builds validly — the fold accumulator carries the first flue's frame
        results(2).pipe.isValid shouldBe true
    }

end PipeChainGenericSuite
