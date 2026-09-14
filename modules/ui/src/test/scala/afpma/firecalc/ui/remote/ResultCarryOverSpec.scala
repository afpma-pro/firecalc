/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.standard.*
import afpma.firecalc.ui.models.project.ProjectId

import cats.data.NonEmptyList
import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Unit tests for the pending-window carry-over: the `(project, kind)` key match
 * and the policy that decides what a pending window shows.
 *
 * The contract is what the user keeps seeing while a remote fetch is in
 * flight: the previous numbers, and the previous errors still to fix — minus
 * the "no result" states the window itself supersedes.
 *
 * Pinned at the store seam because the case that matters most, a real error
 * surviving the window, cannot be provoked from the acceptance harness: the
 * producers are the result builder's defensive `Try` catch and the engine's own
 * invariant guards, none of which the harness can fake into failing.
 *
 * Each test drives its own store (no process-global state between them) and
 * uses `Int` as the result type where the carried value itself never matters.
 */
class ResultCarryOverSpec extends AnyFreeSpec with Matchers:

    private val projectId: ProjectId   = ProjectId("carry-over-spec")
    private val kind     : FireboxKind = FireboxKind.Ecolabeled

    private def invalid(errors: MCalc_Error*): VNelMcalcErr[Int] =
        Validated.invalid(NonEmptyList.fromListUnsafe(errors.toList))

    /** A store holding `stored` under the `(project, kind)` key under test. */
    private def storeOf(stored: VNelMcalcErr[Int]): ResultCarryOver[Int] =
        val store = new ResultCarryOver[Int]
        store.record(projectId, kind, stored)
        store

    "a genuine development-crash marker is NOT superseded" in {
        // What the result builder's Try catch produces when the application
        // cannot be built. Superseding it like the terminal service verdict
        // would lose the only evidence of the crash for the whole window: the
        // window replaces "no result", not "something is broken".
        val stored = invalid(UnexpectedDevError("boom"))
        storeOf(stored).during(projectId, kind) shouldBe stored
    }

    "a valid result carries over verbatim" in {
        val stored: VNelMcalcErr[Int] = Validated.validNel(42)
        storeOf(stored).during(projectId, kind) shouldBe stored
    }

    "the terminal service-unavailable verdict is superseded by the pending marker" in {
        // Nothing to carry over — the previous cycle produced no numbers — and
        // "service unreachable" is a stale claim while a retry is in flight.
        storeOf(invalid(RemoteFireboxUnavailable("service down"))).during(projectId, kind) shouldBe
            Validated.invalidNel(RemoteFireboxPending)
    }

    "only the no-result member of a list is superseded, not the whole list" in {
        // Superseding is per element. A settle emits either the terminal
        // verdict alone or real errors, so this shape is not reached from the
        // store today — it pins the granularity of the rule against a
        // "any no-result member → pending marker" reading.
        val stored = invalid(RemoteFireboxUnavailable("service down"), UnexpectedDevError("boom"))
        storeOf(stored).during(projectId, kind) shouldBe invalid(UnexpectedDevError("boom"))
    }

    "the (project, kind) key decides — another project or another kind reads as the pending marker" in {
        // A kind switch must never carry the previous kind's numbers over: they
        // do not describe the visible inputs.
        val store = storeOf(Validated.validNel(42))
        store.during(ProjectId("some-other-project"), kind) shouldBe Validated.invalidNel(RemoteFireboxPending)
        store.during(projectId, FireboxKind.Door15aCatalog) shouldBe Validated.invalidNel(RemoteFireboxPending)
    }

    "an empty store reads as the pending marker" in {
        new ResultCarryOver[Int].during(projectId, kind) shouldBe Validated.invalidNel(RemoteFireboxPending)
    }

    "recording the pending display again reproduces it — the fixed point that makes the write unconditional" in {
        // Every shape a stored value can take: recording what `during` returned
        // — which is exactly what a pending settle records — reproduces it, so
        // the write can be unconditional. That the production dispatcher records
        // under the same key it read is pinned end-to-end by the acceptance
        // spec's "carry-over, same kind" case.
        val shapes: List[VNelMcalcErr[Int]] = List(
            Validated.validNel(42                                                                  ),
            invalid(UnexpectedDevError("boom")              ),
            invalid(RemoteFireboxUnavailable("service down")),
            invalid           (RemoteFireboxPending                                                ),
            invalid           (RemoteFireboxUnavailable("service down"), UnexpectedDevError("boom")),
            invalid           (RemoteFireboxPending, UnexpectedDevError("boom")                    )
        )

        shapes.foreach { stored =>
            val store = storeOf(stored)
            val shown = store.during(projectId, kind)
            store.record(projectId, kind, shown)
            withClue(s"stored=$stored shown=$shown") {
                store.during(projectId, kind) shouldBe shown
            }
        }
    }

    "a pending dispatch for another key displaces the held entry — the store is the last dispatch, not a per-key table" in {
        // The fixed point is key-local; the slot is not. Recording
        // unconditionally therefore means the marker for a key the store does
        // not hold takes the slot, so a kind switch back within the same window
        // shows the honest marker rather than the previous kind's numbers.
        val store = storeOf(Validated.validNel(42))
        store.record(ProjectId("some-other-project"), kind, Validated.invalidNel(RemoteFireboxPending))
        store.during(projectId, kind) shouldBe Validated.invalidNel(RemoteFireboxPending)
    }

end ResultCarryOverSpec
