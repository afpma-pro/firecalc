/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.remote

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.engine.alg.en15544.{FireboxEngineSource, FireboxEngineSourceConfig, FireboxKind}
import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.{engineStateHelperVar, engineStateVar}
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
import afpma.firecalc.ui.utils.{FireboxCalcCache, FireboxCalcCacheEntry, FireboxKindLabel, RemoteDataState}

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.ownership.Owner
import io.circe.parser.decode
import io.taig.babel.Locale

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

/**
 * The harness the remote-prefetch acceptance specs share: browser stubs for the
 * bare Node test env, signal polling, the serialization chain, the scripted
 * [[FireboxCalcClient]] with the helper that drives the production
 * [[RemoteFireboxPrefetch.runCycle]] seam, plus the canonical Ecolabeled
 * fixtures and the observable-state predicates.
 *
 * One [[FakeClient]] and one set of poll/settle helpers: a change to how a cycle
 * is driven — or to what a scripted client records — lands here once and every
 * spec sees it. Payloads that genuinely diverge stay in their spec:
 * [[RemotePrefetchEndToEndSpec]] keeps its own block-style documents (its
 * no-max-load fixture is derived by string-replacing the block form), its own
 * verdict list and its own marker outputs.
 *
 * [[serialized]]'s chain is package-wide on purpose: the pre-fetch state it
 * protects (`remoteDataStatesVar`, `engineStateVar`, `activeProjectIdVar`) is
 * module-global, so every spec on this seam queues through the same chain.
 */
