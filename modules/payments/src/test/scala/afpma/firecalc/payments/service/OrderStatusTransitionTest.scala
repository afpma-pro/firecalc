/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.Instant
import java.util.UUID

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.impl.OrderServiceImpl
import afpma.firecalc.payments.shared.api.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

object OrderStatusTransitionTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    // Mutable state for in-memory order storage
    case class TestState(
        var orders: Map[OrderId, ProductOrder] = Map.empty
    )

    val testProduct = Product(
        id          = ProductId(UUID.randomUUID()),
        name        = "Test Product",
        description = "A test product",
        price       = BigDecimal("29.99"),
        currency    = Currency.EUR,
        active      = true
    )

    val testCustomer = Customer(
        id                = CustomerId(UUID.randomUUID()),
        email             = "test@example.com",
        customerType      = CustomerType.Individual,
        language          = BackendCompatibleLanguage.English,
        givenName         = Some("Test"),
        familyName        = Some("User"),
        companyName       = None,
        addressLine1      = None,
        addressLine2      = None,
        addressLine3      = None,
        city              = None,
        region            = None,
        postalCode        = None,
        countryCode       = None,
        phoneNumber       = None,
        paymentProviderId = None,
        paymentProvider   = None,
        createdAt         = Instant.now(),
        updatedAt         = Instant.now()
    )

    def mkOrder(status: OrderStatus): ProductOrder = ProductOrder(
        id                = OrderId(UUID.randomUUID()),
        customerId        = testCustomer.id,
        productId         = testProduct.id,
        amount            = testProduct.price,
        currency          = Currency.EUR,
        status            = status,
        paymentProvider   = None,
        paymentId         = None,
        language          = BackendCompatibleLanguage.English,
        invoiceNumber     = None,
        productMetadataId = None,
        createdAt         = Instant.now(),
        updatedAt         = Instant.now()
    )

    def createService(state: TestState): OrderServiceImpl[IO] = {
        val orderRepo = new OrderRepository[IO] {
            def create(
                customerId       : CustomerId,
                productId        : ProductId,
                amount           : BigDecimal,
                currency         : Currency,
                language         : BackendCompatibleLanguage,
                productMetadataId: Option[Long]
            ): IO[ProductOrder] = IO.raiseError(new NotImplementedError("not needed"))

            def findById(id: OrderId): IO[Option[ProductOrder]] =
                IO.pure(state.orders.get(id))

            def updateStatus(id: OrderId, status: OrderStatus): IO[Boolean] = IO.delay {
                state.orders.get(id) match {
                    case Some(order) =>
                        state.orders = state.orders.updated(id, order.copy(status = status, updatedAt = Instant.now()))
                        true
                    case None        => false
                }
            }

            def updatePaymentId(id: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Boolean] =
                IO.pure(true)

            def updateInvoiceNumber(id: OrderId, invoiceNumber: String): IO[Boolean] =
                IO.pure(true)

            def findConfirmedOrdersWithoutInvoiceNumber(): IO[List[ProductOrder]] =
                IO.pure(List.empty)
        }

        val productRepo = new ProductRepository[IO] {
            def findById(id: ProductId): IO[Option[Product]]         = IO.pure(Some(testProduct))
            def findOrCreate(name: String, description: String, price: BigDecimal, currency: Currency): IO[Product] =
                IO.raiseError(new NotImplementedError("not needed"))
            def create(id: ProductId, name: String, description: String, price: BigDecimal, currency: Currency, active: Boolean): IO[Product] =
                IO.raiseError(new NotImplementedError("not needed"))
            def update(id: ProductId, name: String, description: String, price: BigDecimal, currency: Currency, active: Boolean): IO[Product] =
                IO.raiseError(new NotImplementedError("not needed"))
            def upsert(productInfo: v1.ProductInfo): IO[Product] =
                IO.raiseError(new NotImplementedError("not needed"))
        }

        val customerRepo = new CustomerRepository[IO] {
            def findByEmail(email: String): IO[Option[Customer]]                                     = IO.pure(Some(testCustomer))
            def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): IO[Option[Customer]] = IO.pure(Some(testCustomer))
            def findById(id: CustomerId): IO[Option[Customer]]                                       = IO.pure(Some(testCustomer))
            def create(customerInfo: CustomerInfo): IO[Customer]                                     = IO.pure(testCustomer)
            def createFull(customer: Customer): IO[Boolean]                                          = IO.pure(true)
            def updatePaymentProvider(customerId: CustomerId, paymentProviderId: String, paymentProvider: PaymentProvider): IO[Boolean] =
                IO.pure(true)
        }

        val productMetadataRepo = new ProductMetadataRepository[IO] {
            def create(metadata: ProductMetadata): IO[Long]            = IO.pure(1L)
            def findById(id: Long): IO[Option[ProductMetadata]]        = IO.pure(None)
        }

        new OrderServiceImpl[IO](orderRepo, productRepo, customerRepo, productMetadataRepo)
    }

    val tests = Tests {

        test("valid transitions are accepted") {
            val validCases = List(
                (OrderStatus.Pending, OrderStatus.Processing),
                (OrderStatus.Pending, OrderStatus.Confirmed),
                (OrderStatus.Pending, OrderStatus.Failed),
                (OrderStatus.Pending, OrderStatus.Cancelled),
                (OrderStatus.Processing, OrderStatus.Confirmed),
                (OrderStatus.Processing, OrderStatus.Failed),
                (OrderStatus.Processing, OrderStatus.Cancelled),
                (OrderStatus.Confirmed, OrderStatus.PaidOut),
                (OrderStatus.Confirmed, OrderStatus.Failed)
            )

            validCases.foreach { case (from, to) =>
                val state   = TestState()
                val order   = mkOrder(from)
                state.orders = Map(order.id -> order)
                val service = createService(state)

                val result = service.updateOrderStatus(order.id, to).unsafeRunSync()
                Predef.assert(result, s"Expected transition $from -> $to to be accepted, but it was rejected")
                Predef.assert(
                    state.orders(order.id).status == to,
                    s"Expected order status to be $to after transition from $from, but got ${state.orders(order.id).status}"
                )
            }
        }

        test("terminal states reject all transitions") {
            val terminalStatuses  = List(OrderStatus.PaidOut, OrderStatus.Failed, OrderStatus.Cancelled)
            val allTargetStatuses = OrderStatus.values.toList

            terminalStatuses.foreach { terminal =>
                allTargetStatuses.filterNot(_ == terminal).foreach { target =>
                    val state   = TestState()
                    val order   = mkOrder(terminal)
                    state.orders = Map(order.id -> order)
                    val service = createService(state)

                    val result = service.updateOrderStatus(order.id, target).unsafeRunSync()
                    Predef.assert(!result, s"Expected transition $terminal -> $target to be rejected, but it was accepted")
                    Predef.assert(
                        state.orders(order.id).status == terminal,
                        s"Expected order to remain in $terminal, but status changed to ${state.orders(order.id).status}"
                    )
                }
            }
        }

        test("invalid non-terminal transitions are rejected") {
            val invalidCases = List(
                (OrderStatus.Pending, OrderStatus.PaidOut),
                (OrderStatus.Processing, OrderStatus.Pending),
                (OrderStatus.Processing, OrderStatus.PaidOut),
                (OrderStatus.Confirmed, OrderStatus.Pending),
                (OrderStatus.Confirmed, OrderStatus.Processing),
                (OrderStatus.Confirmed, OrderStatus.Cancelled)
            )

            invalidCases.foreach { case (from, to) =>
                val state   = TestState()
                val order   = mkOrder(from)
                state.orders = Map(order.id -> order)
                val service = createService(state)

                val result = service.updateOrderStatus(order.id, to).unsafeRunSync()
                Predef.assert(!result, s"Expected transition $from -> $to to be rejected, but it was accepted")
                Predef.assert(
                    state.orders(order.id).status == from,
                    s"Expected order to remain in $from, but status changed to ${state.orders(order.id).status}"
                )
            }
        }

        test("same-status update is a no-op") {
            OrderStatus.values.foreach { status =>
                val state   = TestState()
                val order   = mkOrder(status)
                state.orders = Map(order.id -> order)
                val service = createService(state)

                val result = service.updateOrderStatus(order.id, status).unsafeRunSync()
                // Should return false (no actual update performed)
                Predef.assert(!result, s"Expected same-status update $status -> $status to return false")
                Predef.assert(
                    state.orders(order.id).status == status,
                    s"Order status should remain $status"
                )
            }
        }

        test("update on missing order returns false") {
            val state   = TestState()
            val service = createService(state)
            val missingId = OrderId(UUID.randomUUID())

            // When order not found, repo.updateStatus returns false
            val result = service.updateOrderStatus(missingId, OrderStatus.Confirmed).unsafeRunSync()
            Predef.assert(!result, "Expected update on missing order to return false")
        }
    }
}
