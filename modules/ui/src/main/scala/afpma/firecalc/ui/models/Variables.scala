/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import algebra.instances.all.given

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.FireCalc_Version.<
import afpma.firecalc.dto.common.toVec3

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.PipesResult_15544
import afpma.firecalc.engine.models.en13384.typedefs.P_L
import afpma.firecalc.engine.models.en15544.std.Outputs
import afpma.firecalc.engine.models.en15544.typedefs.EstimatedOutputTemperatures
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import afpma.firecalc.engine.models.en15544.typedefs.η
import afpma.firecalc.engine.models.geometry.AirIntakeReplay
import afpma.firecalc.engine.models.geometry.AirIntakeReplay.AirIntakeAutoResult
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PipePositionComputer
import afpma.firecalc.engine.models.geometry.PipePositionResult
import afpma.firecalc.engine.models.geometry.PositionTracker
import afpma.firecalc.engine.models.geometry.SlotIntrospector
import afpma.firecalc.engine.models.geometry.SlotIntrospector.{SplitQueryResult, SlotIntrospectionResult}
import afpma.firecalc.engine.models.FireboxSplitFrame
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top_min
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.config.UIConfig
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.models.project.ProjectId
import afpma.firecalc.ui.models.project.ProjectManager.activeProjectIdVar
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
import coulomb.policy.standard.given

import scala.util.*

import afpma.firecalc.domain.AirDistributionBox
import afpma.firecalc.domain.FireboxCoordinateSystem

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
    Var[AppStateSchema](AppStateSchemaHelper.createInitialSchema())

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

val stove_params_max_load_var: Var[Option[Mass]] =
    stove_params_var.zoomLazy(_.maximum_load)((sp, m) =>
        if (m == sp.maximum_load) sp
        else if (m.isDefined) sp.with_mB(m.get)
        else sp
    )

val air_intake_incrdescr_var =
    engineStateVar.zoomLazy(_.air_intake_pipes.descr)((g, x) =>
        g.copy(air_intake_pipes = g.air_intake_pipes.copy(descr = x))
    )

// EngineStateHelper

val engineStateHelperVar =
    engineStateVar.zoomLazy(FireCalcYAML_Loader.apply)((engineState, _) => engineState)

val air_intake_vnel_signal          = engineStateHelperVar.signal.map(_.airIntakePipe)
val air_intake_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.airIntakePipeMappings)

// Firebox

val firebox_var =
    engineStateVar.zoomLazy(_.firebox)((ast, x) => ast.copy(firebox = x))

// ── Air intake position tracking ─────────────────────────────────

/** Stored Auto/Manual mode for the air intake pipe position. */
lazy val airIntakePositionMode_var: Var[AirIntakePosition] =
    engineStateVar.zoomLazy(_.air_intake_pipes.position): (g, x) =>
        g.copy(air_intake_pipes = g.air_intake_pipes.copy(position = x))

/** Wrapper-level initial direction for the air intake pipe. */
lazy val airIntakeInitialDir_var: Var[PipeInitialDirection] =
    engineStateVar.zoomLazy(_.air_intake_pipes.initialDir): (g, x) =>
        g.copy(air_intake_pipes = g.air_intake_pipes.copy(initialDir = x))

/** Descriptor sequence for the air intake pipe. */
lazy val airIntakeDescr_var: Var[Seq[FlowOnlyPipeDescr_13384]] =
    engineStateVar.zoomLazy(_.air_intake_pipes.descr): (g, x) =>
        g.copy(air_intake_pipes = g.air_intake_pipes.copy(descr = x))

/**
 * Effective air intake position derived from mode × direction × firebox × descriptor sequence.
 * Returns (startPoint, optionalFinalPoint) for PositionTracker.
 *
 * - InitialAuto: reverse-compute start so pipe terminates at air distribution box surface.
 * - InitialManual(pos): start at stored pos; no final constraint.
 * - FinalAuto: reverse-compute start so pipe terminates at air distribution box surface.
 * - FinalManual(pos): start at origin; final constraint at stored pos.
 *
 * Note: InitialAuto and FinalAuto are geometrically identical — both use the reverse
 * computation (replay descriptors from origin, offset so end lands at box).
 * The Initial/Final distinction is purely a migration label (SetInitialPosition vs
 * SetFinalPosition found in V3 descriptors).
 */

