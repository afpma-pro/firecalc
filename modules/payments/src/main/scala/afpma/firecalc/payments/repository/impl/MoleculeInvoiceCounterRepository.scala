/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import java.time.Instant

import afpma.firecalc.payments.repository.InvoiceCounterRepository
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*

import cats.effect.Async
import cats.syntax.all.*

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import molecule.db.sqlite.sync.*
import org.typelevel.log4cats.Logger

class MoleculeInvoiceCounterRepository[F[_]](using
    A   : Async[F],
    L   : Logger[F],
    conn: Conn,
    ec  : ExecutionContext
) extends InvoiceCounterRepository[F] {

    val logger = L

    def getNextInvoiceNumber(): F[Long] =
        A.blocking {
            // Atomically increment and return the new value in a single SQL statement.
            // This prevents race conditions where concurrent callers could read the same
            // counter value after separate UPDATE + SELECT operations.
            val now    = Instant.now().toString
            val result = rawQuery(
                s"UPDATE InvoiceCounter SET currentNumber = currentNumber + 1, updatedAt = '$now' RETURNING currentNumber"
            )
            result.headOption.flatMap(_.headOption) match {
                case Some(value: Long) => value
                case Some(value: Int)  => value.toLong
                case Some(other)       => other.toString.toLong
                case None              =>
                    throw new IllegalStateException("Invoice counter not initialized")
            }
        }.handleErrorWith { error =>
            logger.error(s"Failed to get next invoice number: ${error.getMessage}") *>
                A.raiseError(error)
        }

    def initializeCounter(startingNumber: Long): F[Boolean] =
        A.blocking {
            val now = Instant.now()
            // Check if counter already exists
            InvoiceCounter.id.currentNumber_.query.get.headOption match {
                case Some(_) =>
                    // Counter already exists, don't reinitialize
                    false
                case None    =>
                    // Create new counter starting at startingNumber - 1 so first increment gives startingNumber
                    InvoiceCounter
                        .currentNumber(startingNumber - 1)
                        .startingNumber(startingNumber)
                        .updatedAt(now)
                        .createdAt(now)
                        .save
                        .transact
                    true
            }
        }.handleErrorWith { error =>
            logger.error(s"Failed to initialize counter: ${error.getMessage}") *>
                A.pure(false)
        }

    def getCurrentCounter(): F[Option[Long]] =
        A.blocking {
            InvoiceCounter.currentNumber.query.get.headOption
        }
}
