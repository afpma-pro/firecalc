/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran&#231;aise du Po&#235;le Ma&#235;onn&#233; Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.data.NonEmptyList

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Integration tests for the error scoping filter.
 *
 * Verifies that `PanelStatusHelper.filterErrors` correctly applies the
 * scope-containment model end-to-end: slot-scoped errors only appear on
 * panels for the matching slot, type-scoped errors appear on all panels
 * of the matching type, and global errors appear everywhere.
 */
class ErrorScopingIntegrationTest extends AnyFreeSpec with Matchers:

    // ── Helper: build a ValidatedNel with controlled error targets ──

    /**
     * Creates a TargetedError with the given ErrorTarget.
     * Uses UnexpectedDevError (an MCalc_Error subtype) + TargetedError mixin.
     */
    private def mkTargetedError(tgt: ErrorTarget): MCalc_Error =
        new UnexpectedDevError(s"TargetedError($tgt)") with TargetedError:
            def target: ErrorTarget = tgt

    private def mkInvalid(errors: MCalc_Error*): Validated[NonEmptyList[MCalc_Error], Unit] =
        Invalid(NonEmptyList.fromListUnsafe(errors.toList))

    private def mkValid[A](a: A): Validated[NonEmptyList[MCalc_Error], A] =
        Valid(a)

    /** Extract the ErrorTarget from a TargetedError, used for assertions. */
    private def getTarget(err: MCalc_Error): ErrorTarget =
        err match
            case te: TargetedError => te.target
            case _ => ErrorTarget.GlobalTarget

    // ── Slot-scoped error isolation ──

    "FlueSlot#0 panel sees only slot 0 errors, not slot 2 errors" in {
        val scope    = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors   = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
        )
        val filtered = PanelStatusHelper.filterErrors[Unit](scope, errors)

        filtered.isInvalid shouldBe true
        val remaining = filtered.toEither.left.get.head
        getTarget(remaining) shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(0))
    }

    "FlueSlot#2 panel sees only slot 2 errors, not slot 0 errors" in {
        val scope    = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors   = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
        )
        val filtered = PanelStatusHelper.filterErrors[Unit](scope, errors)

        filtered.isInvalid shouldBe true
        val remaining = filtered.toEither.left.get.head
        getTarget(remaining) shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(2))
    }

    "No cross-slot leakage: slot 0 and slot 2 each see only their own" in {
        val scope0 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val scope2 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(1)))
        )

        val filtered0 = PanelStatusHelper.filterErrors[Unit](scope0, errors)
        val filtered2 = PanelStatusHelper.filterErrors[Unit](scope2, errors)

        // Slot 0 sees only its own error
        filtered0.toEither.left.get.toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(0)))

        // Slot 2 sees only its own error
        filtered2.toEither.left.get.toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
    }

    // ── Type-scoped error visibility ──

    "Type-scoped FluePipeT error appears on all FlueSlot panels" in {
        val scope0 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val scope2 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors = mkInvalid(mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)))

        val filtered0 = PanelStatusHelper.filterErrors[Unit](scope0, errors)
        val filtered2 = PanelStatusHelper.filterErrors[Unit](scope2, errors)

        filtered0.isInvalid shouldBe true
        filtered2.isInvalid shouldBe true
    }

    "Type-scoped error does NOT appear on panels of a different type" in {
        val scope  = PanelScope.SlotScope(ConnectorPipeT, SlotIndex.unsafe(0))
        val errors = mkInvalid(mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)))

        val filtered = PanelStatusHelper.filterErrors[Unit](scope, errors)

        // All errors filtered out -> ErrorsInOtherSectionType meta-error
        filtered.toEither.left.get.head shouldBe ErrorsInOtherSectionType
    }

    // ── Global error visibility ──

    "Global errors appear on all panels regardless of scope" in {
        val slotScope  = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val typeScope  = PanelScope.TypeScope(FireboxPipeT)
        val multiScope = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))

        // UnexpectedDevError is not a TargetedError -> treated as global
        val globalErr: MCalc_Error = UnexpectedDevError("global error")

        val errors = mkInvalid(globalErr)

        PanelStatusHelper.filterErrors[Unit](slotScope, errors).isInvalid shouldBe true
        PanelStatusHelper.filterErrors[Unit](typeScope, errors).isInvalid shouldBe true
        PanelStatusHelper.filterErrors[Unit](multiScope, errors).isInvalid shouldBe true
    }

    // ── Mixed error filtering ──

    "Mixed errors: panel sees only its own slot errors + type errors + globals" in {
        val scope  = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(1))
        val errors = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))), // filtered
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(1))), // kept
            mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)          ), // kept
            mkTargetedError(ErrorTarget.TypeTarget(ConnectorPipeT)     ), // filtered
            mkTargetedError(ErrorTarget.GlobalTarget) // kept
        )

        val filtered  = PanelStatusHelper.filterErrors[Unit](scope, errors)
        filtered.isInvalid shouldBe true
        val remaining = filtered.toEither.left.get.toList
        remaining.map(getTarget) should contain theSameElementsAs List(
            ErrorTarget.SlotTarget(SlotIndex.unsafe(1)),
            ErrorTarget.TypeTarget(FluePipeT),
            ErrorTarget.GlobalTarget
        )
    }

    // ── Valid pass-through ──

    "Valid results pass through unchanged" in {
        val scope    = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val valid    = mkValid("result")
        val filtered = PanelStatusHelper.filterErrors[String](scope, valid)

        filtered shouldBe Valid("result")
    }

    // ── ResultsNotComputed always filtered ──

    "ResultsNotComputed is always filtered out" in {
        val scope    = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors   = mkInvalid(ResultsNotComputed)
        val filtered = PanelStatusHelper.filterErrors[Unit](scope, errors)

        // ResultsNotComputed is filtered, no errors left -> ErrorsInOtherSectionType
        filtered.toEither.left.get.head shouldBe ErrorsInOtherSectionType
    }

end ErrorScopingIntegrationTest
