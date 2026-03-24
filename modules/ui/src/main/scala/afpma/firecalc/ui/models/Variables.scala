/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.FireCalc_Version.<

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipesResult_15544
import afpma.firecalc.engine.models.en13384.typedefs.P_L
import afpma.firecalc.engine.models.en15544.std.Outputs
import afpma.firecalc.engine.models.en15544.typedefs.EstimatedOutputTemperatures
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import afpma.firecalc.engine.models.en15544.typedefs.η
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top_min
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.models.schema.AppStateSchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys
import afpma.firecalc.ui.utils.*

import cats.data.Validated
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxTuple2Semigroupal

import com.raquo.airstream.core.Signal
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.WebStorageVar

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.ops.standard.all.given

import scala.util.*

// ============================================================================
// UNIFIED APPLICATION STATE SCHEMA
// ============================================================================

/**
 * Unified application state schema stored in localStorage as a single atomic unit.
 * Contains: engine_state (sent to backend), sensitive_data (client-only), billing_data (client-only)
 */
lazy val appStateSchemaWebStorageVar: WebStorageVar[AppStateSchema] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.APP_STATE_SCHEMA, syncOwner = None)
        .withCodec          (
            encode           = AppStateSchemaHelper.encodeToYaml(_).toOption.getOrElse(""),
            decode           = (raw: String) => {
                // NEW: Attempt migration before decode
                AppStateSchemaMigrations.migrateToLatest(raw) match {
                    case Some(schema) =>
                        // Migration successful
                        Success(schema)

                    case None =>
                        // Migration failed - clear storage and use defaults
                        AppStateSchemaMigrations.clearInvalidData(                                          )
                        Success                                  (AppStateSchemaHelper.createInitialSchema())
                }
            },
            default          = Success(AppStateSchemaHelper.createInitialSchema()),
            syncDistinctByFn = _ == _
        )

lazy val appStateSchemaVar =
    Var[AppStateSchema](appStateSchemaWebStorageVar.now())

// ============================================================================
// ZOOMED VARS FROM UNIFIED SCHEMA
// ============================================================================

import cats.data.Validated.Invalid
import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.engine.models.LocalRegulations

// Engine state (sent to backend for PDF generation)
lazy val engineStateVar = appStateSchemaVar.zoomLazy(_.engine_state)((schema, engine) =>
    // Migrate engine state to latest version if needed
    val migratedEngine =
        if engine.version < FireCalcYAML.LATEST_VERSION then
            FireCalcYAMLMigrations.upgradeToCurrent(engine).getOrElse(engine)
        else engine
    schema.copy(engine_state = migratedEngine)
)

// Sensitive data (NEVER sent to backend) - direct zoom (already ClientProjectData_V1)
val clientProjectDataVar =
    appStateSchemaVar.zoomLazy(_.sensitive_data)((schema, data) => schema.copy(sensitive_data = data))

// Billing data (for payments, NEVER sent to backend) - direct zoom, no transformation needed
val billingInfoVar =
    appStateSchemaVar.zoomLazy(_.billing_data)((schema, billing) => schema.copy(billing_data = billing))

val localeVar       = engineStateVar.zoomLazy(_.locale)((g, x) => g.copy(locale = x))
val displayUnitsVar =
    engineStateVar.zoomLazy(_.display_units)((g, x) => g.copy(display_units = x))

val project_descr_var = engineStateVar.zoomLazy(_.project_description)((ast, x) => ast.copy(project_description = x))

// filename derived var, never updates parent projet_descr_var
// depends on both project description and locale for i18n default name

val filename_var = project_descr_var.zoomLazy(prj =>
    given Locale    = localeVar.now()
    val fname_noext = Option
        .when(prj.reference.nonEmpty)(prj.reference)
        .getOrElse(I18N_UI.default_names.project)
        .map(c => if c.isLetterOrDigit then c else '_')
        .replaceAll("_+", "_")
        .stripPrefix("_")
        .stripSuffix("_")
    s"${fname_noext}${FIRECALC_FILE_EXTENSION}"
)((p, _) => p)

// local conditions

val local_conditions_var = engineStateVar.zoomLazy(_.local_conditions)((ast, x) => ast.copy(local_conditions = x))

