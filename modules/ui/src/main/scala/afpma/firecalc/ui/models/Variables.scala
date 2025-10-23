/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import scala.util.*

import cats.implicits.catsSyntaxOptionId
import cats.implicits.toShow

import cats.data.Validated.Valid
import cats.implicits.catsSyntaxTuple2Semigroupal

import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.engine.utils.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.WebStorageVar
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.LoadQty

import afpma.firecalc.ui.*
import afpma.firecalc.ui.utils.*
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import afpma.firecalc.engine.models.en15544.std.Outputs
import afpma.firecalc.engine.models.en15544.typedefs.EstimatedOutputTemperatures
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.en15544.typedefs.η
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.engine.models.PipeResult
import cats.data.Validated
import afpma.firecalc.engine.models.PipesResult_15544
import com.raquo.airstream.eventbus.EventBus
import afpma.firecalc.ui.components.FireCalcProjet
import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION
import afpma.firecalc.engine.models.en15544.typedefs.CitedConstraints
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.models.en13384.typedefs.P_L
import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.payments.shared.*
import afpma.firecalc.ui.instances.custom_yaml
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1
import afpma.firecalc.ui.models.schema.SchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys
import io.scalaland.chimney.dsl.*

// ============================================================================
// UNIFIED APPLICATION STATE SCHEMA
// ============================================================================

/**
 * Unified application state schema stored in localStorage as a single atomic unit.
 * Contains: engine_state (sent to backend), sensitive_data (client-only), billing_data (client-only)
 */
val appStateSchemaWebStorageVar: WebStorageVar[AppStateSchema_V1] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.APP_STATE_SCHEMA, syncOwner = None)
        .withCodec(
            encode =
                AppStateSchemaHelper.encodeToYaml(_).toOption.getOrElse(""),
            decode = (raw: String) => {
                // NEW: Attempt migration before decode
                SchemaMigrations.migrateToLatest(raw) match {
                    case Some(schema) =>
                        // Migration successful
                        Success(schema)
                    
                    case None =>
                        // Migration failed - clear storage and use defaults
                        SchemaMigrations.clearInvalidData()
                        Success(AppStateSchemaHelper.createInitialSchema())
                }
            },
            default = Success(AppStateSchemaHelper.createInitialSchema()),
            syncDistinctByFn = _ == _
        )

val appStateSchemaVar =
    Var[AppStateSchema_V1](appStateSchemaWebStorageVar.now())

// ============================================================================
// ZOOMED VARS FROM UNIFIED SCHEMA
// ============================================================================

import SchemaTransformers.given
import cats.data.Validated.Invalid
import afpma.firecalc.engine.models.CombustionAirPipe_EN15544
import afpma.firecalc.engine.models.en15544.pipedescr

// Engine state (sent to backend for PDF generation)
val appStateVar = appStateSchemaVar.zoomLazy(_.engine_state)((schema, engine) =>
    schema.copy(engine_state = engine)
)

// Sensitive data (NEVER sent to backend) - direct zoom (already ClientProjectData_V1)
val clientProjectDataVar =
    appStateSchemaVar.zoomLazy(_.sensitive_data)((schema, data) =>
        schema.copy(sensitive_data = data)
    )

// Billing data (for payments, NEVER sent to backend) - direct zoom, no transformation needed
val billingInfoVar =
    appStateSchemaVar.zoomLazy(_.billing_data)((schema, billing) =>
        schema.copy(billing_data = billing)
    )

val localeVar       = appStateVar.zoomLazy(_.locale)((g, x) => g.copy(locale = x))
val displayUnitsVar =
    appStateVar.zoomLazy(_.display_units)((g, x) => g.copy(display_units = x))

val project_descr_var = appStateVar.zoomLazy(_.project_description)((ast, x) =>
    ast.copy(project_description = x)
)

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

val local_conditions_var = appStateVar.zoomLazy(_.local_conditions)((ast, x) =>
    ast.copy(local_conditions = x)
)

val z_geodetical_height_var = local_conditions_var.zoomLazy(_.altitude):
    (lc, z) => lc.copy(altitude = z)

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
    appStateVar.zoomLazy(_.stove_params)((ast, x) => ast.copy(stove_params = x))

val air_intake_incrdescr_var =
    appStateVar.zoomLazy(_.air_intake_descr)((g, x) =>
        g.copy(air_intake_descr = x)
    )

// AppStateHelper

val appStateHelperVar =
    appStateVar.zoomLazy(FireCalcYAML_Loader.apply)((appState, _) => appState)

val air_intake_vnel_signal          = appStateHelperVar.signal.map(_.airIntakePipe)
val air_intake_mappings_vnel_signal =
    appStateHelperVar.signal.map(_.airIntakePipeMappings)

// Firebox

val firebox_var =
    appStateVar.zoomLazy(_.firebox)((ast, x) => ast.copy(firebox = x))

