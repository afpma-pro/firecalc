/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.schema.AppStateSchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys

object ProjectMigration:

    /**
     * Migrate from single-project to multi-project localStorage layout.
     *
     * Called once on app startup. Detects old "app_state_schema" key
     * and moves it to a per-project key.
     *
     * @return Some(projectId) if migration was performed (caller should redirect), None otherwise
     */
    def migrateIfNeeded(): Option[ProjectId] =
        val hasOldData = org.scalajs.dom.window.localStorage.getItem(LocalStorageKeys.APP_STATE_SCHEMA) != null
        val hasNewIndex = ProjectIndex.exists

        if hasOldData && !hasNewIndex then
            // Old single-project data exists, no multi-project index yet → migrate
            val raw = org.scalajs.dom.window.localStorage.getItem(LocalStorageKeys.APP_STATE_SCHEMA)
            AppStateSchemaMigrations.migrateToLatest(raw) match
                case Some(schema) =>
                    val id          = generateProjectId()
                    val name        = schema.engine_state.project_description.reference
                    val projectName = if name.nonEmpty then name else "Projet migré"
                    val now         = scala.scalajs.js.Date.now()
                    val entry       = ProjectEntry(id, projectName, lastModified = now, createdAt = now)

                    ProjectStorage.save(id, schema)
                    ProjectIndex.save(Vector(entry))

                    // Remove old key
                    org.scalajs.dom.window.localStorage.removeItem(LocalStorageKeys.APP_STATE_SCHEMA)

                    org.scalajs.dom.console.log(s"Migrated single-project data to project ${id.value} ('$projectName')")
                    Some(id)

                case None =>
                    // Corrupted data — start fresh
                    org.scalajs.dom.window.localStorage.removeItem(LocalStorageKeys.APP_STATE_SCHEMA)
                    ProjectIndex.save(Vector.empty)
                    org.scalajs.dom.console.warn("Old project data was corrupted, starting fresh")
                    None

        else if !hasNewIndex then
            // First-ever load with new code, no old data
            ProjectIndex.save(Vector.empty)
            None
        else
            // Already migrated
            None