val z_geodetical_height_var = local_conditions_var.zoomLazy(_.altitude): (lc, z) =>
    lc.copy(altitude = z)

val chimney_termination_var =
    local_conditions_var.zoomLazy(_.chimney_termination): (lc, x) =>
        lc.copy(chimney_termination = x)

val chimney_location_on_roof_var =
    chimney_termination_var.zoomLazy(_.chimney_location_on_roof): (cos, x) =>
        cos.copy(chimney_location_on_roof = x)

val adjacent_buildings_var =
    chimney_termination_var.zoomLazy(_.adjacent_buildings): (cos, x) =>
        cos.copy(adjacent_buildings = x)

// stove params
val stove_params_var =
    engineStateVar.zoomLazy(_.stove_params)((ast, x) => ast.copy(stove_params = x))

val air_intake_incrdescr_var =
    engineStateVar.zoomLazy(_.air_intake_descr)((g, x) => g.copy(air_intake_descr = x))

// EngineStateHelper

val engineStateHelperVar =
    engineStateVar.zoomLazy(FireCalcYAML_Loader.apply)((engineState, _) => engineState)

val air_intake_vnel_signal          = engineStateHelperVar.signal.map(_.airIntakePipe)
val air_intake_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.airIntakePipeMappings)

// Firebox

val firebox_var =
    engineStateVar.zoomLazy(_.firebox)((ast, x) => ast.copy(firebox = x))

// FluePipe

val fluepipe_incrdescr_var = engineStateVar.zoomLazy(_.flue_pipe_descr): (g, x) =>
    g.copy(post_firebox_pipes = g.post_firebox_pipes.map {
        case PostFireboxPipeDescrSlot.FlueSlot(_) => PostFireboxPipeDescrSlot.FlueSlot(x)
        case other                                => other
    })

val fluepipe_vnel_signal = engineStateHelperVar.signal.map(_.fluePipe)

val fluepipe_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.fluePipeMappings)

// Connecting Pipe

val connector_pipe_incrdescr_var =
    engineStateVar.zoomLazy(_.connector_pipe_descr): (g, x) =>
        g.copy(post_firebox_pipes = g.post_firebox_pipes.map {
            case PostFireboxPipeDescrSlot.ConnectorSlot(_) => PostFireboxPipeDescrSlot.ConnectorSlot(x)
            case other                                     => other
        })

val connector_pipe_vnel_signal          = engineStateHelperVar.signal.map(_.connectorPipe)
val connector_pipe_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.connectorPipeMappings)

// Chimney Pipe

val chimney_pipe_incrdescr_var =
    engineStateVar.zoomLazy(_.chimney_pipe_descr): (g, x) =>
        g.copy(post_firebox_pipes = g.post_firebox_pipes.map {
            case PostFireboxPipeDescrSlot.ChimneySlot(_) => PostFireboxPipeDescrSlot.ChimneySlot(x)
            case other                                    => other
        })

val chimney_pipe_vnel_signal          = engineStateHelperVar.signal.map(_.chimneyPipe)
val chimney_pipe_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.chimneyPipeMappings)

// Final frames: flue pipe's final frame seeds the connector, connector's seeds the chimney.
// These are derived from the incremental descriptions directly (no engine run needed).

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.FluePipe_Module_15544
import afpma.firecalc.engine.models.ConnectorPipe_Module

lazy val fluepipe_finalFrame_sig: Signal[Option[PipeFrame]] =
    fluepipe_incrdescr_var.signal.map: descr =>
        val (_, finalFrameV) = FluePipe_Module_15544.mkPipeFromIncrDescrWithFinalFrame(descr)
        finalFrameV.toOption.flatten
    .distinct

lazy val connectorpipe_finalFrame_sig: Signal[Option[PipeFrame]] =
    connector_pipe_incrdescr_var.signal.combineWith(fluepipe_finalFrame_sig).map: (descr, flueFinalFrame) =>
        val (_, finalFrameV) = ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(descr, flueFinalFrame)
        finalFrameV.toOption.flatten
    .distinct

// Position tracking: cumulative XYZ coordinates for each pipe's physical segments.
// Chained: connector starts at flue's finalPoint, chimney starts at connector's finalPoint.
// Air intake and flue pipe both start at origin (firebox outlet not modeled spatially).

