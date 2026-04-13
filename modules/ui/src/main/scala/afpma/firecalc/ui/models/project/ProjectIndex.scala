/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.models.schema.LocalStorageKeys

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
        org.scalajs.dom.window.localStorage.setItem(LocalStorageKeys.PROJECTS_INDEX, json)

    def addEntry(entry: ProjectEntry): Unit =
        save(load() :+ entry)

    def removeEntry(id: ProjectId): Unit =
        save(load().filterNot(_.id == id))

    def updateEntry(id: ProjectId, f: ProjectEntry => ProjectEntry): Unit =
        save(load().map(e => if e.id == id then f(e) else e))

    def findEntry(id: ProjectId): Option[ProjectEntry] =
        load().find(_.id == id)
