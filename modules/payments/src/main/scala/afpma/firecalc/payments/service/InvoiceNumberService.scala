/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import cats.effect.{Async, Temporal}
import cats.syntax.all.*
import afpma.firecalc.payments.shared.api.OrderId
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.config.{PaymentsConfig, InvoiceRetryConfig}
import afpma.firecalc.payments.invoice.InvoiceNumberValidator
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import afpma.firecalc.payments.exceptions.OrderUpdateFailedException

trait InvoiceNumberService[F[_]] {
  def generateInvoiceNumber(): F[String]
  def generateInvoiceNumberAndUpdateOrder(orderId: OrderId): F[String]
  def generateInvoiceNumbersRetroactively(): F[Int]
}

object InvoiceNumberService:
    def create[F[_]: Async: Logger](
        config: PaymentsConfig,
        invoiceCounterRepo: InvoiceCounterRepository[F],
        orderRepo: OrderRepository[F]
    )(using conn: Conn, ec: ExecutionContext): F[InvoiceNumberService[F]] =
      Async[F].pure(new InvoiceNumberServiceImpl[F](config, invoiceCounterRepo, orderRepo))

class InvoiceNumberServiceImpl[F[_]: Async: Temporal: Logger](
  config: PaymentsConfig,
  invoiceCounterRepo: InvoiceCounterRepository[F],
  orderRepo: OrderRepository[F]
) extends InvoiceNumberService[F] {
  
  val logger = Logger[F]
  
  def generateInvoiceNumber(): F[String] =
    retryWithExponentialBackoff(
      generateInvoiceNumberAttempt(),
      config.retryConfig
    )
  
  def generateInvoiceNumberAndUpdateOrder(orderId: OrderId): F[String] =
    for {
      _ <- logger.info(s"Generating next invoice number...")
      invoiceNumber <- generateInvoiceNumber()
      success <- orderRepo.updateInvoiceNumber(orderId, invoiceNumber)
      _ <- if (success) {
        logger.info(s"Generated invoice number $invoiceNumber for order ${orderId.value}")
      } else {
        val msg = s"Failed to update order ${orderId.value} with invoice number $invoiceNumber"
        logger.warn(msg) *>
        Async[F].raiseError(new OrderUpdateFailedException(msg))
      }
    } yield invoiceNumber
  
  def generateInvoiceNumbersRetroactively(): F[Int] =
    for {
      _ <- logger.info("Starting retroactive invoice number generation")
      confirmedOrdersWithoutInvoice <- orderRepo.findConfirmedOrdersWithoutInvoiceNumber()
      sortedOrders = confirmedOrdersWithoutInvoice.sortBy(_.updatedAt)
      _ <- logger.info(s"Found ${sortedOrders.length} confirmed orders without invoice numbers")
      generatedCount <- sortedOrders.traverse { order =>
        generateInvoiceNumberAndUpdateOrder(order.id).as(1).handleErrorWith { error =>
          logger.error(s"Failed to generate invoice for order ${order.id}: ${error.getMessage}").as(0)
        }
      }.map(_.sum)
      _ <- logger.info(s"Successfully generated $generatedCount invoice numbers retroactively")
    } yield generatedCount
  
  private def generateInvoiceNumberAttempt(): F[String] =
    for {
      nextNumber <- invoiceCounterRepo.getNextInvoiceNumber()
      invoiceNumber = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        config.invoiceNumberPrefix,
        nextNumber,
        config.invoiceNumberDigits,
        java.time.Instant.now(),
        config.invoiceTimezone
      )
    } yield invoiceNumber
  
  private def retryWithExponentialBackoff[A](
    operation: F[A],
    retryConfig: InvoiceRetryConfig,
    attempt: Int = 0
  ): F[A] =
    operation.handleErrorWith { error =>
      if (attempt < retryConfig.maxRetries) {
        val delay = math.min(
          retryConfig.baseDelayMs * math.pow(2, attempt).toLong,
          retryConfig.maxDelayMs
        ).millis
        
        logger.warn(s"Invoice generation attempt ${attempt + 1} failed, retrying in ${delay.toMillis}ms: ${error.getMessage}") *>
        Temporal[F].sleep(delay) *>
        retryWithExponentialBackoff(operation, retryConfig, attempt + 1)
      } else {
        logger.error(s"Invoice generation failed after ${retryConfig.maxRetries} attempts: ${error.getMessage}") *>
        Async[F].raiseError(error)
      }
    }
}
