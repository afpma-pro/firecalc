/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.engine.api.FireCalcYAML_Loader
import afpma.firecalc.engine.models.geometry.{PipePositionResult, PositionTracker, Vec3}

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal

lazy val localeVar       = engineStateVar.zoomLazy(_.locale)((g, x) => g.copy(locale = x))
lazy val displayUnitsVar =
    engineStateVar.zoomLazy(_.display_units)((g, x) => g.copy(display_units = x))

lazy val project_descr_var = engineStateVar.zoomLazy(_.project_description)((ast, x) => ast.copy(project_description = x))

// filename derived var, never updates parent projet_descr_var
// depends on both project description and locale for i18n default name

lazy val filename_var = project_descr_var.zoomLazy(prj =>
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

lazy val local_conditions_var = engineStateVar.zoomLazy(_.local_conditions)((ast, x) => ast.copy(local_conditions = x))

lazy val z_geodetical_height_var = local_conditions_var.zoomLazy(_.altitude): (lc, z) =>
    lc.copy(altitude = z)

lazy val chimney_termination_var =
    local_conditions_var.zoomLazy(_.chimney_termination): (lc, x) =>
        lc.copy(chimney_termination = x)

lazy val chimney_location_on_roof_var =
    chimney_termination_var.zoomLazy(_.chimney_location_on_roof): (cos, x) =>
        cos.copy(chimney_location_on_roof = x)

lazy val adjacent_buildings_var =
    chimney_termination_var.zoomLazy(_.adjacent_buildings): (cos, x) =>
        cos.copy(adjacent_buildings = x)

// stove params
lazy val stove_params_var =
    engineStateVar.zoomLazy(_.stove_params)((ast, x) => ast.copy(stove_params = x))

lazy val air_intake_incrdescr_var =
    engineStateVar.zoomLazy(_.air_intake_descr)((g, x) => g.copy(air_intake_descr = x))

// EngineStateHelper

lazy val engineStateHelperVar =
    engineStateVar.zoomLazy(FireCalcYAML_Loader.apply)((engineState, _) => engineState)

lazy val air_intake_vnel_signal          = engineStateHelperVar.signal.map(_.airIntakePipe)
lazy val air_intake_mappings_vnel_signal =
    engineStateHelperVar.signal.map(_.airIntakePipeMappings)

// Firebox

lazy val firebox_var =
    engineStateVar.zoomLazy(_.firebox)((ast, x) => ast.copy(firebox = x))

// ── Air intake position tracking ─────────────────────────────────

lazy val airintake_positions_sig: Signal[PipePositionResult] =
    air_intake_incrdescr_var.signal.map: descr =>
        PositionTracker.computeFlowOnly13384(
            descr,
            externalFrame = None,
            startPoint = Vec3(0, 0, 0),
            finalPoint = Some(Vec3(0, 0, -1.0))
        )
    .distinct
