/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.schema.LocalStorageKeys
import scala.scalajs.js

object ProjectIndex:

    import ProjectEntry.given // Circe codecs

    def exists: Boolean =
        org.scalajs.dom.window.localStorage.getItem(LocalStorageKeys.PROJECTS_INDEX) != null

    def load(): Vector[ProjectEntry] =
        val raw = org.scalajs.dom.window.localStorage.getItem(LocalStorageKeys.PROJECTS_INDEX)
        if raw == null || raw.trim.isEmpty then Vector.empty
        else
            io.circe.parser.decode[Vector[ProjectEntry]](raw) match
                case Right(entries) => entries
                case Left(err)      =>
                    org.scalajs.dom.console.error(s"Failed to decode projects index: ${err.getMessage}")
                    Vector.empty

    def save(entries: Vector[ProjectEntry]): Unit =
        val json = io.circe.Encoder[Vector[ProjectEntry]].apply(entries).noSpaces
        try org.scalajs.dom.window.localStorage.setItem(LocalStorageKeys.PROJECTS_INDEX, json)
        catch
            case ex: js.JavaScriptException if LocalStorageUtils.isQuotaExceeded(ex) =>
                val (usage, perKey) = LocalStorageUtils.estimateStorageUsage()
                afpma.firecalc.ui.components.StorageWarningDialog.show(usage, perKey)
            case ex: js.JavaScriptException                                          =>
                org.scalajs.dom.console
                    .error(s"[ProjectIndex] Failed to save index — localStorage unavailable: ${ex.getMessage}")

    /**
     * Appends an index entry. The entry — and its stored name — is built here,
     * from the live project `reference` verbatim (possibly empty): no caller
     * ever hands in a name, so no localized placeholder can enter storage
     * (see the projection contract on [[syncNameOnSave]]).
     */
    def addEntry(id: ProjectId, reference: String, createdAt: Double, lastModified: Double): Unit =
        save(load() :+ ProjectEntry(id, reference, lastModified = lastModified, createdAt = createdAt))

    def removeEntry(id: ProjectId): Unit =
        save(load().filterNot(_.id == id))

    private def updateEntry(id: ProjectId, f: ProjectEntry => ProjectEntry): Unit =
        save(load().map(e => if e.id == id then f(e) else e))

    /**
     * The index entry's `name` is a strictly derived projection of the state's
     * project reference: it is written ONLY from a live reference, verbatim —
     * never a localized placeholder (a stored "Untitled"/"Sans titre" freezes
     * one language into storage and goes stale on the next locale change).
     * The localized fallback for an empty name belongs to display time, see
     * [[ProjectEntry.displayName]].
     *
     * A save re-projects the name AND stamps the modification time.
     */
    def syncNameOnSave(id: ProjectId, reference: String): Unit =
        updateEntry(id, _.copy(name = reference, lastModified = scala.scalajs.js.Date.now()))

    /** Opening a project re-projects the name WITHOUT touching the modification time. */
    def syncNameOnOpen(id: ProjectId, reference: String): Unit =
        updateEntry(id, _.copy(name = reference))

    def findEntry(id: ProjectId): Option[ProjectEntry] =
        load().find(_.id == id)
