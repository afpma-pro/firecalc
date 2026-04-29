/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.time.LocalDate

import afpma.firecalc.invoices.FireCalcInvoiceFactory
import afpma.firecalc.invoices.models.*

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class InvoicePdfGenerationServiceSpec extends AnyFunSuite:

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    // Minimal YAML with one BankTransfer method, Net30 terms
    private val minimalYaml =
        """
          |invoice:
          |  invoiceNumber: "TEST-001"
          |  invoiceDate: "2026-01-01"
          |  dueDate: "2026-01-31"
          |  reference: "-"
          |  sender:
          |    name: "Test Company"
          |    displayName: "Test Company"
          |    address:
          |      street: "1 rue Test"
          |      city: "Paris"
          |      postalCode: "75001"
          |      region: "Ile-de-France"
          |      country: "France"
          |    vatNumber: "FR00000000000"
          |    registrationNumber: "RNA W000000000"
          |    email: "test@example.com"
          |    phone: "+33 1 00 00 00 00"
          |    website: "www.example.com"
          |    logo: !!null
          |  recipient:
          |    name: "Client Corp"
          |    displayName: !!null
          |    address:
          |      street: "2 rue Client"
          |      city: "Lyon"
          |      postalCode: "69001"
          |      region: "Auvergne-Rhone-Alpes"
          |      country: "France"
          |    vatNumber: "-"
          |    registrationNumber: "-"
          |    email: "client@example.com"
          |    phone: "+33 4 00 00 00 00"
          |    website: "www.client.com"
          |    logo: !!null
          |  billTo: !!null
          |  lineItems:
          |    - description: "Service"
          |      quantity: 1
          |      unitPrice: 89.00
          |      taxRate: 0.0
          |      discountPercentage: !!null
          |      unit: "u"
          |      productCode: !!null
          |  paymentTerms:
          |    dueDays: 30
          |    description: "Net 30"
          |    lateFeePercentage: 1.5
          |    discountPercentage: !!null
          |    discountDays: !!null
          |    methods:
          |      - BankTransfer:
          |          iban: "FR7612345678901234567890123"
          |          bic: "BNPAFRPP"
          |    notes: ""
          |  currency: "EUR"
          |  notes: "Test invoice"
          |  discountPercentage: !!null
          |  status: "Draft"
          |template:
          |  logoPosition: "top-left"
          |  primaryColor: "#2563eb"
          |  fontFamily: "Liberation Sans"
          |""".stripMargin

    private val factory: FireCalcInvoiceFactory =
        FireCalcInvoiceFactory
            .fromConfigYaml(minimalYaml)
            .fold(
                err => throw new RuntimeException(s"Test factory init failed: $err"),
                identity
            )

    // Minimal stubs for domain objects needed by OrderCompletionContext
    private val testCustomerId = CustomerId(java.util.UUID.randomUUID())
    private val testProductId  = ProductId (java.util.UUID.randomUUID())
    private val testOrderId    = OrderId   (java.util.UUID.randomUUID())

    private def makeContext(
        paymentProvider: Option[PaymentProvider],
        paymentId      : Option[String]
    ): OrderCompletionContext =
        import afpma.firecalc.payments.shared.api.{BackendCompatibleLanguage, CustomerType}
        OrderCompletionContext          (
            order           = ProductOrder(
                id              = testOrderId,
                customerId      = testCustomerId,
                productId       = testProductId,
                amount          = BigDecimal("89.00"),
                currency        = Currency.EUR,
                status          = OrderStatus.Confirmed,
                paymentProvider = paymentProvider,
                paymentId       = paymentId,
                language        = BackendCompatibleLanguage.French,
                invoiceNumber   = Some("TEST-001"),
                createdAt       = java.time.Instant.now(),
                updatedAt       = java.time.Instant.now()
            ),
            customer        = Customer(
                id           = testCustomerId,
                email        = "client@example.com",
                customerType = CustomerType.Individual,
                language     = BackendCompatibleLanguage.French,
                givenName    = Some("Jean"),
                familyName   = Some("Dupont"),
                city         = Some("Lyon"),
                createdAt    = java.time.Instant.now(),
                updatedAt    = java.time.Instant.now()
            ),
            product         = Product(
                id          = testProductId,
                name        = "FireCalc Report",
                description = "PDF report",
                price       = BigDecimal("89.00"),
                currency    = Currency.EUR,
                active      = true
            ),
            productMetadata = None
        )

    // Stub PaymentService — configurable mandate result per test case
    private class StubPaymentService(mandateResult: IO[Option[MandateSnapshot]]) extends PaymentService[IO]:
        def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): IO[String]                             =
            IO.raiseError(new NotImplementedError("StubPaymentService.createPaymentLink"))
        def processWebhook(body: String, signature: String)                                    : IO[Either[String, WebhookEventStatus]] =
            IO.raiseError(new NotImplementedError("StubPaymentService.processWebhook"))
        def getMandateForPayment(paymentId: String)                                            : IO[Option[MandateSnapshot]]            =
            mandateResult

    private def makeService(stub: StubPaymentService): InvoicePdfGenerationServiceImpl[IO] =
        new InvoicePdfGenerationServiceImpl[IO](factory, stub)

    // Case 1: Order with no paymentId → mandate lookup skipped, methods come from YAML only.
    test("order without paymentId — getMandateForPayment never called, methods = YAML methods only") {
        val stub    = new StubPaymentService(IO.raiseError(new NotImplementedError("should not be called")))
        val service = makeService(stub)
        val context = makeContext(paymentProvider = None, paymentId = None)

        val params = service.buildInvoiceParams(context).unsafeRunSync()

        assert(params.paymentTerms.methods.size == 1                                    )
        assert(params.paymentTerms.methods.head.isInstanceOf[PaymentMethod.BankTransfer])
    }

    // Case 2: paymentId present, full mandate snapshot → SepaMandate prepended, total 2 methods.
    test("paymentId + full mandate — SepaMandate prepended, all three fields populated") {
        val snap    = MandateSnapshot(
            reference              = Some("MD000ABC123"),
            createdDate            = Some(LocalDate.of(2026, 4, 15)),
            nextPossibleChargeDate = Some(LocalDate.of(2026, 5, 2))
        )
        val stub    = new StubPaymentService(IO.pure(Some(snap)))
        val service = makeService(stub)
        val context = makeContext(paymentProvider = Some(PaymentProvider.GoCardless), paymentId = Some("PM123"))

        val params = service.buildInvoiceParams(context).unsafeRunSync()

        assert(params.paymentTerms.methods.size == 2)
        params.paymentTerms.methods.head match
            case PaymentMethod.SepaMandate(ref, date, _, nextCharge) =>
                assert(ref == Some("MD000ABC123")                   )
                assert(date == Some      (LocalDate.of(2026, 4, 15)))
                assert(nextCharge == Some(LocalDate.of(2026, 5, 2) ))
            case other                                               =>
                fail(s"Expected SepaMandate at head, got: $other")
    }

    // Case 3: paymentId + IO.pure(None) → provider reports "no mandate for this payment".
    // Terms are left untouched (no SEPA, no pending placeholder — e.g. a card-only flow).
    test("paymentId + IO.pure(None) — no mandate surfaced, terms unchanged") {
        val stub    = new StubPaymentService(IO.pure(None))
        val service = makeService(stub)
        val context = makeContext(paymentProvider = Some(PaymentProvider.GoCardless), paymentId = Some("PM456"))

        val params = service.buildInvoiceParams(context).unsafeRunSync()

        assert(params.paymentTerms.methods.size == 1                                    )
        assert(params.paymentTerms.methods.head.isInstanceOf[PaymentMethod.BankTransfer])
    }

    // Case 4: paymentId + IO.raiseError → pending SepaMandate rendered, no exception thrown.
    test("paymentId + IO.raiseError — pending SepaMandate rendered, no exception thrown") {
        val stub    = new StubPaymentService(IO.raiseError(new RuntimeException("GC API down")))
        val service = makeService(stub)
        val context = makeContext(paymentProvider = Some(PaymentProvider.GoCardless), paymentId = Some("PM789"))

        val params = service.buildInvoiceParams(context).unsafeRunSync()

        assert(params.paymentTerms.methods.size == 2)
        params.paymentTerms.methods.head match
            case PaymentMethod.SepaMandate(None, None, None, None) => // expected
            case other                                             => fail(s"Expected pending SepaMandate(all None) at head, got: $other")
    }

    // Case 5: paymentId + partial mandate (one field None) → pending SepaMandate rendered.
    test("paymentId + partial mandate — pending SepaMandate rendered, warning logged") {
        val partial = MandateSnapshot(
            reference              = Some("MD000XYZ"),
            createdDate            = Some(LocalDate.of(2026, 4, 15)),
            nextPossibleChargeDate = None // missing field triggers pending path
        )
        val stub    = new StubPaymentService(IO.pure(Some(partial)))
        val service = makeService(stub)
        val context = makeContext(paymentProvider = Some(PaymentProvider.GoCardless), paymentId = Some("PM999"))

        val params = service.buildInvoiceParams(context).unsafeRunSync()

        assert(params.paymentTerms.methods.size == 2)
        params.paymentTerms.methods.head match
            case PaymentMethod.SepaMandate(None, None, None, None) => // expected (pending placeholder)
            case other                                             => fail(s"Expected pending SepaMandate(all None) at head, got: $other")
    }
