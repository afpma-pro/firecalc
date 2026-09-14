/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.ui.models.{firebox_var}
import afpma.firecalc.ui.utils.{FireboxCalcCache, FireboxKindLabel, RemoteDataState}
import afpma.firecalc.units.coulombutils.meters

import com.raquo.airstream.ownership.Owner
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future, Promise}

import utest.*

/**
 * Failure-path acceptance tests for the remote pre-fetch (ticket 12, Phase 2 IP
 * gate). Every test drives the production [[RemoteFireboxPrefetch.runCycle]] seam
 * with a fake [[FireboxCalcClient]] and asserts the OBSERVABLE degrade contract:
 * a remote-only kind that cannot be served (network down, no matching cache) is
 * marked `Failed` and unavailable — it is NEVER silently computed locally
 * (Q0.1-A), and one kind's failure never blocks another (Q11 partial assembly).
 *
 * Scaffolding is shared with the sibling specs — see [[RemotePrefetchHarness]];
 * the optimistic-replay and pending-window subjects live in
 * [[RemotePrefetchStaleWhileRevalidateSpec]].
 */
object RemotePrefetchFailureSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── The shared prefetch harness ───────────────────────────────────────────
    // One fake client, one set of poll/settle helpers: see RemotePrefetchHarness.

    import RemotePrefetchHarness.*

    val tests = Tests {

        test("offline without cache — Failed and the kind is unavailable; no network attempt") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(false                 )
                loadEcolabeled    ("fail-offline-nocache")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            setNavigatorOnline(true           )
                            states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Failed(reason) => assert(reason.nonEmpty)
                                case other                          => sys.error(s"expected Failed, got $other")
                            // Exactly this kind is unavailable; nothing else.
                            assert            (
                                readNow(RemoteFireboxPrefetch.unavailableFireboxKindsSig) == Set(FireboxKind.Ecolabeled)
                            )
                            // Offline short-circuits before any client is created or called.
                            assert            (fake.calls == 0)
                            assert            (store.isEmpty  )
                    }
                }
            }
        }

        test("offline with a matching cache — Cached with the saved timestamp") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true                )
                loadEcolabeled    ("fail-offline-cache")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // Seed a live response so the cache holds a matching entry.
                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            assert            (
                                store.contains(FireboxCalcCache.key("fail-offline-cache", WireFireboxKind.Ecolabeled))
                            )
                            // Now go offline: the ladder is skipped and the cache is replayed.
                            setNavigatorOnline(false)
                            val offlineFake = new FakeClient(_ =>
                                Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled)))
                            )
                            runCycleWith(offlineFake).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isCached(_, FireboxKind.Ecolabeled)
                                ).map { states =>
                                    setNavigatorOnline(true                  )
                                    states(FireboxKind.Ecolabeled) match
                                        case RemoteDataState.Cached(savedAtMs, data) =>
                                            assert(savedAtMs > 0                                           )
                                            assert(data.reportData.ecolabeledOutputs == Some(markerOutputs))
                                        case other                                   => sys.error(s"expected Cached, got $other")
                                    assert            (offlineFake.calls == 0)
                                }
                            }
                    }
                }
            }
        }

        test("connection failure with no cache — Failed with a non-empty typed reason") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true       )
                loadEcolabeled    ("fail-conn")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ =>
                    Future.successful(
                        Left(CalculationFailure.TransportError(new RuntimeException("connection refused")))
                    )
                )
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Failed(reason) => assert(reason.nonEmpty)
                                case other                          => sys.error(s"expected Failed, got $other")
                            assert(store.isEmpty)
                    }
                }
            }
        }

        test("timeout — the prefetch degrades to Failed on the timeout failure class") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true          )
                loadEcolabeled    ("fail-timeout")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ =>
                    Future.successful(Left(CalculationFailure.TransportError(new RuntimeException("timeout"))))
                )
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Failed(reason) => assert(reason.nonEmpty)
                                case other                          => sys.error(s"expected Failed, got $other")
                            assert(store.isEmpty)
                    }
                }
            }
        }

        test("bounded retry exhaustion — terminal Failed after exactly one prefetch-level call") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true        )
                loadEcolabeled    ("fail-retry")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ =>
                    Future.successful(Left(CalculationFailure.TransportError(new RuntimeException("always down"))))
                )
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            // Terminal Failed, never stuck at Absent / Pending.
                            states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Failed(_) => ()
                                case other                     => sys.error(s"expected terminal Failed, got $other")
                            assert(store.isEmpty  )
                            // Retries live inside the production FetchCalcClient, not the prefetch:
                            // the prefetch issues exactly one calculate call per cycle.
                            assert(fake.calls == 1)
                    }
                }
            }
        }

        test("non-2xx protocol error — Failed") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true      )
                loadEcolabeled    ("fail-503")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ =>
                    Future.successful(Left(CalculationFailure.ProtocolError(503, "service_unavailable", "down", None)))
                )
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            assert(isFailed(states, FireboxKind.Ecolabeled))
                            assert(store.isEmpty                           )
                    }
                }
            }
        }

        test("malformed JSON decode error — Failed") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true         )
                loadEcolabeled    ("fail-decode")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ =>
                    Future.successful(
                        Left(CalculationFailure.DecodeError(new RuntimeException("bad json"), "{not json"))
                    )
                )
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            assert(isFailed(states, FireboxKind.Ecolabeled))
                            assert(store.isEmpty                           )
                    }
                }
            }
        }

        test("422 pre-check rejection — every failed check surfaces in the user-visible reason; one call, no cache") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true          )
                loadEcolabeled    ("fail-422-pre")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake =
                    new FakeClient(_ => Future.successful(protocolFailure(422, "calculation_failed", preCheckItems)))
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isRejected(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Rejected(reason) =>
                                    // The reason is the pre-check message composed through the
                                    // very same i18n accessor production renders — byte-identical
                                    // expectation, no brittle French-substring pinning.
                                    assert               (reason == preCheckBanner(preCheckItems)                  )
                                    // Every pre-check item surfaces individually in the message (US4/US7)…
                                    preCheckItems.foreach(item => assert(reason.contains(item.message))            )
                                    // …qualified by the firebox kind (US6)…
                                    assert               (reason.contains(FireboxKindLabel(FireboxKind.Ecolabeled)))
                                    // …instead of the generic "service unreachable" banner.
                                    assert               (!reason.contains(kindUnavailableBanner)                  )
                                case other                            => sys.error(s"expected terminal Rejected, got $other")
                            // Exactly one prefetch-level call: FakeClient has no retry ladder,
                            // so the retry policy lives at the FetchCalcClient seam.
                            assert(fake.calls == 1)
                            // A rejected response is never cached.
                            assert(store.isEmpty  )
                            // The kind is NOT reported unavailable: a geometry rejection is
                            // a verdict from a reachable service, not an outage.
                            assert(
                                readNow(RemoteFireboxPrefetch.unavailableFireboxKindsSig) == Set.empty
                            )
                    }
                }
            }
        }

        test(
            "422 with a matching cache — the geometry verdict wins: no replay, Rejected carries the pre-check reason"
        ) {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true            )
                loadEcolabeled    ("fail-422-cache")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // Seed a live response through the production write path, so the stored
                // fingerprint matches the next cycle's request byte-for-byte.
                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            assert(
                                store.contains(FireboxCalcCache.key("fail-422-cache", WireFireboxKind.Ecolabeled))
                            )
                            // Same request, now rejected by a 422 pre-check. The geometry
                            // verdict is recorded even though a matching cache entry exists:
                            // the rejection path never consults the cache, so the stale
                            // numbers are not replayed over a verdict the service just gave.
                            val failingClient = new FakeClient(_ =>
                                Future.successful(protocolFailure(422, "calculation_failed", preCheckItems))
                            )
                            runCycleWith(failingClient).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isRejected(_, FireboxKind.Ecolabeled)
                                ).map { states =>
                                    states(FireboxKind.Ecolabeled) match
                                        case RemoteDataState.Rejected(reason) =>
                                            assert(reason == preCheckBanner(preCheckItems))
                                        case other                            => sys.error(s"expected Rejected, got $other")
                                    // The seeded success is still in the store, untouched.
                                    assert(
                                        store.contains(
                                            FireboxCalcCache.key("fail-422-cache", WireFireboxKind.Ecolabeled)
                                        )
                                    )
                                    // The network WAS attempted exactly once; a rejection is
                                    // not an outage — the kind stays out of the unavailable set.
                                    assert(failingClient.calls == 1)
                                    assert(
                                        readNow(RemoteFireboxPrefetch.unavailableFireboxKindsSig) == Set.empty
                                    )
                                }
                            }
                    }
                }
            }
        }

        test("422 without details — generic kind-unavailable banner, no pre-check phrasing") {
            serialized {
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // A 422 whose payload carries NO per-item details (empty list): the
                // non-empty guard inside the canonical preCheckDetails predicate must fall
                // through to the generic kind-unavailable banner, never the pre-check phrasing.
                expectGenericBanner("fail-422-nodet", protocolFailure(422, "calculation_failed", Nil))
            }
        }

        test("422 with an out-of-contract discriminator — items alone never unlock the pre-check banner") {
            serialized {
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // The canonical predicate requires the documented 422 + calculation_failed
                // pairing TOGETHER with items: an out-of-contract 422 carrying a DIFFERENT
                // discriminator is not a geometry verdict even when it ships per-item
                // details, so it keeps the generic kind-unavailable banner.
                expectGenericBanner("fail-422-baddisc", protocolFailure(422, "validation_error", preCheckItems))
            }
        }

        test("400 precedence — a malformed request body (with details) keeps the generic banner, never 422") {
            serialized {
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // malformed_request (400) can ALSO carry per-item details
                // client-side, so the per-item 422 rendering must be gated by
                // the status/discriminator pairing via preCheckDetails: a decode
                // failure is never mistaken for a geometry verdict (US11). The
                // equality pin against the generic banner carries the
                // classification contract; payload: see decodeFailureItems.
                // (FakeClient has no retry ladder, so `calls == 1` records the
                // single scripted response, not the retry policy — wire→typed
                // decode precedence and the never-retry-4xx rule live at the
                // FetchCalcClient seam.)
                expectGenericBanner(
                    "fail-400-preced",
                    protocolFailure(
                        400,
                        "malformed_request",
                        decodeFailureItems,
                        message = "Request body could not be decoded"
                    )
                )
            }
        }

        test("response kind mismatch — conversion is kind-agnostic, and the cache is keyed by the REQUEST kind") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true           )
                loadEcolabeled    ("fail-mismatch")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // The server answers with a Traditional-kind response to an Ecolabeled request.
                val fake = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Traditional))))
                runCycleWith(fake).flatMap { _ =>
                    // HONEST OUTCOME (read from convertResponse/runCycle): the wire→engine conversion
                    // never inspects `response.kind` — contributions are kind-agnostic — so the cycle
                    // still records Live. The mismatch is therefore NOT rejected by the prefetch; this
                    // test documents that gap and pins the one invariant that still holds: the response
                    // is cached under the REQUEST's kind key, so a later Traditional-kind request can
                    // never read this Ecolabeled-keyed entry as a hit.
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                        states =>
                            assert(isLive(states, FireboxKind.Ecolabeled))
                            val ecoKey  = FireboxCalcCache.key("fail-mismatch", WireFireboxKind.Ecolabeled)
                            val tradKey = FireboxCalcCache.key("fail-mismatch", WireFireboxKind.Traditional)
                            assert(store.contains(ecoKey)  )
                            assert(!store.contains(tradKey))
                    }
                }
            }
        }

        test("unsupported served kind — Door15a fails while a co-configured Ecolabeled stays Live (partial assembly)") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true)
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // Door15a: the server does not serve this kind.
                loadDoor15a("fail-partial-door")
                val doorFake = new FakeClient(_ =>
                    Future.successful(
                        Left(CalculationFailure.ProtocolError(404, "unsupported_kind", "kind not served", None))
                    )
                )
                runCycleWith(doorFake)
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Door15aCatalog))
                            .flatMap { _ =>
                                // Ecolabeled: a different project, served successfully.
                                loadEcolabeled("fail-partial-eco")
                                val ecoFake = new FakeClient(_ =>
                                    Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled)))
                                )
                                runCycleWith(ecoFake)
                                    .flatMap { _ =>
                                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(s =>
                                            isFailed(s, FireboxKind.Door15aCatalog) && isLive(s, FireboxKind.Ecolabeled)
                                        ).map { states =>
                                            // One kind's failure did not block the other.
                                            assert(isFailed(states, FireboxKind.Door15aCatalog))
                                            assert(isLive(states, FireboxKind.Ecolabeled)      )
                                            // Only the failed kind is unavailable.
                                            assert(
                                                readNow(RemoteFireboxPrefetch.unavailableFireboxKindsSig) == Set(
                                                    FireboxKind.Door15aCatalog
                                                )
                                            )
                                            // The failed kind wrote no cache entry; the successful one did.
                                            assert(
                                                !store.contains(
                                                    FireboxCalcCache
                                                        .key("fail-partial-door", WireFireboxKind.Door15aCatalog)
                                                )
                                            )
                                            assert(
                                                store.contains(
                                                    FireboxCalcCache.key("fail-partial-eco", WireFireboxKind.Ecolabeled)
                                                )
                                            )
                                        }
                                    }
                            }
                    }
            }
        }

        test("stale in-flight suppression — a superseded cycle's late response is dropped and its client cancelled") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true        )
                loadEcolabeled    ("fail-stale")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // Cycle A: gated — its response never arrives until we say so.
                val gateA     = Promise[Either[CalculationFailure, FireboxCalcResponse]]()
                val fakeA     = new FakeClient(_ => gateA.future)
                val outputsB  = WireEcolabeledOutputs("B-wins", "Lr", "Ls", "Lt", "Zt", "St")
                val responseB = FireboxCalcResponse(
                    ApiVersion.v1,
                    WireFireboxKind.Ecolabeled,
                    "fr",
                    WireValidated.Valid(WireFireboxContribution.Without                                                 ),
                    WireValidated.Valid(WireFireboxContribution.Without                                                 ),
                    List(
                        ConstraintVerdict(
                            ConstraintTerm.GlassArea,
                            "glass area",
                            ConstraintDescriptor.Opaque,
                            ConstraintOutcome.Satisfied
                        )
                    ),
                    FireboxReportData  (WireTable(None, None, Nil), WireTable(None, None, Nil), Nil, Nil, Some(outputsB))
                )
                val fakeB     = new FakeClient(_ => Future.successful(Right(responseB)))

                // Start cycle A (gated).
                runCycleWith(fakeA)
                    .flatMap { _ =>
                        assert(fakeA.calls == 1)
                        // Edit + start cycle B with the fast fake.
                        firebox_var.update {
                            case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.500.meters)
                            case other => other
                        }
                        runCycleWith(fakeB)
                            .flatMap { _ =>
                                // B lands Live.
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled))
                                    .flatMap { _ =>
                                        // Starting B cancelled A's client.
                                        assert(fakeA.cancels == 1)
                                        // Now release A with a DIFFERENT response; the cycle guard must drop it.
                                        val outputsA  = WireEcolabeledOutputs("A-stale", "Lr", "Ls", "Lt", "Zt", "St")
                                        val responseA = responseB.copy(reportData =
                                            responseB.reportData.copy(ecolabeledOutputs = Some(outputsA))
                                        )
                                        gateA.success(Right(responseA))
                                        // Give the dropped callback a chance to (not) write.
                                        sleep(50).map { _ =>
                                            val states = readNow(RemoteFireboxPrefetch.remoteDataStatesSig)
                                            states(FireboxKind.Ecolabeled) match
                                                case RemoteDataState.Live(data) =>
                                                    // Still B's payload — A's late completion was suppressed.
                                                    assert(data.reportData.ecolabeledOutputs == Some(outputsB))
                                                case other                      => sys.error(s"expected Live(B), got $other")
                                            // The winning response's cache entry is present.
                                            assert(
                                                store.contains(
                                                    FireboxCalcCache.key("fail-stale", WireFireboxKind.Ecolabeled)
                                                )
                                            )
                                        }
                                    }
                            }
                    }
            }
        }
    }
