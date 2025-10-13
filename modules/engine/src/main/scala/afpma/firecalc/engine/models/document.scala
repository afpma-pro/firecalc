/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.*

case class Document(
    name: String,
    date: String,
    version: String,
    revision: String,
    status: String,
    author: String
)

object Document {
    given showDoc: Show[Document] = Show.show[Document] { d =>
        import d.*
        s"""
        |============================
        |DOCUMENT
        |============================
        |name: $name
        |date: $date
        |version: $version
        |revision: $revision
        |status: $status
        |author: $author
        |============================
        """.stripMargin
    }
}