/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository.impl

import cats.effect.IO
import cats.syntax.all.*
import afpma.firecalc.payments.domain
import afpma.firecalc.payments.shared.api
import afpma.firecalc.payments.shared.api.{ProductId, CustomerInfo, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2}
import afpma.firecalc.payments.repository.*
import CustomerSyntax.{toDomainCustomer}
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import afpma.firecalc.payments.utils.*
import java.time.Instant
import java.util.UUID
import org.typelevel.log4cats.Logger
import molecule.db.sqlite.async.*
import cats.effect.kernel.Async
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import io.circe.syntax.*
import io.circe.parser.*

class MoleculeOrderRepository[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext) extends OrderRepository[F]:
  
  val logger = Logger[F]

  def create(
    customerId: domain.CustomerId, 
    productId: ProductId, 
    amount: BigDecimal, 
    currency: domain.Currency,
    language: BackendCompatibleLanguage, 
    productMetadataId: Option[Long]
  ): F[domain.ProductOrder] =
    for
      _ <- logger.info(s"Creating order for customer: ${customerId}, product: ${productId}")
      now <- Async[F].delay(Instant.now())
      orderId = UUID.randomUUID()
      paymentProvider: Option[domain.PaymentProvider] = None
      paymentId: Option[String] = None
      
      // Create ProductOrder with optional ProductMetadata reference
      _ <- productMetadataId match {
        case Some(productMetadataId) =>
          future2AsyncF {
            ProductOrder.orderId.customerId.productId.amount.currency.status.paymentProvider.paymentId.language.productMetadata.createdAt.updatedAt
              .insert(
                (orderId, customerId.value, productId.value, amount, currency.toString, "Pending", paymentProvider.map(_.toString).getOrElse(""), paymentId.getOrElse(""), language.code, productMetadataId, now, now)
              )
              .transact
          }
        case None =>
          future2AsyncF {
            ProductOrder.orderId.customerId.productId.amount.currency.status.paymentProvider.paymentId.language.createdAt.updatedAt
              .insert(
                (orderId, customerId.value, productId.value, amount, currency.toString, "Pending", paymentProvider.map(_.toString).getOrElse(""), paymentId.getOrElse(""), language.code, now, now)
              )
              .transact
          }
      }
      
      order = domain.ProductOrder(
        id = api.OrderId(orderId), 
        customerId = customerId, 
        productId = productId, 
        amount = amount, 
        currency = currency,
        status = domain.OrderStatus.Pending, 
        paymentProvider = paymentProvider, 
        paymentId = paymentId, 
        language = language, 
        invoiceNumber = None,
        productMetadataId = productMetadataId, 
        createdAt = now, 
        updatedAt = now
      )
      _ <- logger.info(s"Created order: ${order.id}")
    yield order

  def findById(id: api.OrderId): F[Option[domain.ProductOrder]] =
    for
      _ <- logger.debug(s"Finding order by id: ${id}")
      result <- future2AsyncF {
        ProductOrder.orderId(id.value).customerId.productId.amount.currency.status.paymentProvider.paymentId.language.createdAt.updatedAt
          .invoiceNumber_?
          .ProductMetadata.?(ProductMetadata.id)
          .query
          .get
          .map(_
            .headOption
            .map:
                case (orderId, customerId, productId, amount, currency, status, paymentProviderStr, paymentId, languageCode, createdAt, updatedAt, invoiceNumberOpt, productMetadataIdOpt) =>
                    domain.ProductOrder(
                        id = api.OrderId(orderId), 
                        customerId = domain.CustomerId(customerId),
                        productId = api.ProductId(productId),
                        amount = amount, 
                        currency = domain.Currency.valueOf(currency.toString),
                        status = domain.OrderStatus.valueOf(status),
                        paymentProvider = if paymentProviderStr.isEmpty then None else Some(domain.PaymentProvider.valueOf(paymentProviderStr)),
                        paymentId = if paymentId.isEmpty then None else Some(paymentId),
                        language = BackendCompatibleLanguage.fromCodeWithFallback(languageCode),
                        invoiceNumber = invoiceNumberOpt,
                        productMetadataId = productMetadataIdOpt,
                        createdAt = createdAt, 
                        updatedAt = updatedAt
                    )
          ) 
      }
    yield result

  def updateStatus(id: api.OrderId, status: domain.OrderStatus): F[Boolean] =
    for
      _ <- logger.info(s"Updating order ${id} status to $status")
      now <- Async[F].delay(Instant.now())
      internalId <- future2AsyncF {
        ProductOrder.id.orderId_(id.value).query.get.map(_.head)
      }
      result <- future2AsyncF {
        ProductOrder(internalId).status(OrderStatus.valueOf(status.toString)).updatedAt(now).update.transact
      }
      _ <- logger.info(s"Order status updated: $result")
    yield true

  def updatePaymentId(id: api.OrderId, paymentId: String, paymentProvider: domain.PaymentProvider): F[Boolean] =
    for
      _ <- logger.info(s"Updating order ${id.value} payment ID to $paymentId")
      now <- Async[F].delay(Instant.now())
      moleculePaymentProvider <- Try(PaymentProvider.valueOf(paymentProvider.toString)) match {
        case scala.util.Success(provider) => Async[F].pure(provider)
        case scala.util.Failure(ex) => Async[F].raiseError(new IllegalArgumentException(s"Invalid payment provider: ${paymentProvider.toString}", ex))
      }
      internalId <- future2AsyncF {
        ProductOrder.id.orderId_(id.value).query.get.map(_.head)
      }
      result <- future2AsyncF {
        ProductOrder(internalId).paymentId(paymentId).paymentProvider(moleculePaymentProvider).updatedAt(now).upsert.transact
      }
    yield true

  def updateInvoiceNumber(orderId: api.OrderId, invoiceNumber: String): F[Boolean] =
    for
      _ <- logger.info(s"Updating order ${orderId.value} invoice number to $invoiceNumber")
      now <- Async[F].delay(Instant.now())
      internalId <- future2AsyncF {
        ProductOrder.id.orderId_(orderId.value).query.get.map(_.head)
      }
      txReport <- future2AsyncF {
        ProductOrder(internalId).invoiceNumber(invoiceNumber).updatedAt(now).upsert.transact
      }
    yield txReport.id == internalId

  def findConfirmedOrdersWithoutInvoiceNumber(): F[List[domain.ProductOrder]] =
    for
      _ <- logger.debug("Finding confirmed orders without invoice numbers")
      result <- future2AsyncF {
        ProductOrder.orderId.customerId.productId.amount.currency.status_(OrderStatus.Confirmed).paymentProvider_?.paymentId_?.language
          .invoiceNumber_()  // tacit - ensures invoice number is null/empty
          .createdAt.updatedAt
          .ProductMetadata.?(ProductMetadata.id)
          .query
          .get
          .map(_.map {
            case (orderId, customerId, productId, amount, currency, paymentProviderStrOpt, paymentIdOpt, languageCode, createdAt, updatedAt, productMetadataIdOpt) =>
              domain.ProductOrder(
                id = api.OrderId(orderId),
                customerId = domain.CustomerId(customerId),
                productId = api.ProductId(productId),
                amount = amount,
                currency = domain.Currency.valueOf(currency.toString),
                status = domain.OrderStatus.Confirmed,
                paymentProvider = paymentProviderStrOpt match
                    case Some(paymentProviderStr) => 
                        if paymentProviderStr.isEmpty then None else Some(domain.PaymentProvider.valueOf(paymentProviderStr))
                    case None => None
                ,
                paymentId = paymentIdOpt match
                    case Some(paymentId) => if paymentId.isEmpty then None else Some(paymentId)
                    case None => None
                ,
                language = BackendCompatibleLanguage.fromCodeWithFallback(languageCode),
                invoiceNumber = None, // These are orders without invoice numbers
                productMetadataId = productMetadataIdOpt,
                createdAt = createdAt,
                updatedAt = updatedAt
              )
          })
      }
    yield result
