/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.v5.Firebox_V4
import afpma.firecalc.engine.alg.en15544.{FireboxEngineSource, FireboxEngineSourceConfig, FireboxKind}
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.ui.models.{engineStateVar, engineStateHelperVar, firebox_var}
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
import afpma.firecalc.ui.utils.{FireboxCalcCache, FireboxKindLabel, RemoteDataState}
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.units.coulombutils.meters

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.ownership.Owner
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

import utest.*

/**
 * Failure-path acceptance tests for the remote pre-fetch (ticket 12, Phase 2 IP
 * gate). Every test drives the production [[RemoteFireboxPrefetch.runCycle]] seam
 * with a fake [[FireboxCalcClient]] and asserts the OBSERVABLE degrade contract:
 * a remote-only kind that cannot be served (network down, no matching cache) is
 * marked `Failed` and unavailable — it is NEVER silently computed locally
 * (Q0.1-A), and one kind's failure never blocks another (Q11 partial assembly).
 */
object RemotePrefetchFailureSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── Shared helpers (mirror RemotePrefetchEndToEndSpec) ────────────────────

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

    private def readNow[A](sig: Signal[A])(using Owner): A =
        var v: Option[A] = None
        val sub = sig.addObserver(Observer[A](x => v = Some(x)))
        sub.kill()
        v.get

    private def settleHelper()(using Owner): Future[Unit] =
        awaitState(engineStateHelperVar.signal)(
            _.fcProj.firebox == engineStateVar.now().firebox,
            timeoutMs = 2000
        ).map(_ => ())

    /** Serialize async test bodies (the pre-fetch state is module-global). */
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

    private val markerOutputs = WireEcolabeledOutputs("Y-fail", "Lr", "Ls", "Lt", "Zt", "St")

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

    // The shipped 422 payload: calculation_failed with per-item pre-check
    // messages, localized by the server in the request locale. Shared by the
    // pre-check tests so the fixture blocks stay one-liners.
    private val preCheckItems = List(
        WireErrorItem("air_manifold_height", "La hauteur du manifold d'air (W) devrait être ≥ 5.0 cm"),
        WireErrorItem("glass_area", "L'aire de vitrage est trop petite pour la charge maximale"      )
    )

    // A 400 malformed_request payload mirroring the normative OpenAPI example:
    // a wire→typed decode verdict names the offending wire field, not an engine
    // pre-check result.
    private val decodeFailureItems = List(
        WireErrorItem("/kind", "Unknown WireFireboxKind: xyz")
    )

    /** A scripted protocol-error response for the [[FakeClient]]. */
    private def protocolFailure(
        status : Int,
        error  : String,
        items  : List[WireErrorItem],
        message: String = "Engine computation failed"
    ): Either[CalculationFailure, FireboxCalcResponse] =
        Left(CalculationFailure.ProtocolError(status, error, message, Some(items)))

    /** The expected generic banner, composed through the same i18n accessor production renders. */
    private def kindUnavailableBanner(using Locale): String =
        I18N_UI.remote_calc.kind_unavailable(FireboxKindLabel(FireboxKind.Ecolabeled))

    /** The expected pre-check banner for `items`, composed through the same accessor. */
    private def preCheckBanner(items: List[WireErrorItem])(using Locale): String =
        I18N_UI.remote_calc.precheck_failed(
            FireboxKindLabel             (FireboxKind.Ecolabeled),
            items.map(_.message).mkString(", "                  )
        )

    /**
     * Assert the observable generic-banner contract for a failure that must NOT
     * unlock the pre-check banner: the kind lands terminal `Failed` carrying
     * EXACTLY the generic banner — composed through the same i18n accessor
     * production renders, so the equality pin makes mis-classification
     * impossible to pass: any pre-check rendering of the payload differs from
     * the `kind_unavailable` string and fails the assert. No per-scenario
     * negative assertions are needed. The client must have been called once,
     * and nothing may be cached.
     */
    private def expectGenericBanner(
        projectId: String,
        failure  : Either[CalculationFailure, FireboxCalcResponse]
    )(using Locale, Owner): Future[Unit] =
        val store = installWindow()
        setNavigatorOnline(true     )
        loadEcolabeled    (projectId)
        val fake = new FakeClient(_ => Future.successful(failure))
        runCycleWith(fake).flatMap { _ =>
            awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Ecolabeled)).map { states =>
                states(FireboxKind.Ecolabeled) match
                    case RemoteDataState.Failed(reason) =>
                        assert(reason == kindUnavailableBanner)
                    case other                          => sys.error(s"expected terminal Failed, got $other")
                assert(fake.calls == 1)
                assert(store.isEmpty  )
            }
        }

    private def isLive(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)    : Boolean =
        states.get(kind).exists { case RemoteDataState.Live(_) => true; case _ => false }
    private def isFailed(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)  : Boolean =
        states.get(kind).exists { case RemoteDataState.Failed(_) => true; case _ => false }
    private def isCached(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)  : Boolean =
        states.get(kind).exists { case RemoteDataState.Cached(_, _) => true; case _ => false }
    private def isRejected(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Boolean =
        states.get(kind).exists { case RemoteDataState.Rejected(_) => true; case _ => false }

    private def runCycleWith(fake: FakeClient)(using Locale, Owner): Future[Unit] =
        val old = RemoteFireboxPrefetch.calcClientFactory
        RemoteFireboxPrefetch.calcClientFactory = _ =>
            RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
        settleHelper().map { _ =>
            RemoteFireboxPrefetch.runCycle(bothRemote)
            RemoteFireboxPrefetch.calcClientFactory = old
        }

    // ── Fixtures ──────────────────────────────────────────────────────────────

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

    private val door15aYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description: { reference: door15a-test, date: "", country: France }
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
          |  Door15aFirebox_Catalog:
          |    reference: "Door15aFirebox_Catalog_Example"
          |    firebox_depth: { value: "0.39", unit: meter }
          |    firebox_width: { value: "0.29", unit: meter }
          |    firebox_height: { value: "1.0", unit: meter }
          |    load_size_nominal: { value: "14.5", unit: kilogram }
          |    sb: { value: "1.6", unit: centimeter }
          |    pressure_loss_table_raw: "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16"
          |    expectedAirIntakePipeShapes:
          |      - Circle:
          |          diameter: { value: "0.2", unit: meter }
          |    actualAirIntakePipeShape:
          |      Circle:
          |        diameter: { value: "0.2", unit: meter }
          |    co2_dry_nominal: { value: "12.0", unit: percent }
          |    emissions_values:
          |      firebox_name: "x"
          |      accredited_or_notified_body: "y"
          |      test_reports: []
          |      emissions_values:
          |        co: { polluant_name: "CO", valueO: 1.0, test_method: "", o2ref: { value: "13.0", unit: percent } }
          |        dust: { polluant_name: "Dust", valueO: 1.0, test_method: "", o2ref: { value: "13.0", unit: percent } }
          |        ogc: { polluant_name: "OGC", valueO: 1.0, test_method: "", o2ref: { value: "13.0", unit: percent } }
          |        nox: { polluant_name: "NOx", valueO: 1.0, test_method: "", o2ref: { value: "13.0", unit: percent } }
          |    glass_area: { value: "0.05", unit: meter^2 }
          |    height_of_lowest_opening: { value: "0.05", unit: meter }
          |    heat_output_reduced: "NotDefined"
          |post_firebox_pipes: []""".stripMargin

    private def loadEcolabeled(projectId: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(ecolabeledYaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                                    )

    private def loadDoor15a(projectId: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(door15aYaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                                 )

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

                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                // Door15a: the server does not serve this kind.
                loadDoor15a("fail-partial-door")
                val doorFake = new FakeClient(_ =>
                    Future.successful(
                        Left(CalculationFailure.ProtocolError(404, "unsupported_kind", "kind not served", None))
                    )
                )
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(doorFake, () => doorFake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isFailed(_, FireboxKind.Door15aCatalog))
                            .flatMap { _ =>
                                // Ecolabeled: a different project, served successfully.
                                loadEcolabeled("fail-partial-eco")
                                val ecoFake = new FakeClient(_ =>
                                    Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled)))
                                )
                                RemoteFireboxPrefetch.calcClientFactory =
                                    _ => RemoteFireboxPrefetch.PrefetchClient(ecoFake, () => ecoFake.cancels += 1)
                                settleHelper()
                                    .map { _ =>
                                        RemoteFireboxPrefetch.runCycle(bothRemote)
                                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                                    }
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

                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                // Cycle A: gated — its response never arrives until we say so.
                val gateA      = Promise[Either[CalculationFailure, FireboxCalcResponse]]()
                val fakeA      = new FakeClient(_ => gateA.future)
                val outputsB   = WireEcolabeledOutputs("B-wins", "Lr", "Ls", "Lt", "Zt", "St")
                val responseB  = FireboxCalcResponse(
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
                val fakeB      = new FakeClient(_ => Future.successful(Right(responseB)))

                var next = fakeA
                RemoteFireboxPrefetch.calcClientFactory = _ =>
                    val f = next
                    RemoteFireboxPrefetch.PrefetchClient(f, () => f.cancels += 1)

                // Start cycle A (gated).
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                    }
                    .flatMap { _ =>
                        assert(fakeA.calls == 1)
                        // Edit + start cycle B with the fast fake.
                        next = fakeB
                        firebox_var.update {
                            case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.500.meters)
                            case other => other
                        }
                        settleHelper()
                            .map { _ =>
                                RemoteFireboxPrefetch.runCycle(bothRemote)
                            }
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
                                            RemoteFireboxPrefetch.calcClientFactory = oldFactory
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