/**
 * Shared upstream signal: computes the air intake replay once and derives
 * both the effective start offset and the connection point on the box surface.
 * Used by both `airIntakeEffectivePosition_sig` and `airIntakeDisplayedPosition_sig`.
 */
lazy val airIntakeAutoResult_sig       : Signal[AirIntakeAutoResult]              =
    airIntakeInitialDir_var.signal
        .combineWith(airIntakeDescr_var.signal)
        .combineWith(firebox_var.signal)
        .map: (initialDir, descr, fb) =>
            AirIntakeReplay.computeAirIntakeAuto        (
                descr         = descr,
                initialDir    = initialDir,
                boxXWidth     = fb.firebox_width.value,
                boxYDepth     = fb.firebox_depth.value,
                boxZBottom    = AirDistributionBox.CenterZ,
                boxZHeight    = AirDistributionBox.Z_HEIGHT,
                innerShapeOpt = None
            )
lazy val airIntakeEffectivePosition_sig: Signal[(Position3D, Option[Position3D])] =
    airIntakePositionMode_var.signal
        .combineWith(airIntakeAutoResult_sig)
        .map: (mode, autoResult) =>
            mode match
                case AirIntakePosition.InitialManual(pos)                        =>
                    (pos, None)
                case AirIntakePosition.FinalManual(pos)                          =>
                    (Position3D.Origin, Some(pos))
                // InitialAuto and FinalAuto are geometrically identical — both use the
                // same reverse computation (replay from origin, offset so end lands at box).
                // The Initial/Final distinction is purely a migration label
                // (SetInitialPosition vs SetFinalPosition found in V3 descriptors).
                case AirIntakePosition.InitialAuto | AirIntakePosition.FinalAuto =>
                    (autoResult.startPosition, None)

/**
 * Displayed position for the air intake Position3D form.
 *
 * Initial modes (InitialAuto, InitialManual) display the start position.
 * Final modes (FinalAuto, FinalManual) display the final/end position.
 *
 * For FinalAuto, the final position is the connection point on the air
 * distribution box surface, computed by the same replay as the start offset.
 */
lazy val airIntakeDisplayedPosition_sig: Signal[Position3D] =
    airIntakePositionMode_var.signal
        .combineWith(airIntakeEffectivePosition_sig)
        .combineWith(airIntakeAutoResult_sig)
        .map: t =>
            val (mode, startPos, _finalPos, autoResult) = t
            mode match
                case AirIntakePosition.InitialManual(p) => p
                case AirIntakePosition.FinalManual(p)   => p
                case AirIntakePosition.InitialAuto      =>
                    startPos
                case AirIntakePosition.FinalAuto        =>
                    autoResult.connectionPoint

lazy val airintake_positions_sig: Signal[PipePositionResult] =
    airIntakeInitialDir_var.signal
        .combineWith(airIntakeDescr_var.signal)
        .combineWith(airIntakeEffectivePosition_sig)
        .map: (initialDir, descr, effectiveStart, effectiveFinal) =>
            val result = PositionTracker.computeFlowOnly13384(
                descr,
                initialDirection = initialDir,
                externalFrame    = None,
                startPoint       = effectiveStart.toVec3,
                finalPoint       = effectiveFinal.map(_.toVec3)
            )
            result
        .distinct

// ── Post-firebox generic topology ─────────────────────────────────
// Slot-indexed reactive state for dynamic N-pipe UI.

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.v7.endsWithSingularFlowResistance
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.common.{PipeInitialDirection, Position3D}
import afpma.firecalc.engine.models.ChimneyPipe_Module
import afpma.firecalc.engine.models.SlotBuildResult
import afpma.firecalc.engine.ops.generic.{PostFireboxPipeChain, TopologyError}

