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
import afpma.firecalc.ui.models.{engineStateVar, firebox_var}
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
import afpma.firecalc.ui.utils.{FireboxCalcCache, RemoteDataState}

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.ownership.Owner
import afpma.firecalc.units.coulombutils.meters
import io.circe.syntax.*
import io.taig.babel.{Locale, Locales}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

import utest.*

/**
 * End-to-end acceptance tests for the browser remote-calculation pre-fetch
 * (ticket 12, Phase 2 IP gate), driven through the production seam
 * [[RemoteFireboxPrefetch.runCycle]] with a fake [[FireboxCalcClient]] standing
 * in for the browser's `FetchCalcClient`.
 *
 * The fake implements the very same [[FireboxCalcClient]] interface production
 * uses, so the real machinery — request construction, wire→engine conversion,
 * fingerprint-cache read/write, per-kind verdict recording — runs against
 * scripted HTTP outcomes (SPEC_02 testing decision 1: the normative seam, not an
 * in-process Austrian fake).
 *
 * Assertions are on observable state only: the per-kind `remoteDataStatesSig`
 * (the single source of truth), the derived `prefetchedRemoteDataSig` /
 * `prefetchedRemoteCachedAtSig` / `unavailableFireboxKindsSig`, the captured wire
 * requests, and the persisted `localStorage` cache contents.
 */
