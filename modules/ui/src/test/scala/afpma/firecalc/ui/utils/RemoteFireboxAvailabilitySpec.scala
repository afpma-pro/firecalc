/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.engine.alg.en15544.FireboxEngineSource
import afpma.firecalc.engine.alg.en15544.FireboxEngineSourceConfig
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.ui.remote.RemoteFireboxPrefetch

import utest.*

/**
 * Unit tests for [[RemoteFireboxAvailability]] — the pure per-kind resolution
 * behind partial registry assembly (Q11).
 *
 * Acceptance covered:
 *  - a remote failure recorded for ONE kind marks only that kind unavailable;
 *    every other kind — in particular every locally computable one — stays
 *    usable;
 *  - a remote-only kind is NEVER satisfied by a local engine on failure: no
 *    combination of source config and state yields `KindComputation.Local` for a
 *    `Remote`-configured kind.
 */
object RemoteFireboxAvailabilitySpec extends TestSuite:

    private val BaseUri = "http://calc.test/v1"

    /** Only `Ecolabeled` computes remotely; every other kind is local. */
    private val ecoRemote: FireboxEngineSourceConfig =
        FireboxEngineSourceConfig(Map(FireboxKind.Ecolabeled -> FireboxEngineSource.Remote(BaseUri)))

    /** Only `Door15aCatalog` computes remotely; every other kind is local. */
    private val door15aRemote: FireboxEngineSourceConfig =
        FireboxEngineSourceConfig(Map(FireboxKind.Door15aCatalog -> FireboxEngineSource.Remote(BaseUri)))

    /** `Ecolabeled` and `AFPMA_PRSE` compute remotely. */
    private val twoRemote: FireboxEngineSourceConfig =
        FireboxEngineSourceConfig(
            Map(
                FireboxKind.Ecolabeled -> FireboxEngineSource.Remote(BaseUri),
                FireboxKind.AFPMA_PRSE -> FireboxEngineSource.Remote(BaseUri)
            )
        )

    /** Opaque payload stub — the availability logic never inspects it. */
    private case object StubData

    private val failed: RemoteDataState[StubData.type] = RemoteDataState.Failed("service unreachable")

    val tests = Tests:

        test("cycle guard — cycle 1 cannot overwrite cycle 2 when outcomes complete out of order") {
            val kind        = FireboxKind.Ecolabeled
            val cycle1Token = 1
            val cycle2Token = 2
            val current     = Map(kind -> cycle2Token)
            var outcome     = "cycle 2"

            RemoteFireboxPrefetch.guardCurrentCycle(current, kind, cycle1Token) {
                outcome = "stale cycle 1"
            }
            assert(outcome == "cycle 2")

            RemoteFireboxPrefetch.guardCurrentCycle(current, kind, cycle2Token) {
                outcome = "current cycle 2"
            }
            assert(outcome == "current cycle 2")
        }

        // ── Baseline: the historical all-local configuration ───────────────

        test("AllLocal — every closed kind is Local and usable, nothing unavailable") {
            val cfg = FireboxEngineSourceConfig.AllLocal
            FireboxKind.closed.foreach: kind =>
                assert(RemoteFireboxAvailability.computationFor(kind, cfg, Map.empty) == KindComputation.Local)
            assert(RemoteFireboxAvailability.usableKinds(cfg, Map.empty) == FireboxKind.closed)
            assert(RemoteFireboxAvailability.unavailableKinds(cfg, Map.empty).isEmpty)
        }

        test("AllLocal — a stray recorded failure cannot make a Local kind unavailable") {
            // Defensive: the state map is keyed by kind, and a Local kind must
            // ignore it entirely — otherwise a config flip would silently strand
            // a locally computable kind.
            val cfg    = FireboxEngineSourceConfig.AllLocal
            val states = Map(FireboxKind.Ecolabeled -> failed)
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, cfg, states) ==
                    KindComputation.Local
            )
            assert(RemoteFireboxAvailability.unavailableKinds(cfg, states).isEmpty)
        }

        // ── Q11: partial assembly — only the failing kind is unavailable ───

        test("partial assembly — one kind's failure marks ONLY that kind unavailable") {
            val states = Map(FireboxKind.Ecolabeled -> failed)

            assert(RemoteFireboxAvailability.unavailableKinds(ecoRemote, states) == Set(FireboxKind.Ecolabeled))

            // Every other closed kind keeps working.
            val expectedUsable = FireboxKind.closed - FireboxKind.Ecolabeled
            assert(RemoteFireboxAvailability.usableKinds(ecoRemote, states) == expectedUsable)
            expectedUsable.foreach: kind =>
                assert(RemoteFireboxAvailability.computationFor(kind, ecoRemote, states) == KindComputation.Local)
        }

        test("partial assembly — one remote kind failing leaves the other remote kind alone") {
            // Ecolabeled failed; AFPMA_PRSE has fresh data. Both are remote, so
            // this is the case a global failure flag used to get wrong.
            val states = Map(
                FireboxKind.Ecolabeled -> failed,
                FireboxKind.AFPMA_PRSE -> RemoteDataState.Live(StubData)
            )
            assert(RemoteFireboxAvailability.unavailableKinds(twoRemote, states) == Set(FireboxKind.Ecolabeled))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.AFPMA_PRSE, twoRemote, states) ==
                    KindComputation.Remote(None, StubData)
            )
            assert(RemoteFireboxAvailability.isUsable(FireboxKind.AFPMA_PRSE, twoRemote, states))
        }

        test("partial assembly — every remote kind failing does not touch local kinds") {
            val states = Map(
                FireboxKind.Ecolabeled -> failed,
                FireboxKind.AFPMA_PRSE -> failed
            )
            assert(
                RemoteFireboxAvailability.unavailableKinds(twoRemote, states) ==
                    Set(FireboxKind.Ecolabeled, FireboxKind.AFPMA_PRSE)
            )
            assert(
                RemoteFireboxAvailability.usableKinds(twoRemote, states) ==
                    FireboxKind.closed -- Set(FireboxKind.Ecolabeled, FireboxKind.AFPMA_PRSE)
            )
        }

        // ── Q0.1-A: a remote-only kind never falls back to the local engine ─

        test("remote-only kind — a failure yields Unavailable, never Local") {
            val states = Map(FireboxKind.Ecolabeled -> failed)
            RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) match
                case KindComputation.Unavailable(kind, reason) =>
                    assert(kind == FireboxKind.Ecolabeled )
                    assert(reason == "service unreachable")
                case other                                     =>
                    throw new java.lang.AssertionError(s"expected Unavailable, got $other")
        }

        test("remote-only kind — a geometry rejection yields Rejected, never Unavailable") {
            // A 422 pre-check verdict from a REACHABLE service: the kind is NOT
            // unavailable — only the project's inputs are rejected — so it must
            // stay out of the unavailable banner while still carrying its
            // per-kind reason (the FireboxPanel constraint tooltip).
            val reason = "firebox_height = 0"
            val states: Map[FireboxKind, RemoteDataState[StubData.type]] =
                Map(FireboxKind.Door15aCatalog -> RemoteDataState.Rejected(reason))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Door15aCatalog, door15aRemote, states) ==
                    KindComputation.Rejected(FireboxKind.Door15aCatalog, reason)
            )
            assert(!RemoteFireboxAvailability.isUsable(FireboxKind.Door15aCatalog, door15aRemote, states))
            assert(RemoteFireboxAvailability.unavailableKinds(door15aRemote, states).isEmpty)
            assert(
                RemoteFireboxAvailability.usableKinds(door15aRemote, states) ==
                    FireboxKind.closed - FireboxKind.Door15aCatalog
            )
            // Every other closed kind is untouched — still Local.
            (FireboxKind.closed - FireboxKind.Door15aCatalog).foreach: kind =>
                assert(RemoteFireboxAvailability.computationFor(kind, door15aRemote, states) == KindComputation.Local)
        }

        test("remote-only kind — NO state yields Local for any remote kind") {
            // The invariant, checked exhaustively over the state space rather
            // than on one sample: a Remote-configured kind must never resolve to
            // the local engine, whatever its data provenance.
            val allStates = List(
                RemoteDataState.Absent,
                RemoteDataState.Live  (StubData    ),
                RemoteDataState.Cached(1d, StubData),
                failed
            )
            for
                kind  <- List(FireboxKind.Ecolabeled, FireboxKind.AFPMA_PRSE)
                state <- allStates
            do
                val resolved = RemoteFireboxAvailability.computationFor(kind, twoRemote, state)
                assert(resolved != KindComputation.Local)
        }

        test("remote-only kind — pending is not usable and not unavailable") {
            // A fetch in flight must not be reported as a failure (it would flash
            // a spurious "unavailable" message) and must not be computed locally.
            val states = Map.empty[FireboxKind, RemoteDataState[StubData.type]]
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) ==
                    KindComputation.Pending(FireboxKind.Ecolabeled)
            )
            assert(!RemoteFireboxAvailability.isUsable(FireboxKind.Ecolabeled, ecoRemote, states))
            assert(RemoteFireboxAvailability.unavailableKinds(ecoRemote, states).isEmpty         )
        }

        // ── Cached provenance is usable but distinguishable from live ──────

        test("cached data — usable, and reports the save timestamp") {
            val states = Map(FireboxKind.Ecolabeled -> RemoteDataState.Cached(1_700_000_000_000d, StubData))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) ==
                    KindComputation.Remote(Some(1_700_000_000_000d), StubData)
            )
            assert(RemoteFireboxAvailability.isUsable(FireboxKind.Ecolabeled, ecoRemote, states))
            assert(RemoteFireboxAvailability.unavailableKinds(ecoRemote, states).isEmpty        )
        }

        test("live data — usable, and carries no cache timestamp") {
            val states = Map(FireboxKind.Ecolabeled -> RemoteDataState.Live(StubData))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) ==
                    KindComputation.Remote(None, StubData)
            )
        }

        test("stateOf — a kind with no entry reads as Absent") {
            assert(RemoteFireboxAvailability.stateOf(Map.empty, FireboxKind.Ecolabeled) == RemoteDataState.Absent)
            assert(
                RemoteFireboxAvailability.stateOf(
                    Map(FireboxKind.Ecolabeled -> RemoteDataState.Live(StubData)),
                    FireboxKind.Traditional
                ) == RemoteDataState.Absent
            )
        }

        // ── sourceFor decision seam (relocated from RemoteRegistrySuite) ───

        test("sourceFor — returns Remote for a configured kind") {
            assert(ecoRemote.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Remote(BaseUri))
        }

        test("sourceFor — returns Local for a kind absent from the config") {
            assert(ecoRemote.sourceFor(FireboxKind.Traditional) == FireboxEngineSource.Local)
            assert(ecoRemote.sourceFor(FireboxKind.AFPMA_PRSE) == FireboxEngineSource.Local )
        }

        test("sourceFor — AllLocal config yields Local for every kind") {
            val allLocal = FireboxEngineSourceConfig.AllLocal
            assert(allLocal.sourceFor(FireboxKind.Traditional) == FireboxEngineSource.Local   )
            assert(allLocal.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Local    )
            assert(allLocal.sourceFor(FireboxKind.AFPMA_PRSE) == FireboxEngineSource.Local    )
            assert(allLocal.sourceFor(FireboxKind.SingleTested) == FireboxEngineSource.Local  )
            assert(allLocal.sourceFor(FireboxKind.Door15aCatalog) == FireboxEngineSource.Local)
            assert(allLocal.sourceFor(FireboxKind.CustomForLab) == FireboxEngineSource.Local  )
        }

        test("sourceFor — multiple remote kinds resolve independently") {
            assert(twoRemote.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Remote(BaseUri))
            assert(twoRemote.sourceFor(FireboxKind.AFPMA_PRSE) == FireboxEngineSource.Remote(BaseUri))
            assert(twoRemote.sourceFor(FireboxKind.Traditional) == FireboxEngineSource.Local         )
        }

        // ── Ticket 09: the Ecolabeled remote-only cutover ──────────────────

        test("ticket 09 — VITE_FIREBOX_REMOTE_KINDS=ecolabeled routes Ecolabeled to the calc backend only") {
            // The exact env-file value, parsed the way UIConfig parses it:
            // Ecolabeled resolves Remote and — with no fetch concluded yet —
            // is Pending, NOT locally computable.
            val (cfg, unknown) = FireboxEngineSourceConfig.fromRemoteKeys("ecolabeled".split(",").toSeq, BaseUri)
            assert(unknown.isEmpty                                                             )
            assert(cfg.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Remote(BaseUri))
            RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, cfg, Map.empty) match
                case KindComputation.Pending(FireboxKind.Ecolabeled) => ()
                case other                                           => throw new java.lang.AssertionError(s"expected Pending, got $other")
        }

        test("ticket 09 — remote-only Ecolabeled: every failure mode ends Unavailable, never Local") {
            // Transport, protocol, decode and unsupported-kind failures all
            // arrive at the availability layer as a recorded Failed state;
            // none of them may resolve to the local Austrian engine.
            val reasons = List(
                "service unreachable",
                "unexpected response status: 503",
                "undecodable response body",
                "unsupported kind: ecolabeled"
            )
            for reason <- reasons do
                val states = Map(FireboxKind.Ecolabeled -> RemoteDataState.Failed(reason))
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) match
                    case KindComputation.Unavailable(FireboxKind.Ecolabeled, r) => assert(r == reason)
                    case other                                                  =>
                        throw new java.lang.AssertionError(s"expected Unavailable($reason), got $other")
                assert(!RemoteFireboxAvailability.isUsable(FireboxKind.Ecolabeled, ecoRemote, states))
        }

        test("ticket 09 — an Ecolabeled outage leaves every other kind serving as before") {
            val states = Map(FireboxKind.Ecolabeled -> failed)
            val others = FireboxKind.closed - FireboxKind.Ecolabeled
            assert(RemoteFireboxAvailability.unavailableKinds(ecoRemote, states) == Set(FireboxKind.Ecolabeled))
            others.foreach: kind =>
                assert(RemoteFireboxAvailability.computationFor(kind, ecoRemote, states) == KindComputation.Local)
                assert(RemoteFireboxAvailability.isUsable(kind, ecoRemote, states)                               )
        }

        test("ticket 09 — a cached Ecolabeled verdict stays in Ecolabeled's slot, never another kind's") {
            // The per-kind state map is the availability-side expression of
            // cache-key isolation: a replayed Ecolabeled response makes
            // Ecolabeled usable (labelled cached) and cannot satisfy any other
            // kind — theirs read Absent and keep their configured source.
            val states = Map(FireboxKind.Ecolabeled -> RemoteDataState.Cached(1_700_000_000_000d, StubData))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, ecoRemote, states) ==
                    KindComputation.Remote(Some(1_700_000_000_000d), StubData)
            )
            assert(RemoteFireboxAvailability.isUsable(FireboxKind.Ecolabeled, ecoRemote, states))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Traditional, ecoRemote, states) ==
                    KindComputation.Local
            )
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Door15aCatalog, ecoRemote, states) ==
                    KindComputation.Local
            )
            assert(RemoteFireboxAvailability.unavailableKinds(ecoRemote, states).isEmpty        )
        }

        // ── Ticket 10: the Door15aCatalog remote-only cutover ──────────────

        test("ticket 10 — VITE_FIREBOX_REMOTE_KINDS=ecolabeled,door15a-catalog routes both Austrian kinds remotely") {
            // The exact deployed env-file value, parsed the way UIConfig parses
            // it: both Austrian kinds resolve Remote — Pending with no fetch
            // concluded yet, NOT locally computable — and nothing else does.
            val (cfg, unknown) =
                FireboxEngineSourceConfig.fromRemoteKeys("ecolabeled,door15a-catalog".split(",").toSeq, BaseUri)
            assert(unknown.isEmpty)
            assert(cfg.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Remote(BaseUri)    )
            assert(cfg.sourceFor(FireboxKind.Door15aCatalog) == FireboxEngineSource.Remote(BaseUri))
            (FireboxKind.closed -- Set(FireboxKind.Ecolabeled, FireboxKind.Door15aCatalog)).foreach: kind =>
                assert(cfg.sourceFor(kind) == FireboxEngineSource.Local)
            RemoteFireboxAvailability.computationFor(FireboxKind.Door15aCatalog, cfg, Map.empty) match
                case KindComputation.Pending(FireboxKind.Door15aCatalog) => ()
                case other                                               => throw new java.lang.AssertionError(s"expected Pending, got $other")
        }

        test("ticket 10 — remote-only Door15aCatalog: every failure mode ends Unavailable, never Local") {
            // Door15aCatalog was locally computable before the cutover; pin
            // that no failure reason — including an unsupported-kind verdict
            // naming its own wire key — can send it back to the browser
            // engine.
            val reasons = List(
                "service unreachable",
                "unexpected response status: 503",
                "undecodable response body",
                "unsupported kind: door15a-catalog"
            )
            for reason <- reasons do
                val states = Map(FireboxKind.Door15aCatalog -> RemoteDataState.Failed(reason))
                RemoteFireboxAvailability.computationFor(FireboxKind.Door15aCatalog, door15aRemote, states) match
                    case KindComputation.Unavailable(FireboxKind.Door15aCatalog, r) => assert(r == reason)
                    case other                                                      =>
                        throw new java.lang.AssertionError(s"expected Unavailable($reason), got $other")
                assert(!RemoteFireboxAvailability.isUsable(FireboxKind.Door15aCatalog, door15aRemote, states))
        }

        test("ticket 10 — a Door15aCatalog outage isolates from the other deployed remote kind") {
            // Under the deployed two-kind config, a Door15aCatalog failure
            // marks only Door15aCatalog unavailable: Ecolabeled still resolves
            // remotely (Pending here — no fetch concluded) and local kinds are
            // untouched.
            val (cfg, _) =
                FireboxEngineSourceConfig.fromRemoteKeys("ecolabeled,door15a-catalog".split(",").toSeq, BaseUri)
            val states   = Map(FireboxKind.Door15aCatalog -> failed)
            assert(RemoteFireboxAvailability.unavailableKinds(cfg, states) == Set(FireboxKind.Door15aCatalog))
            RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, cfg, states) match
                case KindComputation.Pending(FireboxKind.Ecolabeled) => ()
                case other                                           => throw new java.lang.AssertionError(s"expected Pending, got $other")
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Traditional, cfg, states) ==
                    KindComputation.Local
            )
        }

        test("ticket 10 — a cached Door15aCatalog verdict stays in Door15aCatalog's slot, never another kind's") {
            // Complement of the ticket-09 slot test: a replayed Door15aCatalog
            // response makes Door15aCatalog usable (labelled cached) and cannot
            // satisfy Ecolabeled, which reads Absent and keeps its own source.
            val states = Map(FireboxKind.Door15aCatalog -> RemoteDataState.Cached(1_700_000_000_000d, StubData))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Door15aCatalog, door15aRemote, states) ==
                    KindComputation.Remote(Some(1_700_000_000_000d), StubData)
            )
            assert(RemoteFireboxAvailability.isUsable(FireboxKind.Door15aCatalog, door15aRemote, states))
            assert(
                RemoteFireboxAvailability.computationFor(FireboxKind.Ecolabeled, door15aRemote, states) ==
                    KindComputation.Local
            )
            assert(RemoteFireboxAvailability.unavailableKinds(door15aRemote, states).isEmpty            )
        }

end RemoteFireboxAvailabilitySpec