// ── Primary Var: the post-firebox pipe slots ─────────────────────

/**
 * Writable Var for the post-firebox pipe slot vector.
 * Mutations here (add/remove/reorder/edit) propagate through engineStateVar
 * and trigger re-computation of all derived signals.
 */
lazy val postFireboxSlots_var: Var[Seq[PostFireboxPipeDescrSlot_V7]] =
    engineStateVar.zoomLazy(_.post_firebox_pipes.slots): (g, x) =>
        g.copy(post_firebox_pipes = g.post_firebox_pipes.copy(slots = x))

/** Wrapper-level initial direction for the post-firebox pipe chain. */
lazy val postFireboxInitialDir_var: Var[PipeInitialDirection] =
    engineStateVar.zoomLazy(_.post_firebox_pipes.initialDirection): (g, x) =>
        g.copy(post_firebox_pipes = g.post_firebox_pipes.copy(initialDirection = x))

/** Stored Auto/Manual mode for the post-firebox pipe start position. */
lazy val postFireboxStartPositionMode_var: Var[PostFireboxStartPosition] =
    engineStateVar.zoomLazy(_.post_firebox_pipes.initialPosition): (g, x) =>
        g.copy(post_firebox_pipes = g.post_firebox_pipes.copy(initialPosition = x))

/** Shared upstream signal: convert DTO slots to engine slots once per reactive cycle. */
lazy val enginePostFireboxSlots_sig: Signal[Seq[PostFireboxPipeSlot]] =
    postFireboxSlots_var.signal.map(PostFireboxPipeSlot.fromDto)

/**
 * Shared upstream signal: extracts the first inner shape and split query from
 * engine slots once per reactive cycle. Used by both `postFireboxAutoPosition_sig`
 * and `slotPositions_sig` to avoid duplicated introspection traversals.
 */
lazy val slotIntrospection_sig: Signal[SlotIntrospector.SlotIntrospectionResult] =
    enginePostFireboxSlots_sig.map: engineSlots =>
        val firstShape = SlotIntrospector
            .firstInnerShapeIn(engineSlots)
            .getOrElse(afpma.firecalc.ui.instances.defaultable.pipeShapeInner.default)
        val splitQuery = SlotIntrospector.querySplit(engineSlots)
        SlotIntrospector.SlotIntrospectionResult(firstShape, splitQuery)

/**
 * Auto-computed post-firebox start position from direction × firebox × firstSlotShape.
 * Independent of mode — used only when mode is Auto (see postFireboxEffectivePosition_sig).
 */
lazy val postFireboxAutoPosition_sig: Signal[Position3D] =
    postFireboxInitialDir_var.signal
        .combineWith(firebox_var.signal)
        .combineWith(slotIntrospection_sig)
        .map: (dir, fb, introspection) =>
            introspection.splitQuery match
                case SplitQueryResult.LeadingSplit(absDir)                       =>
                    PipePositionComputer.computeBranchStartAfterSplit    (
                        absDir     = absDir,
                        boxXWidth  = fb.firebox_width.value,
                        boxYDepth  = fb.firebox_depth.value,
                        boxZBottom = FireboxCoordinateSystem.FireboxBaseCenterZ,
                        boxZHeight = fb.firebox_height.value,
                        innerShape = introspection.firstShape
                    )
                case SplitQueryResult.NoLeadingSplit(_) | SplitQueryResult.Empty =>
                    PipePositionComputer.computePostFireboxStart (
                        direction  = dir,
                        boxXWidth  = fb.firebox_width.value,
                        boxYDepth  = fb.firebox_depth.value,
                        boxZBottom = FireboxCoordinateSystem.FireboxBaseCenterZ,
                        boxZHeight = fb.firebox_height.value,
                        innerShape = introspection.firstShape
                    )

/**
 * Effective post-firebox start position derived from mode × autoPosition.
 * In Auto mode, returns the reactively computed position.
 * In Manual mode, returns the stored position directly (ignores autoPosition).
 */
