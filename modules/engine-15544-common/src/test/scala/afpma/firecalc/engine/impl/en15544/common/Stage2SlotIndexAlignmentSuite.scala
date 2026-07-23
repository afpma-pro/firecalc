/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.data.NonEmptyList

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verifies that Stage 2 slot errors carry the correct global SlotIndex.
 *
 * Stage 2 handles slots after the firebox (connector, chimney, etc.).
 * The Stage 2 compute uses `zipWithIndex` with a global offset
 * (`lastFluePipeSlotIdx + 1 + localIdx`) so errors from Stage 2 slots
 * carry the correct global `SlotIndex`.
 *
 * This test verifies that an error from a Stage 2 slot (e.g., ConnectorSlot
 * at global index 2) carries `SlotTarget(SlotIndex(2))`, NOT `SlotTarget(0)`
 * (which would be the wrong local index) and NOT `TypeTarget(ConnectorPipeT)`.
 */
class Stage2SlotIndexAlignmentSuite extends AnyFlatSpec with Matchers:
    import ConnectorPipe_Module.FullDescrResult.extractPipe

    // Local copy of PanelScope for testing (mirrors ui.panels.PanelScope).
    // Kept here to avoid a test-time dependency on the ui module.
    private enum PanelScope:
        case SlotScope(pipeType: PipeType, slotIndex: SlotIndex)
        case TypeScope(pipeType: PipeType)
        case MultiTypeScope(pipeTypes: List[PipeType])

        def sees(target: ErrorTarget): Boolean =
            (this, target) match
                case (PanelScope.SlotScope(_, si), ErrorTarget.SlotTarget(ti)   ) => si == ti
                case (PanelScope.SlotScope(pt, _), ErrorTarget.TypeTarget(tt)   ) => pt == tt
                case (PanelScope.SlotScope(_, _), ErrorTarget.GlobalTarget      ) => true
                case (PanelScope.TypeScope(_), ErrorTarget.SlotTarget(_)        ) => false
                case (PanelScope.TypeScope(pt), ErrorTarget.TypeTarget(tt)      ) => pt == tt
                case (PanelScope.TypeScope(_), ErrorTarget.GlobalTarget         ) => true
                case (PanelScope.MultiTypeScope(_), ErrorTarget.SlotTarget(_)   ) => false
                case (PanelScope.MultiTypeScope(pts), ErrorTarget.TypeTarget(tt)) => pts.contains(tt)
                case (PanelScope.MultiTypeScope(_), ErrorTarget.GlobalTarget    ) => true

    "Stage 2 ConnectorSlot error" should "carry global SlotIndex(2), not local index 0" in {
        // Scenario: post-firebox sequence [FlueSlot, FlueSlot, ConnectorSlot, ChimneySlot]
        // lastFluePipeSlotIdx = 1, so ConnectorSlot is at global index 2.
        // We simulate what the Stage 2 compute does: pass slotIdx = Some(SlotIndex(2))
        // to ConnectorPipe_Module.mkPipeFromIncrDescr.

        val lastFluePipeSlotIdx = 1                                           // Two flue slots before connector
        val connectorLocalIdx   = 0                                           // First slot in Stage 2
        val expectedGlobalIdx   = lastFluePipeSlotIdx + 1 + connectorLocalIdx // = 2

        // Build a connector pipe description that triggers an AddElementMissingAfterSetProp error:
        // set innerShape without adding a section element after it.
        val builder = ConnectorPipe_Module.incremental
        import builder.*
        val descrSeq: Seq[ThermalPipeDescr_13384] = Seq(
            innerShape(PipeShape.Circle(100.mm)),
            roughness(2.mm)
            // Missing: addSectionSlopped("s1", 1.meters) — triggers AddElementMissingAfterSetProp
        )

        // Call mkPipeFromIncrDescr with the global slotIndex (as Stage 2 compute does).
        val slotCtx = SlotContext.forSlot(SlotIndex.unsafe(expectedGlobalIdx))
        val result  = ConnectorPipe_Module.mkPipeFromIncrDescr(descrSeq)(using slotCtx)

        // The result should be Invalid with an AddElementMissingAfterSetProp error.
        val pipeResult = result.extractPipe
        pipeResult.isValid shouldBe false

        // Extract errors from the ValidatedNel
        val errors: NonEmptyList[IncrementalValidation_Error] = pipeResult match
            case cats.data.Validated.Invalid(errs) => errs
            case _                                 => fail("Expected Invalid result")

        errors.toList should have size 1
        val err = errors.head
        err shouldBe a[AddElementMissingAfterSetProp[?]]

        val addErr = err.asInstanceOf[AddElementMissingAfterSetProp[?]]
        addErr.sectionTyp shouldBe ConnectorPipeT
        addErr.slotIndex shouldBe Some(SlotIndex.unsafe(2))

        // KEY ASSERTION: The error's target is SlotTarget(SlotIndex(2)),
        // NOT SlotTarget(SlotIndex(0)) (wrong local index)
        // and NOT TypeTarget(ConnectorPipeT) (would happen if slotIndex were None).
        addErr.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(2))
    }

    "Stage 2 ConnectorSlot error" should "NOT be visible to a different flue slot scope" in {
        // This verifies that an error targeting SlotTarget(SlotIndex(2))
        // is NOT the same as SlotTarget(SlotIndex(0)) — demonstrating no cross-slot leakage.

        val lastFluePipeSlotIdx = 1
        val connectorLocalIdx   = 0
        val expectedGlobalIdx   = lastFluePipeSlotIdx + 1 + connectorLocalIdx // = 2

        val builder = ConnectorPipe_Module.incremental
        import builder.*
        val descrSeq: Seq[ThermalPipeDescr_13384] = Seq(
            innerShape(PipeShape.Circle(100.mm))
            // Missing addSectionSlopped → triggers error
        )

        val slotCtx = SlotContext.forSlot(SlotIndex.unsafe(expectedGlobalIdx))
        val result  = ConnectorPipe_Module.mkPipeFromIncrDescr(descrSeq)(using slotCtx)

        val pipeResult = result.extractPipe
        pipeResult.isValid shouldBe false

        val errors: NonEmptyList[IncrementalValidation_Error] = pipeResult match
            case cats.data.Validated.Invalid(errs) => errs
            case _                                 => fail("Expected Invalid result")

        val errTarget = errors.head.target

        // The error targets SlotIndex(2), NOT SlotIndex(0).
        errTarget shouldBe ErrorTarget.SlotTarget     (SlotIndex.unsafe(2))
        errTarget should not be ErrorTarget.SlotTarget(SlotIndex.unsafe(0))

        // Verify via PanelScope.sees: no cross-panel leak
        PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0)).sees(errTarget) shouldBe false
        PanelScope.SlotScope(ConnectorPipeT, SlotIndex.unsafe(2)).sees(errTarget) shouldBe true
    }

    "Stage 2 index computation" should "map local index 0 to global index 2 when lastFluePipeSlotIdx is 1" in {
        // Direct test of the index computation formula used in Stage 2:
        // slotIdx = SlotIndex.from(lastFluePipeSlotIdx + 1 + localIdx)

        val lastFluePipeSlotIdx = 1
        val localIdx            = 0
        val computed            = SlotIndex.from(lastFluePipeSlotIdx + 1 + localIdx)

        computed shouldBe Some(SlotIndex.unsafe(2))
        computed.map(_.value) shouldBe Some(2)
    }

    "Stage 2 index computation" should "map local index 1 to global index 3 when lastFluePipeSlotIdx is 1" in {
        // ChimneySlot at local index 1 should be at global index 3.

        val lastFluePipeSlotIdx = 1
        val localIdx            = 1
        val computed            = SlotIndex.from(lastFluePipeSlotIdx + 1 + localIdx)

        computed shouldBe Some(SlotIndex.unsafe(3))
        computed.map(_.value) shouldBe Some(3)
    }

end Stage2SlotIndexAlignmentSuite
