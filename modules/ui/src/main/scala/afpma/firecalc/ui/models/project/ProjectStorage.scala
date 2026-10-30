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

    /** Keeps the load path to one decode per page-load — see [[ProjectDecodeMemo]]. */
    private val decodeMemo = new ProjectDecodeMemo

    /**
     * Read a project's persisted state as a three-way outcome (see [[ProjectLoadOutcome]]):
     * absent, present-but-undecodable, or decoded. Anything that decides what the USER
     * sees when a project does not open MUST go through this: the Option-shaped read
     * used to report corrupted data as "no such project", which made the silent bounce
     * of an unreadable project indistinguishable from a deleted one.
     *
     * Never mutates storage: a corrupt payload is left exactly as it is.
     */
    def loadOutcome(id: ProjectId): ProjectLoadOutcome =
        val raw = org.scalajs.dom.window.localStorage.getItem(LocalStorageKeys.projectAppState(id.value))
        decodeMemo.get(id, raw).getOrElse {
            val outcome = outcomeOfRaw(raw)
            outcome match
                case ProjectLoadOutcome.Missing =>
                    // Nothing stored: a null/blank payload can never match a remembered
                    // (id, raw) entry, so there is nothing to remember and nothing to clear.
                    ()
                case ProjectLoadOutcome.Corrupt =>
                    org.scalajs.dom.console.error(
                        s"Failed to load/migrate project ${id.value} — data may be corrupted, left untouched in localStorage"
                    )
                    decodeMemo.remember          (id, raw, outcome)
                case ok: ProjectLoadOutcome.Ok => decodeMemo.remember(id, raw, ok)
            outcome
        }

    /**
     * The pure mapping the load path branches on: the stored payload (`null`/blank means
     * the key is absent) → outcome, through the schema-migration chain. No storage
     * access, so the three cases are unit-testable (ProjectStorageSpec).
     */
    def outcomeOfRaw(raw: String): ProjectLoadOutcome =
        if raw == null || raw.trim.isEmpty then ProjectLoadOutcome.Missing
        else
            AppStateSchemaMigrations.migrateToLatest(raw) match
                case Some(schema) => ProjectLoadOutcome.Ok(schema)
                case None         => ProjectLoadOutcome.Corrupt

    def save(id: ProjectId, schema: AppStateSchema): Unit =
        // The payload changes either way (or the write fails and the previous payload
        // stands): in both cases no remembered outcome may outlive this call.
        decodeMemo.clear()
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
        decodeMemo.clear                              ()
        org.scalajs.dom.window.localStorage.removeItem(
            LocalStorageKeys.projectAppState(id.value)
        )