lazy val postFireboxEffectivePosition_sig: Signal[Position3D] =
    postFireboxStartPositionMode_var.signal
        .combineWith(postFireboxAutoPosition_sig)
        .map: (mode, autoPos) =>
            mode match
                case PostFireboxStartPosition.Manual(pos) => pos
                case PostFireboxStartPosition.Auto        => autoPos

/**
 * App-wide Var for the post-firebox rotation offer toast.
 *
 * Set by PostFireboxPipePanels' angle-edit observer when a pinned direction-change
 * element's bend angle is edited AND downstream rotation would preserve chain shape.
 * Read by the AppToasts component in the navbar.
 *
 * Living outside the panel so the toast persists at the app level (fixed top-right
 * position), independent of accordion scroll/collapse, and can be cleared by any flow
 * (user action, project reload, etc.).
 */
lazy val rotateOffer_var: Var[Option[afpma.firecalc.engine.models.geometry.ChainEditDispatcher.Offer]] = Var(None)

/**
 * Last dispatcher-written slot snapshot. Set by auto-dispatch (PostFireboxPipePanels'
 * snapshot observer) and by toast strategy clicks (AppToasts). Any snapshot emission
 * whose value equals this is treated as a pure echo and ignored by the observer — so
 * binder roundtrips (bidirectional `RelativeDirectionInput` syncs, normalization passes)
 * cannot corrupt `prevSnapshot` / `rotateOffer_var` with spurious edits.
 *
 * Value-based rather than count-based suppression: one dispatcher write can fan out
 * into an arbitrary number of echoes (300ms debounced snapshot + ~200ms bidirsync
 * roundtrip + possible normalize pass). A single boolean would only absorb the first.
 */
lazy val lastDispatcherWrite_var: Var[Option[Seq[PostFireboxPipeDescrSlot_V7]]] = Var(None)

// ── Slot-indexed build results ───────────────────────────────────

/**
 * Generic slot-indexed build results from PipeChainGeneric.
 * Each SlotBuildResult carries type-erased pipe, IdsMapping (Int → Option[Int]),
 * and final PipeFrame — indexed by slot position.
 */
lazy val slotBuildResults_sig: Signal[Vector[SlotBuildResult]] =
    engineStateHelperVar.signal.map(_.slotBuildResults)

/** Topology validation: permissive — errors are exposed as TopologyError values. */
lazy val topologyValidation_sig: Signal[Validated[cats.data.NonEmptyList[TopologyError], PostFireboxPipeChain]] =
    engineStateHelperVar.signal.map(_.topologyValidation)

// ── Slot-indexed frame chain ─────────────────────────────────────

/**
 * Final PipeFrame per slot, extracted from slotBuildResults.
 * slotFinalFrames(i) is the frame after all elements in slot i,
 * and serves as the initial frame for slot i+1.
 */
lazy val slotFinalFrames_sig: Signal[Vector[Option[PipeFrame]]] =
    slotBuildResults_sig.map(_.map(_.finalFrame))

/**
 * The initial frame for slot at index `idx`: derived from the V7 wrapper-level
 * `postFireboxInitialDir_var` for slot 0, otherwise the final frame of the previous slot.
 *
 * Slot 0 has no preceding slot, so the frame must come from the wrapper direction —
 * the same value the engine uses (`PipeChainGeneric.build` / `wrapperInitialDirection`).
 * Previously this returned `Signal.fromValue(None)`, which left `frameBefore` permanently
 * empty for the first slot, causing the direction badge to render `emptyNode` for every
 * element with `absDir = None`.
 */
def slotInitialFrameSig(idx: Int): Signal[Option[PipeFrame]] =
    if idx <= 0 then
        postFireboxInitialDir_var.signal
            .combineWith(enginePostFireboxSlots_sig)
            .map: (dir, slots) =>
                val isLeadingSplit = slots.headOption.exists(FireboxSplitFrame.isLeadingSplit)
                val dirVec         =
                    if isLeadingSplit then Vec3.Up
                    else
                        Vec3.fromAzimuthElevation(
                            dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
                            InclinationDirection.toDegrees                       (dir.inclination)
                        )
                Some(PipeFrame.initial(dirVec))
    else slotFinalFrames_sig.map(frames => frames.lift(idx - 1).flatten)