object RemotePrefetchEndToEndSpec extends TestSuite:

    given ExecutionContext = ExecutionContext.global

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Install a `window` + `localStorage` stub for the bare Node test env. */
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

    /** Poll a signal until `p` holds (signals are read via a live subscription). */
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

    /** Read a signal's current value synchronously (subscribe, capture, kill). */
    private def readNow[A](sig: Signal[A])(using Owner): A =
        var v: Option[A] = None
        val sub = sig.addObserver(Observer[A](x => v = Some(x)))
        sub.kill()
        v.get

    /**
     * Wait for the derived `engineStateHelperVar` to catch up with `engineStateVar`.
     * The helper is a `zoomLazy` var whose recomputation propagates on a later
     * microtask/macrotask, so a `runCycle` issued synchronously after a `set` would
     * read a STALE firebox. Production never hits this because its binder is
     * triggered BY the helper's change; tests drive `runCycle` directly and must
     * settle first.
     */
    private def settleHelper()(using Owner): Future[Unit] =
        awaitState(afpma.firecalc.ui.models.engineStateHelperVar.signal)(
            _.fcProj.firebox == engineStateVar.now().firebox,
            timeoutMs = 2000
        ).map(_ => ())

    /**
     * Serialize the async test bodies. The pre-fetch state (`remoteDataStatesVar`,
     * `engineStateVar`, `activeProjectIdVar`) is module-global, and utest runs the
     * suite's async tests concurrently; without serialization two cycles supersede
     * each other through the cycle guard and clobber the shared project vars. The
     * JS event loop makes the synchronous enqueue atomic, so this chain is safe.
     */
    private var chain                            : Future[Unit] = Future.successful(())
    private def serialized[A](body: => Future[A]): Future[A]    =
        val result = chain.flatMap(_ => body)
        chain = result.map(_ => ()).recover { case _ => () }
        result

    /** A scripted [[FireboxCalcClient]] recording every request and cancel. */
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

    /** Marker outputs so a test can prove the exact payload travelled through. */
    private val markerOutputs = WireEcolabeledOutputs("Y-eco-42", "Lr-42", "Ls-42", "Lt-42", "Zt-42", "St-42")

    private val markerConstraints = List(
        ConstraintVerdict(
            ConstraintTerm.GlassArea,
            "glass area",
            ConstraintDescriptor.Opaque,
            ConstraintOutcome.Satisfied
        ),
        ConstraintVerdict(
            ConstraintTerm.MB,
            "m_B",
            ConstraintDescriptor.Opaque,
            ConstraintOutcome.Violated("mb_too_big", "m_B exceeded the limit", blocking = true)
        )
    )

    private def successResponse(kind: WireFireboxKind, withEcoOutputs: Boolean = true): FireboxCalcResponse =
        FireboxCalcResponse(
            ApiVersion.v1,
            kind,
            "fr",
            WireValidated.Valid(WireFireboxContribution.Without),
            WireValidated.Valid(WireFireboxContribution.Without),
            markerConstraints,
            FireboxReportData  (
                WireTable(None, None, Nil),
                WireTable(None, None, Nil),
                Nil,
                Nil,
                if withEcoOutputs then Some(markerOutputs) else None
            )
        )

    private def isLive(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Boolean =
        states.get(kind).exists {
            case RemoteDataState.Live(_) => true
            case _                       => false
        }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val ecolabeledYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Ecolabeled:
          |    heat_output_reduced: null
          |    version:
          |      type: "either"
          |      left_or_right: "left"
          |      value: "Version 1"
          |    air_intake_shape: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_first_row_of_air_injectors:
          |      value: "0.1"
          |      unit: meter
          |    door_opening_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |    ash_pit_height:
          |      value: "0.05"
          |      unit: meter
          |    air_manifold_height:
          |      value: "0.05"
          |      unit: meter
          |    firebox_floor_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_inner_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_outer_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    air_column_thickness:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_sides:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_rear:
          |      value: "0.05"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R1:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R2:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R3:
          |      value: "0"
          |      unit: meter
          |    injector_height:
          |      value: "0"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin

    /** The MaxLoad Ecolabeled fixture with `maximum_load` removed — the degradation fixture (US18). */
    private val ecolabeledNoMaxLoadYaml =
        ecolabeledYaml.replace(
            """  maximum_load:
               |    value: "14.5"
               |    unit: kilogram
               |""".stripMargin,
            ""
        )

    private val door15aYaml =
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: door15a-test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Door15aFirebox_Catalog:
          |    reference: "Door15aFirebox_Catalog_Example"
          |    firebox_depth:
          |      value: "0.39"
          |      unit: "meter"
          |    firebox_width:
          |      value: "0.29"
          |      unit: "meter"
          |    firebox_height:
          |      value: "1.0"
          |      unit: "meter"
          |    load_size_nominal:
          |      value: "14.5"
          |      unit: "kilogram"
          |    sb:
          |      value: "1.6"
          |      unit: "centimeter"
          |    sb_min:
          |      value: "1.6"
          |      unit: "centimeter"
          |    sb_max:
          |      value: "4.0"
          |      unit: "centimeter"
          |    mb_min:
          |      value: "10.0"
          |      unit: "kilogram"
          |    mb_max:
          |      value: "25.0"
          |      unit: "kilogram"
          |    pressure_loss_table_raw: "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16"
          |    expectedAirIntakePipeShapes:
          |      - Circle:
          |          diameter:
          |            value: "0.2"
          |            unit: "meter"
          |    actualAirIntakePipeShape:
          |      Circle:
          |        diameter:
          |          value: "0.2"
          |          unit: "meter"
          |    co2_dry_nominal:
          |      value: "12.0"
          |      unit: "percent"
          |    emissions_values:
          |      firebox_name: "15a firebox"
          |      accredited_or_notified_body: "Test Laboratory"
          |      test_reports:
          |        - name: "PL-19075-1-P"
          |          date: "9.6.2020"
          |      emissions_values:
          |        co:
          |          polluant_name: "CO"
          |          valueO: 1154.0
          |          test_method: ""
          |          o2ref:
          |            value: "13.0"
          |            unit: "percent"
          |        dust:
          |          polluant_name: "Dust"
          |          valueO: 25.0
          |          test_method: ""
          |          o2ref:
          |            value: "13.0"
          |            unit: "percent"
          |        ogc:
          |          polluant_name: "OGC"
          |          valueO: 40.0
          |          test_method: ""
          |          o2ref:
          |            value: "13.0"
          |            unit: "percent"
          |        nox:
          |          polluant_name: "NOx"
          |          valueO: 119.0
          |          test_method: ""
          |          o2ref:
          |            value: "13.0"
          |            unit: "percent"
          |    glass_area:
          |      value: "0.05"
          |      unit: "meter^2"
          |    height_of_lowest_opening:
          |      value: "0.05"
          |      unit: "meter"
          |    heat_output_reduced: "NotDefined"
          |post_firebox_pipes: []""".stripMargin

    private def loadEcolabeled(projectId: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(ecolabeledYaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                                    )

    private def loadEcolabeledNoMaxLoad(projectId: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(ecolabeledNoMaxLoadYaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                                             )

    private def loadDoor15a(projectId: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(door15aYaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                                 )

    val tests = Tests {

        test("open — Ecolabeled: a successful cycle records Live and carries the response through") {
            serialized {
                installWindow     (              )
                setNavigatorOnline(true          )
                loadEcolabeled    ("e2e-eco-open")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                            states =>
                                states(FireboxKind.Ecolabeled) match
                                    case RemoteDataState.Live(data) =>
                                        assert(data.constraints == markerConstraints                   )
                                        assert(data.reportData.ecolabeledOutputs == Some(markerOutputs))
                                    case other                      => sys.error(s"expected Live, got $other")
                                // The derived signal projects the same payload for the active kind.
                                assert(readNow(RemoteFireboxPrefetch.prefetchedRemoteDataSig).isDefined)
                                assert(fake.calls == 1                                                 )
                        }
                    }
            }
        }

        test("open — Door15aCatalog: a successful cycle records Live and carries the response through") {
            serialized {
                installWindow     (               )
                setNavigatorOnline(true           )
                loadDoor15a       ("e2e-door-open")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Door15aCatalog))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Door15aCatalog))
                            .map { states =>
                                states(FireboxKind.Door15aCatalog) match
                                    case RemoteDataState.Live(data) => assert(data.constraints == markerConstraints)
                                    case other                      => sys.error(s"expected Live, got $other")
                                assert(fake.calls == 1)
                            }
                    }
            }
        }

        test("request shape — Door15aCatalog: kind is door15aCatalog and the air-intake shape is carried") {
            serialized {
                installWindow     (                )
                setNavigatorOnline(true            )
                loadDoor15a       ("e2e-door-shape")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Door15aCatalog))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Door15aCatalog))
                            .map { _ =>
                                val req = fake.requests.head
                                assert(req.apiVersion == ApiVersion.v1                  )
                                assert(req.standard == WireStandardVariant.Strict       )
                                assert(req.kind == WireFireboxKind.Door15aCatalog       )
                                assert(req.locale == "fr"                               )
                                assert(req.operatingEnvelope.isDefined                  )
                                assert(req.operatingEnvelope.get.altitude.value == "200")
                                // The Door15a DTO travels verbatim.
                                assert(req.firebox == engineStateVar.now().firebox      )
                                // The air-intake shape is read from the built strict application (here the
                                // project declares no air-intake pipe, so the shape is absent on the wire).
                                assert(req.airIntakePipeShape.isEmpty                   )
                                assert(fake.calls == 1                                  )
                                assert(fake.kindsCalls == 0                             )
                            }
                    }
            }
        }

        test("edit — Door15aCatalog: a DTO change is reflected in the next request and its fingerprint") {
            serialized {
                installWindow     (               )
                setNavigatorOnline(true           )
                loadDoor15a       ("e2e-door-edit")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Door15aCatalog))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Door15aCatalog))
                            .flatMap { _ =>
                                val req0 = fake.requests.last
                                firebox_var.update {
                                    case d: Firebox_V4.Door15aFirebox_Catalog => d.copy(firebox_depth = 0.500.meters)
                                    case other => other
                                }
                                settleHelper()
                                    .map { _ =>
                                        RemoteFireboxPrefetch.runCycle(bothRemote)
                                    }
                                    .flatMap { _ =>
                                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(s =>
                                            isLive(s, FireboxKind.Door15aCatalog) && fake.requests.size >= 2
                                        ).map { _ =>
                                            RemoteFireboxPrefetch.calcClientFactory = oldFactory
                                            val req1 = fake.requests.last
                                            req1.firebox match
                                                case d: Firebox_V4.Door15aFirebox_Catalog =>
                                                    assert(d.firebox_depth == 0.500.meters)
                                                case other => sys.error(s"expected Door15a, got $other")
                                            assert(req0.asJson.noSpaces != req1.asJson.noSpaces)
                                            assert(
                                                FireboxCalcCache.fingerprint(req0.asJson.noSpaces) !=
                                                    FireboxCalcCache.fingerprint(req1.asJson.noSpaces)
                                            )
                                        }
                                    }
                            }
                    }
            }
        }

        test("edit — a DTO change is reflected in the next request and its fingerprint") {
            serialized {
                installWindow     (              )
                setNavigatorOnline(true          )
                loadEcolabeled    ("e2e-eco-edit")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled))
                            .flatMap { _ =>
                                val req0 = fake.requests.last
                                // Edit one DTO field through the same var the form uses.
                                firebox_var.update {
                                    case e: Firebox_V4.Ecolabeled => e.copy(firebox_depth = 0.500.meters)
                                    case other => other
                                }
                                settleHelper()
                                    .map { _ =>
                                        RemoteFireboxPrefetch.runCycle(bothRemote)
                                    }
                                    .flatMap { _ =>
                                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(s =>
                                            isLive(s, FireboxKind.Ecolabeled) && fake.requests.size >= 2
                                        ).map { _ =>
                                            RemoteFireboxPrefetch.calcClientFactory = oldFactory
                                            val req1 = fake.requests.last
                                            // The captured DTO reflects the edit.
                                            req1.firebox match
                                                case e: Firebox_V4.Ecolabeled => assert(e.firebox_depth == 0.500.meters)
                                                case other => sys.error(s"expected Ecolabeled, got $other")
                                            // The two requests differ as wire JSON, and so do their fingerprints.
                                            assert(req0.asJson.noSpaces != req1.asJson.noSpaces)
                                            assert(
                                                FireboxCalcCache.fingerprint(req0.asJson.noSpaces) !=
                                                    FireboxCalcCache.fingerprint(req1.asJson.noSpaces)
                                            )
                                        }
                                    }
                            }
                    }
            }
        }

        test("request shape — the single normative POST /v1/calculate seam input") {
            serialized {
                installWindow     (               )
                setNavigatorOnline(true           )
                loadEcolabeled    ("e2e-eco-shape")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                            _ =>
                                val req = fake.requests.head
                                assert(req.apiVersion == ApiVersion.v1                  )
                                assert(req.standard == WireStandardVariant.Strict       )
                                assert(req.kind == WireFireboxKind.Ecolabeled           )
                                assert(req.locale == "fr"                               )
                                // Operating envelope carries the project altitude.
                                assert(req.operatingEnvelope.isDefined                  )
                                assert(req.operatingEnvelope.get.altitude.value == "200")
                                // The firebox DTO travels verbatim.
                                assert(req.firebox == engineStateVar.now().firebox      )
                                // PDF-initiation contract: exactly one calculate call, no discovery.
                                assert(fake.calls == 1                                  )
                                assert(fake.kindsCalls == 0                             )
                        }
                    }
            }
        }

        test("request shape — EN 15544 params: m_B and n_min are read off the built application") {
            serialized {
                installWindow     (                )
                setNavigatorOnline(true            )
                loadEcolabeled    ("e2e-eco-params")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                            _ =>
                                val req  = fake.requests.head
                                // The server builds its ConstraintContext from m_B and n_min; without
                                // them the m_B-dependent constraints come back NotEvaluated(MissingMB).
                                // Both are read off the built strict application: the MaxLoad fixture
                                // takes `stove_params.maximum_load` (14.5 kg) verbatim and `n_min` is
                                // `stove_params.min_efficiency` (70 %).
                                val mB   = req.params.m_B.getOrElse  (sys.error("m_B absent on the wire")  )
                                val nMin = req.params.n_min.getOrElse(sys.error("n_min absent on the wire"))
                                assert(mB.unit == "kilogram"      )
                                assert(mB.value.toDouble == 14.5  )
                                assert(nMin.unit == "percent"     )
                                assert(nMin.value.toDouble == 70.0)
                                assert(fake.calls == 1            )
                                // Wire bytes: the exact serialized params object — key names, value
                                // strings and wire units pinned so client and server cannot drift (US17).
                                val paramsWire =
                                    """"params":{"m_B":{"value":"14.5","unit":"kilogram"},"n_min":{"value":"70","unit":"percent"}}"""
                                assert(req.asJson.noSpaces.contains(paramsWire))
                        }
                    }
            }
        }

        test("degradation — a project without a maximum load sends m_B absent and stays Live") {
            serialized {
                installWindow          (                  )
                setNavigatorOnline     (true              )
                loadEcolabeledNoMaxLoad("e2e-eco-degraded")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                // The server's degradation contract: without m_B the request still succeeds (HTTP 200);
                // only the m_B-dependent constraints come back NotEvaluated(MissingMB), not an error.
                val missingMB  = ConstraintVerdict(
                    ConstraintTerm.MB,
                    "m_B",
                    ConstraintDescriptor.Opaque,
                    ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB)
                )
                val resp       = successResponse(WireFireboxKind.Ecolabeled, withEcoOutputs = false)
                    .copy(constraints = List(missingMB))
                val fake       = new FakeClient(_ => Future.successful(Right(resp)))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                            states =>
                                val req = fake.requests.head
                                // m_B is absent on the wire — the params object carries no m_B quantity.
                                assert(!req.asJson.noSpaces.contains("\"m_B\":{")       )
                                // n_min is required and still goes out, verbatim.
                                assert(
                                    req.asJson.noSpaces.contains("\"n_min\":{\"value\":\"70\",\"unit\":\"percent\"}")
                                )
                                assert(req.params.m_B.isEmpty                           )
                                assert(req.params.n_min.exists(_.value.toDouble == 70.0))
                                // The request succeeded — exactly one call — and the MissingMB
                                // verdict came back through the Live state verbatim.
                                assert(fake.calls == 1                                  )
                                states(FireboxKind.Ecolabeled) match
                                    case RemoteDataState.Live(data) =>
                                        assert(data.constraints == List(missingMB))
                                    case other                      => sys.error(s"expected Live, got $other")
                        }
                    }
            }
        }

        test("constraints and outputs — the Live state carries the verdicts and ecolabeled outputs verbatim") {
            serialized {
                installWindow     (                  )
                setNavigatorOnline(true              )
                loadEcolabeled    ("e2e-eco-verdicts")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val fake       = new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                val oldFactory = RemoteFireboxPrefetch.calcClientFactory
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                        RemoteFireboxPrefetch.calcClientFactory = oldFactory
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled)).map {
                            states =>
                                states(FireboxKind.Ecolabeled) match
                                    case RemoteDataState.Live(data) =>
                                        val satisfied = data.constraints.collect {
                                            case c if c.outcome == ConstraintOutcome.Satisfied => c
                                        }
                                        val violated  = data.constraints.collect {
                                            case c @ ConstraintVerdict(_, _, _, ConstraintOutcome.Violated(_, _, _)) =>
                                                c
                                        }
                                        assert(satisfied.size == 1)
                                        assert(violated.size == 1                                      )
                                        assert(violated.head.term == ConstraintTerm.MB                 )
                                        assert(data.reportData.ecolabeledOutputs == Some(markerOutputs))
                                    case other                      => sys.error(s"expected Live, got $other")
                        }
                    }
            }
        }

        test("save/reload — a project reload still matches the cache fingerprint (offline serves Cached)") {
            serialized {
                val store = installWindow()
                setNavigatorOnline(true            )
                loadEcolabeled    ("e2e-eco-reload")
                given Locale = Locales.fr
                given Owner  = new Owner {}

                val oldFactory  = RemoteFireboxPrefetch.calcClientFactory
                val successFake =
                    new FakeClient(_ => Future.successful(Right(successResponse(WireFireboxKind.Ecolabeled))))
                RemoteFireboxPrefetch.calcClientFactory =
                    _ => RemoteFireboxPrefetch.PrefetchClient(successFake, () => successFake.cancels += 1)
                settleHelper()
                    .map { _ =>
                        RemoteFireboxPrefetch.runCycle(bothRemote)
                    }
                    .flatMap { _ =>
                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(isLive(_, FireboxKind.Ecolabeled))
                            .flatMap { _ =>
                                // A cache entry now exists for this project + kind.
                                val key = FireboxCalcCache.key("e2e-eco-reload", WireFireboxKind.Ecolabeled)
                                assert(store.contains(key))
                                // Reload the project the way "open a saved .fcalc file" does: decode the
                                // source YAML fresh and install it. (NOTE: `encodeToYamlTry` → `decodeAndMigrateTry`
                                // does NOT round-trip — the V7 encoder emits `post_firebox_pipes.slots: null`
                                // which the V7 decoder rejects with "expecting array". That is a pre-existing
                                // production encode/decode asymmetry outside this test's scope; re-decoding the
                                // source exercises the identical decode path a real file load uses, and proves the
                                // fingerprint is stable across a genuine project reload.)
                                val reloaded = FireCalcYAMLMigrations.decodeAndMigrateTry(ecolabeledYaml).get
                                engineStateVar.set(reloaded)
                                // Offline: the network path fails, so the only way to serve is the cache.
                                setNavigatorOnline(false   )
                                val offlineFake = new FakeClient(_ =>
                                    Future.successful(
                                        Left(CalculationFailure.TransportError(new RuntimeException("offline")))
                                    )
                                )
                                RemoteFireboxPrefetch.calcClientFactory = _ =>
                                    RemoteFireboxPrefetch.PrefetchClient(offlineFake, () => offlineFake.cancels += 1)
                                settleHelper()
                                    .map { _ =>
                                        RemoteFireboxPrefetch.runCycle(bothRemote)
                                    }
                                    .flatMap { _ =>
                                        awaitState(RemoteFireboxPrefetch.remoteDataStatesSig)(
                                            _.get(FireboxKind.Ecolabeled).exists {
                                                case RemoteDataState.Cached(_, _) => true
                                                case _                            => false
                                            }
                                        ).map { states =>
                                            RemoteFireboxPrefetch.calcClientFactory = oldFactory
                                            setNavigatorOnline(true                                                                )
                                            states(FireboxKind.Ecolabeled) match
                                                case RemoteDataState.Cached(savedAtMs, data) =>
                                                    // Same payload as the live response — the fingerprint matched across the round-trip.
                                                    assert(data.constraints == markerConstraints                   )
                                                    assert(data.reportData.ecolabeledOutputs == Some(markerOutputs))
                                                    assert(savedAtMs > 0                                           )
                                                case other                                   => sys.error(s"expected Cached, got $other")
                                            // The derived cached-at signal surfaces the timestamp.
                                            assert            (readNow(RemoteFireboxPrefetch.prefetchedRemoteCachedAtSig).isDefined)
                                            // The offline short-circuit never reached the client.
                                            assert            (offlineFake.calls == 0                                              )
                                        }
                                    }
                            }
                    }
            }
        }
    }