import afpma.firecalc.engine.models.geometry.{PositionTracker, PipePositionResult}
import afpma.firecalc.engine.models.geometry.Vec3

lazy val fluepipe_positions_sig: Signal[PipePositionResult] =
    fluepipe_incrdescr_var.signal
        .combineWith(firebox_var.signal)
        .map: (descr, firebox) =>
            val fbHeightM = firebox.firebox_height.value
            PositionTracker.computeFlowOnly15544(descr, externalFrame = None, startPoint = Vec3(0, 0, fbHeightM + 1.0))
        .distinct

lazy val connectorpipe_positions_sig: Signal[PipePositionResult] =
    connector_pipe_incrdescr_var.signal
        .combineWith(fluepipe_finalFrame_sig, fluepipe_positions_sig)
        .map: (descr, flueFinalFrame, fluePositions) =>
            PositionTracker.computeThermal13384(descr, flueFinalFrame, fluePositions.finalPoint)
        .distinct

lazy val chimneypipe_positions_sig: Signal[PipePositionResult] =
    chimney_pipe_incrdescr_var.signal
        .combineWith(connectorpipe_finalFrame_sig, connectorpipe_positions_sig)
        .map: (descr, connFinalFrame, connPositions) =>
            PositionTracker.computeThermal13384(descr, connFinalFrame, connPositions.finalPoint)
        .distinct

lazy val airintake_positions_sig: Signal[PipePositionResult] =
    air_intake_incrdescr_var.signal.map: descr =>
        PositionTracker.computeFlowOnly13384(
            descr,
            externalFrame = None,
            startPoint = Vec3(0, 0, 0),
            finalPoint = Some(Vec3(0, 0, -1.0))
        )
    .distinct

// ── Post-firebox generic topology ─────────────────────────────────
// Slot-indexed reactive state for dynamic N-pipe UI.

import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.engine.models.SlotBuildResult
import afpma.firecalc.engine.ops.generic.{PostFireboxPipeChain, TopologyError}

// ── Primary Var: the post-firebox pipe slots ─────────────────────

/** Writable Var for the post-firebox pipe slot vector.
  * Mutations here (add/remove/reorder/edit) propagate through engineStateVar
  * and trigger re-computation of all derived signals.
  */
lazy val postFireboxSlots_var: Var[Seq[PostFireboxPipeDescrSlot]] =
    engineStateVar.zoomLazy(_.post_firebox_pipes): (g, x) =>
        g.copy(post_firebox_pipes = x)

// ── Slot-indexed build results ───────────────────────────────────

/** Generic slot-indexed build results from PipeChainGeneric.
  * Each SlotBuildResult carries type-erased pipe, IdsMapping (Int → Option[Int]),
  * and final PipeFrame — indexed by slot position.
  */
lazy val slotBuildResults_sig: Signal[Vector[SlotBuildResult]] =
    engineStateHelperVar.signal.map(_.slotBuildResults)

/** Topology validation: permissive — errors are exposed as TopologyError values. */
lazy val topologyValidation_sig: Signal[Validated[cats.data.NonEmptyList[TopologyError], PostFireboxPipeChain]] =
    engineStateHelperVar.signal.map(_.topologyValidation)

// ── Slot-indexed frame chain ─────────────────────────────────────

/** Final PipeFrame per slot, extracted from slotBuildResults.
  * slotFinalFrames(i) is the frame after all elements in slot i,
  * and serves as the initial frame for slot i+1.
  */
lazy val slotFinalFrames_sig: Signal[Vector[Option[PipeFrame]]] =
    slotBuildResults_sig.map(_.map(_.finalFrame))

/** The initial frame for slot at index `idx`: None for slot 0,
  * otherwise the final frame of the previous slot.
  */
def slotInitialFrameSig(idx: Int): Signal[Option[PipeFrame]] =
    if idx <= 0 then Signal.fromValue(None)
    else slotFinalFrames_sig.map(frames => frames.lift(idx - 1).flatten)

// ── Slot-indexed position tracking ───────────────────────────────

