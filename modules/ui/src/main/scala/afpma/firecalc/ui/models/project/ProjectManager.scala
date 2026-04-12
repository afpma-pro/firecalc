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
        import afpma.firecalc.ui.models.{appStateSchemaVar, undoManager}

        // No-op if already on this project
        if activeProjectIdVar.now().contains(id) then return true

        // Save current project first
        saveCurrentProject()

        // Load the new project
        ProjectStorage.load(id) match
            case Some(schema) =>
                undoManager.withRestoring {
                    appStateSchemaVar.set(schema)
                }
                undoManager.reset()
                import afpma.firecalc.ui.models.fireboxCacheStateVar
                val cachedFirebox = loadFireboxCache(id)
                fireboxCacheStateVar.set(cachedFirebox)
                activeProjectIdVar.set(Some(id))
                true
            case None =>
                org.scalajs.dom.console.error(s"Project ${id.value} not found in localStorage")
                false

    /** Save the current project's state to localStorage. */
    def saveCurrentProject(): Unit =
        import afpma.firecalc.ui.models.{appStateSchemaVar, fireboxCacheStateVar}

        activeProjectIdVar.now().foreach { id =>
            val schema = appStateSchemaVar.now()
            ProjectStorage.save(id, schema)
            saveFireboxCache(id, fireboxCacheStateVar.now())
            // Update index metadata
            val name = schema.engine_state.project_description.reference
            ProjectIndex.updateEntry(id, _.copy(
                name = if name.nonEmpty then name else "Sans titre",
                lastModified = scala.scalajs.js.Date.now()
            ))
        }

    /** Create a new blank project, switch to it, and return its ID. */
    def createNewProject(): ProjectId =
        saveCurrentProject()
        val id      = generateProjectId()
        val schema  = AppStateSchemaHelper.createInitialSchema()
        val now     = scala.scalajs.js.Date.now()
        ProjectStorage.save(id, schema)
        ProjectIndex.addEntry(ProjectEntry(id, "", lastModified = now, createdAt = now))
        switchToProject(id)
        id

    /** Delete a project from storage and index. */
    def deleteProject(id: ProjectId): Unit =
        if activeProjectIdVar.now().contains(id) then
            import afpma.firecalc.ui.models.undoManager
            activeProjectIdVar.set(None)
            undoManager.reset()
        ProjectStorage.delete(id)
        org.scalajs.dom.window.localStorage.removeItem(
            afpma.firecalc.ui.models.schema.LocalStorageKeys.projectFireboxCache(id.value)
        )
        ProjectIndex.removeEntry(id)

    /** Create a new project from imported AppStateSchema (e.g., from file open). */
    def openFromFile(schema: AppStateSchema): ProjectId =
        saveCurrentProject()
        val id   = generateProjectId()
        val name = schema.engine_state.project_description.reference
        val now  = scala.scalajs.js.Date.now()
        ProjectStorage.save(id, schema)
        ProjectIndex.addEntry(ProjectEntry(id, if name.nonEmpty then name else "Importé", lastModified = now, createdAt = now))
        switchToProject(id)
        id

    def saveFireboxCache(id: ProjectId, cache: afpma.firecalc.ui.models.FireboxCacheState): Unit =
        import io.circe.Encoder
        val json = Encoder[afpma.firecalc.ui.models.FireboxCacheState].apply(cache).noSpaces
        org.scalajs.dom.window.localStorage.setItem(
            afpma.firecalc.ui.models.schema.LocalStorageKeys.projectFireboxCache(id.value),
            json
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
