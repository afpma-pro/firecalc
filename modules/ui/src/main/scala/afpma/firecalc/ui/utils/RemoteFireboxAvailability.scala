/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.engine.alg.en15544.FireboxEngineSource
import afpma.firecalc.engine.alg.en15544.FireboxEngineSourceConfig
import afpma.firecalc.engine.alg.en15544.FireboxKind

/**
 * Provenance AND payload of the remote firebox data the UI currently holds for
 * one kind (Q11: per-kind, so one kind's remote failure cannot block another).
 *
 * The payload travels inside the state (T1.3): "this slot holds one kind's
 * data" is a fact of the type — a kind/data mismatch is unrepresentable, not
 * guarded. `D` is the engine-side remote-data record in production and a stub
 * in unit tests.
 */
enum RemoteDataState[+D]:

    /** Nothing fetched for this kind yet — a fetch may still be in flight. */
    case Absent extends RemoteDataState[Nothing]

    /** Fresh data from a successful fetch in this session. */
    case Live(data: D)

    /**
     * Replayed from the per-project cache after the network path failed.
     *
     * `savedAtMs` is when the cached response was originally computed; the UI
     * MUST surface it rather than present cached numbers as live.
     */
    case Cached(savedAtMs: Double, data: D)

    /**
     * The fetch is over and produced nothing usable — no cache entry matched
     * either. `reason` is already localized: it is built where the project
     * `Locale` is in scope (the pre-fetch), not here.
     */
    case Failed(reason: String) extends RemoteDataState[Nothing]

    /**
     * The fetch concluded with a geometry pre-check verdict from a REACHABLE
     * service: the kind is available, the project's inputs are not. `reason`
     * is already localized, built the same way as `Failed`'s.
     */
    case Rejected(reason: String) extends RemoteDataState[Nothing]

/**
 * What the UI may compute for one firebox kind, given that kind's configured
 * engine source and the remote data currently held for it.
 *
 * There is deliberately no case that computes a `Remote`-configured kind with
 * the local engine. That is the IP-protection rule (Q0.1-A) expressed in the
 * type: "unavailable" and "pending" are honest outcomes, a silent local
 * fallback is not.
 */
enum KindComputation[+D]:

    /** The kind is configured `Local` — compute in-process, as always. */
    case Local extends KindComputation[Nothing]

    /**
     * Remote data is available — carried here, so the dispatch seam hands the
     * consumer the payload instead of pointing at a second store. `cachedAtMs`
     * is `None` for a live response and `Some(ms)` when it was replayed from
     * cache — the UI MUST label the cached case rather than present stale
     * numbers as fresh.
     */
    case Remote(cachedAtMs: Option[Double], data: D)

    /**
     * The kind computes remotely and the fetch has not concluded yet. Not an
     * error and NOT a licence to compute locally: results are simply not
     * available yet.
     */
    case Pending(kind: FireboxKind) extends KindComputation[Nothing]

    /**
     * The kind computes remotely, the fetch is over, and nothing (network or
     * cache) is available. `reason` is localized and names the kind.
     */
    case Unavailable(kind: FireboxKind, reason: String) extends KindComputation[Nothing]

    /**
     * The kind computes remotely and the fetch concluded: a healthy service
     * rejected the geometry. Not an availability failure — the kind is
     * reachable; `reason` is localized and names the kind.
     */
    case Rejected(kind: FireboxKind, reason: String) extends KindComputation[Nothing]

/**
 * Per-kind resolution of "can the UI serve this kind right now?" (Q11).
 *
 * Pure and DOM-free so the whole decision — including the never-fall-back-local
 * invariant — is unit-testable without a browser. `Variables.scala` holds the
 * mutable `Map[FireboxKind, RemoteDataState]` and consults this object; nothing
 * here reads Airstream, `localStorage`, or `navigator`.
 *
 * Branching is exhaustive over [[FireboxEngineSource]] and [[RemoteDataState]] —
 * no catch-all arm.
 */