lazy val slotPositions_sig: Signal[Vector[PipePositionResult]] =
    postFireboxSlots_var.signal
        .combineWith(slotFinalFrames_sig, firebox_var.signal)
        .map: (slots, frames, firebox) =>
            val fbHeightM = firebox.firebox_height.value
            slots.zipWithIndex.foldLeft((Vector.empty[PipePositionResult], Vec3(0, 0, fbHeightM + 1.0))):
                case ((results, startPoint), (slot, idx)) =>
                    val prevFrame = if idx == 0 then None else frames.lift(idx - 1).flatten
                    val pos = slot match
                        case PostFireboxPipeDescrSlot.FlueSlot(descr) =>
                            PositionTracker.computeFlowOnly15544(descr, externalFrame = None, startPoint = startPoint)
                        case PostFireboxPipeDescrSlot.ThermalFlueSlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                        case PostFireboxPipeDescrSlot.ConnectorSlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                        case PostFireboxPipeDescrSlot.ChimneySlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                    (results :+ pos, pos.finalPoint)
            ._1
        .distinct

// ── Per-slot accessor helpers ────────────────────────────────────

/** Per-slot pipe result (type-erased). Returns Invalid if slot index out of bounds. */
def slotPipeResultSig(idx: Int): Signal[ValidatedNel[IncrementalValidation_Error, Any]] =
    slotBuildResults_sig.map: results =>
        results.lift(idx).map(_.pipe).getOrElse(
            Validated.invalidNel(FluePipeNotDefinedYet) // fallback — slot doesn't exist
        )

/** Per-slot IdsMapping function (Int → Option[Int]). Returns Invalid if slot index out of bounds. */
def slotMappingFnSig(idx: Int): Signal[ValidatedNel[IncrementalValidation_Error, Int => Option[Int]]] =
    slotBuildResults_sig.map: results =>
        results.lift(idx).map(_.idsMappingFn).getOrElse(
            Validated.invalidNel(FluePipeNotDefinedYet) // fallback — slot doesn't exist
        )

// ── Backward-compat signals ──────────────────────────────────────
// These derive from the slot vector but expose the same types as before.
// Consumed by: FluePipePanel, ConnectorPipePanel, ChimneyPipePanel,
// GraphPanel, Viz3DPanel. Will be removed when those are migrated.

/** The descriptor slots as a signal (backward compat). */
lazy val postFireboxDescrSlots_sig: Signal[Vector[PostFireboxPipeDescrSlot]] =
    postFireboxSlots_var.signal.map(_.toVector).distinct

/** All post-firebox pipe results as a vector (backward compat). */
lazy val postFireboxPipeResults_sig: Signal[VNelMcalcErr[Vector[PipeResult]]] =
    results_en15544_strict_sig.map: vnelAppl =>
        vnelAppl.andThen(_.primary.postFireboxPipeResults)

// Results for EN15544 Strict

lazy val results_en15544_strict_sig: Signal[ValidatedNel[MCalc_Error, EN15544_Strict_Application]] =
    engineStateHelperVar.signal
        // emits at most once during interval (prevent too much computing)
        // .composeChanges(_.throttle(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .composeChanges(_.debounce(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .map: helper =>
            scala.util.Try(helper.make_en15544_Strict_Application) match
                case scala.util.Success(result) => result
                case scala.util.Failure(e)      => Validated.invalidNel(UnexpectedDevError(e.getMessage))

lazy val en15544_strict_validate_results_except_emissions: Signal[Boolean] =
    results_en15544_strict_sig
        .combineWith(project_descr_var.signal)
        .map((vnelAppl, prj) =>
            vnelAppl
                .map(
                    _.validateResultsExceptEmissionsValues(prj.country).fold(nel => false, _ => true)
                )
                .getOrElse(false)
        )

lazy val en15544_strict_local_regulations_and_check_results
    : Signal[(Option[LocalRegulations], LocalRegulations.ParamCheckResults)] =
    results_en15544_strict_sig
        .combineWith(project_descr_var.signal)
        .map((vnelAppl, prj) =>
            vnelAppl
                .map { appl =>
                    val lreg  = LocalRegulations.findBy(prj.country, appl.inputs.design.firebox.type_of_appliance)
                    val pcres = appl.check_emissions_and_efficiency_values_with_local_regulations(lreg)
                    (Some(lreg), pcres)
                }
                .getOrElse((None, LocalRegulations.ParamCheckResults.empty))
        )

