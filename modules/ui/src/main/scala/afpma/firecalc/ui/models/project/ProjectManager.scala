/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import com.raquo.airstream.state.Var

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchema

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
                import afpma.firecalc.ui.models.{fireboxCacheStateVar, FireboxCacheState}
                fireboxCacheStateVar.set(FireboxCacheState.empty)
                activeProjectIdVar.set(Some(id))
                true
            case None =>
                org.scalajs.dom.console.error(s"Project ${id.value} not found in localStorage")
                false

    /** Save the current project's state to localStorage. */
    def saveCurrentProject(): Unit =
        import afpma.firecalc.ui.models.appStateSchemaVar

        activeProjectIdVar.now().foreach { id =>
            val schema = appStateSchemaVar.now()
            ProjectStorage.save(id, schema)
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
