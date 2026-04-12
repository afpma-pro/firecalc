/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.AppStateSchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys

object ProjectStorage:

    def load(id: ProjectId): Option[AppStateSchema] =
        val key = LocalStorageKeys.projectAppState(id.value)
        val raw = org.scalajs.dom.window.localStorage.getItem(key)
        if raw == null || raw.trim.isEmpty then None
        else
            val result = AppStateSchemaMigrations.migrateToLatest(raw)
            if result.isEmpty then
                org.scalajs.dom.console.error(s"Failed to load/migrate project ${id.value} — data may be corrupted")
            result

    def save(id: ProjectId, schema: AppStateSchema): Unit =
        AppStateSchemaHelper.encodeToYaml(schema) match
            case scala.util.Success(yaml) =>
                org.scalajs.dom.window.localStorage.setItem(
                    LocalStorageKeys.projectAppState(id.value),
                    yaml
                )
            case scala.util.Failure(e)    =>
                org.scalajs.dom.console.error(s"Failed to save project ${id.value}: ${e.getMessage}")

    def delete(id: ProjectId): Unit =
        org.scalajs.dom.window.localStorage.removeItem(
            LocalStorageKeys.projectAppState(id.value)
        )