lazy val en15544_strict_check_emissions_and_efficiency_values_with_local_regulations
    : Signal[LocalRegulations.ParamCheckResults] =
    en15544_strict_local_regulations_and_check_results.map(_._2)

lazy val results_en13384_sig: Signal[VNelMcalcErr[EN13384_1_A1_2019_Common_Application]] =
    results_en15544_strict_sig.signal.map: strict_15544 =>
        strict_15544.map(_.en13384_application)

lazy val results_en15544_pressure_requirements: Signal[VNelMcalcErr[PressureRequirement]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.pressureRequirement_EN15544
    )

lazy val results_en15544_outputs: Signal[VNelMcalcErr[Outputs]] =
    results_en15544_strict_sig.mapVNelE(strict =>
        strict.primary.outputs
    )

lazy val results_en15544_air_intake_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.airIntake))

lazy val results_en15544_combustion_air_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.combustionAir))

lazy val results_en15544_firebox_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.firebox))

lazy val results_en15544_channel_pipe  : Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.flue))
lazy val results_en15544_connector_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.connector))
lazy val results_en15544_chimney_pipe  : Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.chimney))

lazy val results_en15544_estimated_output_temperatures: Signal[VNelMcalcErr[EstimatedOutputTemperatures]] =
    results_en15544_strict_sig.mapVNelE(strict =>
        strict.primary.estimated_output_temperatures
    )

lazy val chimney_wall_temp_above_condensation_temp_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNelE(
        strict =>
            strict.primary
                .validateChimneyWallTempIsAboveCondensationTemp()
                .map(_ => true)
        ,
        default = false
    )

lazy val results_en15544_t_chimney_wall_top: Signal[VNelMcalcErr[t_chimney_wall_top]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.t_chimney_wall_top
    )

lazy val results_en15544_t_chimney_wall_top_min: Signal[VNelMcalcErr[t_chimney_wall_top_min]] =
    results_en15544_strict_sig.mapVNelE(_.formulas.t_chimney_wall_top_min)

lazy val results_en15544_efficiency: Signal[VNelMcalcErr[η]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.η
    )

lazy val eff_and_min_eff: Signal[(VNelMcalcErr[Percentage], VNelMcalcErr[Option[Percentage]])] =
    results_en15544_efficiency.combineWith(
        results_en15544_emissions_and_efficiency_values
            .map(_.andThen(_.min_efficiency_full_stove_nominal))
    )

lazy val effInRange_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNelE(
        strict =>
            strict.primary
                .validateEfficiencyIsAboveMinEfficiency()
                .map(_ => true)
        ,
        false
    )

lazy val results_en15544_emissions_and_efficiency_values: Signal[VNelMcalcErr[EmissionsAndEfficiencyValues]] =
    results_en15544_strict_sig.mapVNelE(_.emissions_and_efficiency_values)

extension (d: Double)
    private def filterNaN: Option[Double] = Option.when(!d.isNaN)(d)

extension (od: Option[Double])
    private def filterNaN: Option[Double] = od.filter(!_.isNaN)

