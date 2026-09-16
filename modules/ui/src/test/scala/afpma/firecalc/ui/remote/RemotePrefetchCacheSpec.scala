/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.engine.alg.en15544.{FireboxEngineSource, FireboxEngineSourceConfig, FireboxKind}
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import afpma.firecalc.ui.models.{engineStateVar, engineStateHelperVar, firebox_var, local_conditions_var}
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
import afpma.firecalc.ui.utils.{FireboxCalcCache, FireboxCalcCacheEntry, RemoteDataState}
import afpma.firecalc.units.coulombutils.meters

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.ownership.Owner
import io.circe.parser.decode
import io.circe.syntax.*
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

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
 */
object RemotePrefetchCacheSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── Shared helpers (mirror the sibling specs) ─────────────────────────────

    private def installWindow(): scala.collection.mutable.Map[String, String] =
        val g = js.Dynamic.global.globalThis
        if js.isUndefined            (g.selectDynamic("window")) || g.selectDynamic("window") == null then
            g.updateDynamic("window")(js.Dynamic.literal()     )
        val store = scala.collection.mutable.Map[String, String]()
        org.scalajs.dom.window.asInstanceOf[js.Dynamic].localStorage = js.Dynamic.literal(
            getItem    = ((k: String) => store.getOrElse(k, null)): js.Function1[String, String],
            setItem    = ((k: String, v: String) => store.update(k, v)): js.Function2[String, String, Unit],
            removeItem = ((k: String) => store.remove(k)): js.Function1[String, Unit]
        )
        store

    private def setNavigatorOnline(online: Boolean): Unit =
        org.scalajs.dom.window.asInstanceOf[js.Dynamic].navigator = js.Dynamic.literal(onLine = online)

    private def sleep(ms: Double): Future[Unit] =
        val p = Promise[Unit]()
        js.timers.setTimeout(ms)(p.success(()))
        p.future

    private def awaitState[A](sig: Signal[A])(p: A => Boolean, timeoutMs: Double = 5000)(using
        Owner
    ): Future[A] =
        val latest = scala.collection.mutable.ArrayBuffer.empty[Option[A]]
        latest += None
        val sub    = sig.addObserver(Observer[A](v => latest(0) = Some(v)))
        def loop(deadline: Double): Future[A] =
            latest(0) match
                case Some(v) if p(v) =>
                    sub.kill         ( )
                    Future.successful(v)
                case _               =>
                    if js.Date.now() > deadline then
                        sub.kill     (                                                                      )
                        Future.failed(new RuntimeException(s"timeout waiting for signal; last=${latest(0)}"))
                    else sleep(10).flatMap(_ => loop(deadline))
        loop(js.Date.now() + timeoutMs)

    private def settleHelper()(using Owner): Future[Unit] =
        awaitState(engineStateHelperVar.signal)(
            _.fcProj.firebox == engineStateVar.now().firebox,
            timeoutMs = 2000
        ).map(_ => ())

    private var chain                            : Future[Unit] = Future.successful(())
    private def serialized[A](body: => Future[A]): Future[A]    =
        val result = chain.flatMap(_ => body)
        chain = result.map(_ => ()).recover { case _ => () }
        result

    final class FakeClient(
        respond: FireboxCalcRequest => Future[Either[CalculationFailure, FireboxCalcResponse]]
    ) extends FireboxCalcClient[Future]:
        val requests   = scala.collection.mutable.Buffer[FireboxCalcRequest]()
        var calls      = 0
        var cancels    = 0
        var kindsCalls = 0
        def getKinds   = { kindsCalls += 1; Future.successful(Right(DiscoveryResponse(ApiVersion.v1, Nil))) }
        def calculate(req: FireboxCalcRequest) = { calls += 1; requests += req; respond(req) }
        def close = Future.successful(())

    private val bothRemote = FireboxEngineSourceConfig(
        Map    (
            FireboxKind.Ecolabeled     -> FireboxEngineSource.Remote("http://test.local/v1"),
            FireboxKind.Door15aCatalog -> FireboxEngineSource.Remote("http://test.local/v1")
        )
    )

    private val markerOutputs = WireEcolabeledOutputs("Y-cache", "Lr", "Ls", "Lt", "Zt", "St")

    private def successResponse(kind: WireFireboxKind): FireboxCalcResponse =
        FireboxCalcResponse(
            ApiVersion.v1,
            kind,
            "fr",
            WireValidated.Valid(WireFireboxContribution.Without                                                      ),
            WireValidated.Valid(WireFireboxContribution.Without                                                      ),
            List(
                ConstraintVerdict(
                    ConstraintTerm.GlassArea,
                    "glass area",
                    ConstraintDescriptor.Opaque,
                    ConstraintOutcome.Satisfied
                )
            ),
            FireboxReportData  (WireTable(None, None, Nil), WireTable(None, None, Nil), Nil, Nil, Some(markerOutputs))
        )

    private def failingClient: FakeClient =
        new FakeClient(_ => Future.successful(Left(CalculationFailure.TransportError(new RuntimeException("down")))))

    private def isLive(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)  : Boolean =
        states.get(kind).exists { case RemoteDataState.Live(_) => true; case _ => false }
    private def isFailed(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Boolean =
        states.get(kind).exists { case RemoteDataState.Failed(_) => true; case _ => false }
    private def isCached(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Boolean =
        states.get(kind).exists { case RemoteDataState.Cached(_, _) => true; case _ => false }

    private def runCycleWith(fake: FakeClient)(using Locale, Owner): Future[Unit] =
        val old = RemoteFireboxPrefetch.calcClientFactory
        RemoteFireboxPrefetch.calcClientFactory = _ =>
            RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
        settleHelper().map { _ =>
            RemoteFireboxPrefetch.runCycle(bothRemote)
            RemoteFireboxPrefetch.calcClientFactory = old
        }

    private def readEntry(
        store    : scala.collection.mutable.Map[String, String],
        projectId: String,
        kind     : WireFireboxKind
    ): FireboxCalcCacheEntry =
        decode[FireboxCalcCacheEntry](store(FireboxCalcCache.key(projectId, kind))).toOption.get

    // ── Fixture ───────────────────────────────────────────────────────────────

    private val ecolabeledYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description: { reference: test, date: "", country: France }
          |local_conditions:
          |  altitude: { value: "200", unit: meter }
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load: { value: "14.5", unit: kilogram }
          |  heating_cycle: { value: "8", unit: hour }
          |  min_efficiency: { value: "70", unit: percent }
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Ecolabeled:
          |    heat_output_reduced: null
          |    version: { type: "either", left_or_right: "left", value: "Version 1" }
          |    air_intake_shape: null
          |    firebox_depth: { value: "0.442", unit: meter }
          |    firebox_width: { value: "0.332", unit: meter }
          |    firebox_height: { value: "0.641", unit: meter }
          |    height_of_first_row_of_air_injectors: { value: "0.1", unit: meter }
          |    door_opening_width: { value: "0.3", unit: meter }
          |    glass_width: { value: "0.3", unit: meter }
          |    glass_height: { value: "0.3", unit: meter }
          |    ash_pit_height: { value: "0.05", unit: meter }
          |    air_manifold_height: { value: "0.05", unit: meter }
          |    firebox_floor_thickness: { value: "0.05", unit: meter }
          |    firebox_inner_wall_thickness: { value: "0.05", unit: meter }
          |    firebox_outer_wall_thickness: { value: "0.05", unit: meter }
          |    air_column_thickness: { value: "0.05", unit: meter }
          |    width_between_two_air_columns_sides: { value: "0.05", unit: meter }
          |    width_between_two_air_columns_rear: { value: "0.05", unit: meter }
          |    reinforcement_bars_offset_in_corners_R1: { value: "0", unit: meter }
          |    reinforcement_bars_offset_in_corners_R2: { value: "0", unit: meter }
          |    reinforcement_bars_offset_in_corners_R3: { value: "0", unit: meter }
          |    injector_height: { value: "0", unit: meter }
          |post_firebox_pipes: []""".stripMargin

    private val ecolabeledYamlEn = ecolabeledYaml.replace("locale: fr", "locale: en")

    private def loadEcolabeled(projectId: String, yaml: String = ecolabeledYaml): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(yaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                          )

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
