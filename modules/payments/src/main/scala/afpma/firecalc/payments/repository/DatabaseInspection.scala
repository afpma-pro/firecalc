/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.repository.impl.*

import cats.effect.Async

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger

/**
 * Facts about the whole database FILE, not about one table: they were carried by
 * `InvoiceCounterRepository` only because the boot read first needed them there,
 * but the invoice counter does not own the journal mode or the activity
 * timestamps of the file it lives in. This is that small persistence port.
 */
trait DatabaseInspection[F[_]] {

    /** The live `PRAGMA journal_mode` of the opened database. */
    def readJournalMode(): F[String]

    /**
     * Raw timestamp strings from every table that carries an activity timestamp:
     * Customer, ProductOrder and InvoiceCounter (createdAt + updatedAt) plus
     * ProductMetadata.createdAt and PurchaseIntent.createdAt. PurchaseIntent.expiresAt
     * is deliberately excluded: a future deadline is not past activity. Parsing and
     * comparison are left to the caller because SQLite orders ISO-8601 strings wrongly
     * when fractional-second precision differs.
     */
    def rawActivityTimestamps(): F[List[String]]
}

object DatabaseInspection:
    def create[F[_]: Async: Logger](using conn: Conn): F[DatabaseInspection[F]] =
        Async[F].pure(new MoleculeDatabaseInspection[F])
