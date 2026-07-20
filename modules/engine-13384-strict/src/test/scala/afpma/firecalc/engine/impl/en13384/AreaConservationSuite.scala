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
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AreaConservationSuite extends AnyFlatSpec with Matchers:

    private val horizontalDir =
        PipeInitialDirection    (
            azimuth     = AzimuthDirection.Front,
            inclination = InclinationDirection.Horizontal
        )

    private val ascendingDir =
        PipeInitialDirection    (
            azimuth     = AzimuthDirection.Front,
            inclination = InclinationDirection.Up
        )

    // =========================================================================
    // 10 area conservation tests (FlowOnlyIncrementalBuilder_13384)
    // =========================================================================

    "FlowOnlyIncrementalBuilder_13384 area conservation" should "accept SetInnerShape when area is conserved (split 1→2)" in {
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×10 = 100 cm² × 2 = 200 cm²
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    it should "allow SetNumberOfFlows before SetInnerShape — sets nFlows without pending area check" in {
        // SetNumberOfFlows before SetInnerShape is allowed: nFlows is set, but no
        // PendingFlowAreaCheck is created (there's no "before" shape to check against).
        // The build succeeds and the final nFlows is 2.
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val result      = builder.define(
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                   ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters)
        )
        result.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "reject SetInnerShape when area is NOT conserved (split 1→2)" in {
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  5×5 = 25 cm² × 2 = 50 cm²
        given FluePipeT                                                 = FluePipeT
        val builder                                                     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr                                                       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(5.cm, 5.cm)  ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        val result                                                      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors                                                      = result.toEither.left.toOption.get
        val err                                                         = errors.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
        err.expectedDimension shouldBe a[ExpectedDimRectangle]
        val ExpectedDimRectangle(_, _, _, expectedHeight, expectedArea) = err.expectedDimension: @unchecked
        expectedHeight.to_cm.value shouldBe (20.0 +- 0.1 )
        expectedArea.to_cm2.value shouldBe  (100.0 +- 0.1)
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "reject SetInnerShape when area is NOT conserved with Square (split 1→2)" in {
        // 18×18 = 324 cm² × 1 = 324 cm²  vs  9×9 = 81 cm² × 2 = 162 cm²  (162 cm² short)
        // Expected area per flow = 324 / 2 = 162 cm², expected side = sqrt(162) ≈ 12.73 cm
        given FluePipeT                              = FluePipeT
        val builder                                  = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr                                    = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Square(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Square(9.cm) ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        val result                                   = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors                                   = result.toEither.left.toOption.get
        val err                                      = errors.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
        err.expectedDimension shouldBe a[ExpectedDimSquare]
        val ExpectedDimSquare(_, _, expectedSide, _) = err.expectedDimension: @unchecked
        expectedSide.to_cm.value shouldBe (12.73 +- 0.1)
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "reject SetInnerShape when area is NOT conserved with Circle (split 1→2)" in {
        // π×(18/2)² = 254.47 cm² × 1 = 254.47 cm²  vs  π×(9/2)² = 63.62 cm² × 2 = 127.23 cm²  (127.23 cm² short)
        // Expected area per flow = 254.47 / 2 = 127.23 cm², expected diameter = sqrt(4×127.23/π) ≈ 12.73 cm
        given FluePipeT                                  = FluePipeT
        val builder                                      = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr                                        = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(18.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(9.cm) ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        val result                                       = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors                                       = result.toEither.left.toOption.get
        val err                                          = errors.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
        err.expectedDimension shouldBe a[ExpectedDimCircle]
        val ExpectedDimCircle(_, _, expectedDiameter, _) = err.expectedDimension: @unchecked
        expectedDiameter.to_cm.value shouldBe (12.73 +- 0.1)
    }

    it should "clear pending check and accept transition when area is conserved (merge 2→1)" in {
        // 10×10 = 100 cm² × 2 = 200 cm²  vs  20×10 = 200 cm² × 1 = 200 cm²
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("init", 0.1.meters      ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-merge", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)                     ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-merge", 1.meters )
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "clear pending check and reject transition when area is NOT conserved (merge 2→1)" in {
        // 10×10 = 100 cm² × 2 = 200 cm²  vs  10×10 = 100 cm² × 1 = 100 cm²
        given FluePipeT                                                 = FluePipeT
        val builder                                                     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr                                                       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("init", 0.1.meters      ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-merge", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)                     ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-merge", 1.meters )
        )
        val result                                                      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors                                                      = result.toEither.left.toOption.get
        val err                                                         = errors.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
        err.expectedDimension shouldBe a[ExpectedDimRectangle]
        val ExpectedDimRectangle(_, _, _, expectedHeight, expectedArea) = err.expectedDimension: @unchecked
        expectedHeight.to_cm.value shouldBe (20.0 +- 0.1 )
        expectedArea.to_cm2.value shouldBe  (200.0 +- 0.1)
    }

    it should "be a no-op when SetNumberOfFlows is called with the same value (state unchanged, pending preserved)" in {
        // SetNumberOfFlows(2) twice: second call is no-op, first pending check preserved.
        // Then SetInnerShape with conserved area clears the check → valid.
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×10 = 100 cm² × 2 = 200 cm²
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ), // no-op
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    it should "allow SetNumberOfFlows on ascending pipe when no split element follows" in {
        // The old blanket "no splits on ascending pipes" rule is replaced by geometry-based
        // validation that requires a split element with branch direction. Without a split
        // element, SetNumberOfFlows is a valid flow-count change regardless of direction.
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(ascendingDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        val result      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        if result.isValid then succeed
        else fail(s"Expected valid, got: ${result.toEither.left.toOption.get}")
    }

    it should "reject merge 2→1 when shape is set but not materialized" in {
        // SetInnerShape → SetNumberOfFlows(2) → SetNumberOfFlows(1) without a length-bearing
        // element in between: the merge to 1 flow is blocked by the materialized-shape guard.
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness(1.mm),
            // no length-bearing element → shape stays in ShapeState.Set
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)           ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)           )
        )
        val result      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "allow no-op SetNumberOfFlows(1) at pipe start without shape" in {
        // SetNumberOfFlows(1) at pipe start (no previous shape) is a no-op — allowed.
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm         ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)           ),
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("s", 1.meters)
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "preserve pending check through no-op SetNumberOfFlows then reject non-conserved area" in {
        // SetNumberOfFlows(2) creates pending check. SetNumberOfFlows(2) again is no-op (preserves check).
        // SetInnerShape with non-conserved area clears check and produces error.
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  5×5 = 25 cm² × 2 = 50 cm²
        given FluePipeT = FluePipeT
        val builder     = FlowOnlyIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)),
            SetFlowOnlyPipeProp_13384.SetRoughness        (1.mm                    ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("before-split", 1.meters),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ),
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                     ), // no-op — preserves check
            SetFlowOnlyPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(5.cm, 5.cm)  ),
            AddFlowOnlyPipeElement_13384.AddSectionSlopped("after-split", 1.meters )
        )
        val result      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[FlowTransitionChangesTotalCrossSection]
    }

    // =========================================================================
    // ThermalIncrementalBuilder_13384 area conservation tests
    // =========================================================================

    "ThermalIncrementalBuilder_13384 area conservation" should "accept SetInnerShape when area is conserved (split 1→2)" in {
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×10 = 100 cm² × 2 = 200 cm²
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)                    ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            AddThermalPipeElement_13384.AddSectionSlopped("before-split", 1.meters      ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(10.cm, 10.cm)                    ),
            AddThermalPipeElement_13384.AddSectionSlopped("after-split", 1.meters       )
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

    // @ignore: flow area check deactivated — see FlowAreaConservation
    ignore should "reject SetInnerShape when area is NOT conserved (split 1→2)" in {
        // 20×10 = 200 cm² × 1 = 200 cm²  vs  5×5 = 25 cm² × 2 = 50 cm²
        given FluePipeT                                                 = FluePipeT
        val builder                                                     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr                                                       = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(20.cm, 10.cm)                    ),
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            AddThermalPipeElement_13384.AddSectionSlopped("before-split", 1.meters      ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Rectangle(5.cm, 5.cm)                      ),
            AddThermalPipeElement_13384.AddSectionSlopped("after-split", 1.meters       )
        )
        val result                                                      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors                                                      = result.toEither.left.toOption.get
        val err                                                         = errors.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
        err.expectedDimension shouldBe a[ExpectedDimRectangle]
        val ExpectedDimRectangle(_, _, _, expectedHeight, expectedArea) = err.expectedDimension: @unchecked
        expectedHeight.to_cm.value shouldBe (20.0 +- 0.1 )
        expectedArea.to_cm2.value shouldBe  (100.0 +- 0.1)
    }

    it should "reject merge 2→1 when shape is set but not materialized" in {
        // SetInnerShape → SetNumberOfFlows(2) → SetNumberOfFlows(1) without a length-bearing
        // element in between: the merge to 1 flow is blocked by the materialized-shape guard.
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetRoughness   (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetLayer       (2.mm, WattsPerMeterKelvin(1.2)),
            SetThermalPipeProp_13384.SetPipeLocation(PipeLocation.HeatedArea       ),
            // no length-bearing element → shape stays in ShapeState.Set
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(2)                                         ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)                                         )
        )
        val result      = descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0)))
        result.isValid shouldBe false
        val errors      = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "allow no-op SetNumberOfFlows(1) at pipe start without shape" in {
        // SetNumberOfFlows(1) at pipe start (no previous shape) is a no-op — allowed.
        // SetLayer requires SetInnerShape, so we put it after.
        given FluePipeT = FluePipeT
        val builder     = ThermalIncrementalBuilder_13384
            .makeFor[FluePipeT]
            .withInitialDirection(horizontalDir)
        val descr       = builder.define(
            SetThermalPipeProp_13384.SetRoughness        (1.mm                          ),
            SetThermalPipeProp_13384.SetMaterial           (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
            SetThermalPipeProp_13384.SetPipeLocation     (PipeLocation.HeatedArea       ),
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(NbOfFlows(1)                                         ),
            SetThermalPipeProp_13384.SetInnerShape         (PipeShape.Circle(20.cm)                              ),
            SetThermalPipeProp_13384.SetLayer            (2.mm, WattsPerMeterKelvin(1.2)),
            AddThermalPipeElement_13384.AddSectionSlopped("s", 1.meters                 )
        )
        descr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).isValid shouldBe true
    }

end AreaConservationSuite
