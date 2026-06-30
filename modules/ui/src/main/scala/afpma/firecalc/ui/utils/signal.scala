/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.engine.utils.VNelString

import com.raquo.airstream.core.EventStream
import com.raquo.airstream.core.Signal

extension [A](svnele: Signal[VNelMcalcErr[A]])

    def mapVNelE[B](f: A => B): Signal[VNelMcalcErr[B]] =
        svnele.map(_.map(f))

    def flatMapVNelE[B](f: A => VNelMcalcErr[B]): Signal[VNelMcalcErr[B]] =
        svnele.map(_.andThen(f))

    def mapAndFoldVNelE[B](f: A => B, default: B): Signal[B] =
        svnele.map(_.map(f).getOrElse(default))

    def flatMapAndFoldVNelE[B](f: A => VNelMcalcErr[B], default: B): Signal[B] =
        svnele.map(_.andThen(f).getOrElse(default))

extension [A](signal: Signal[A])
    def combineWithDistinct[B](that: Signal[B]): Signal[(A, B)] =
        signal.combineWith(that).distinct

    def combineWithDistinct[B, C](s1: Signal[B], s2: Signal[C]): Signal[(A, B, C)] =
        signal.combineWith(s1, s2).distinct

    def combineWithDistinct[B, C, D](s1: Signal[B], s2: Signal[C], s3: Signal[D]): Signal[(A, B, C, D)] =
        signal.combineWith(s1, s2, s3).distinct

    def combineWithDistinct[B, C, D, E](
        s1: Signal[B],
        s2: Signal[C],
        s3: Signal[D],
        s4: Signal[E]
    ): Signal[(A, B, C, D, E)] =
        signal.combineWith(s1, s2, s3, s4).distinct

    def combineWithDistinct[B, C, D, E, F](
        s1: Signal[B],
        s2: Signal[C],
        s3: Signal[D],
        s4: Signal[E],
        s5: Signal[F]
    ): Signal[(A, B, C, D, E, F)] =
        signal.combineWith(s1, s2, s3, s4, s5).distinct

extension [A](svnel: Signal[VNelString[A]])

    def mapVNelString[B](f: A => B): Signal[VNelString[B]] =
        svnel.map(_.map(f))

    def flatMapVNelString[B](f: A => VNelString[B]): Signal[VNelString[B]] =
        svnel.map(_.andThen(f))

    def mapAndFoldVNel[B](f: A => B, default: B): Signal[B] =
        svnel.map(_.map(f).getOrElse(default))

    def flatMapAndFoldVNel[B](f: A => VNelString[B], default: B): Signal[B] =
        svnel.map(_.andThen(f).getOrElse(default))

// ============================================================================
// Debounce with stale-event rejection
// ============================================================================

/**
 * Debounce a signal while guarding against stale side-effects.
 *
 * Captures the identity at the moment the signal value changes, debounces, then
 * compares the captured identity with the current one. If they differ
 * (e.g., the user navigated to another project during the debounce window),
 * the handler is NOT called.
 *
 * This prevents a debounced write from targeting the wrong entity after
 * navigation. Pattern:
 *
 *   BEFORE: sig.changes .debounce(N) .map { id = readIdentity() /* stale */ }
 *   AFTER:  sig.changes .map { (v, readIdentity()) } .debounce(N)
 *           .withCurrentValueOf(identitySignal)
 *           .collect { case (v, capturedId, currentId) if capturedId == currentId => handler(v) }
 *
 * @param signal         Signal whose changes to debounce.
 * @param readIdentity   Thunk that reads the current identity (e.g. `() => activeProjectIdVar.now()`).
 *                       Called synchronously inside `.map()` BEFORE debounce.
 * @param identitySignal Signal to observe for staleness detection via `.withCurrentValueOf`.
 * @param debounceMs     Debounce window in milliseconds.
 * @param handler        Side-effect to run with the value, only if the identity is still current.
 */
def debounceWithStaleGuard[A, I](
    signal        : Signal[A],
    readIdentity  : () => I,
    identitySignal: Signal[I],
    debounceMs    : Int
)(handler: A => Unit): EventStream[Unit] =
    signal.changes.distinct
        .map(v => (v, readIdentity()))
        .debounce(debounceMs)
        .withCurrentValueOf(identitySignal)
        .collect { case (v, capturedId, currentId) if capturedId == currentId => handler(v) }
