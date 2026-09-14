/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.standard.{
    FireboxGeometryRejectedError,
    MCalc_Error,
    RemoteFireboxPending,
    RemoteFireboxUnavailable
}
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.ui.models.{firebox_var, resultsWithProjectSig}
import afpma.firecalc.ui.utils.{FireboxCalcCacheStore, RemoteDataState}
import afpma.firecalc.units.coulombutils.meters

import cats.data.Validated
import cats.syntax.show.*
import com.raquo.airstream.core.Observer
import com.raquo.airstream.ownership.Owner
import io.circe.syntax.*
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future, Promise}

import utest.*

/**
 * Stale-while-revalidate acceptance tests for the remote pre-fetch: what the
 * panels show while a fetch is in flight.
 *
 * Two subjects, one contract. Optimistic replay (issue 01): a matching cache
 * entry is served synchronously, so the sections show the last known numbers
 * instead of an empty loading state, and the live response replaces it.
 * Pending window (issue 02): when there is nothing to replay, the window
 * carries the last settled computation of the SAME (project, kind) verbatim —
 * pending marker included — and never a stale verdict: a different kind, a
 * superseded failure or a user error each get the honest display.
 *
 * Driven through the production [[RemoteFireboxPrefetch.runCycle]] seam with the
 * shared [[RemotePrefetchHarness]]. Failure paths live in
 * [[RemotePrefetchFailureSpec]]; the wire/cache contract in
 * [[RemotePrefetchCacheSpec]] and [[RemotePrefetchEndToEndSpec]].
 */
object RemotePrefetchStaleWhileRevalidateSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── The shared prefetch harness ───────────────────────────────────────────
    // One fake client, one set of poll/settle helpers: see RemotePrefetchHarness.

    import RemotePrefetchHarness.*

    val tests = Tests {

        // ─ Optimistic replay (D2, issue 01) ────────────────────────────────

        test("optimistic cache hit — state is Cached immediately after runCycle returns") {
            serialized {
                installWindow     (               )
                setNavigatorOnline(true           )
                loadEcolabeled    ("opt-cache-hit")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val wireKind = WireFireboxKind.Ecolabeled
                val seedFake = new FakeClient(_ => Future.successful(Right(successResponse(wireKind))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            val slowFake = new FakeClient(_ => Future.never)
                            runCycleWith(slowFake).map { _ =>
                                val states = readNow(RemoteFireboxPrefetch.remoteDataStatesSig)
                                assert(isCached(states, FireboxKind.Ecolabeled))
                            }
                    }
                }
            }
        }

        test("optimistic cache hit then live update — Cached -> Live and the cache entry is rewritten") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true            )
                loadEcolabeled    ("opt-cache-live")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val wireKind = WireFireboxKind.Ecolabeled
                val seedFake = new FakeClient(_ => Future.successful(Right(successResponse(wireKind))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            val seedSavedAtMs = readEntry(store, "opt-cache-live", wireKind).savedAtMs
                            val liveFake      = new FakeClient(_ =>
                                sleep(10).flatMap(_ => Future.successful(Right(successResponse(wireKind))))
                            )
                            runCycleWith(liveFake).flatMap { _ =>
                                // Before any network completion: the seeded entry is replayed synchronously.
                                val initialStates = readNow(RemoteFireboxPrefetch.remoteDataStatesSig)
                                assert(isCached(initialStates, FireboxKind.Ecolabeled)                      )
                                assert(cachedSavedAt(initialStates, FireboxKind.Ecolabeled) == seedSavedAtMs)
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isLive(_, FireboxKind.Ecolabeled)
                                ).map { _ =>
                                    // The success path rewrote the cache with a newer stamp.
                                    assert(readEntry(store, "opt-cache-live", wireKind).savedAtMs > seedSavedAtMs)
                                }
                            }
                    }
                }
            }
        }

        test("fingerprint mismatch — no optimistic Cached; failing network lands terminal Failed") {
            serialized {
                installWindow     (                    )
                setNavigatorOnline(true                )
                loadEcolabeled    ("opt-cache-mismatch")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val wireKind = WireFireboxKind.Ecolabeled
                // Seed an entry computed from a DIFFERENT request body: the response
                // payload is fine, but its fingerprint cannot match this cycle's
                // request, so nothing may be served (guards stale geometry).
                FireboxCalcCacheStore.write(
                    "opt-cache-mismatch",
                    wireKind,
                    """{"different":"request"}""",
                    successResponse(wireKind).asJson.noSpaces
                )
                val failingFake = failingClient
                runCycleWith(failingFake).flatMap { _ =>
                    // Synchronously after the cycle started: Absent (Pending), not Cached.
                    assert(!isCached(readNow(RemoteFireboxPrefetch.remoteDataStatesSig), FireboxKind.Ecolabeled))
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                        isFailed(_, FireboxKind.Ecolabeled)
                    ).map { states =>
                        // Terminal Failed — the mismatched entry was never replayed.
                        states(FireboxKind.Ecolabeled) match
                            case RemoteDataState.Failed(_) => ()
                            case other                     => sys.error(s"expected terminal Failed, got $other")
                        assert(failingFake.calls == 1)
                    }
                }
            }
        }

        // ── Pending-window acceptance (D3, issue 02) ─────────────────────────

        test("prefetchInFlightSig — true while in flight; false at Live, Cached and Failed") {
            serialized {
                installWindow     (               )
                setNavigatorOnline(true           )
                loadEcolabeled    ("pend-inflight")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val gate     = Promise[Either[CalculationFailure, FireboxCalcResponse]]()
                val neverYet = new FakeClient(_ => gate.future)
                runCycleWith(neverYet)
                    .flatMap { _ =>
                        // No cache entry for this project, no response yet: Pending.
                        assert                                               (readNow(RemoteFireboxPrefetch.prefetchInFlightSig)                )
                        // D1 exclusivity: while the active kind is Pending, the cached
                        // signal is empty — the banner's connecting/cached notices can
                        // never overlap for it.
                        assert                                               (readNow(RemoteFireboxPrefetch.prefetchedRemoteCachedAtSig).isEmpty)
                        gate.success                                         (Right(successResponse(WireFireboxKind.Ecolabeled))                )
                        // The Live outcome closes the window.
                        awaitState(RemoteFireboxPrefetch.prefetchInFlightSig)(inFlight => !inFlight                                             )
                    }
                    .flatMap { _ =>
                        // Cached closes it too: an offline cycle with the entry written
                        // above replays synchronously — the never-completing client is
                        // never consulted, no in-flight window opens.
                        setNavigatorOnline(false)
                        val neverCached =
                            new FakeClient(_ => Promise[Either[CalculationFailure, FireboxCalcResponse]]().future)
                        runCycleWith(neverCached).map { _ =>
                            assert            (isCached(readNow(RemoteFireboxPrefetch.remoteDataStatesSig), FireboxKind.Ecolabeled))
                            assert            (readNow(RemoteFireboxPrefetch.prefetchInFlightSig) == false                         )
                            assert            (readNow(RemoteFireboxPrefetch.prefetchedRemoteCachedAtSig).isDefined                )
                            setNavigatorOnline(true                                                                                )
                        }
                    }
                    .flatMap { _ =>
                        // Failed closes it: fresh project (no cache), immediate transport
                        // failure — true synchronously at cycle start, false once terminal.
                        loadEcolabeled("pend-inflight-failed")
                        val failing = failingClient
                        runCycleWith(failing)
                            .flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isFailed(_, FireboxKind.Ecolabeled)
                                )
                            }
                            .flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.prefetchInFlightSig)(inFlight => !inFlight)
                            }
                    }
            }
        }

        test("connecting banner text — composed through the production i18n accessor") {
            serialized {
                // The notice's whole content is this accessor's value; pin it against
                // the spec string in both shipped languages.
                val fr = { given Locale = Locales.fr; (RemoteFireboxPending: MCalc_Error).show }
                val en = { given Locale = Locales.en; (RemoteFireboxPending: MCalc_Error).show }
                assert           (fr == "Connexion au service de calcul du foyer…"      )
                assert           (en == "Connecting to the firebox calculation service…")
                Future.successful(()                                                    )
            }
        }

        test("carry-over, same kind — the pending window keeps the last computed result") {
            serialized {
                installWindow     (            )
                setNavigatorOnline(true        )
                loadEcolabeled    ("carry-same")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake)
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled))
                    }
                    .flatMap { _ =>
                        // Let the debounced computation settle; whatever shape the engine
                        // makes of the marker payload, it is the last NON-pending result
                        // and the pending window must reproduce it byte-for-byte.
                        sleep(1400)
                    }
                    .map { _ => readNow(resultsWithProjectSig) }
                    .flatMap { settled =>
                        // Edit + a cycle whose fetch never completes: Pending for the
                        // same (project, kind). The debounce fires ~1 s later; before the
                        // fix the recompute emitted the ResultsNotComputed meta-error,
                        // now it must carry the previous numbers verbatim.
                        firebox_var.update {
                            case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.510.meters)
                            case other => other
                        }
                        val never =
                            new FakeClient(_ => Promise[Either[CalculationFailure, FireboxCalcResponse]]().future)
                        runCycleWith(never).flatMap { _ =>
                            assert(readNow(RemoteFireboxPrefetch.prefetchInFlightSig))
                            sleep(1400).map { _ =>
                                val during = readNow(resultsWithProjectSig)
                                // Stale-while-revalidate: the sections keep seeing the
                                // previous numbers while the fetch is in flight — the
                                // exact instance, not a recomputation.
                                assert                     (during._2 == settled._2                           )
                                settled._2.toOption.foreach(app => assert(during._2.toOption.exists(_ eq app)))
                            }
                        }
                    }
            }
        }

        test("stale remote-failure banner is superseded — the window emits the honest marker") {
            serialized {
                installWindow     (             )
                setNavigatorOnline(true         )
                loadEcolabeled    ("carry-stale")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val failingFake = failingClient
                runCycleWith(failingFake)
                    .flatMap { _ =>
                        // The settled terminal Failed outcome: the service-unreachable
                        // verdict, typed distinctly from a development error.
                        awaitState(resultsWithProjectSig) { (_, v) =>
                            v match
                                case Validated.Invalid(nel) =>
                                    nel.head match
                                        case _: RemoteFireboxUnavailable => true
                                        case _ => false
                                case Validated.Valid(_)     => false
                        }
                    }
                    .flatMap { settled =>
                        // Rendered for the user as the localized banner — no
                        // `DEV_ERROR:` prefix, which the dev-crash renderer adds.
                        val terminal = settled._2.toEither.left.get.head
                        assert(ShowUsingLocale[MCalc_Error].show(terminal) == kindUnavailableBanner)
                        // Edit + a revalidation cycle that never completes: the previous
                        // run's failure banner is superseded by the pending window.
                        firebox_var.update {
                            case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.530.meters)
                            case other => other
                        }
                        val never =
                            new FakeClient(_ => Promise[Either[CalculationFailure, FireboxCalcResponse]]().future)
                        runCycleWith(never).flatMap { _ =>
                            awaitState(resultsWithProjectSig) { (_, v) =>
                                v match
                                    case Validated.Invalid(nel) => nel.head == RemoteFireboxPending
                                    case Validated.Valid(_)     => false
                            }.map { (_, v) =>
                                v match
                                    case Validated.Invalid(nel) =>
                                        assert(nel.toList == List(RemoteFireboxPending)           )
                                        // No stale "service unreachable" message survives.
                                        assert(!nel.exists(_ == settled._2.toEither.left.get.head))
                                    case Validated.Valid(_)     =>
                                        sys.error(s"expected the pending marker, got a valid result")
                            }
                        }
                    }
            }
        }

        test("kind switch never carries over — the new kind gets the honest marker") {
            serialized {
                installWindow     (              )
                setNavigatorOnline(true          )
                loadEcolabeled    ("carry-switch")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake)
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled))
                    }
                    .flatMap { _ =>
                        // Let the debounced computation settle so a non-pending result is
                        // stored for (carry-switch, Ecolabeled).
                        sleep(1400)
                    }
                    .flatMap { _ =>
                        // Same project, different (remote) kind; its fetch never completes.
                        loadDoor15a("carry-switch")
                        val never =
                            new FakeClient(_ => Promise[Either[CalculationFailure, FireboxCalcResponse]]().future)
                        runCycleWith(never).flatMap { _ =>
                            awaitState(resultsWithProjectSig) { (_, v) =>
                                v match
                                    case Validated.Invalid(nel) => nel.head == RemoteFireboxPending
                                    case Validated.Valid(_)     => false
                            }.map { (_, v) =>
                                // The Ecolabeled numbers on screen do NOT describe the
                                // Door15a inputs — no carry-over across kinds.
                                assert(v == Validated.invalidNel(RemoteFireboxPending))
                            }
                        }
                    }
            }
        }

        test("prior user errors persist — a geometry rejection carries through the window") {
            serialized {
                installWindow     (            )
                setNavigatorOnline(true        )
                loadEcolabeled    ("carry-user")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val rejectFake =
                    new FakeClient(_ => Future.successful(protocolFailure(422, "calculation_failed", preCheckItems)))
                runCycleWith(rejectFake)
                    .flatMap { _ =>
                        // Settled outcome: a per-item geometry verdict the user must fix.
                        awaitState(resultsWithProjectSig) { (_, v) =>
                            v match
                                case Validated.Invalid(nel) =>
                                    nel.head.isInstanceOf[FireboxGeometryRejectedError]
                                case Validated.Valid(_)     => false
                        }
                    }
                    .flatMap { settled =>
                        firebox_var.update {
                            case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.540.meters)
                            case other => other
                        }
                        val never =
                            new FakeClient(_ => Promise[Either[CalculationFailure, FireboxCalcResponse]]().future)
                        runCycleWith(never).flatMap { _ =>
                            sleep(1400).map { _ =>
                                val during = readNow(resultsWithProjectSig)
                                // The actionable verdict survives the revalidation window
                                // untouched — the window supersedes no-result
                                // states only.
                                assert(during._2 == settled._2)
                            }
                        }
                    }
            }
        }

        test("one settled computation writes the carry-over store once, however many subscribers") {
            serialized {
                installWindow     (            )
                setNavigatorOnline(true        )
                loadEcolabeled    ("write-once")
                given Owner = new Owner {}

                // Store writes, result-signal changes, and what each of two
                // panel-like subscribers saw of those changes.
                var writes  = 0
                var settles = 0
                var seenA   = 0
                var seenB   = 0

                val countedStore =
                    ResultCarryOver.strict.storedSig.changes.addObserver(Observer(_ => writes += 1))
                val countedResult =
                    resultsWithProjectSig.changes.addObserver(Observer(_ => settles += 1))
                val subA = resultsWithProjectSig.addObserver(Observer(_ => seenA += 1))
                val subB = resultsWithProjectSig.addObserver(Observer(_ => seenB += 1))

                // Subscribing pushed the current value to each panel observer and
                // settled the store once. From here on, only the edit's settle counts.
                writes  = 0
                settles = 0
                seenA   = 0
                seenB   = 0

                // One edit → one debounced settle, with both subscribers live.
                firebox_var.update {
                    case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.560.meters)
                    case other => other
                }

                sleep(1400).map { _ =>
                    assert            (settles >= 1     ) // the settle happened at all
                    assert            (seenA == settles ) // both subscribers are live
                    assert            (seenB == settles )
                    // One write per settled computation — not one per subscriber,
                    // and not one per re-evaluation of the dispatcher's map.
                    assert            (writes == settles)
                    countedStore.kill (                 )
                    countedResult.kill(                 )
                    subA.kill         (                 )
                    subB.kill         (                 )
                }
            }
        }

    }