def makeQuadrionSubtotalForSingle(
    outputsSig: Signal[VNelMcalcErr[Outputs]]
)(
    toPipeResult: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Valid(outputs) =>
            outputs.pipesResult_15544.map(toPipeResult) match
                case Validated.Valid(pres) =>
                    Some(
                        QuadrionSubtotal   (
                            ph    = pres.ph.value.filterNaN,
                            pr    = (-1.0 * pres.pR.value).filterNaN,
                            pu    = pres.pu.map(pu => (-1.0 * pu).value).toOption.filterNaN,
                            sigma = pres.`ph-(pR+pu)`.map(_.value).toOption.filterNaN
                        )
                    )
                case _                     => None
        case _                        => None

def makeQuadrionSubtotalForFirebox(
    outputsSig: Signal[VNelMcalcErr[Outputs]]
)(
    to_cc_intlair_pres: PipesResult_15544 => PipeResult,
    to_cc_firebox_pres: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Invalid(_)     =>
            None
        case Validated.Valid(outputs) =>
            val cc_intlair_pres =
                outputs.pipesResult_15544.map(to_cc_intlair_pres)
            val cc_firebox_pres =
                outputs.pipesResult_15544.map(to_cc_firebox_pres)
            (cc_intlair_pres, cc_firebox_pres) match
                case (Valid(cc_intlair_pres), Valid(cc_firebox_pres)) =>
                    Some(
                        QuadrionSubtotal   (
                            ph    = (cc_intlair_pres.ph + cc_firebox_pres.ph).value.filterNaN,
                            pr    = (-1.0 * (cc_intlair_pres.pR + cc_firebox_pres.pR).value).filterNaN,
                            pu    = (cc_intlair_pres.pu, cc_firebox_pres.pu)
                                .mapN((l, r) => -1.0 * (l + r).value)
                                .toOption
                                .filterNaN,
                            sigma = (
                                cc_intlair_pres.`ph-(pR+pu)`,
                                cc_firebox_pres.`ph-(pR+pu)`
                            ).mapN((l, r) => (l + r).value).toOption.filterNaN
                        )
                    )
                case (l, r                                          ) =>
                    None

// lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

lazy val errorBusConsole = new EventBus[(String, VNelString[?])]

// expert mode

val expertModeVar = Var[Boolean](false)
val expertModeOn  = expertModeVar.signal
val expertModeOff = expertModeOn.map(!_)

// 3D visualization panel
val viz3DPanelVar  = Var[Boolean](false)
val viz3DPanelOn   = viz3DPanelVar.signal
val viz3DPanelOff  = viz3DPanelOn.map(!_)

// Graph (2D chart) panel
val graphPanelVar = Var[Boolean](false)
val graphPanelOn  = graphPanelVar.signal
val graphPanelOff = graphPanelOn.map(!_)

// Undo / Redo
lazy val undoManager = UndoManager(maxDepth = 1000)

def performUndo(): Unit =
    undoManager.undo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }

def performRedo(): Unit =
    undoManager.redo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }

// pour récupérer les erreurs de type AngleN2 missing etc...
val air_intake_pipe_vnel2_signal = results_en15544_air_intake_pipe.map: p_vnel =>
    p_vnel.andThen(p => p.`ph-(pR+pu)`)

val en13384_P_L_sig: Signal[VNelMcalcErr[P_L]] =
    results_en13384_sig.map(_.map(_.P_L))

// Build a Signal saying when all conditions / constraints are met
// TODO: add EN 13384 conditions ?

lazy val conditions_and_results_satisfied_except_emissions_sig: Signal[Boolean] =
    en15544_strict_validate_results_except_emissions

lazy val conditions_and_results_satisfied_except_emissions_not_sig =
    conditions_and_results_satisfied_except_emissions_sig.map(!_)

lazy val emissions_and_efficiency_values_satisfied_sig: Signal[Boolean] =
    en15544_strict_check_emissions_and_efficiency_values_with_local_regulations
        .map(_.allCriteriasAreMet)

lazy val emissions_and_efficiency_values_satisfied_not_sig =
    emissions_and_efficiency_values_satisfied_sig.map(!_)

lazy val all_conditions_and_results_satisfied_sig: Signal[Boolean] =
    en15544_strict_validate_results_except_emissions
        .combineWith(emissions_and_efficiency_values_satisfied_sig)
        .map(_ && _)

lazy val all_conditions_and_results_not_satisfied_sig =
    all_conditions_and_results_satisfied_sig.map(!_)

// ============================================================================
// CATALOG STATE
// ============================================================================

import afpma.firecalc.ui.models.CatalogState
import afpma.firecalc.ui.models.CatalogStateCodec.given
import afpma.firecalc.ui.models.UIState
import afpma.firecalc.ui.models.UIState.given
import io.circe.Encoder
import io.circe.parser

lazy val catalogWebStorageVar: WebStorageVar[CatalogState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.CATALOG_STATE, syncOwner = None)
        .withCodec(
            encode           = (state: CatalogState) =>
                Encoder[CatalogState].apply(state).noSpaces,
            decode           = (raw: String) =>
                parser.decode[CatalogState](raw) match
                    case Right(state) => Success(state)
                    case Left(_)      => Success(CatalogState.empty),
            default          = Success(CatalogState.empty),
            syncDistinctByFn = _ == _
        )

