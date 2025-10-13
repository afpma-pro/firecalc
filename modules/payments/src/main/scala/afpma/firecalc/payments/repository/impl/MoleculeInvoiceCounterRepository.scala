/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import cats.effect.Async
import cats.syntax.all.*
import afpma.firecalc.payments.repository.InvoiceCounterRepository
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import molecule.db.sqlite.sync.*
import org.typelevel.log4cats.Logger
import java.time.Instant
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext

class MoleculeInvoiceCounterRepository[F[_]](using 
    A: Async[F],
    L: Logger[F],
    conn: Conn,
    ec: ExecutionContext,
) extends InvoiceCounterRepository[F] {
  
  val logger = L

  def getNextInvoiceNumber(): F[Long] =
    A.blocking {
      // First try to get the counter ID 
      InvoiceCounter.id.currentNumber_.query.get.headOption match {
        case Some(counterId) =>
          // Atomically increment the counter using Molecule's +(1) operation
          InvoiceCounter(counterId).currentNumber.+(1).updatedAt(Instant.now()).update.transact
          // Get the updated value
          InvoiceCounter(counterId).currentNumber.query.get.head
        case None =>
          // No counter exists, throw error - should be initialized first
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
        case None =>
          // Create new counter starting at startingNumber - 1 so first increment gives startingNumber
          InvoiceCounter
            .currentNumber(startingNumber - 1)
            .startingNumber(startingNumber)
            .updatedAt(now)
            .createdAt(now)
            .save.transact
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
