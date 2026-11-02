/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.repository.impl.*

import cats.effect.Async

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger

trait InvoiceCounterRepository[F[_]] {
    def getNextInvoiceNumber(                    ): F[Long]
    def initializeCounter   (startingNumber: Long): F[Boolean]
    def getCurrentCounter   (                    ): F[Option[Long]]

    /**
     * Raise the single counter row's current number to newCurrent. This is the only
     * writer of the current number besides the mint itself, and the bounded boot repair
     * (InvoiceCounterRepair) calls it ONLY with a value already verified against the
     * issued invoice numbers. The startingNumber anchor is never rewritten.
     */
    def raiseCounterTo(newCurrent: Long): F[Unit]

    /** Every InvoiceCounter row, raw: zero, one or many rows and null columns all come back as data. */
    def allCounterRows(): F[List[CounterRow]]
}

object InvoiceCounterRepository:
    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[InvoiceCounterRepository[F]] =
        Async[F].pure(new MoleculeInvoiceCounterRepository[F])