// FluePipe

val fluepipe_incrdescr_var = appStateVar.zoomLazy(_.flue_pipe_descr)((g, x) =>
    g.copy(flue_pipe_descr = x)
)

val fluepipe_vnel_signal = appStateHelperVar.signal.map(_.fluePipe)

val fluepipe_mappings_vnel_signal =
    appStateHelperVar.signal.map(_.fluePipeMappings)

// Connecting Pipe

val connector_pipe_incrdescr_var =
    appStateVar.zoomLazy(_.connector_pipe_descr)((g, x) =>
        g.copy(connector_pipe_descr = x)
    )

val connector_pipe_vnel_signal          = appStateHelperVar.signal.map(_.connectorPipe)
val connector_pipe_mappings_vnel_signal =
    appStateHelperVar.signal.map(_.connectorPipeMappings)

// Chimney Pipe

val chimney_pipe_incrdescr_var =
    appStateVar.zoomLazy(_.chimney_pipe_descr)((g, x) =>
        g.copy(chimney_pipe_descr = x)
    )

val chimney_pipe_vnel_signal          = appStateHelperVar.signal.map(_.chimneyPipe)
val chimney_pipe_mappings_vnel_signal =
    appStateHelperVar.signal.map(_.chimneyPipeMappings)

// Results for EN15544 Strict

def run_en15544_strict[X](using
    strict: EN15544_Strict_Application
)(run: strict.Params_15544 ?=> VNelString[X]): VNelString[X] =
    val p: strict.Params_15544 =
        (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.givens.nominal)
    run(using p)

lazy val results_en15544_strict_sig
    : Signal[VNelString[EN15544_Strict_Application]] =
    appStateHelperVar.signal
        // emits at most once during interval (prevent too much computing)
        // .composeChanges(_.throttle(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .composeChanges(_.debounce(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .map(_.make_en15544_Strict_Application)

lazy val en15544_strict_validate_results: Signal[Boolean] =
    results_en15544_strict_sig
        .mapAndFoldVNel(
            _.validateResults.fold(nel => false, _ => true),
            default = false
        )

lazy val results_en13384_sig
    : Signal[VNelString[EN13384_1_A1_2019_Common_Application]] =
    results_en15544_strict_sig.signal.map: strict_15544 =>
        strict_15544.map(_.en13384_application)

lazy val results_en15544_pressure_requirements
    : Signal[VNelString[PressureRequirement]] =
    results_en15544_strict_sig.flatMapVNelString(strict =>
        val p = (
            DraftCondition.DraftMinOrPositivePressureMax,
            LoadQty.givens.nominal
        )
        strict.pressureRequirement_EN15544(using p).asVNelString
    )

lazy val results_en15544_outputs: Signal[VNelString[Outputs]] =
    results_en15544_strict_sig.mapVNelString(strict =>
        val p = (
            DraftCondition.DraftMinOrPositivePressureMax,
            LoadQty.givens.nominal
        )
        strict.outputs(using p)
    )

lazy val results_en15544_air_intake_pipe: Signal[VNelString[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.airIntake).asVNelString)

lazy val results_en15544_combustion_air_pipe: Signal[VNelString[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.combustionAir).asVNelString)

lazy val results_en15544_firebox_pipe: Signal[VNelString[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.firebox).asVNelString)

lazy val results_en15544_channel_pipe: Signal[VNelString[PipeResult]]   =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.flue).asVNelString)
lazy val results_en15544_connector_pipe: Signal[VNelString[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.connector).asVNelString)
lazy val results_en15544_chimney_pipe: Signal[VNelString[PipeResult]]   =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.chimney).asVNelString)

lazy val results_en15544_estimated_output_temperatures
    : Signal[VNelString[EstimatedOutputTemperatures]] =
    results_en15544_strict_sig.mapVNelString(strict =>
        val p = (
            DraftCondition.DraftMinOrPositivePressureMax,
            LoadQty.givens.nominal
        )
        strict.estimated_output_temperatures(using p)
    )

// TODO: retrieve 45°C from standard / engine. Do not hardcode it here. UI should not have knowledge of this.
lazy val tChimneyWallToOutAbove45_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNel(
        strict =>
            val p = strict.runValidationAtParams
            strict
                .validateChimneyWallTempIsAbove45DegreesCelsius()(using p)
                .map(_ => true)
                .asVNelString
        ,
        default = false
    )

lazy val results_en15544_t_chimney_wall_top
    : Signal[VNelString[t_chimney_wall_top]] =
    results_en15544_strict_sig.mapVNelString(strict =>
        val p = (
            DraftCondition.DraftMinOrPositivePressureMax,
            LoadQty.givens.nominal
        )
        strict.t_chimney_wall_top(using p)
    )

lazy val results_en15544_efficiency: Signal[VNelString[η]] =
    results_en15544_strict_sig.mapVNelString(strict =>
        val p = (
            DraftCondition.DraftMinOrPositivePressureMax,
            LoadQty.givens.nominal
        )
        strict.η(using p)
    )