private[remote] object RemotePrefetchHarness:

    given ExecutionContext = ExecutionContext.global

    // ── Browser stubs for the bare Node test env ──────────────────────────────

    /** Install a `window` + `localStorage` stub for the bare Node test env. */
    def installWindow(): scala.collection.mutable.Map[String, String] =
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

    def setNavigatorOnline(online: Boolean): Unit =
        org.scalajs.dom.window.asInstanceOf[js.Dynamic].navigator = js.Dynamic.literal(onLine = online)

    // ── Async plumbing ────────────────────────────────────────────────────────

    def sleep(ms: Double): Future[Unit] =
        val p = Promise[Unit]()
        js.timers.setTimeout(ms)(p.success(()))
        p.future

    /** Poll a signal until `p` holds (signals are read via a live subscription). */
    def awaitState[A](sig: Signal[A])(p: A => Boolean, timeoutMs: Double = 5000)(using
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
    def readNow[A](sig: Signal[A])(using Owner): A =
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
    def settleHelper()(using Owner): Future[Unit] =
        awaitState(engineStateHelperVar.signal)(
            _.fcProj.firebox == engineStateVar.now().firebox,
            timeoutMs = 2000
        ).map(_ => ())

    /**
     * Serialize the async test bodies. The pre-fetch state (`remoteDataStatesVar`,
     * `engineStateVar`, `activeProjectIdVar`) is module-global, so every async body
     * in every spec queues through this one chain: two cycles can never supersede
     * each other through the cycle guard, nor clobber the shared project vars,
     * whatever order the runner picks. The JS event loop makes the synchronous
     * enqueue atomic, so this chain is safe.
     */
    private var chain                    : Future[Unit] = Future.successful(())
    def serialized[A](body: => Future[A]): Future[A]    =
        val result = chain.flatMap(_ => body)
        chain = result.map(_ => ()).recover { case _ => () }
        result

    // ── Driving the production seam ───────────────────────────────────────────

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

    /**
     * A client whose `calculate` always fails with a transport error. Tests that name
     * their own failure class (a timeout, a refused connection) keep their own message.
     */
    def failingClient: FakeClient =
        new FakeClient(_ => Future.successful(Left(CalculationFailure.TransportError(new RuntimeException("down")))))

    val bothRemote = FireboxEngineSourceConfig(
        Map    (
            FireboxKind.Ecolabeled     -> FireboxEngineSource.Remote("http://test.local/v1"),
            FireboxKind.Door15aCatalog -> FireboxEngineSource.Remote("http://test.local/v1")
        )
    )

    /**
     * Swap in `fake`, settle the derived engine state, run one cycle, restore the
     * previous factory. Restoring as soon as `runCycle` returns is enough: the
     * cycle captures its client synchronously.
     */
    def runCycleWith(fake: FakeClient)(using Locale, Owner): Future[Unit] =
        val old = RemoteFireboxPrefetch.calcClientFactory
        RemoteFireboxPrefetch.calcClientFactory = _ =>
            RemoteFireboxPrefetch.PrefetchClient(fake, () => fake.cancels += 1)
        settleHelper().map { _ =>
            RemoteFireboxPrefetch.runCycle(bothRemote)
            RemoteFireboxPrefetch.calcClientFactory = old
        }

    // ── Observable state ──────────────────────────────────────────────────────

    def isLive(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)    : Boolean =
        states.get(kind).exists { case RemoteDataState.Live(_) => true; case _ => false }
    def isFailed(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)  : Boolean =
        states.get(kind).exists { case RemoteDataState.Failed(_) => true; case _ => false }
    def isCached(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind)  : Boolean =
        states.get(kind).exists { case RemoteDataState.Cached(_, _) => true; case _ => false }
    def isRejected(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Boolean =
        states.get(kind).exists { case RemoteDataState.Rejected(_) => true; case _ => false }

    /** Decode the raw cache entry stored for `(projectId, kind)`. */
    def readEntry(
        store    : scala.collection.mutable.Map[String, String],
        projectId: String,
        kind     : WireFireboxKind
    ): FireboxCalcCacheEntry =
        decode[FireboxCalcCacheEntry](store(FireboxCalcCache.key(projectId, kind))).toOption.get

    /** The `savedAtMs` carried by the kind's `Cached` state. */
    def cachedSavedAt(states: Map[FireboxKind, RemoteDataState[?]], kind: FireboxKind): Double =
        states.get(kind).collect { case RemoteDataState.Cached(savedAtMs, _) => savedAtMs }.get

    // ── Canonical Ecolabeled payloads ─────────────────────────────────────────

    /** Marker outputs the specs assert on, so a payload can be traced end to end. */
    val markerOutputs = WireEcolabeledOutputs("Y-ok", "Lr", "Ls", "Lt", "Zt", "St")

    /** The canonical success payload: one `GlassArea`/`Satisfied` verdict, [[markerOutputs]]. */
    def successResponse(kind: WireFireboxKind): FireboxCalcResponse =
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
    val preCheckItems = List(
        WireErrorItem("air_manifold_height", "La hauteur du manifold d'air (W) devrait être ≥ 5.0 cm"),
        WireErrorItem("glass_area", "L'aire de vitrage est trop petite pour la charge maximale"      )
    )

    // A 400 malformed_request payload mirroring the normative OpenAPI example:
    // a wire→typed decode verdict names the offending wire field, not an engine
    // pre-check result.
    val decodeFailureItems = List(
        WireErrorItem("/kind", "Unknown WireFireboxKind: xyz")
    )

    /** A scripted protocol-error response for the [[FakeClient]]. */
    def protocolFailure(
        status : Int,
        error  : String,
        items  : List[WireErrorItem],
        message: String = "Engine computation failed"
    ): Either[CalculationFailure, FireboxCalcResponse] =
        Left(CalculationFailure.ProtocolError(status, error, message, Some(items)))

    /** The expected generic banner, composed through the same i18n accessor production renders. */
    def kindUnavailableBanner(using Locale): String =
        I18N_UI.remote_calc.kind_unavailable(FireboxKindLabel(FireboxKind.Ecolabeled))

    /** The expected pre-check banner for `items`, composed through the same accessor. */
    def preCheckBanner(items: List[WireErrorItem])(using Locale): String =
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
    def expectGenericBanner(
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

    // ── Fixtures ──────────────────────────────────────────────────────────────

    val ecolabeledYaml =
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

    val door15aYaml =
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

    /** The same Ecolabeled document in the other shipped language. */
    val ecolabeledYamlEn = ecolabeledYaml.replace("locale: fr", "locale: en")

    /** Load `yaml` as the active project's engine state. */
    def loadProject(projectId: String, yaml: String): Unit =
        engineStateVar.set    (FireCalcYAMLMigrations.decodeAndMigrateTry(yaml).get)
        activeProjectIdVar.set(Some(ProjectId(projectId))                          )

    /** Load the canonical Ecolabeled document — or `yaml`, e.g. [[ecolabeledYamlEn]]. */
    def loadEcolabeled(projectId: String, yaml: String = ecolabeledYaml): Unit =
        loadProject(projectId, yaml)

    /** Load the condensed Door15a catalog document. */
    def loadDoor15a(projectId: String): Unit =
        loadProject(projectId, door15aYaml)