// ── Slot-indexed position tracking ───────────────────────────────

lazy val slotPositions_sig: Signal[Vector[PipePositionResult]] =
    postFireboxSlots_var.signal
        .combineWith(
            slotIntrospection_sig,
            slotFinalFrames_sig,
            postFireboxInitialDir_var.signal,
            postFireboxEffectivePosition_sig,
            firebox_var.signal
        )
        .map: (slots, introspection, frames, initialDir, effectivePosition, fb) =>
            // Slot 0 starts at the effective position (Auto: computed, Manual: stored)
            val slot0Start =
                if slots.isEmpty then Vec3(0, 0, 0)
                else effectivePosition.toVec3

            // When the leading element of slot 0 is a split, the actual split position
            // is the firebox top center, not branchOneStart. Pass it so PositionTracker
            // records the correct split position for the symmetry plane.
            val slot0SplitPosition: Option[Vec3] =
                introspection.splitQuery match
                    case SplitQueryResult.LeadingSplit(absDir)                       =>
                        val shapeHeight = PipePositionComputer.innerHeight(introspection.firstShape)
                        Some(
                            PipePositionComputer.computeSplitPosition(
                                FireboxCoordinateSystem.FireboxBaseCenterZ,
                                fb.firebox_height.value,
                                shapeHeight,
                                absDir
                            )
                        )
                    case SplitQueryResult.NoLeadingSplit(_) | SplitQueryResult.Empty =>
                        None

            slots.zipWithIndex
                .foldLeft((Vector.empty[PipePositionResult], slot0Start)):
                    case ((results, startPoint), (slot, idx)) =>
                        val prevFrame = if idx == 0 then None else frames.lift(idx - 1).flatten
                        val splitPos  = if idx == 0 then slot0SplitPosition else None
                        val pos       = slot match
                            case PostFireboxPipeDescrSlot_V7.FlueSlot(descr)        =>
                                PositionTracker.computeFlowOnly15544(
                                    descr,
                                    initialDirection = initialDir,
                                    externalFrame    = prevFrame,
                                    startPoint       = startPoint,
                                    splitPosition    = splitPos
                                )
                            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(descr) =>
                                PositionTracker.computeThermal13384(
                                    descr,
                                    initialDirection = initialDir,
                                    externalFrame    = prevFrame,
                                    startPoint       = startPoint,
                                    splitPosition    = splitPos
                                )
                            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(descr)   =>
                                PositionTracker.computeThermal13384(
                                    descr,
                                    initialDirection = initialDir,
                                    externalFrame    = prevFrame,
                                    startPoint       = startPoint,
                                    splitPosition    = splitPos
                                )
                            case PostFireboxPipeDescrSlot_V7.ChimneySlot(descr)     =>
                                PositionTracker.computeThermal13384(
                                    descr,
                                    initialDirection = initialDir,
                                    externalFrame    = prevFrame,
                                    startPoint       = startPoint,
                                    splitPosition    = splitPos
                                )
                            case PostFireboxPipeDescrSlot_V7.NoFlueSlot             =>
                                PipePositionResult(Seq.empty, startPoint, None)
                        (results :+ pos, pos.finalPoint)
                ._1
        .distinct

// ── Chimney end-cap (symbolic disc) inputs ───────────────────────

/**
 * Inputs needed to render the symbolic end-cap disc at the chimney's end:
 * the chimney's `PipePositionResult` and its terminal inner cross-section.
 *
 * `Some((pos, shape))` only when:
 *   - There is a last `ChimneySlot`, AND
 *   - Its descr `endsWithSingularFlowResistance` (per the DTO marker trait), AND
 *   - The chimney has a known terminal shape via `ChimneyPipe_Module.lastInnerShape`
 *     (folds through `PipeFullDescr.lastInnerGeom`, honouring any
 *     `SectionGeometryChange` along the way).
 *
 * Otherwise `None` and no disc is rendered.
 */