object RemoteFireboxAvailability:

    /**
     * The default state for a kind with no recorded outcome: nothing fetched.
     *
     * Callers read the state map through [[stateOf]] rather than
     * `Map#getOrElse` at each site, so "no entry" has one meaning.
     */
    def stateOf[D](states: Map[FireboxKind, RemoteDataState[D]], kind: FireboxKind): RemoteDataState[D] =
        states.getOrElse(kind, RemoteDataState.Absent)

    /**
     * Resolve what may be computed for `kind`.
     *
     * A `Local`-configured kind is `Local` whatever the remote state says: that
     * is what makes the registry assemble *partially* instead of failing whole
     * (Q11). A `Remote`-configured kind never resolves to `Local`.
     */
    def computationFor[D](
        kind        : FireboxKind,
        sourceConfig: FireboxEngineSourceConfig,
        state       : RemoteDataState[D]
    ): KindComputation[D] =
        sourceConfig.sourceFor(kind) match
            case FireboxEngineSource.Local     => KindComputation.Local
            case FireboxEngineSource.Remote(_) =>
                state match
                    case RemoteDataState.Absent                  => KindComputation.Pending(kind)
                    case RemoteDataState.Live(data)              => KindComputation.Remote(None, data)
                    case RemoteDataState.Cached(savedAtMs, data) => KindComputation.Remote(Some(savedAtMs), data)
                    case RemoteDataState.Failed(reason)          => KindComputation.Unavailable(kind, reason)
                    case RemoteDataState.Rejected(reason)        => KindComputation.Rejected(kind, reason)

    /** Convenience over [[computationFor]] reading the state out of the map. */
    def computationFor[D](
        kind        : FireboxKind,
        sourceConfig: FireboxEngineSourceConfig,
        states      : Map[FireboxKind, RemoteDataState[D]]
    ): KindComputation[D] =
        computationFor(kind, sourceConfig, stateOf(states, kind))

    /**
     * Whether `kind` can produce results right now (locally or remotely).
     *
     * `Pending` counts as not-yet-usable, not as unavailable: the distinction is
     * what keeps a transient in-flight window out of the "unavailable" set.
     */
    def isUsable[D](
        kind        : FireboxKind,
        sourceConfig: FireboxEngineSourceConfig,
        states      : Map[FireboxKind, RemoteDataState[D]]
    ): Boolean =
        computationFor(kind, sourceConfig, states) match
            case KindComputation.Local             => true
            case KindComputation.Remote(_, _)      => true
            case KindComputation.Pending(_)        => false
            case KindComputation.Unavailable(_, _) => false
            case KindComputation.Rejected(_, _)    => false

    /**
     * The kinds the UI can serve — the "partially assembled registry" as a set.
     *
     * Every `Local` kind is in it unconditionally; a `Remote` kind is in it only
     * while its data is actually held.
     */
    def usableKinds[D](
        sourceConfig: FireboxEngineSourceConfig,
        states      : Map[FireboxKind, RemoteDataState[D]],
        kinds       : Set[FireboxKind] = FireboxKind.closed
    ): Set[FireboxKind] =
        kinds.filter(isUsable(_, sourceConfig, states))

    /**
     * The kinds that are genuinely missing their remote data — and only those.
     *
     * This is the complement that makes Q11 observable: a failure recorded for
     * one kind must never grow this set beyond that kind. Geometry rejections
     * (`Rejected`) are deliberately excluded: the service is available, only
     * the project's inputs are not.
     */
    def unavailableKinds[D](
        sourceConfig: FireboxEngineSourceConfig,
        states      : Map[FireboxKind, RemoteDataState[D]],
        kinds       : Set[FireboxKind] = FireboxKind.closed
    ): Set[FireboxKind] =
        kinds.filter: kind =>
            computationFor(kind, sourceConfig, states) match
                case KindComputation.Unavailable(_, _) => true
                case KindComputation.Local             => false
                case KindComputation.Remote(_, _)      => false
                case KindComputation.Pending(_)        => false
                case KindComputation.Rejected(_, _)    => false

end RemoteFireboxAvailability
