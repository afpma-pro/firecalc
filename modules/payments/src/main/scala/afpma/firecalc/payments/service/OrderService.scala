/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import cats.effect.IO
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.*
import cats.effect.Async
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.email.*
import org.typelevel.log4cats.Logger
import org.http4s.client.Client
import afpma.firecalc.payments.service.impl.*
import afpma.firecalc.payments.shared.api.*

// Callback context containing all relevant entities for order completion
case class OrderCompletionContext(
  order: ProductOrder,
  customer: Customer,
  product: Product,
  productMetadata: Option[ProductMetadata]
)

// State transition representation
case class OrderStateTransition(from: OrderStatus, to: OrderStatus)

// Type alias for order completion callbacks
type OrderCompletionCallback[F[_]] = OrderCompletionContext => F[Unit]

// Callback registration with transition triggers
case class TransitionCallback[F[_]](
  name: String,
  triggers: Set[OrderStateTransition],
  callback: OrderCompletionCallback[F]
)

// Final state callback - triggers only once when reaching a specific final status
case class FinalStateCallback[F[_]](
  name: String,
  finalStatus: OrderStatus,
  callback: OrderCompletionCallback[F]
)

trait OrderService[F[_]]:
  def createOrder(customerId: CustomerId, productId: ProductId, amount: BigDecimal, language: BackendCompatibleLanguage, productMetadataId: Option[Long]): F[ProductOrder]
  def findOrder(orderId: OrderId): F[Option[ProductOrder]]
  def findProduct(orderId: OrderId): F[Option[Product]]
  def updateOrderStatus(orderId: OrderId, status: OrderStatus): F[Boolean]
  def updatePaymentId(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): F[Boolean]
  def updatePaymentIdIfNeededAndPresent(orderId: OrderId, paymentId: Option[String], paymentProvider: PaymentProvider): F[Boolean]
  
  // Transition-based callback registration methods
  def registerCallbackForTransitions(name: String, transitions: Set[OrderStateTransition], callback: OrderCompletionCallback[F]): F[Unit]
  def registerCallbackForTransition(name: String, transition: OrderStateTransition, callback: OrderCompletionCallback[F]): F[Unit]
  def registerCallbackForStatusChangeTo(name: String, toStatuses: Set[OrderStatus], callback: OrderCompletionCallback[F]): F[Unit]
  
  // Final state callback registration methods
  def registerCallbackForFinalStatus(name: String, finalStatus: OrderStatus, callback: OrderCompletionCallback[F]): F[Unit]
  def registerCallbackForFinalStatuses(name: String, finalStatuses: Set[OrderStatus], callback: OrderCompletionCallback[F]): F[Unit]
  
  // Convenience methods for common scenarios
//   def registerInvoiceNumberGenerationCallback(invoiceNumberService: InvoiceNumberService[F]): F[Unit]

object OrderService:
  def create[F[_]: Async](
    orderRepo: OrderRepository[F],
    productRepo: ProductRepository[F],
    customerRepo: CustomerRepository[F],
    productMetadataRepo: ProductMetadataRepository[F]
  )(implicit logger: Logger[F]): F[OrderService[F]] =
    Async[F].pure(new OrderServiceImpl[F](orderRepo, productRepo, customerRepo, productMetadataRepo))
