/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.common.FireCalc_Version.<

import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.AppStateSchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys

import com.raquo.airstream.state.Var
import com.raquo.airstream.web.WebStorageVar

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
    Var[AppStateSchema](AppStateSchemaHelper.createInitialSchema())

// ============================================================================
// ZOOMED VARS FROM UNIFIED SCHEMA
// ============================================================================

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
