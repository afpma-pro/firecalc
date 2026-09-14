/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import afpma.firecalc.ui.models.{firebox_var, local_conditions_var}
import afpma.firecalc.ui.utils.{FireboxCalcCache, RemoteDataState}
import afpma.firecalc.units.coulombutils.meters

import com.raquo.airstream.ownership.Owner
import io.circe.parser.decode
import io.circe.syntax.*
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future}

import utest.*

/**
 * Cache acceptance tests for the remote pre-fetch (ticket 12, Phase 2 IP gate).
 *
 * The cache is the offline/degrade store: a successful response is persisted
 * under `(project id, kind)` stamped with the fingerprint of the request that
 * produced it ([[FireboxCalcCache]]). These tests drive the production
 * [[RemoteFireboxPrefetch.runCycle]] seam and assert the OBSERVABLE cache
 * contract on the persisted `localStorage` contents and the served state:
 * entries originate only in remote responses, the fingerprint covers every
 * request-defining input, a matching entry is replayed on network failure, a
 * mismatched entry never leaks stale numbers, and replay performs zero network
 * (and therefore zero local-Austrian) computation.
 *
 * Scaffolding is shared with the sibling specs — see [[RemotePrefetchHarness]].
 */
object RemotePrefetchCacheSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── The shared prefetch harness ───────────────────────────────────────────
    // One fake client, one set of poll/settle helpers: see RemotePrefetchHarness.

    import RemotePrefetchHarness.*

    val tests = Tests {

        test("entries originate in remote responses — one key, the exact served body, nothing on failure") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true          )
                loadEcolabeled    ("cache-origin")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val served = successResponse(WireFireboxKind.Ecolabeled)
                val fake   = new FakeClient(_ => Future.successful(Right(served)))
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            val key = FireboxCalcCache.key("cache-origin", WireFireboxKind.Ecolabeled)
                            // Exactly one cache entry, under (project, kind).
                            assert(store.keySet == Set(key))
                            // Its body decodes to the very response the fake served.
                            val entry = readEntry(store, "cache-origin", WireFireboxKind.Ecolabeled)
                            assert        (decode[FireboxCalcResponse](entry.responseBody) == Right(served))
                            assert        (entry.projectId == "cache-origin"                               )
                            assert        (entry.kind == WireFireboxKind.Ecolabeled.wire                   )
                            // A failed cycle writes nothing.
                            loadEcolabeled("cache-origin-failed"                                           )
                            runCycleWith(failingClient).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isFailed(_, FireboxKind.Ecolabeled)
                                ).map { _ =>
                                    assert(store.keySet == Set(key))
                                }
                            }
                    }
                }
            }
        }

        test("fingerprint covers every request-defining input — dimension, locale and altitude each change it") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true      )
                loadEcolabeled    ("cache-fp")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                // Baseline cycle B0.
                runCycleWith(fake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            val req0 = fake.requests.last
                            val f0   = readEntry(store, "cache-fp", WireFireboxKind.Ecolabeled).fingerprint
                            // The stored fingerprint IS the digest of the whole serialized request.
                            assert(f0 == FireboxCalcCache.fingerprint(req0.asJson.noSpaces))

                            def cycleAndFingerprint(): Future[String] =
                                runCycleWith(fake).flatMap { _ =>
                                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                        isLive(_, FireboxKind.Ecolabeled)
                                    ).map { _ =>
                                        readEntry(store, "cache-fp", WireFireboxKind.Ecolabeled).fingerprint
                                    }
                                }

                            // (1) a DTO dimension change.
                            firebox_var.update {
                                case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.500.meters)
                                case other => other
                            }
                            cycleAndFingerprint().flatMap { f1 =>
                                assert        (f1 != f0                    )
                                // (2) a locale change (re-load the identical project in English).
                                loadEcolabeled("cache-fp", ecolabeledYamlEn)
                                cycleAndFingerprint().flatMap { f2 =>
                                    assert                     (f2 != f0                     )
                                    // (3) an operating-envelope altitude change.
                                    local_conditions_var.update(_.copy(altitude = 500.meters))
                                    cycleAndFingerprint().map { f3 =>
                                        assert(f3 != f0)
                                    }
                                }
                            }
                    }
                }
            }
        }

        test("a matching entry retains established behavior — network failure replays the identical data as Cached") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true          )
                loadEcolabeled    ("cache-replay")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        states =>
                            val seedData = states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Live(data) => data
                                case other                      => sys.error(s"expected Live, got $other")
                            // Fail the network (still online, so the ladder runs and fails), cache matches.
                            runCycleWith(failingClient).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isCached(_, FireboxKind.Ecolabeled)
                                ).map { states2 =>
                                    states2(FireboxKind.Ecolabeled) match
                                        case RemoteDataState.Cached(savedAtMs, data) =>
                                            assert(savedAtMs > 0   )
                                            // Identical payload to the live response.
                                            assert(data == seedData)
                                            // The replayed entry is still the one persisted by the seed cycle.
                                            assert(
                                                store.contains(
                                                    FireboxCalcCache.key("cache-replay", WireFireboxKind.Ecolabeled)
                                                )
                                            )
                                        case other                                   => sys.error(s"expected Cached, got $other")
                                }
                            }
                    }
                }
            }
        }

        test("mismatches leak no stale output — changed inputs or another project never hit the entry") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true)
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // Seed project A's entry from request B0.
                loadEcolabeled("cache-iso-a")
                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        _ =>
                            val keyA = FireboxCalcCache.key("cache-iso-a", WireFireboxKind.Ecolabeled)
                            assert(store.contains(keyA))

                            // (a) another project id, SAME firebox (so the fingerprint would match): it must
                            // NOT read project A's entry — the key includes the project id.
                            loadEcolabeled("cache-iso-b")
                            runCycleWith(failingClient).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isFailed(_, FireboxKind.Ecolabeled)
                                ).flatMap { statesB =>
                                    assert(isFailed(statesB, FireboxKind.Ecolabeled))
                                    assert(
                                        !store.contains(FireboxCalcCache.key("cache-iso-b", WireFireboxKind.Ecolabeled))
                                    )

                                    // (b) back to project A, but the inputs changed (request B1): the B0 entry
                                    // must not satisfy B1 — fingerprint mismatch is a miss, not a stale hit.
                                    loadEcolabeled("cache-iso-a")
                                    firebox_var.update {
                                        case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.500.meters)
                                        case other => other
                                    }
                                    runCycleWith(failingClient).flatMap { _ =>
                                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                            isFailed(_, FireboxKind.Ecolabeled)
                                        ).map { statesA =>
                                            assert(isFailed(statesA, FireboxKind.Ecolabeled))
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }

        test(
            "no cache path invokes Austrian calculation — replay makes zero network calls and serves the stored payload"
        ) {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true              )
                loadEcolabeled    ("cache-noaustrian")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val seedFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                runCycleWith(seedFake).flatMap { _ =>
                    awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).flatMap {
                        states =>
                            val seedData    = states(FireboxKind.Ecolabeled) match
                                case RemoteDataState.Live(data) => data
                                case other                      => sys.error(s"expected Live, got $other")
                            // Offline: the ladder is skipped entirely, so the replay client is never called.
                            setNavigatorOnline(false)
                            val replayFake  = new FakeClient(_ =>
                                Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled)))
                            )
                            val callsBefore = replayFake.calls
                            runCycleWith(replayFake).flatMap { _ =>
                                awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                    isCached(_, FireboxKind.Ecolabeled)
                                ).map { states2 =>
                                    setNavigatorOnline(true                           )
                                    // Zero network calls on the cache-hit cycle.
                                    assert            (replayFake.calls == callsBefore)
                                    states2(FireboxKind.Ecolabeled) match
                                        case RemoteDataState.Cached(_, data) =>
                                            // The served payload is the stored remote response (marker outputs intact),
                                            // not a locally recomputed result.
                                            assert(data == seedData                                        )
                                            assert(data.reportData.ecolabeledOutputs == Some(markerOutputs))
                                            // The served payload came from the persisted entry.
                                            assert(
                                                store.contains(
                                                    FireboxCalcCache.key("cache-noaustrian", WireFireboxKind.Ecolabeled)
                                                )
                                            )
                                        case other                           => sys.error(s"expected Cached, got $other")
                                }
                            }
                    }
                }
            }
        }
    }
