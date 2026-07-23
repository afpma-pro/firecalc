/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.SlotIndex
import afpma.firecalc.engine.typeclasses.PropsStateOps

import cats.Show
import cats.data.ValidatedNel

import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.domain.ShapeState
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests that slot indices propagate correctly through error paths.
 *
 * Verifies that:
 * - Slotted operations produce ErrorTarget.SlotTarget(real_index), not SlotTarget(0).
 * - Standalone (None) operations produce ErrorTarget.TypeTarget(pipeType), never SlotTarget(0).
 */
class SlotIndexPropagationSuite extends AnyFlatSpec with Matchers:

    // Simple shape type for kernel-level tests
    case class TestShape(label: String)
    given Show[TestShape] = Show.show[TestShape](s => s"TestShape(${s.label})")

    // Minimal test state and PropsStateOps instance for builder-level tests
    case class TestState(
        shapeState: ShapeState,
        roughness : Option[Roughness] = None,
        nFlows    : NbOfFlows         = NbOfFlows(1)
    )

    given PropsStateOps[TestState] with
        def isValid                (state: TestState                                     ): Boolean                      = true
        def getShapeState          (state: TestState                                     ): ShapeState                   = state.shapeState
        def getRoughness           (state: TestState                                     ): Option[Roughness]            = state.roughness
        def getNFlows              (state: TestState                                     ): NbOfFlows                    = state.nFlows
        def getPendingFlowAreaCheck(state: TestState                                     ): Option[PendingFlowAreaCheck] = None
        def setPendingFlowAreaCheck(state: TestState, check: Option[PendingFlowAreaCheck]): TestState                    = state
        def setInnerShape(state: TestState, shape: PipeShape): TestState =
            state.copy(shapeState = ShapeState.Set(shape))
        def setNFlows(state: TestState, nFlows: NbOfFlows)   : TestState =
            state.copy(nFlows = nFlows)
        def materialize(state: TestState): TestState = state

    // ========================================================================
    // Tests 1-3: DFC / FlowOnly interpolation error propagation
    // ========================================================================

    "DynamicFrictionCoeffOp.interpolateHelperE" should "propagate SlotIndex to SlotTarget when slotted" in {
        // Test 1: DFC_13384 slotted propagation
        given SlotContext = SlotContext.forSlot(SlotIndex.unsafe(3))

        val shape  = TestShape("test")
        val result = DynamicFrictionCoeffOp.interpolateHelperE[TestShape](
            shape             = shape,
            sectionTyp        = FluePipeT,
            resName           = "test_table",
            tsvTableRawString = "x\ty\n0\t1\n10\t2",
            xHeader           = "x",
            xi                = 100.0, // Out of range [0, 10]
            xMinMax           = (0.0, 10.0),
            yHeaderSelectFunc = _ => Right("y"),
            yCriteria         = None
        )

        result.isLeft shouldBe true
        val err = result.left.toOption.get
        err shouldBe a[SingularFlowResistanceCoeffError.ValueOutOfBound[?]]
        err.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(3))
    }

    it should "propagate None to TypeTarget when standalone" in {
        // Test 2: DFC_13384 standalone (None) propagation
        given SlotContext = SlotContext.unslotted

        val shape  = TestShape("test")
        val result = DynamicFrictionCoeffOp.interpolateHelperE[TestShape](
            shape             = shape,
            sectionTyp        = FluePipeT,
            resName           = "test_table",
            tsvTableRawString = "x\ty\n0\t1\n10\t2",
            xHeader           = "x",
            xi                = 100.0, // Out of range [0, 10]
            xMinMax           = (0.0, 10.0),
            yHeaderSelectFunc = _ => Right("y"),
            yCriteria         = None
        )

        result.isLeft shouldBe true
        val err = result.left.toOption.get
        err shouldBe a[SingularFlowResistanceCoeffError.ValueOutOfBound[?]]
        err.target shouldBe ErrorTarget.TypeTarget(FluePipeT)
    }

    it should "propagate real SlotIndex for FlowOnly_15544 path" in {
        // Test 3: FlowOnly_15544 real index propagation
        // Tested at the kernel helper level where SlotContext enters
        given SlotContext = SlotContext.forSlot(SlotIndex.unsafe(2))

        val shape  = TestShape("test")
        val result = DynamicFrictionCoeffOp.interpolateHelperE[TestShape](
            shape             = shape,
            sectionTyp        = CombustionAirPipeT,
            resName           = "test_table",
            tsvTableRawString = "x\ty\n0\t1\n10\t2",
            xHeader           = "x",
            xi                = -5.0, // Out of range [0, 10]
            xMinMax           = (0.0, 10.0),
            yHeaderSelectFunc = _ => Right("y"),
            yCriteria         = None
        )

        result.isLeft shouldBe true
        val err = result.left.toOption.get
        err shouldBe a[SingularFlowResistanceCoeffError.ValueOutOfBound[?]]
        err.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(2))
    }

    // ========================================================================
    // Tests 4-5: Builder validateMaterialized error propagation
    // ========================================================================

    "PropsStateOps.validateMaterialized" should "produce TypeTarget when standalone (None)" in {
        // Test 4: Builder/validateMaterialized standalone (None)
        given SlotContext = SlotContext.unslotted

        val state = TestState(shapeState = ShapeState.Set(PipeShape.Circle(100.mm))) // Set but not materialized
        val result: ValidatedNel[IncrementalValidation_Error, Unit] =
            PropsStateOps[TestState].validateMaterialized       (
                state        = state,
                op           = ShapeNotMaterialized.Operation.SetInnerShape,
                pt           = FluePipeT,
                elementIndex = 1,
                elementName  = "test_element"
            )

        result.isValid shouldBe false
        val errors = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
        errors.head.target shouldBe ErrorTarget.TypeTarget(FluePipeT)
    }

    it should "produce SlotTarget when slotted (Some)" in {
        // Test 5: Builder/validateMaterialized slotted (Some)
        given SlotContext = SlotContext.forSlot(SlotIndex.unsafe(5))

        val state = TestState(shapeState = ShapeState.Set(PipeShape.Circle(100.mm))) // Set but not materialized
        val result: ValidatedNel[IncrementalValidation_Error, Unit] =
            PropsStateOps[TestState].validateMaterialized       (
                state        = state,
                op           = ShapeNotMaterialized.Operation.SetInnerShape,
                pt           = FluePipeT,
                elementIndex = 1,
                elementName  = "test_element"
            )

        result.isValid shouldBe false
        val errors = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
        errors.head.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(5))
    }

end SlotIndexPropagationSuite