lazy val catalogStateVar: Var[CatalogState] = Var(catalogWebStorageVar.now())

// Per-category derived Signals
lazy val door15aFireboxesSignal: Signal[Seq[Firebox_V3.Door15aFirebox_Catalog]] =
    catalogStateVar.signal.map(_.door_15a_fireboxes.values.toSeq)

lazy val singleTestedFireboxesSignal: Signal[Seq[Firebox_V3.SingleTested]] =
    catalogStateVar.signal.map(_.single_tested_fireboxes.values.toSeq)

lazy val pipePresetsSignal: Signal[Seq[SetThermalPipeProp_13384_V3.SetPropertiesInBatch]] =
    catalogStateVar.signal.map(_.pipe_presets.values.toSeq)

lazy val casingPresetsSignal: Signal[Seq[SetThermalPipeProp_13384_V3.SetPropertiesInBatch]] =
    catalogStateVar.signal.map(_.casing_presets.values.toSeq)

lazy val flowResistancePresetsSignal: Signal[Seq[FlowResistanceCatalogEntry]] =
    catalogStateVar.signal.map(_.flow_resistance_presets.values.toSeq)

lazy val anglePresetsSignal: Signal[Seq[AnglePresetCatalogEntry]] =
    catalogStateVar.signal.map(_.angle_presets.values.toSeq)

// ============================================================================
// VIZ ELEMENT IDENTIFICATION
// ============================================================================

enum VizElementId:
    case FluePipeElement(elementIndex: Int)
    case ConnectorPipeElement(elementIndex: Int)
    case ChimneyPipeElement(elementIndex: Int)
    case AirIntakePipeElement(elementIndex: Int)
    case PostFireboxSlotElement(slotIndex: Int, elementIndex: Int)
    case FireboxElement

object VizElementId:
    def fromName(name: String): Option[VizElementId] = name match
        case s"Flue #$idx"       => idx.toIntOption.map(FluePipeElement(_))
        case s"Connector #$idx"  => idx.toIntOption.map(ConnectorPipeElement(_))
        case s"Chimney #$idx"    => idx.toIntOption.map(ChimneyPipeElement(_))
        case s"Air Intake #$idx" => idx.toIntOption.map(AirIntakePipeElement(_))
        case s"Slot$si #$ei"     => for s <- si.trim.toIntOption; e <- ei.toIntOption yield PostFireboxSlotElement(s, e)
        case "Firebox"           => Some(FireboxElement)
        case _                   => None

// Ephemeral hover/select state (not persisted to localStorage)
val vizHoveredElement: Var[Option[VizElementId]]  = Var(None)
val vizSelectedElement: Var[Option[VizElementId]] = Var(None)

// ============================================================================
// UI STATE (persisted to localStorage, with migration from VIZ_CAMERA_STATE)
// ============================================================================

import org.scalajs.dom

private def migrateOldCameraState(): Option[CameraState] =
    try
        val raw = dom.window.localStorage.getItem(LocalStorageKeys.VIZ_CAMERA_STATE)
        if raw == null || raw.isEmpty then None
        else
            parser.decode[CameraState](raw) match
                case Right(cs) =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    Some(cs)
                case Left(_) =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    None
    catch case _: Throwable => None

lazy val uiStateWebStorageVar: WebStorageVar[UIState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.UI_STATE, syncOwner = None)
        .withCodec(
            encode           = (state: UIState) =>
                Encoder[UIState].apply(state).noSpaces,
            decode           = (raw: String) =>
                parser.decode[UIState](raw) match
                    case Right(state) => Success(state)
                    case Left(_)      => Success(UIState.empty),
            default          = Success {
                // On first load: migrate old VIZ_CAMERA_STATE key if present
                val migratedCamera = migrateOldCameraState()
                UIState(cameraState = migratedCamera)
            },
            syncDistinctByFn = _ == _
        )

lazy val uiStateVar: Var[UIState] = Var(uiStateWebStorageVar.now())

def panelOpenedVar(key: String): Var[Boolean] =
    uiStateVar.zoomLazy(
        _.panelStates.getOrElse(key, false)
    )((state, v) => state.copy(panelStates = state.panelStates.updated(key, v)))
