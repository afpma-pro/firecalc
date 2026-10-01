/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.FireCalcYAML

import afpma.firecalc.engine.alg.en15544.FireboxEngineSource
import afpma.firecalc.engine.alg.en15544.FireboxEngineSourceConfig
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544

import afpma.firecalc.ui.LAMINAR_COMPUTE_RESULTS_DELAY_MS
import afpma.firecalc.ui.config.UIConfig
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.utils.FireboxKindLabel
import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import afpma.firecalc.ui.models.engineStateHelperVar
import afpma.firecalc.ui.models.firebox_var
import afpma.firecalc.ui.models.localeVar
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
import afpma.firecalc.firebox_calc_client_js.FetchCalcClient
import afpma.firecalc.ui.utils.FireboxCalcCacheStore
import afpma.firecalc.ui.utils.KindComputation
import afpma.firecalc.ui.utils.NetworkStatus
import afpma.firecalc.ui.utils.RemoteDataState
import afpma.firecalc.ui.utils.RemoteFireboxAvailability

import cats.data.Validated

import com.raquo.airstream.core.Signal
import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.ownership.Subscription
import com.raquo.airstream.state.Var
import com.raquo.laminar.nodes.ReactiveElement
import com.raquo.laminar.api.L.*

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import io.taig.babel.Locale
import org.scalajs.dom

/**
 * The remote firebox pre-fetch subsystem, extracted from
 * `Variables.scala`.
 *
 * Owns the ENTIRE feature: the single atomic state map, the pre-fetch
 * orchestration (request construction, decode + wire→engine conversion, cache
 * read/write policy, offline short-circuit, retry-ladder adaptation), and the
 * derived public signals. `Variables.scala` and the panels only consume the
 * signals exported here.
 *
 * State model: one `Var[Map[FireboxKind, RemoteDataState[StrictRemoteData]]]`.
 * The payload travels inside the state (`Live(data)` / `Cached(at, data)`), so
 * every publish/degrade/clear is one atomic `Var.update` and a kind/data
 * mismatch is unrepresentable — the four-Var pairing apparatus this replaces
 * (data slot, kind slot, provenance map, paired write sites) is deleted, not
 * relocated.
 *
 * The subsystem is started from the frontend boot path:
 * [[prefetchRemoteDataBinder]] is mounted by `Frontend.app.amend(...)` next to
 * the six sibling subscriptions, so "is this feature wired?" is answerable by
 * reading the boot path — the binder is mounted, not an unreferenced
 * `lazy val` that nothing would force.
 */
