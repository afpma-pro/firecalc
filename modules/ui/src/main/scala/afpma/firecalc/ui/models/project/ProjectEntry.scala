/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import io.circe.*
import io.circe.generic.semiauto.*
import upickle.default.*

opaque type ProjectId = String

object ProjectId:
    def apply(s: String): ProjectId = s

    extension (id: ProjectId) def value: String = id

    given Encoder[ProjectId] = Encoder.encodeString
    given Decoder[ProjectId] = Decoder.decodeString
    given ReadWriter[ProjectId] = readwriter[String].bimap[ProjectId](_.value, ProjectId.apply)

case class ProjectEntry(id: ProjectId, name: String, lastModified: Double, createdAt: Double)

object ProjectEntry:
    given Encoder[ProjectEntry] = deriveEncoder
    given Decoder[ProjectEntry] = deriveDecoder

def generateProjectId(): ProjectId =
    val ts   = java.lang.Long.toString(System.currentTimeMillis(), 36)
    val rand = java.lang.Integer.toString(scala.util.Random.nextInt(0xFFFF), 36)
    ProjectId(s"${ts}_${rand}")
