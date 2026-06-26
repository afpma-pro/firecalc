/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.AppStateSchemaMigrations
import afpma.firecalc.ui.models.schema.LocalStorageKeys
import afpma.firecalc.ui.components.StorageWarningDialog

import org.scalajs.dom
import scala.scalajs.js

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
                try
                    dom.window.localStorage.setItem(
                        LocalStorageKeys.projectAppState(id.value),
                        yaml
                    )
                catch
                    case ex: js.JavaScriptException if LocalStorageUtils.isQuotaExceeded(ex) =>
                        val (usage, perKey) = LocalStorageUtils.estimateStorageUsage()
                        StorageWarningDialog.show(usage, perKey)
                    case _ : js.JavaScriptException                                          =>
                        dom.console.error(
                            s"[ProjectStorage] Failed to save project ${id.value} — localStorage unavailable"
                        )
            case scala.util.Failure(e)    =>
                dom.console.error(s"Failed to save project ${id.value}: ${e.getMessage}")

    def delete(id: ProjectId): Unit =
        org.scalajs.dom.window.localStorage.removeItem(
            LocalStorageKeys.projectAppState(id.value)
        )