lazy val chimneyEndCapInputs_sig: Signal[Option[(PipePositionResult, PipeShape)]] =
    postFireboxSlots_var.signal
        .combineWith(slotPositions_sig)
        .map: (slots, positions) =>
            val lastChimneyIdxOpt = slots.zipWithIndex
                .collect:
                    case (s: PostFireboxPipeDescrSlot_V7.ChimneySlot, i) => (s, i)
                .lastOption
            for
                (slot, idx) <- lastChimneyIdxOpt
                if slot.descr.endsWithSingularFlowResistance
                upstreamFrame = positions.lift(idx - 1).flatMap(_.finalFrame)
                // UI visualization — not in a slot build context
                shape <- ChimneyPipe_Module.lastInnerShape(slot.descr, upstreamFrame)(using SlotContext.unslotted)
                pos   <- positions.lift(idx)
            yield (pos, shape)

// ── Per-slot accessor helpers ────────────────────────────────────

/** Per-slot IdsMapping function (Int → Option[Int]). Returns Invalid if slot index out of bounds. */
def slotMappingFnSig(idx: Int): Signal[ValidatedNel[IncrementalValidation_Error, Int => Option[Int]]] =
    slotBuildResults_sig.map: results =>
        results
            .lift(idx)
            .map(_.idsMappingFn)
            .getOrElse(
                Validated.invalidNel(FluePipeNotDefinedYet) // fallback — slot doesn't exist
            )

/** All post-firebox pipe results as a tagged vector. */
lazy val postFireboxPipeResults_sig: Signal[VNelMcalcErr[Vector[(PipeType, PipeResult)]]] =
    results_en15544_strict_sig.map: vnelAppl =>
        vnelAppl.andThen(_.primary.postFireboxPipeResults)

// Results for EN15544 Strict

/**
 * Debounced EN15544 strict computation with project-ID staleness detection.
 *
 * During the debounce window after a project switch, the signal returns
 * `ResultsNotComputed` (filtered from panel displays) instead of stale
 * values from the previous project.
 */
