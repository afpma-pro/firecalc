/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import cats.effect.{Async, Ref}
import cats.syntax.all.*
import cats.effect.std.Supervisor
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.exceptions.*
import java.util.UUID
import scala.util.Random
import org.typelevel.log4cats.Logger

class OrderServiceImpl[F[_]: Async](
  orderRepo: OrderRepository[F],
  productRepo: ProductRepository[F],
  customerRepo: CustomerRepository[F],
  productMetadataRepo: ProductMetadataRepository[F]
)(implicit logger: Logger[F]) extends OrderService[F]:

  // Thread-safe storage for transition-based completion callbacks
  private val transitionCallbacksRef: Ref[F, List[TransitionCallback[F]]] = 
    Ref.unsafe(List.empty[TransitionCallback[F]])
  
  // Thread-safe storage for final state callbacks
  private val finalStateCallbacksRef: Ref[F, List[FinalStateCallback[F]]] = 
    Ref.unsafe(List.empty[FinalStateCallback[F]])
  
  // Thread-safe storage to track which final state callbacks have been executed for each order
  private val executedFinalCallbacksRef: Ref[F, Map[OrderId, Set[String]]] = 
    Ref.unsafe(Map.empty[OrderId, Set[String]])

  def createOrder(
    customerId: CustomerId, 
    productId: ProductId, 
    amount: BigDecimal, 
    language: BackendCompatibleLanguage, 
    productMetadataId: Option[Long]
  ): F[ProductOrder] =
    for
      _ <- logger.info(s"Creating order for customer: ${customerId.value}")
      productOpt <- productRepo.findById(productId)
      product <- productOpt.liftTo[F](ProductNotFoundException(productId.value.toString))
      order <- orderRepo.create(customerId, productId, amount, product.currency, language, productMetadataId)
      _ <- logger.info(s"Order created: ${order.id}")
    yield order

  def findOrder(orderId: OrderId): F[Option[ProductOrder]] =
    orderRepo.findById(orderId)

  def findProduct(orderId: OrderId): F[Option[Product]] = 
    for
        order <- findOrder(orderId).flatMap { orderOpt => 
            Async[F].fromOption(orderOpt, OrderNotFoundException(orderId.value.toString))
        }
        product <- productRepo.findById(order.productId)
    yield product

  def updateOrderStatus(orderId: OrderId, status: OrderStatus): F[Boolean] =
    for
      oldOrderOpt <- findOrder(orderId)
      result <- orderRepo.updateStatus(orderId, status)
      _ <- (oldOrderOpt, result) match
        case (Some(oldOrder), true) if oldOrder.status != status =>
          // Order status changed, trigger both transition and final state callbacks
          val transition = OrderStateTransition(oldOrder.status, status)
          for
            updatedOrderOpt <- findOrder(orderId)
            _ <- updatedOrderOpt match
              case Some(updatedOrder) => 
                for
                  _ <- executeTransitionCallbacks(transition, updatedOrder)
                  _ <- executeFinalStateCallbacks(updatedOrder)
                yield ()
              case None => logger.warn(s"Could not find order ${orderId} after status update")
          yield ()
        case _ => Async[F].unit
    yield result

  def updatePaymentId(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): F[Boolean] =
    orderRepo.updatePaymentId(orderId, paymentId, paymentProvider)

  def updatePaymentIdIfNeededAndPresent(orderId: OrderId, paymentId: Option[String], paymentProvider: PaymentProvider): F[Boolean] =
    paymentId match
        case None      => Async[F].pure(false)
        case Some(pId) =>
            for orderOpt  <- findOrder(orderId)
                result    <- orderOpt match
                    case Some(order) => 
                        order.paymentId match
                            case Some(existingId) =>
                                if (existingId == pId) Async[F].pure(true)
                                else Async[F].raiseError(PaymentIdMismatchException(orderId.value.toString, existingId, pId))
                            case None =>
                                updatePaymentId(orderId, pId, paymentProvider)
                    case None =>
                        Async[F].pure(false)
            yield result

  // Transition-based callback registration methods
  def registerCallbackForTransitions(name: String, transitions: Set[OrderStateTransition], callback: OrderCompletionCallback[F]): F[Unit] =
    transitionCallbacksRef.update(_ :+ TransitionCallback(name, transitions, callback))

  def registerCallbackForTransition(name: String, transition: OrderStateTransition, callback: OrderCompletionCallback[F]): F[Unit] =
    registerCallbackForTransitions(name, Set(transition), callback)

  def registerCallbackForStatusChangeTo(name: String, toStatuses: Set[OrderStatus], callback: OrderCompletionCallback[F]): F[Unit] =
    // Create transitions from any status to the target statuses
    val transitions = for {
      fromStatus <- OrderStatus.values.toSet
      toStatus <- toStatuses
      if fromStatus != toStatus
    } yield OrderStateTransition(fromStatus, toStatus)
    registerCallbackForTransitions(name, transitions, callback)

  // Final state callback registration methods
  def registerCallbackForFinalStatus(name: String, finalStatus: OrderStatus, callback: OrderCompletionCallback[F]): F[Unit] =
    finalStateCallbacksRef.update(_ :+ FinalStateCallback(name, finalStatus, callback))

  def registerCallbackForFinalStatuses(name: String, finalStatuses: Set[OrderStatus], callback: OrderCompletionCallback[F]): F[Unit] =
    // Register a callback for each final status
    finalStatuses.toList.traverse_ { status =>
      registerCallbackForFinalStatus(s"$name-$status", status, callback)
    }

