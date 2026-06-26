/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchema

import com.raquo.airstream.state.Var

object ProjectManager:

    /** The currently active project ID. None = no project loaded (selector page). */
    val activeProjectIdVar: Var[Option[ProjectId]] = Var(None)

    /**
     * Switch to a different project.
     * 1. Saves the current project (if any)
     * 2. Loads the new project from localStorage
     * 3. Sets appStateSchemaVar
     * 4. Resets the undo manager
     *
     * @return true if project was loaded, false if not found
     */
    def switchToProject(id: ProjectId): Boolean =
        // No-op if already on this project
        if activeProjectIdVar.now().contains(id) then return true

        // Save current project first
        saveCurrentProject()

        // Load and install the new project
        restoreProjectState(id)

    /**
     * Check whether a project exists in storage without loading it.
     *
     * Used by Frontend.renderPage to decide whether to render HomeView or redirect to
     * the selector, without actually installing the project into appStateSchemaVar —
     * that installation is deferred to onMountCallback to avoid clobbering the still-
     * mounted previous page's reactive subtree.
     */
    def projectExists(id: ProjectId): Boolean =
        ProjectStorage.load(id).isDefined

    /**
     * Install the given project's persisted state into the in-memory vars
     * (appStateSchemaVar, fireboxCacheStateVar, activeProjectIdVar), reset the undo
     * manager, and return true iff the project was found.
     *
     * Unlike switchToProject, this does NOT save the current project first and does NOT
     * check whether the target id is already active. It is intended to be called either
     * from switchToProject (post-save) or from Frontend.main()'s pre-render preload, so
     * that the reactive graph sees the restored state on its very first access.
     */
    def restoreProjectState(id: ProjectId): Boolean =
        import afpma.firecalc.ui.models.{appStateSchemaVar, fireboxCacheStateVar, undoManager}

        ProjectStorage.load(id) match
            case Some(schema) =>
                undoManager.withRestoring {
                    appStateSchemaVar.set(schema)
                }
                undoManager.reset()
                val cachedFirebox = loadFireboxCache(id)
                fireboxCacheStateVar.set(cachedFirebox)
                activeProjectIdVar.set  (Some(id)     )
                true
            case None         =>
                org.scalajs.dom.console.error(s"Project ${id.value} not found in localStorage")
                false

    /** Save the current project's state to localStorage. */
    def saveCurrentProject(): Unit =
        import afpma.firecalc.ui.models.{appStateSchemaVar, fireboxCacheStateVar}

        activeProjectIdVar.now().foreach { id =>
            val schema = appStateSchemaVar.now()
            ProjectStorage.save(id, schema                    )
            saveFireboxCache   (id, fireboxCacheStateVar.now())
            // Update index metadata
            val name = schema.engine_state.project_description.reference
            ProjectIndex.updateEntry(
                id,
                _.copy        (
                    name         = if name.nonEmpty then name else "Sans titre",
                    lastModified = scala.scalajs.js.Date.now()
                )
            )
        }

    /** Create a new blank project, switch to it, and return its ID. */
    def createNewProject(): ProjectId =
        saveCurrentProject()
        val id     = generateProjectId()
        val schema = AppStateSchemaHelper.createInitialSchema()
        val now    = scala.scalajs.js.Date.now()
        ProjectStorage.save  (id, schema                                               )
        ProjectIndex.addEntry(ProjectEntry(id, "", lastModified = now, createdAt = now))
        switchToProject      (id                                                       )
        id

    /** Delete a project from storage and index. */
    def deleteProject(id: ProjectId): Unit =
        if activeProjectIdVar.now().contains(id) then
            import afpma.firecalc.ui.models.undoManager
            activeProjectIdVar.set(None)
            undoManager.reset     (    )
        ProjectStorage.delete                                  (id)
        org.scalajs.dom.window.localStorage.removeItem         (
            afpma.firecalc.ui.models.schema.LocalStorageKeys.projectFireboxCache(id.value)
        )
        ProjectIndex.removeEntry                               (id)
        // Allow the storage warning dialog to show again after freeing space
        afpma.firecalc.ui.components.StorageWarningDialog.reset(  )

    /** Create a new project from imported AppStateSchema (e.g., from file open). */
    def openFromFile(schema: AppStateSchema): ProjectId =
        saveCurrentProject()
        val id   = generateProjectId()
        val name = schema.engine_state.project_description.reference
        val now  = scala.scalajs.js.Date.now()
        ProjectStorage.save  (id, schema)
        ProjectIndex.addEntry(
            ProjectEntry(id, if name.nonEmpty then name else "Importé", lastModified = now, createdAt = now)
        )
        switchToProject      (id        )
        id

    def saveFireboxCache(id: ProjectId, cache: afpma.firecalc.ui.models.FireboxCacheState): Unit =
        import io.circe.Encoder
        import scala.scalajs.js
        val json = Encoder[afpma.firecalc.ui.models.FireboxCacheState].apply(cache).noSpaces
        try
            org.scalajs.dom.window.localStorage.setItem(
                afpma.firecalc.ui.models.schema.LocalStorageKeys.projectFireboxCache(id.value),
                json
            )
        catch
            case ex: js.JavaScriptException if LocalStorageUtils.isQuotaExceeded(ex) =>
                val (usage, perKey) = LocalStorageUtils.estimateStorageUsage()
                afpma.firecalc.ui.components.StorageWarningDialog.show(usage, perKey)
            case _ : js.JavaScriptException                                          =>
                org.scalajs.dom.console.error(
                    s"[ProjectManager] Failed to save firebox cache ${id.value} — localStorage unavailable"
                )

    private def loadFireboxCache(id: ProjectId): afpma.firecalc.ui.models.FireboxCacheState =
        val key = afpma.firecalc.ui.models.schema.LocalStorageKeys.projectFireboxCache(id.value)
        val raw = org.scalajs.dom.window.localStorage.getItem(key)
        if raw == null || raw.trim.isEmpty then
            // Migration: fall back to legacy global key on first load
            val globalRaw = org.scalajs.dom.window.localStorage.getItem(
                afpma.firecalc.ui.models.schema.LocalStorageKeys.FIREBOX_CACHE
            )
            if globalRaw == null || globalRaw.trim.isEmpty then afpma.firecalc.ui.models.FireboxCacheState.empty
            else
                io.circe.parser.decode[afpma.firecalc.ui.models.FireboxCacheState](globalRaw) match
                    case Right(state) => state
                    case Left(_)      => afpma.firecalc.ui.models.FireboxCacheState.empty
        else
            io.circe.parser.decode[afpma.firecalc.ui.models.FireboxCacheState](raw) match
                case Right(state) => state
                case Left(_)      => afpma.firecalc.ui.models.FireboxCacheState.empty
