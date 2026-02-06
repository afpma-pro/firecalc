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
}

object InvoiceCounterRepository:
    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[InvoiceCounterRepository[F]] =
        Async[F].pure(new MoleculeInvoiceCounterRepository[F])
