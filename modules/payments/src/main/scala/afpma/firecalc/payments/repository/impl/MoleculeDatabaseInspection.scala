/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import afpma.firecalc.payments.repository.DatabaseInspection

import cats.effect.Async

import molecule.db.common.spi.Conn
import molecule.db.sqlite.sync.*

class MoleculeDatabaseInspection[F[_]](using
    A   : Async[F],
    conn: Conn
) extends DatabaseInspection[F] {

    def readJournalMode(): F[String] =
        A.blocking {
            rawQuery("PRAGMA journal_mode;").headOption.flatMap(_.headOption) match
                case Some(mode) => mode.toString
                case None       => "unknown"
        }

    def rawActivityTimestamps(): F[List[String]] =
        A.blocking {
            rawQuery(
                """SELECT createdAt FROM Customer      UNION ALL
                  |SELECT updatedAt FROM Customer      UNION ALL
                  |SELECT createdAt FROM ProductOrder  UNION ALL
                  |SELECT updatedAt FROM ProductOrder  UNION ALL
                  |SELECT createdAt FROM ProductMetadata UNION ALL
                  |SELECT createdAt FROM PurchaseIntent UNION ALL
                  |SELECT createdAt FROM InvoiceCounter UNION ALL
                  |SELECT updatedAt FROM InvoiceCounter""".stripMargin
            ).flatMap(_.headOption).collect { case s: String => s }
        }
}
