/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
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
 * Integration tests for the error scoping verdict.
 *
 * Verifies that `PanelStatusHelper.verdict` correctly applies the
 * scope-containment model end-to-end: slot-scoped errors only appear on
 * panels for the matching slot, type-scoped errors appear on all panels
 * of the matching type, and global errors appear everywhere.
 *
 * Precedence (unit-tested at every case below):
 *  1. valid result → `Ok`;
 *  2. real errors this scope sees → `Shown` (reals only);
 *  3. otherwise, a real error hidden in another scope → `OtherSection`;
 *  4. otherwise every member was a pending state → `Neutral`.
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

    /** Extract the error list from a `Shown` verdict, failing otherwise. */
    private def shownErrors(verdict: PanelVerdict): NonEmptyList[MCalc_Error] =
        verdict match
            case PanelVerdict.Shown(errs) => errs
            case other                    => fail(s"Expected Shown, got $other")

    // ── Slot-scoped error isolation ──

    "FlueSlot#0 panel sees only slot 0 errors, not slot 2 errors" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors  = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
        )
        val verdict = PanelStatusHelper.verdict(scope, errors)

        shownErrors(verdict).toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(0)))
    }

    "FlueSlot#2 panel sees only slot 2 errors, not slot 0 errors" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors  = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
        )
        val verdict = PanelStatusHelper.verdict(scope, errors)

        shownErrors(verdict).toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
    }

    "No cross-slot leakage: slot 0 and slot 2 each see only their own" in {
        val scope0 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val scope2 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2))),
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(1)))
        )

        val verdict0 = PanelStatusHelper.verdict(scope0, errors)
        val verdict2 = PanelStatusHelper.verdict(scope2, errors)

        // Slot 0 sees only its own error
        shownErrors(verdict0).toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(0)))

        // Slot 2 sees only its own error
        shownErrors(verdict2).toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(2)))
    }

    // ── Type-scoped error visibility ──

    "Type-scoped FluePipeT error appears on all FlueSlot panels" in {
        val scope0 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val scope2 = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        val errors = mkInvalid(mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)))

        val verdict0 = PanelStatusHelper.verdict(scope0, errors)
        val verdict2 = PanelStatusHelper.verdict(scope2, errors)

        shownErrors(verdict0).toList.map(getTarget) shouldBe List(ErrorTarget.TypeTarget(FluePipeT))
        shownErrors(verdict2).toList.map(getTarget) shouldBe List(ErrorTarget.TypeTarget(FluePipeT))
    }

    "Type-scoped error does NOT appear on panels of a different type" in {
        val scope  = PanelScope.SlotScope(ConnectorPipeT, SlotIndex.unsafe(0))
        val errors = mkInvalid(mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)))

        val verdict = PanelStatusHelper.verdict(scope, errors)

        // All errors filtered out → OtherSection (the meta-error)
        verdict shouldBe PanelVerdict.OtherSection
    }

    // ── Global error visibility ──

    "Global errors appear on all panels regardless of scope" in {
        val slotScope  = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val typeScope  = PanelScope.TypeScope(FireboxPipeT)
        val multiScope = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))

        // UnexpectedDevError is not a TargetedError → treated as global
        val globalErr: MCalc_Error = UnexpectedDevError("global error")

        val errors = mkInvalid(globalErr)

        shownErrors(PanelStatusHelper.verdict(slotScope, errors) ).toList should have size 1
        shownErrors(PanelStatusHelper.verdict(typeScope, errors) ).toList should have size 1
        shownErrors(PanelStatusHelper.verdict(multiScope, errors)).toList should have size 1
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

        val verdict = PanelStatusHelper.verdict(scope, errors)
        shownErrors(verdict).toList.map(getTarget) should contain theSameElementsAs List(
            ErrorTarget.SlotTarget(SlotIndex.unsafe(1)),
            ErrorTarget.TypeTarget(FluePipeT),
            ErrorTarget.GlobalTarget
        )
    }

    // ── Valid pass-through ──

    "Valid results yield the Ok verdict" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val valid   = mkValid("result")
        val verdict = PanelStatusHelper.verdict(scope, valid)

        verdict shouldBe PanelVerdict.Ok
    }

    // ── Neutral system-state sentinels (D3) ──

    "ResultsNotComputed alone renders neutral, NOT the meta-error" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors  = mkInvalid(ResultsNotComputed)
        val verdict = PanelStatusHelper.verdict(scope, errors)

        // The sentinel is classified as Neutral by the sealed family;
        // the panel renders no icon / no message.
        verdict shouldBe PanelVerdict.Neutral
    }

    "RemoteFireboxPending is global (kept by every scope) and neutral" in {
        val slotScope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val typeScope = PanelScope.TypeScope(FireboxPipeT)

        val verdictSlot = PanelStatusHelper.verdict(slotScope, mkInvalid(RemoteFireboxPending))
        val verdictType = PanelStatusHelper.verdict(typeScope, mkInvalid(RemoteFireboxPending))

        verdictSlot shouldBe PanelVerdict.Neutral
        verdictType shouldBe PanelVerdict.Neutral
    }

    "RemoteFireboxUnavailable is NOT neutral — the terminal verdict stays visible" in {
        val scope    = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val terminal = RemoteFireboxUnavailable("service unreachable")
        val verdict  = PanelStatusHelper.verdict(scope, mkInvalid(terminal))

        // Only the pending sub-family renders neutral. A service that cannot
        // answer is a verdict the user must see, so the panel shows it.
        shownErrors(verdict).toList.head shouldBe terminal
    }

    "A real error in another scope still wins over a pending marker" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        // Slot 2 targeted error (hidden here) + a pending marker (kept).
        val errors  = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(2))),
            RemoteFireboxPending
        )
        val verdict = PanelStatusHelper.verdict(scope, errors)

        // The user must fix something that lives in another section: the
        // meta-error is the honest message, even mid-pending.
        verdict shouldBe PanelVerdict.OtherSection
    }

    "A visible real error coexisting with a pending marker → Shown(reals only)" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        // Slot 0 targeted error (visible here) + a pending marker (stripped).
        val errors  = mkInvalid(
            mkTargetedError(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))),
            RemoteFireboxPending
        )
        val verdict = PanelStatusHelper.verdict(scope, errors)

        // The visible real is shown; the pending sentinel is stripped —
        // Shown carries reals only, never the marker.
        shownErrors(verdict).toList.map(getTarget) shouldBe List(ErrorTarget.SlotTarget(SlotIndex.unsafe(0)))
    }

    // ── FireboxGeometryRejectedError scoping ──

    "Firebox panel MultiTypeScope sees FireboxGeometryRejectedError as itself, not the meta-error" in {
        val scope   = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))
        val geoErr  = FireboxGeometryRejectedError("geometrie refusée")
        val errors  = mkInvalid(geoErr)
        val verdict = PanelStatusHelper.verdict(scope, errors)

        // TypeTarget(FireboxPipeT) is in the multi-type list → the real error is kept
        shownErrors(verdict).toList.head shouldBe geoErr
    }

    "CombustionAir TypeScope does NOT see FireboxGeometryRejectedError" in {
        val scope   = PanelScope.TypeScope(CombustionAirPipeT)
        val errors  = mkInvalid(FireboxGeometryRejectedError("geometrie refusée"))
        val verdict = PanelStatusHelper.verdict(scope, errors)

        // TypeScope(pt).sees(TypeTarget(tt)) is pt == tt: CombustionAirPipeT != FireboxPipeT
        verdict shouldBe PanelVerdict.OtherSection
    }

    "AirIntake TypeScope does NOT see FireboxGeometryRejectedError" in {
        val scope   = PanelScope.TypeScope(AirIntakePipeT)
        val errors  = mkInvalid(FireboxGeometryRejectedError("geometrie refusée"))
        val verdict = PanelStatusHelper.verdict(scope, errors)

        verdict shouldBe PanelVerdict.OtherSection
    }

    "Flue SlotScope does NOT see FireboxGeometryRejectedError" in {
        val scope   = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors  = mkInvalid(FireboxGeometryRejectedError("geometrie refusée"))
        val verdict = PanelStatusHelper.verdict(scope, errors)

        verdict shouldBe PanelVerdict.OtherSection
    }

    "Mixed errors: Flue SlotScope keeps the flue error, drops the firebox geometry rejection" in {
        val scope  = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        val errors = mkInvalid(
            FireboxGeometryRejectedError("geometrie refusée"), // filtered
            mkTargetedError(ErrorTarget.TypeTarget(FluePipeT)) // kept
        )

        val verdict = PanelStatusHelper.verdict(scope, errors)
        // A scoped error remains → no OtherSection meta-error
        shownErrors(verdict).toList.map(getTarget) shouldBe List(ErrorTarget.TypeTarget(FluePipeT))
    }

end ErrorScopingIntegrationTest
