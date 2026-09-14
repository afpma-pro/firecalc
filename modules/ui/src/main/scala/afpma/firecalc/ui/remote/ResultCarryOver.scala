/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.standard.NoResultError
import afpma.firecalc.engine.standard.RemoteFireboxPending
import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.ui.models.project.ProjectId

import cats.data.NonEmptyList
import cats.data.Validated

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var

/**
 * Stale-while-revalidate carry-over: the store of what the last settled
 * computation produced, and the policy that decides what a pending window shows
 * of it.
 *
 * A remote fetch that is in flight produces no result of its own — this store is
 * what the UI keeps showing meanwhile. It owns its state and nothing else: no
 * rendering, no element, and no reference to the result signal that feeds it, so
 * the policy can be exercised on its own values with no UI in the picture.
 */
object ResultCarryOver:

    /** What the store holds: the key [[ResultCarryOver.record]] writes and [[ResultCarryOver.during]] matches on. */
    final case class Entry[A](
        projectId: ProjectId,
        kind     : FireboxKind,
        result   : VNelMcalcErr[A]
    )

    /** The store the UI writes: the EN15544 strict result. */
    val strict: ResultCarryOver[EN15544_Strict_Application] = new ResultCarryOver

/**
 * One result type's carry-over store: a single slot holding the last
 * `(project, kind, result)` a settled computation produced, plus the read a
 * pending window performs on it.
 *
 * The result type is a parameter rather than a fixed type so the policy is
 * testable on its own values, with no process-global state behind it.
 */
final class ResultCarryOver[A]:

    private val storedVar: Var[Option[ResultCarryOver.Entry[A]]] = Var(None)

    /**
     * The store's content, as a signal: one emission per [[record]].
     *
     * The store has exactly ONE writer — the settled-result computation — and
     * that is an invariant, not a comment: an emission count above the number of
     * settles means the write was duplicated (a second writer, or a `map`
     * re-evaluated once per subscriber). Reading the store is [[during]]'s job,
     * so this view exists to make the invariant observable by the suite that
     * pins it — hence package-scoped, not public.
     */
    private[ui] val storedSig: Signal[Option[ResultCarryOver.Entry[A]]] = storedVar.signal

    /**
     * Record what a settled computation produced for `(projectId, kind)` —
     * called after EVERY dispatch, pending windows included.
     *
     * Recording the pending window's own display is what makes this write
     * unconditional: [[during]] is a fixed point of its own output, so a pending
     * settle re-writes the value it just emitted instead of needing an "is
     * pending" flag to stay out of the store.
     *
     * The store is the last dispatch, not a per-key table: a pending dispatch
     * for a key it does not hold records the pending marker for that key, which
     * displaces the previous key's entry. A kind switch back within the same
     * window therefore shows the honest marker rather than the other kind's
     * numbers.
     */
    def record(projectId: ProjectId, kind: FireboxKind, result: VNelMcalcErr[A]): Unit =
        storedVar.set(Some(ResultCarryOver.Entry(projectId, kind, result)))

    /**
     * What a pending window shows for `(projectId, kind)`: the stored result,
     * or — when the store holds nothing for that key (fresh load, kind switch,
     * project switch) — the pending marker.
     *
     * Stale-while-revalidate: the last settled result carries over untouched,
     * EXCEPT for [[NoResultError]]s, which the window supersedes. Superseding is
     * per [[NoResultError]], not per concrete type: "no result last cycle" is
     * exactly what the window replaces with "still waiting". Concretely, the
     * previous cycle's terminal service-unavailable verdict is stale while a
     * retry runs, and there were no numbers to keep anyway — the honest display
     * is the pending marker. Every other error is a real one — an input problem,
     * a geometry verdict, a genuine development-crash marker — and survives
     * untouched, so the user keeps seeing what they must fix. A `Valid` result
     * carries over verbatim: that is the whole point of the window.
     *
     * '''Fixed point.''' `during` reproduces its own output: a recorded
     * carry-over is a `Valid` result or an `Invalid` with no [[NoResultError]]
     * left, and both come back verbatim; the pending marker is itself a
     * [[NoResultError]], so it re-derives to itself; and a key the store does not
     * hold reads as the marker, which records as itself. That is the property
     * [[record]] leans on when it writes unconditionally.
     */
    def during(projectId: ProjectId, kind: FireboxKind): VNelMcalcErr[A] =
        storedVar.now() match
            case Some(ResultCarryOver.Entry(storedProjectId, storedKind, stored))
                if storedProjectId == projectId && storedKind == kind =>
                supersedeNoResult(stored)
            case _ =>
                Validated.invalidNel(RemoteFireboxPending)

    private def supersedeNoResult(stored: VNelMcalcErr[A]): VNelMcalcErr[A] =
        stored match
            case Validated.Valid(_)     => stored
            case Validated.Invalid(nel) =>
                val kept = nel.filter:
                    case _: NoResultError => false
                    case _ => true
                if kept.length == nel.length then stored
                else
                    NonEmptyList
                        .fromList(kept)
                        .fold[VNelMcalcErr[A]](Validated.invalidNel(RemoteFireboxPending))(Validated.Invalid(_))
