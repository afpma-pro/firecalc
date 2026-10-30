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
     * @return true if project was loaded, false if it is absent or its stored data is unreadable
     */
    def switchToProject(id: ProjectId): Boolean =
        // No-op if already on this project
        if activeProjectIdVar.now().contains(id) then return true

        // Save current project first
        saveCurrentProject()

        // Load and install the new project
        restoreProjectState(id)

    /**
     * Read a project's persisted state as a three-way outcome (see [[ProjectLoadOutcome]])
     * without installing it.
     *
     * Used by Frontend.renderPage to decide what to render: `Ok` opens the project,
     * `Missing` redirects quietly to the selector with a translated transient message,
     * `Corrupt` opens the application-error dialog first (the bytes are still there —
     * deleting the card is the recovery). The installation into appStateSchemaVar stays
     * deferred to onMountCallback to avoid clobbering the still-mounted previous page's
     * reactive subtree.
     *
     * Reading here and again from restoreProjectState on mount costs ONE decode: see
     * [[ProjectDecodeMemo]].
     */
    def projectOutcome(id: ProjectId): ProjectLoadOutcome =
        ProjectStorage.loadOutcome(id)

    /**
     * Install the given project's persisted state into the in-memory vars
     * (appStateSchemaVar, fireboxCacheStateVar, activeProjectIdVar), reset the undo
     * manager, and return true iff readable persisted state was found. Absent and
     * present-but-unreadable both return false; only the caller that renders the page
     * tells the user which of the two it was (see projectOutcome).
     *
     * The persisted `locale` field is stripped on install: it is project content
     * (it governs the exported `.fcalc`), never a UI-string authority. The installed
     * schema keeps the locale the URL resolved (the route render writes it into the
     * root Var before any node is constructed), so restoring can never clobber the
     * show-time channel.
     * Saving that schema back persists the current URL's locale (round-trip).
     *
     * Unlike switchToProject, this does NOT save the current project first and does NOT
     * check whether the target id is already active. It is intended to be called either
     * from switchToProject (post-save) or from Frontend.main()'s pre-render preload, so
     * that the reactive graph sees the restored state on its very first access.
     */
    def restoreProjectState(id: ProjectId): Boolean =
        import afpma.firecalc.ui.models.{appStateSchemaVar, currentLocale, fireboxCacheStateVar, undoManager}

        ProjectStorage.loadOutcome(id) match
            case ProjectLoadOutcome.Ok(loaded) =>
                val schema = loaded.copy(engine_state = loaded.engine_state.copy(locale = currentLocale))
                undoManager.withRestoring {
                    appStateSchemaVar.set(schema)
                }
                undoManager.reset()
                // Re-project the index name from the state's reference on OPEN, not
                // just on save: the card must show the same name the state carries,
                // without requiring a fresh save (see ProjectIndex.syncNameOnOpen).
                ProjectIndex.syncNameOnOpen(id, schema.engine_state.project_description.reference)
                val cachedFirebox = loadFireboxCache(id)
                fireboxCacheStateVar.set(cachedFirebox)
                activeProjectIdVar.set  (Some(id)     )
                true
            case ProjectLoadOutcome.Corrupt    =>
                // Distinct from "not found": the payload is still in storage and only a
                // delete (projects list) removes it. renderPage is what tells the user.
                org.scalajs.dom.console.error(s"Project ${id.value} is stored but unreadable — not installed")
                false
            case ProjectLoadOutcome.Missing    =>
                org.scalajs.dom.console.error(s"Project ${id.value} not found in localStorage")
                false

    /** Save the current project's state to localStorage. */
    def saveCurrentProject(): Unit =
        import afpma.firecalc.ui.models.{appStateSchemaVar, fireboxCacheStateVar}

        activeProjectIdVar.now().foreach { id =>
            val schema = appStateSchemaVar.now()
            ProjectStorage.save        (id, schema                                           )
            saveFireboxCache           (id, fireboxCacheStateVar.now()                       )
            // Update index metadata: the name is re-projected verbatim from the
            // state's live reference (see ProjectIndex.syncNameOnSave).
            ProjectIndex.syncNameOnSave(id, schema.engine_state.project_description.reference)
        }

    /** Create a new blank project, switch to it, and return its ID. */
    def createNewProject(): ProjectId =
        saveCurrentProject()
        val id     = generateProjectId()
        val schema = AppStateSchemaHelper.createInitialSchema()
        val now    = scala.scalajs.js.Date.now()
        ProjectStorage.save  (id, schema                                 )
        ProjectIndex.addEntry(id, "", createdAt = now, lastModified = now)
        switchToProject      (id                                         )
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
        val id        = generateProjectId()
        val reference = schema.engine_state.project_description.reference
        val now       = scala.scalajs.js.Date.now()
        ProjectStorage.save  (id, schema                                        )
        // The raw reference goes into the index verbatim — possibly empty; the
        // localized "Untitled" fallback belongs to display time only.
        ProjectIndex.addEntry(id, reference, createdAt = now, lastModified = now)
        switchToProject      (id                                                )
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