lazy val eff_and_min_eff
    : Signal[(VNelString[Percentage], VNelString[Option[Percentage]])] =
    results_en15544_efficiency
        .combineWith(results_en15544_emissions_and_efficiency_values)
        .map((eff, eev) => (eff, eev.map(_.min_efficiency_full_stove_nominal)))

lazy val effInRange_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNel(
        strict =>
            val p = strict.runValidationAtParams
            strict
                .validateEfficiencyIsAboveMinEfficiency()(using p)
                .map(_ => true)
                .asVNelString
        ,
        false
    )

lazy val results_en15544_emissions_and_efficiency_values
    : Signal[VNelString[EmissionsAndEfficiencyValues]] =
    results_en15544_strict_sig.mapVNelString(_.emissions_and_efficiency_values)

def makeQuadrionSubtotalForSingle(
    outputsSig: Signal[VNelString[Outputs]]
)(
    toPipeResult: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Valid(outputs) =>
            outputs.pipesResult_15544.map(toPipeResult) match
                case Validated.Valid(pres) =>
                    Some(
                        QuadrionSubtotal(
                            ph = pres.ph.value.some,
                            pr = (-1.0 * pres.pR.value).some,
                            pu =
                                (pres.pu.map(pu => (-1.0 * pu).value)).toOption,
                            sigma = pres.`ph-(pR+pu)`.map(_.value).toOption
                        )
                    )
                case _                     => None
        case _                        => None

def makeQuadrionSubtotalForFirebox(
    outputsSig: Signal[VNelString[Outputs]]
)(
    to_cc_intlair_pres: PipesResult_15544 => PipeResult,
    to_cc_firebox_pres: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Invalid(_)   => 
            None
        case Validated.Valid(outputs) =>
            val cc_intlair_pres =
                outputs.pipesResult_15544.map(to_cc_intlair_pres)
            val cc_firebox_pres =
                outputs.pipesResult_15544.map(to_cc_firebox_pres)
            (cc_intlair_pres, cc_firebox_pres) match
                case (Valid(cc_intlair_pres), Valid(cc_firebox_pres)) =>
                    Some(
                        QuadrionSubtotal(
                            ph =
                                (cc_intlair_pres.ph + cc_firebox_pres.ph).value.some,
                            pr =
                                (cc_intlair_pres.pR + cc_firebox_pres.pR).value.some,
                            pu = (cc_intlair_pres.pu, cc_firebox_pres.pu)
                                .mapN((l, r) => (l + r).value)
                                .toOption,
                            sigma = (
                                cc_intlair_pres.`ph-(pR+pu)`,
                                cc_firebox_pres.`ph-(pR+pu)`
                            ).mapN((l, r) => (l + r).value).toOption
                        )
                    )
                case (l, r)                                           =>
                    None

// lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

lazy val errorBusConsole = new EventBus[(String, VNelString[?])]

// expert mode

val expertModeVar = Var[Boolean](false)
val expertModeOn  = expertModeVar.signal
val expertModeOff = expertModeOn.map(!_)

// contraints / error validation for firebox

val citedConstraintsValidation_sig: Signal[VNelString[CitedConstraints]] =
    results_en15544_strict_sig.mapVNelString(_.citedConstraints)

// pour récupérer les erreurs de type AngleN2 missing etc...
val air_intake_pipe_vnel2_signal = results_en15544_air_intake_pipe.map:
    p_vnel => p_vnel.andThen(p => p.`ph-(pR+pu)`.asVNelString)

// pressure final calc ok ?
val firebox_vnel2_signal =
    results_en15544_combustion_air_pipe
        .combineWith(results_en15544_firebox_pipe)
        .map((vp1, vp2) =>
            vp1.map(_.`ph-(pR+pu)`).andThen(_ => vp2.map(_.`ph-(pR+pu)`))
        )

// injectors air velocity ok ?
val firebox_vnel3_signal = results_en15544_strict_sig.map(_.andThen(strict =>
    import cats.syntax.validated.catsSyntaxValidatedId
    given Locale = localeVar.now()
    given p: strict.Params_15544 = strict.Params_15544.DraftMin_LoadNominal
    strict.validate_injectors_air_velocity.toOption
        .map(f => f.leftMap(_.map(_.show)))
        .getOrElse(().validNel)
))

val en13384_P_L_sig: Signal[VNelString[P_L]] =
    results_en13384_sig.map(_.map(_.P_L))

// Build a Signal saying when all conditions / constraints are met
// TODO: add EN 13384 conditions ?

lazy val all_conditions_and_results_satisfied_sig: Signal[Boolean] =
    en15544_strict_validate_results

lazy val all_conditions_and_results_not_satisfied_sig =
    all_conditions_and_results_satisfied_sig.map(!_)