object RemoteFireboxPrefetch:

    /** The strict descriptor pair used by RemoteFireboxData. */
    type StrictRemoteData = RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ]

    /**
     * Per-kind provenance AND payload of remote firebox data (partial
     * assembly). A kind with no entry is
     * [[afpma.firecalc.ui.utils.RemoteDataState.Absent]].
     *
     * The verdict derived from this map lives in the pure
     * [[afpma.firecalc.ui.utils.RemoteFireboxAvailability]] (unit-tested),
     * which has no case that computes a `Remote`-configured kind locally — the
     * IP-protection rule is expressed in its result type, not in a
     * convention here.
     */
    private val remoteDataStatesVar: Var[Map[FireboxKind, RemoteDataState[StrictRemoteData]]] =
        Var(Map.empty)

    /** Monotonic generation per kind; a newer cycle supersedes every older ladder. */
    private val cycleTokensVar: Var[Map[FireboxKind, Int]] = Var(Map.empty)

    /**
     * A live calculation client plus its cancellation hook.
     *
     * Cancellation is adapter-specific — the fetch adapter aborts its
     * `AbortController`, and the shared [[FireboxCalcClient]] interface
     * deliberately has no cancel operation — so the factory supplies the hook
     * alongside the client it built.
     */
    private[ui] final case class PrefetchClient(
        client: FireboxCalcClient[Future],
        cancel: () => Unit
    )

    /**
     * The seam through which the pre-fetch obtains its calculation client.
     *
     * Production wiring is the shared JS/fetch adapter ([[FetchCalcClient]]).
     * Acceptance tests substitute a fake that implements the very same
     * [[FireboxCalcClient]] interface the browser uses in production, so the
     * real pre-fetch machinery — request construction, cycle suppression,
     * fingerprint-cache read/write, verdict recording — runs against scripted
     * HTTP outcomes rather than an in-process Austrian fake: the seam is the
     * real client interface, not a shared in-process Austrian core.
     */
    private[ui] var calcClientFactory: FireboxCalcClientConfig => PrefetchClient =
        config =>
            val client = FetchCalcClient(config)
            PrefetchClient(client, () => client.cancel)

    /** In-flight client tracked with its cycle generation to prevent stale eviction. */
    private case class TrackedClient(client: PrefetchClient, generation: Int)

    /** Track in-flight clients per kind for cancellation on cycle change. */
    private val activeClientsVar: Var[Map[FireboxKind, TrackedClient]] = Var(Map.empty)

    /** Cancel any in-flight client for `kind` and remove it from tracking. */
    private def cancelActiveClient(kind: FireboxKind): Unit =
        activeClientsVar.now().get(kind) match
            case Some(tc) =>
                tc.client.cancel       (        )
                activeClientsVar.update(_ - kind)
            case None     =>
                // No active client — nothing to cancel.
                ()

    /** Run a continuation only while its captured per-kind cycle is current. */
    private[ui] def guardCurrentCycle(
        cycleTokens: Map[FireboxKind, Int],
        kind       : FireboxKind,
        token      : Int
    )(continuation: => Unit): Unit =
        if cycleTokens.get(kind).contains(token) then continuation

    private def guardCurrentCycle(kind: FireboxKind, token: Int)(continuation: => Unit): Unit =
        guardCurrentCycle(cycleTokensVar.now(), kind, token)(continuation)

    /** Start a cycle atomically from the JS event loop's perspective. */
    private def startCycle(kind: FireboxKind): Int =
        cancelActiveClient(kind)
        val token = cycleTokensVar.now().getOrElse(kind, 0) + 1
        cycleTokensVar.update(_.updated(kind, token)      )
        setRemoteDataState   (kind, RemoteDataState.Absent)
        token

    /** Read-only view of the per-kind remote-data state. */
    val remoteDataStatesSig: Signal[Map[FireboxKind, RemoteDataState[StrictRemoteData]]] =
        remoteDataStatesVar.signal

    /** Record `state` for `kind`, leaving every other kind's verdict untouched. */
    private def setRemoteDataState(kind: FireboxKind, state: RemoteDataState[StrictRemoteData]): Unit =
        remoteDataStatesVar.update(_.updated(kind, state))

    /**
     * Drop any remote data held for `kind` and reset its verdict to `Absent`.
     *
     * Used on the `Local` / disabled / build-failed paths: none of them is a
     * remote failure, so recording one would wrongly mark the kind unavailable.
     */
    private def clearRemoteDataFor(kind: FireboxKind): Unit =
        remoteDataStatesVar.update(_ - kind)

    /**
     * The closed `FireboxKind` of a project firebox DTO.
     *
     * Exhaustive over `Firebox_V4`'s five sealed subtypes — no catch-all.
     * This is the single UI-side kind resolution: the wire kind
     * sent to the server derives from it via
     * [[WireConversions.toWireFireboxKind]], so the two cannot disagree.
     */
    def fireboxKindOf(firebox: afpma.firecalc.dto.v5.Firebox_V4): FireboxKind = firebox match
        case _: afpma.firecalc.dto.v5.Firebox_V4.Traditional            => FireboxKind.Traditional
        case _: afpma.firecalc.dto.v5.Firebox_V4.Ecolabeled             => FireboxKind.Ecolabeled
        case _: afpma.firecalc.dto.v5.Firebox_V4.AFPMA_PRSE             => FireboxKind.AFPMA_PRSE
        case _: afpma.firecalc.dto.v5.Firebox_V4.SingleTested           => FireboxKind.SingleTested
        case _: afpma.firecalc.dto.v5.Firebox_V4.Door15aFirebox_Catalog => FireboxKind.Door15aCatalog

    /**
     * Bumped whenever the pre-fetch should be attempted again without any
     * project edit — today only by the browser's `online` event, so returning
     * connectivity retries automatically instead of requiring a reload.
     */
    private val prefetchRetryTickVar: Var[Int] = Var(0)

    /** Register the online retry callback for one binder mount. */
    private def registerOnlineRetry(): () => Unit =
        NetworkStatus.onOnline: () =>
            dom.console.log            ("[S5.4] browser reported online — retrying firebox-calc pre-fetch")
            prefetchRetryTickVar.update(_ + 1                                                             )

    /**
     * One pre-fetch cycle for the CURRENT project firebox against an explicit
     * per-kind source configuration.
     *
     * This is the exact cycle the production binder runs on every project
     * change — availability check, per-kind source resolution, strict
     * application build (for the air-intake pipe shape), and the async
     * pre-fetch — with the source configuration passed in rather than read
     * from [[UIConfig]]. Acceptance tests drive it directly with a fake
     * [[calcClientFactory]] and a test-owned config; production passes
     * `UIConfig.fireboxEngineSource`. Behavior is identical either way.
     */
    private[ui] def runCycle(sourceConfig: FireboxEngineSourceConfig)(using Locale, Owner): Unit =
        val helper         = engineStateHelperVar.now()
        val currentFirebox = helper.fcProj.firebox
        val currentKind    = fireboxKindOf(currentFirebox)
        if UIConfig.uiAvailability.allows(currentFirebox) then
            // Check the static config first — zero engine work on Local path.
            sourceConfig.sourceFor(currentKind) match
                case FireboxEngineSource.Local           =>
                    // No pre-fetch needed; clear any stale remote data.
                    clearRemoteDataFor(currentKind)
                case FireboxEngineSource.Remote(baseUri) =>
                    // Remote: build the application only here (needed for
                    // `airIntakePipeShape` and the EN 15544 params below).
                    scala.util.Try(helper.make_en15544_Strict_Application) match
                        case scala.util.Success(Validated.Valid(app)) =>
                            prefetchFireboxCalc(baseUri, helper, app)
                        case _                                        =>
                            // Build failed; clear stale data for this kind.
                            clearRemoteDataFor(currentKind)
        else
            // Firebox type disabled; clear stale data for this kind.
            clearRemoteDataFor           (currentKind   )

    /**
     * The subsystem's start seam: a Laminar binder mounted from the frontend
     * boot path (`Frontend.app.amend(...)`), alongside the sibling top-level
     * subscriptions. Each mount starts the feature and registers one online
     * listener; unmounting cancels the outer and per-attempt subscriptions and
     * deregisters that listener. Remounting starts a fresh owned lifecycle.
     *
     * Watches `engineStateHelperVar` + `activeProjectIdVar` + the online-retry
     * tick, checks `UIConfig.fireboxEngineSource.sourceFor(kind)` first, and
     * only on the `Remote(baseUri)` branch builds the application (needed for
     * `airIntakePipeShape` and the EN 15544 params). `Local` and disabled
     * branches perform zero engine work.
     */
    lazy val prefetchRemoteDataBinder: Binder.Base =
        Binder { element =>
            ReactiveElement.bindSubscriptionUnsafe(element) { mountContext =>
                val unregisterOnline = registerOnlineRetry()
                val _                = new Subscription(mountContext.owner, unregisterOnline)

                engineStateHelperVar.signal
                    .combineWith(activeProjectIdVar.signal, prefetchRetryTickVar.signal)
                    .composeChanges(_.debounce(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
                    .addObserver(
                        Observer[(FireCalcYAML_Loader, Option[ProjectId], Int)] { case _ =>
                            runCycle(UIConfig.fireboxEngineSource)(using
                                engineStateHelperVar.now().fcProj.locale,
                                mountContext.owner
                            )
                        }
                    )           (using mountContext.owner)
            }
        }

    /**
     * Perform the async HTTP pre-fetch to the remote Firebox Calculation API,
     * with early offline detection, built-in retry + exponential backoff
     * (via `FetchCalcClient`), and a per-project response cache.
     *
     * Uses the shared `FetchCalcClient` adapter which handles POST serialization,
     * retry policy, timeout (30s), and `AbortController` cancellation.
     *
     * Four outcomes, all recorded against THIS kind only, so another
     * kind's results are never affected:
     *
     *  - '''Live''' — a response arrived. The decoded response is re-serialized
     *    and cached under (project id, kind) with a fingerprint of the request
     *    that produced it.
     *  - '''Cached''' — the cache holds a response computed from an identical
     *    request. It is replayed optimistically before the network path starts,
     *    or after the network path has failed; a successful fetch then upgrades
     *    `Cached` → `Live`. This allows the UI to show results immediately,
     *    labelled with their computation date.
     *  - '''Failed''' — neither network nor cache produced anything. The kind
     *    is marked unavailable; it is NOT computed locally: the Austrian kinds
     *    are network-only, and a silent local fallback would defeat IP
     *    protection.
     *  - '''Rejected''' — a reachable service answered with a geometry
     *    pre-check verdict. The kind stays available (the service is not
     *    down — the project's inputs are), so it is NOT marked `Failed` and
     *    never enters the "unavailable types" banner; the verdict travels to
     *    the panel's "Respect des contraintes" status instead.
     *
     * `navigator.onLine == false` short-circuits before any request, because
     * no attempt can succeed while the browser reports no interface. A `true`
     * reading proves nothing (captive portal, dead server) and therefore still
     * goes through the full retry ladder — see [[NetworkStatus]].
     *
     * Cycle token suppression + `AbortController` cancellation provides
     * stricter stale response prevention than the previous callback chain.
     */
    private def prefetchFireboxCalc(
        baseUri: String,
        helper : FireCalcYAML_Loader,
        app    : EN15544_Strict_Application
    )(using locale: Locale, owner: Owner): Unit =
        val fcProj  = helper.fcProj
        val firebox = fcProj.firebox
        val kind    = fireboxKindOf(firebox)

        // Capture this cycle's generation before starting any asynchronous work.
        // The reset and all later writes are synchronous JS callbacks.
        val cycleToken = startCycle(kind)

        // The wire kind derives from the single DTO→kind match above via the
        // total bijection — the two mappings cannot disagree.
        val wireKind = WireConversions.toWireFireboxKind(kind)

        // Build the FireboxCalcRequest from the current engine state.
        //
        // The request carries the operating envelope so the server
        // can derive site-conditioned densities/flows for the `AsResult` kinds
        // (Ecolabeled). The altitude comes from the project's `localConditions`
        // (the DTO already carries it). Per-draft-condition outside-temperature
        // overrides are left absent: the national-accepted-data overrides are not
        // in scope at this build site, and absent overrides reproduce the local
        // strict application's own combustion-air default (the standalone merges
        // them over the strict default, not the EN 13384 standard default);
        // per-condition overrides travel in the envelope when present. Without
        // the envelope the remote path answers `Invalid(MissingOperatingEnvelope)`
        // for Ecolabeled.
        import afpma.firecalc.units.others.sunits.SUnits.given
        // The EN 15544 params the server needs to build its
        // ConstraintContext, read off the built strict application so the
        // remote request stays in lockstep with the local computation.
        // `m_B` is the charge mass the application actually uses
        // (`stove_params.maximum_load` for MaxLoad sizing, else
        // `m_B_calc(P_n, t_n, n_min)`); `n_min` is
        // `stove_params.min_efficiency`. Without them the server cannot build
        // the ConstraintContext and answers NotEvaluated(MissingMB) for the
        // m_B-dependent constraints. `app.m_B` throws when a MaxLoad project
        // has no `maximum_load`; that degrades to absent so the server
        // reports MissingMB for just those constraints instead of failing
        // the whole request.
        val params  = Standard15544Params(
            m_B   = scala.util.Try(app.m_B).toOption.map(mb => WireConversions.qtyDToWireQty[Kilogram](mb)),
            n_min = Some(WireConversions.qtyDToWireQty[Percent](app.n_min))
        )
        val request = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = wireKind,
            firebox            = firebox,
            params             = params,
            airIntakePipeShape = app.airIntakePipeShape.map(WireConversions.toWireShape),
            locale             = fcProj.locale.language.value,
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = Some(
                WireOperatingEnvelope            (
                    altitude             = WireConversions.qtyDToWireQty[Meter](fcProj.local_conditions.altitude),
                    temperatureOverrides = None
                )
            )
        )

        val requestBody = request.asJson.noSpaces

        // The cache is keyed per project, so a project with no id yet (selector
        // page) simply has no cache — never a shared/global bucket that two
        // projects could read from each other.
        val projectIdOpt = activeProjectIdVar.now().map(_.value)
        val kindLabel    = FireboxKindLabel(kind)
        def decodeAndConvert(responseText: String): scala.util.Try[StrictRemoteData] =
            decode[FireboxCalcResponse](responseText) match
                case Right(calcResponse) => convertResponse(calcResponse, fcProj)
                case Left(decodeError)   =>
                    scala.util.Failure(decodeError)

        /**
         * Replay the cached response for this exact (project, kind, request).
         *
         * A cached body is decoded through the very same `decodeAndConvert` the
         * network path uses, so cache and live responses cannot drift. Returns
         * whether a usable entry was served.
         */
        def serveFromCache(): Boolean =
            projectIdOpt.flatMap(FireboxCalcCacheStore.read(_, wireKind, requestBody)) match
                case None        => false
                case Some(entry) =>
                    decodeAndConvert(entry.responseBody) match
                        case scala.util.Success(data) =>
                            setRemoteDataState(kind, RemoteDataState.Cached(entry.savedAtMs, data))
                            dom.console.log   (
                                s"[S5.4] serving CACHED firebox-calc results for ${wireKind.wire} (saved at ${entry.savedAtMs})"
                            )
                            true
                        case scala.util.Failure(err)  =>
                            dom.console.warn(
                                s"[S5.4] cached firebox-calc response for ${wireKind.wire} is unusable: ${err.getMessage}"
                            )
                            false

        /**
         * Terminal degraded state: serve the cache if it matches, otherwise mark
         * THIS kind unavailable. Never falls back to the local engine.
         */
        def degrade(unavailableMsg: String, logLine: String): Unit =
            dom.console.warn(s"[S5.4] $logLine")
            if !serveFromCache() then setRemoteDataState(kind, RemoteDataState.Failed(unavailableMsg))
        // Stale-while-revalidate: optimistically replay a fingerprint-matching
        // entry before any network I/O; a successful fetch below upgrades it to Live.
        serveFromCache()

        // The browser reports no network interface, so every attempt
        // in the ladder would fail on DNS. Skip it and degrade immediately; the
        // `online` listener re-triggers this pre-fetch when connectivity returns.
        if NetworkStatus.isReportedOffline then
            degrade(
                I18N_UI.remote_calc.offline_unavailable(kindLabel),
                s"browser reports offline — skipping the retry ladder for ${wireKind.wire}"
            )
        else
            val config         = FireboxCalcClientConfig(
                baseUri     = baseUri,
                retryPolicy = RetryPolicy(
                    maxAttempts  = UIConfig.prefetchRetry.maxRetries + 1,
                    initialDelay = UIConfig.prefetchRetry.baseDelayMs.millis,
                    maxDelay     = UIConfig.prefetchRetry.maxDelayMs.millis
                ),
                timeout     = 30.seconds
            )
            val prefetchClient = calcClientFactory(config)
            activeClientsVar.update(_.updated(kind, TrackedClient(prefetchClient, cycleToken)))

            // Generation captured at registration time; stale onComplete from
            // cycle N cannot evict the client for cycle N+1.

            prefetchClient.client.calculate(request).onComplete {
                case scala.util.Success(Right(calcResponse)) =>
                    guardCurrentCycle(kind, cycleToken) {
                        convertResponse(calcResponse, fcProj) match
                            case scala.util.Success(remoteData) =>
                                setRemoteDataState(kind, RemoteDataState.Live(remoteData))
                                projectIdOpt.foreach { projectId =>
                                    // Re-serialize the decoded response for caching.
                                    val responseBody = calcResponse.asJson.noSpaces
                                    FireboxCalcCacheStore.write(projectId, wireKind, requestBody, responseBody)
                                }
                                dom.console.log   (
                                    s"[S5.4] Remote firebox data prefetched for ${wireKind.wire}"
                                )
                            case scala.util.Failure(err)        =>
                                degrade(
                                    I18N_UI.remote_calc.kind_unavailable(kindLabel),
                                    s"Remote firebox calc conversion failed: ${err.getMessage}"
                                )
                    }
                    cleanupTrackedClient(kind, cycleToken)

                case scala.util.Success(Left(calcFailure)) =>
                    guardCurrentCycle(kind, cycleToken) {
                        if calcFailure.preCheckDetails.isDefined then
                            // Geometry pre-check verdict from a REACHABLE service:
                            // record Rejected, not Failed — the kind stays out of
                            // the unavailable-kinds banner. No cache replay on this
                            // path: only successful responses are cached, keyed on
                            // the request fingerprint — a rejected request can
                            // never match a stored success.
                            dom.console.warn  (
                                s"[S5.4] Remote firebox calc rejected the geometry for ${wireKind.wire}: ${calcFailure.description}"
                            )
                            setRemoteDataState(
                                kind,
                                RemoteDataState.Rejected(unavailableMessageFor(kindLabel, calcFailure))
                            )
                        else
                            degrade(
                                unavailableMessageFor(kindLabel, calcFailure),
                                s"Remote firebox calc failed: ${calcFailure.description}"
                            )
                    }
                    cleanupTrackedClient(kind, cycleToken)

                case scala.util.Failure(err) =>
                    // Unexpected Future failure (e.g. uncaught exception in the adapter).
                    guardCurrentCycle(kind, cycleToken) {
                        degrade(
                            I18N_UI.remote_calc.kind_unavailable(kindLabel),
                            s"Remote firebox calc failed unexpectedly: ${err.getMessage}"
                        )
                    }
                    cleanupTrackedClient(kind, cycleToken)
            }

    /**
     * The user-visible degrade banner for a failed firebox-calc prefetch.
     *
     * A geometry pre-check rejection carries the failed checks as per-item
     * details; surface them, qualified by the firebox kind, instead of the
     * generic banner. The classification is delegated to the
     * canonical [[CalculationFailure.preCheckDetails]] predicate on the shared
     * ADT — gated on the documented status/discriminator pairing TOGETHER WITH
     * non-empty details, NOT on details presence alone — so a 400
     * malformed_request that also carries items is never mistaken for a geometry
     * verdict, and it gives the payments report consumer the same classification
     * to key off, so the two surfaces cannot drift once it adopts it. Every other
     * failure keeps the existing generic banner.
     */
    private def unavailableMessageFor(kindLabel: String, failure: CalculationFailure)(using
        Locale
    ): String =
        failure.preCheckDetails match
            case Some(items) =>
                I18N_UI.remote_calc.precheck_failed(kindLabel, items.map(_.message).mkString(", "))
            case None        => I18N_UI.remote_calc.kind_unavailable(kindLabel)

    // =========================================================================
    //  Derived public signals — pure projections of the one state map
    // =========================================================================

    /**
     * The ACTIVE firebox kind's computation verdict, as a signal.
     *
     * One shared projection over the state map + the selected firebox: the
     * cached-at, prefetched-data and in-flight signals below are trivial maps
     * over it, so a new [[KindComputation]] variant forces a decision at
     * exactly this one site instead of silently missing three consumers.
     */
    private lazy val activeKindComputationSig: Signal[KindComputation[StrictRemoteData]] =
        remoteDataStatesSig
            .combineWith(firebox_var.signal)
            .map: (states, currentFirebox) =>
                given Locale = localeVar.now()
                RemoteFireboxAvailability.computationFor(
                    fireboxKindOf(currentFirebox),
                    UIConfig.fireboxEngineSource,
                    states
                )

    /**
     * Signal carrying pre-fetched remote firebox data for the ACTIVE firebox
     * kind.
     *
     * Emits `Some(data)` when remote data is held for the kind currently
     * selected — either fresh from the network or replayed from the
     * fingerprint-matched per-project cache. Emits `None` when the engine
     * source is `Local`, the fetch has not yet completed, or it ended with
     * neither a response nor a matching cache entry. Serving one kind's data
     * for another is unrepresentable: the payload lives inside the per-kind
     * state.
     */
    lazy val prefetchedRemoteDataSig: Signal[Option[StrictRemoteData]] =
        activeKindComputationSig.map(_.projection.data)

    /**
     * When the currently held remote data came from cache rather than the
     * network.
     *
     * `Some(savedAtMs)` drives the "results are cached / offline" banner:
     * cached numbers MUST be labelled with their age, never presented as live.
     */
    lazy val prefetchedRemoteCachedAtSig: Signal[Option[Double]] =
        activeKindComputationSig.map(_.projection.cachedAt)

    /**
     * The kinds whose remote data is genuinely missing — and only those.
     *
     * Empty when no kind is configured `Remote` and while a fetch is in
     * flight. A failure recorded for one kind grows this set by exactly
     * that kind, which is what makes "the registry assembles partially"
     * observable in the UI.
     */
    lazy val unavailableFireboxKindsSig: Signal[Set[FireboxKind]] =
        remoteDataStatesSig.map: states =>
            given Locale = localeVar.now()
            RemoteFireboxAvailability.unavailableKinds(UIConfig.fireboxEngineSource, states)

    /**
     * The refusal reason when availability resolution refused the ACTIVE
     * firebox kind — `Some` only for an Austrian kind configured `Local`
     * (ADR-0010).
     *
     * The fourth rendering decision of the exhaustive projection chain: the
     * refusal banner notice. Kept strictly separate from the connecting and
     * unavailable notices — a deploy condition never rides the network
     * verdicts, and a network blip never surfaces as a missing calculator
     * (story 8).
     */
    lazy val activeKindRefusalSig: Signal[Option[String]] =
        activeKindComputationSig.map:
            case KindComputation.Refused(_, reason) => Some(reason)
            case KindComputation.Local              => None
            case KindComputation.Remote(_, _)       => None
            case KindComputation.Pending(_)         => None
            case KindComputation.Unavailable(_, _)  => None
            case KindComputation.Rejected(_, _)     => None

    /**
     * Whether a remote fetch for the ACTIVE firebox kind is still in flight.
     *
     * True while that kind's [[KindComputation]] is `Pending` — a
     * Remote-configured kind with no recorded outcome yet. Since the
     * optimistic replay, this window only exists on a cache miss.
     * Drives the "Connecting…" banner notice; mutually exclusive with the
     * cached notice for the active kind.
     */
    lazy val prefetchInFlightSig: Signal[Boolean] =
        activeKindComputationSig.map(_.projection.inFlight)

    /**
     * Remove the tracked client for a kind if the generation still matches.
     * Prevents stale onComplete callbacks from evicting a newer client.
     */
    private def cleanupTrackedClient(kind: FireboxKind, generation: Int): Unit =
        activeClientsVar.update(m =>
            m.get(kind) match
                case Some(tc) if tc.generation == generation => m - kind
                case _                                       => m
        )

    /**
     * Pure wire-to-Strict conversion: `FireboxCalcResponse` → `StrictRemoteData`.
     *
     * Uses typed module-specific converters — no casts.
     * Extracted to object level so it is scannable and testable in isolation.
     */
    private def convertResponse(
        calcResponse: FireboxCalcResponse,
        fcYaml      : FireCalcYAML
    ): scala.util.Try[StrictRemoteData] =
        scala.util.Try {
            val fbContribution       =
                WireConversions.fromWireValidatedFireboxContribution[FireboxPipe_Module_15544.FullDescr](
                    calcResponse.fireboxContribution
                )(using
                    WireConversions.fromWireFireboxPipeFullDescr,
                    fcYaml.locale
                )
            val caContribution       =
                WireConversions
                    .fromWireValidatedFireboxContribution[CombustionAirPipe_Module_15544.FullDescr](
                        calcResponse.combustionAirContribution
                    )(using
                        WireConversions.fromWireCombustionAirPipeFullDescr,
                        fcYaml.locale
                    )
            RemoteFireboxData[
                FireboxPipe_Module_15544.FullDescr,
                CombustionAirPipe_Module_15544.FullDescr
            ](
                fireboxContribution       = fbContribution,
                combustionAirContribution = caContribution,
                constraints               = calcResponse.constraints,
                reportData                = calcResponse.reportData
            )
        }
end RemoteFireboxPrefetch