lazy val resultsWithProjectSig: Signal[(ProjectId, VNelMcalcErr[EN15544_Strict_Application])] =
    engineStateHelperVar.signal
        .combineWith(activeProjectIdVar.signal)
        .composeChanges(_.debounce(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .map { (helper, projectIdOpt) =>
            val projectId      = projectIdOpt.getOrElse(ProjectId(""))
            val currentFirebox = helper.fcProj.firebox
            val result         =
                if !UIConfig.uiAvailability.allows(currentFirebox) then
                    Validated.invalidNel(FireboxTypeDisabledError(currentFirebox.typeName))
                else
                    scala.util.Try(helper.make_en15544_Strict_Application) match
                        case scala.util.Success(r) => r
                        case scala.util.Failure(e) => Validated.invalidNel(UnexpectedDevError(e.getMessage))
            (projectId, result)
        }

/**
 * Raw debounced result (no staleness check) — for activation defaults so the
 *  previous computation's derived values carry over when switching sizing methods.
 */
lazy val results_en15544_strict_raw_sig: Signal[VNelMcalcErr[EN15544_Strict_Application]] =
    resultsWithProjectSig.map(_._2)

/**
 * Staleness-checked result — for error display and panel status.
 *  Returns ResultsNotComputed when the project ID at computation time doesn't
 *  match the current active project, preventing stale data from being shown.
 */
lazy val results_en15544_strict_sig: Signal[VNelMcalcErr[EN15544_Strict_Application]] =
    resultsWithProjectSig
        .combineWith(activeProjectIdVar.signal)
        .map {
            (
                computedProjectId  : ProjectId,
                vnel               : VNelMcalcErr[EN15544_Strict_Application],
                currentProjectIdOpt: Option[ProjectId]
            ) =>
                val currentProjectId = currentProjectIdOpt.getOrElse(ProjectId(""))
                if computedProjectId == currentProjectId then vnel
                else Validated.invalidNel(ResultsNotComputed)
        }

lazy val en15544_strict_validate_results_except_emissions: Signal[Boolean] =
    results_en15544_strict_sig
        .combineWithDistinct(project_descr_var.signal)
        .distinct
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
        .combineWithDistinct(project_descr_var.signal)
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
    results_en15544_strict_sig.flatMapVNelE(strict => strict.primary.pressureRequirement_EN15544)

lazy val results_en15544_outputs: Signal[VNelMcalcErr[Outputs]] =
    results_en15544_strict_sig.mapVNelE(strict => strict.primary.outputs)

lazy val results_en15544_air_intake_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.flatMapVNelE(_.pipesResult_15544.airIntake)

lazy val results_en15544_combustion_air_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.flatMapVNelE(_.pipesResult_15544.combustionAir)

lazy val results_en15544_firebox_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.flatMapVNelE(_.pipesResult_15544.firebox)

lazy val results_en15544_estimated_output_temperatures: Signal[VNelMcalcErr[EstimatedOutputTemperatures]] =
    results_en15544_strict_sig.mapVNelE(strict => strict.primary.estimated_output_temperatures)

lazy val chimney_wall_temp_above_condensation_temp_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNelE(
        strict =>
            strict.primary.validateChimneyWallTempIsAboveCondensationTemp
                .map(_ => true),
        default = false
    )

lazy val results_en15544_t_chimney_wall_top: Signal[VNelMcalcErr[t_chimney_wall_top]] =
    results_en15544_strict_sig.flatMapVNelE(strict => strict.primary.t_chimney_wall_top)

lazy val results_en15544_t_chimney_wall_top_min: Signal[VNelMcalcErr[t_chimney_wall_top_min]] =
    results_en15544_strict_sig.mapVNelE(_.formulas.t_chimney_wall_top_min)

/**
 * Flue-gas velocity bounds (EN 15544 §4.9.3) as plain doubles, with standard fallbacks
 * when the 15544 application is not yet resolved. Consumed by the graph layer to draw
 * horizontal operating-window reference lines.
 */
lazy val results_en15544_flue_gas_velocity_bounds: Signal[(Double, Double)] =
    results_en15544_strict_sig.map:
        case cats.data.Validated.Valid(strict) =>
            (strict.formulas.flueGasVelocityMin.value, strict.formulas.flueGasVelocityMax.value)
        case _                                 =>
            (1.2, 6.0)

lazy val results_en15544_efficiency: Signal[VNelMcalcErr[η]] =
    results_en15544_strict_sig.flatMapVNelE(strict => strict.primary.η)

lazy val eff_and_min_eff: Signal[VNelMcalcErr[(Percentage, n_min)]] =
    results_en15544_strict_sig.flatMapVNelE(strict => strict.primary.η.map(eff => (eff, strict.n_min)))

lazy val results_en15544_emissions_and_efficiency_values: Signal[VNelMcalcErr[EmissionsAndEfficiencyValues]] =
    results_en15544_strict_sig.mapVNelE(_.emissions_and_efficiency_values)

extension (d: Double) private def filterNaN: Option[Double] = Option.when(!d.isNaN)(d)

extension (od: Option[Double]) private def filterNaN: Option[Double] = od.filter(!_.isNaN)

def makeQuadrionSubtotalForSingle(
    outputsSig: Signal[VNelMcalcErr[Outputs]]
)(
    toPipeResult: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Valid(outputs) =>
            outputs.pipesResult_15544.accumulateErrors.map(toPipeResult) match
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
                outputs.pipesResult_15544.accumulateErrors.map(to_cc_intlair_pres)
            val cc_firebox_pres =
                outputs.pipesResult_15544.accumulateErrors.map(to_cc_firebox_pres)
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
val viz3DPanelVar = Var[Boolean](false)
val viz3DPanelOn  = viz3DPanelVar.signal
val viz3DPanelOff = viz3DPanelOn.map(!_)

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
import org.scalajs.dom
import afpma.firecalc.engine.models.en15544.typedefs.n_min

lazy val catalogDecodeFailed: Var[Boolean] = Var(false)

lazy val catalogWebStorageVar: WebStorageVar[CatalogState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.CATALOG_STATE, syncOwner = None)
        .withCodec          (
            encode           = (state: CatalogState) => Encoder[CatalogState].apply(state).noSpaces,
            decode           = (raw: String) =>
                parser.decode[CatalogState](raw) match
                    case Right(state) => Success(state)
                    case Left(err)    =>
                        dom.console.warn       (
                            s"[FireCalc] Catalog cache decode failed, resetting to empty. Error: ${err.getMessage}"
                        )
                        catalogDecodeFailed.set(true              )
                        Success                (CatalogState.empty),
            default          = Success(CatalogState.empty),
            syncDistinctByFn = _ == _
        )

lazy val catalogStateVar: Var[CatalogState] = Var(catalogWebStorageVar.now())

// Per-category derived Signals
lazy val door15aFireboxesSignal: Signal[Seq[Firebox.Door15aFirebox_Catalog]] =
    catalogStateVar.signal.map(_.door_15a_fireboxes.values.toSeq)

lazy val singleTestedFireboxesSignal: Signal[Seq[Firebox.SingleTested]] =
    catalogStateVar.signal.map(_.single_tested_fireboxes.values.toSeq)

lazy val pipePresetsSignal: Signal[Seq[SetThermalPipeProp_13384.SetPropertiesInBatch]] =
    catalogStateVar.signal.map(_.pipe_presets.values.toSeq)

lazy val casingPresetsSignal: Signal[Seq[SetThermalPipeProp_13384.SetPropertiesInBatch]] =
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
// Set-based to support highlighting multiple elements (e.g. two neighbors at a graph boundary)
val vizHoveredElement : Var[Set[VizElementId]] = Var(Set.empty)
val vizSelectedElement: Var[Set[VizElementId]] = Var(Set.empty)

/** Toggle selection: if clicking the same set, deselect; otherwise select the new set. */
def toggleVizSelection(newSelection: Set[VizElementId]): Unit =
    if newSelection.nonEmpty && newSelection == vizSelectedElement.now() then vizSelectedElement.set(Set.empty   )
    else vizSelectedElement.set                                                                     (newSelection)

// ============================================================================
// UI STATE (persisted to localStorage, with migration from VIZ_CAMERA_STATE)
// ============================================================================

private def migrateOldCameraState(): Option[CameraState] =
    try
        val raw = dom.window.localStorage.getItem(LocalStorageKeys.VIZ_CAMERA_STATE)
        if raw == null || raw.isEmpty then None
        else
            parser.decode[CameraState](raw) match
                case Right(cs) =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    Some                              (cs                               )
                case Left(_)   =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    None
    catch case _: Throwable => None

lazy val uiStateWebStorageVar: WebStorageVar[UIState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.UI_STATE, syncOwner = None)
        .withCodec          (
            encode           = (state: UIState) => Encoder[UIState].apply(state).noSpaces,
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

lazy val fireboxCacheWebStorageVar: WebStorageVar[FireboxCacheState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.FIREBOX_CACHE, syncOwner = None)
        .withCodec          (
            encode           = (state: FireboxCacheState) => Encoder[FireboxCacheState].apply(state).noSpaces,
            decode           = (raw: String) =>
                io.circe.parser.decode[FireboxCacheState](raw) match
                    case Right(state) => scala.util.Success(state)
                    case Left(_)      => scala.util.Success(FireboxCacheState.empty),
            default          = scala.util.Success(FireboxCacheState.empty),
            syncDistinctByFn = _ == _
        )

lazy val fireboxCacheStateVar: Var[FireboxCacheState] = Var(fireboxCacheWebStorageVar.now())

def panelOpenedVar(key: String): Var[Boolean] =
    uiStateVar.zoomLazy(
        _.panelStates.getOrElse(key, false)
    )((state, v) => state.copy(panelStates = state.panelStates.updated(key, v)))
