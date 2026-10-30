/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import io.circe.*
import io.circe.generic.semiauto.*
import io.taig.babel.Locale
import upickle.default.*

opaque type ProjectId = String

object ProjectId:
    def apply(s: String): ProjectId = s

    extension (id: ProjectId) def value: String = id

    given Encoder[ProjectId]    = Encoder.encodeString
    given Decoder[ProjectId]    = Decoder.decodeString
    given ReadWriter[ProjectId] = readwriter[String].bimap[ProjectId](_.value, ProjectId.apply)

case class ProjectEntry(id: ProjectId, name: String, lastModified: Double, createdAt: Double)

object ProjectEntry:
    given Encoder[ProjectEntry] = deriveEncoder
    given Decoder[ProjectEntry] = deriveDecoder

    /**
     * The ONLY projection from a stored index name to user-visible text: the name as
     * stored when empty renders the localized "Untitled" fallback of the CURRENT
     * display locale. The stored name is a raw projection of the project reference
     * (see [[ProjectIndex.syncNameOnSave]] / [[ProjectIndex.syncNameOnOpen]]), never a
     * localized placeholder, so no language is ever frozen into storage. Every surface
     * that shows a project name (selector card title, delete confirmation, error
     * dialogs) goes through here, so they can never disagree.
     *
     * MUST stay an extension `def` resolving its `given Locale` at the display site:
     * an object-scope `val` would freeze the boot locale (Scala.js initialisation).
     */
    extension (entry: ProjectEntry)
        def displayName(using Locale): String =
            if entry.name.nonEmpty then entry.name else I18N_UI.project_selector.no_name

def generateProjectId(): ProjectId =
    val ts   = java.lang.Long.toString(System.currentTimeMillis(), 36)
    val rand = java.lang.Integer.toString(scala.util.Random.nextInt(0xffff), 36)
    ProjectId(s"${ts}_${rand}")