//   def registerInvoiceNumberGenerationCallback(invoiceNumberService: InvoiceNumberService[F]): F[Unit] = {
//     val invoiceCallback: OrderCompletionCallback[F] = (context: OrderCompletionContext) =>
//       // Only generate invoice number if it's not already set
//       if (context.order.invoiceNumber.isEmpty) {
//         invoiceNumberService.generateInvoiceNumberForOrder(context.order.id)
//           .handleErrorWith { error =>
//             logger.error(s"Invoice number generation failed for order ${context.order.id}: ${error.getMessage}")
//             // Don't re-throw - allow order status change to succeed
//           }
//       } else {
//         Async[F].unit
//       }
      
//     // Register when status switches to 'Processing'
//     registerCallbackForFinalStatus("invoice-number-generation", OrderStatus.Processing, invoiceCallback)
//   }

  // Private methods for callback execution
  private def executeTransitionCallbacks(transition: OrderStateTransition, order: ProductOrder): F[Unit] =
    for
      transitionCallbacks <- transitionCallbacksRef.get
      context <- buildCompletionContext(order)
      triggeredCallbacks = transitionCallbacks.filter(_.triggers.contains(transition))
      _ <- logger.info(s"Executing ${triggeredCallbacks.size} callbacks for transition ${transition.from} -> ${transition.to} on order ${order.id}")
      _ <- triggeredCallbacks.traverse_ { transitionCallback =>
        Async[F].start(
          (for
            _ <- logger.info(s"Executing transition callback: ${transitionCallback.name} for order ${order.id}")
            _ <- transitionCallback.callback(context)
            _ <- logger.info(s"Transition callback '${transitionCallback.name}' executed successfully for order ${order.id}")
          yield ()).handleErrorWith(error =>
            logger.error(s"Transition callback '${transitionCallback.name}' failed for order ${order.id}: ${error.getMessage}")
          )
        )
      }
    yield ()

  private def executeFinalStateCallbacks(order: ProductOrder): F[Unit] =
    for
      finalStateCallbacks <- finalStateCallbacksRef.get
      executedCallbacks <- executedFinalCallbacksRef.get
      context <- buildCompletionContext(order)
      // Find callbacks that match the current order status
      matchingCallbacks = finalStateCallbacks.filter(_.finalStatus == order.status)
      // Filter out callbacks that have already been executed for this order
      alreadyExecuted = executedCallbacks.getOrElse(order.id, Set.empty)
      pendingCallbacks = matchingCallbacks.filterNot(callback => alreadyExecuted.contains(callback.name))
      _ <- logger.info(s"Executing ${pendingCallbacks.size} final state callbacks for status ${order.status} on order ${order.id}")
      _ <- pendingCallbacks.traverse_ { finalCallback =>
        Async[F].start(
          (for
            _ <- logger.info(s"Executing final state callback: ${finalCallback.name} for order ${order.id}")
            _ <- finalCallback.callback(context)
            _ <- logger.info(s"Final state callback '${finalCallback.name}' executed successfully for order ${order.id}")
            // Mark this callback as executed for this order
            _ <- executedFinalCallbacksRef.update { executed =>
              val orderCallbacks = executed.getOrElse(order.id, Set.empty)
              executed.updated(order.id, orderCallbacks + finalCallback.name)
            }
          yield ()).handleErrorWith(error =>
            logger.error(s"Final state callback '${finalCallback.name}' failed for order ${order.id}: ${error.getMessage}")
          )
        )
      }
    yield ()

  private def buildCompletionContext(order: ProductOrder): F[OrderCompletionContext] =
    for
      customer          <- customerRepo
        .findById(order.customerId)
        .flatMap(_.liftTo[F](CustomerNotFoundException(order.customerId.value)))
      product           <- productRepo
        .findById(order.productId)
        .flatMap(_.liftTo[F](ProductNotFoundException(order.productId.value.toString)))
      productMetadata   <- order.productMetadataId.traverse(id => productMetadataRepo.findById(id)).map(_.flatten)
    yield OrderCompletionContext(order, customer, product, productMetadata)
