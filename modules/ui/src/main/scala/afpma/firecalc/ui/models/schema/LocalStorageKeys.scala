/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

/**
 * Single source of truth for localStorage keys used in the application.
 *
 * Centralizing these keys ensures consistency across the codebase and
 * prevents typos or mismatches between read/write operations.
 */
object LocalStorageKeys {

    /**
     * Key for storing the application state schema (AppStateSchema_V1).
     *
     * This key is used by:
     * - Variables.scala: WebStorageVar definition
     * - SchemaMigrations.scala: Clear invalid data operation
     */
    val APP_STATE_SCHEMA: String = "app_state_schema"

    val CATALOG_STATE: String = "catalog_state"

    val VIZ_CAMERA_STATE: String = "viz_camera_state"

    val UI_STATE: String = "ui_state"

    val PROJECTS_INDEX: String = "projects_index"

    def projectAppState(projectId: String): String = s"project_${projectId}_app_state"

}
