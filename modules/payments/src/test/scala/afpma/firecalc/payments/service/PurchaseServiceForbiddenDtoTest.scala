/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.Instant
import java.util.UUID

import afpma.firecalc.domain.FireboxAvailability
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.{
    FlowOnlyPipeDescr_15544,
    PostFireboxPipeDescrSlot_V7,
    SetFlowOnlyPipeProp_15544,
    FireCalcYAML_V7
}
import afpma.firecalc.payments.GenerateExampleProjectFixture
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.email.{
    EmailAddress,
    EmailMessage,
    EmailResult,
    EmailSent,
    EmailService,
    AuthenticationCodeEmail,
    AdminNotification,
    InvoiceEmail,
    PaymentLinkEmail,
    PdfReportEmail,
    UserNotification
}
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.impl.PurchaseServiceImpl
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.units.coulombutils.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

/**
 * Early-rejection tests for backend-forbidden DTOs (`IsBackendForbidden`).
 *
 * Mirrors `PurchaseServiceFireboxAvailabilityTest`: the check runs in
 * `createPurchaseIntent` as the first business validation (zero side effects
 * before it). A project whose decoded DTO tree contains a forbidden variant
 * (here `SetInnerShapePreventSectionGeometryChangeAuto`) must raise
 * `ForbiddenDtoException` (errorCode `backend_forbidden_dto`) before any
 * repository write.
 *
 * The forbidden project is built programmatically (reusing
 * `GenerateExampleProjectFixture.exampleFireCalcYaml` as a valid V7 base) with
 * the forbidden variant injected into a FlueSlot, then encoded to YAML + base64
 * — the exact wire shape the backend receives.
 */
object PurchaseServiceForbiddenDtoTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    val testProduct = Product(
        id        = ProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        sku       = "test",
        price     = BigDecimal("29.99"),
        currency  = Currency.EUR,
        active    = true,
        taxRate   = BigDecimal("20.0"),
        taxExempt = false
    )

    val testProductCopyConfig: ProductCopyConfig = ProductCopyConfig(
        entries = Map(
            "test" -> Map(
                "default" -> ProductCopy(name = "Test Product", description = "Test product for unit tests")
            )
        )
    )

    val testCustomerInfo = CustomerInfo(
        email        = "test@example.com",
        phoneNumber  = None,
        customerType = CustomerType.Individual,
        language     = BackendCompatibleLanguage.English,
        givenName    = Some("John"),
        familyName   = Some("Doe"),
        companyName  = None,
        city         = Some("Paris"),
        addressLine1 = None,
        addressLine2 = None,
        addressLine3 = None,
        region       = None,
        postalCode   = None,
        countryCode  = Some(CountryCode_ISO_3166_1_ALPHA_2.FR)
    )

    class SideEffectTrackingRepos {
        var products       : Map[ProductId, Product]            = Map(testProduct.id -> testProduct)
        var customers      : Map[String, Customer]              = Map.empty
        var customersById  : Map[CustomerId, Customer]          = Map.empty
        var purchaseIntents: Map[PurchaseToken, PurchaseIntent] = Map.empty
        var productMetadata: Map[Long, ProductMetadata]         = Map.empty
        var nextMetadataId = 1L

        var emailsSent        : List[AuthenticationCodeEmail]             = List.empty
        var authCodes         : Map[String, String]                       = Map.empty
        var paymentLinks      : List[(OrderId, BigDecimal, CustomerInfo)] = List.empty
        var authCodesGenerated: Int                                       = 0
    }

    def createMockServices(repos: SideEffectTrackingRepos) = {
        val productRepo = new ProductRepository[IO] {
            def findById(id: ProductId): IO[Option[Product]] = IO.pure(repos.products.get(id))
            def findOrCreate(
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("findOrCreate not needed"))
            def create(
                id       : ProductId,
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                active   : Boolean,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("create not needed"))
            def update(
                id       : ProductId,
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                active   : Boolean,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("update not needed"))
            def upsert(productInfo: v1.ProductInfo): IO[Product] =
                IO.raiseError(new NotImplementedError("upsert not needed"))
        }

        val customerRepo = new CustomerRepository[IO] {
            def create(customerInfo: CustomerInfo)                                : IO[Customer]         = IO.delay {
                repos.customers = repos.customers + (customerInfo.email -> Customer(
                    id                = CustomerId(UUID.randomUUID()),
                    email             = customerInfo.email,
                    customerType      = customerInfo.customerType,
                    language          = customerInfo.language,
                    givenName         = customerInfo.givenName,
                    familyName        = customerInfo.familyName,
                    companyName       = customerInfo.companyName,
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
                ))
                repos.customers.find(_._1 == customerInfo.email).get._2
            }
            def findByEmail(email: String): IO[Option[Customer]] = IO.pure(repos.customers.get(email))
            def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): IO[Option[Customer]] =
                IO.pure(repos.customers.get(email))
            def findById  (customerId: CustomerId): IO[Option[Customer]] = IO.pure(None)
            def createFull(customer  : Customer  ): IO[Boolean]          = ???
            def updatePaymentProvider(
                customerId       : CustomerId,
                paymentProviderId: String,
                paymentProvider  : PaymentProvider
            ): IO[Boolean] = ???
        }

        val purchaseIntentRepo = new PurchaseIntentRepository[IO] {
            def create(
                productId        : ProductId,
                amount           : BigDecimal,
                currency         : Currency,
                authCode         : String,
                customerId       : CustomerId,
                productMetadataId: Option[Long]
            )                                    : IO[PurchaseIntent]         = IO.delay {
                val intent = PurchaseIntent(
                    token             = PurchaseToken(UUID.randomUUID()),
                    productId         = productId,
                    amount            = amount,
                    currency          = currency,
                    authCode          = authCode,
                    customerId        = customerId,
                    processed         = false,
                    productMetadataId = productMetadataId,
                    expiresAt         = Instant.now().plusSeconds(3600),
                    createdAt         = Instant.now()
                )
                repos.purchaseIntents = repos.purchaseIntents + (intent.token -> intent)
                intent
            }
            def createWithInternalCustomerId(
                productId         : ProductId,
                amount            : BigDecimal,
                currency          : Currency,
                authCode          : String,
                customerInternalId: Long,
                productMetadataId : Option[Long]
            )                                    : IO[PurchaseIntent]         = ???
            def findByToken(token: PurchaseToken): IO[Option[PurchaseIntent]] =
                IO.pure(repos.purchaseIntents.get(token))
            def findByTokenAndCode     (token: PurchaseToken, code: String ): IO[Option[PurchaseIntent]] = ???
            def atomicMarkAsProcessed  (token: PurchaseToken               ): IO[Boolean]                = IO.pure(true)
            def deleteExpired          (                                   ): IO[Int]                    = ???
            def incrementFailedAttempts(token: PurchaseToken               ): IO[Unit]                   = IO.unit
            def countRecentByEmail     (email: String, since      : Instant): IO[Int]                    = IO.pure(0)
        }

        val productMetadataRepo = new ProductMetadataRepository[IO] {
            def create(metadata: ProductMetadata): IO[Long] = IO.delay {
                val id = repos.nextMetadataId; repos.nextMetadataId += 1;
                repos.productMetadata = repos.productMetadata + (id -> metadata); id
            }
            def findById(id: Long): IO[Option[ProductMetadata]] = IO.pure(repos.productMetadata.get(id))
        }

        val authService = new AuthenticationService[IO] {
            def generateAuthCode(                      ): IO[String]             = IO.delay { repos.authCodesGenerated += 1; "123456" }
            def generateJWT     (customerId: CustomerId): IO[String]             = IO.pure(s"jwt-${customerId.value}")
            def validateJWT     (token     : String    ): IO[Option[CustomerId]] = ???
        }

        val orderService = new OrderService[IO] {
            def createOrder(
                customerId       : CustomerId,
                productId        : ProductId,
                amount           : BigDecimal,
                language         : BackendCompatibleLanguage,
                productMetadataId: Option[Long]
            ): IO[ProductOrder] = IO.raiseError(new NotImplementedError("should not be called"))
            def findOrder  (orderId: OrderId): IO[Option[ProductOrder]] = ???
            def findProduct(orderId: OrderId): IO[Option[Product]]      = ???
            def markOrderConfirmed(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Unit] =
                ???
            def markOrderFailed  (orderId: OrderId                     ): IO[Unit]             = ???
            def findCustomer     (orderId: OrderId                     ): IO[Option[Customer]] = ???
            def updateOrderStatus(orderId: OrderId, status: OrderStatus): IO[Boolean]          = ???
            def updatePaymentId(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Boolean] =
                ???
            def updatePaymentIdIfNeededAndPresent(
                orderId        : OrderId,
                paymentId      : Option[String],
                paymentProvider: PaymentProvider
            )                                                                                         : IO[Boolean] = ???
            def registerCallbackForStatusChangeTo(
                name      : String,
                toStatuses: Set[OrderStatus],
                callback  : OrderCompletionCallback[IO]
            )                                                                                         : IO[Unit]    = ???
            def registerCallbackForTransition(
                name      : String,
                transition: OrderStateTransition,
                callback  : OrderCompletionCallback[IO]
            )                                                                                         : IO[Unit]    = ???
            def registerCallbackForTransitions(
                name       : String,
                transitions: Set[OrderStateTransition],
                callback   : OrderCompletionCallback[IO]
            )                                                                                         : IO[Unit]    = ???
            def registerCallbackForFinalStatus(
                name       : String,
                finalStatus: OrderStatus,
                callback   : OrderCompletionCallback[IO]
            )                                                                                         : IO[Unit]    = ???
            def registerCallbackForFinalStatuses(
                name         : String,
                finalStatuses: Set[OrderStatus],
                callback     : OrderCompletionCallback[IO]
            )                                                                                         : IO[Unit]    = ???
        }

        val paymentService = new PaymentService[IO] {
            def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): IO[String] =
                IO.raiseError(new NotImplementedError("should not be called"))
            def processWebhookEvent(event: GoCardlessWebhookEvent): IO[Either[String, String]] = ???
            def sendPaymentLinkEmail(orderId: OrderId): IO[EmailResult] = ???
            def processWebhook(
                body     : String,
                signature: String
            ): IO[Either[String, afpma.firecalc.payments.service.impl.WebhookEventStatus]] = ???
            def getMandateForPayment(paymentId: String): IO[Option[afpma.firecalc.payments.domain.MandateSnapshot]] =
                ???
        }

        val emailService = new EmailService[IO] {
            def sendUserAuthenticationCode(
                authCode: AuthenticationCodeEmail
            )(using language: BackendCompatibleLanguage)                                          : IO[EmailResult] = IO.delay {
                repos.emailsSent = authCode :: repos.emailsSent; EmailSent
            }
            def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage) : IO[EmailResult] =
                IO.pure(EmailSent)
            def sendUserInvoiceWithReport(
                invoice  : InvoiceEmail,
                pdfReport: PdfReportEmail,
                bcc      : List[EmailAddress] = List.empty
            )(using language: BackendCompatibleLanguage)                                          : IO[EmailResult] = IO.pure(EmailSent)
            def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): IO[EmailResult] =
                IO.pure(EmailSent)
            def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using
                language: BackendCompatibleLanguage
            )                                                                                     : IO[EmailResult] = IO.pure(EmailSent)
            def sendUserPdfReport(pdfReport: PdfReportEmail)(using
                language: BackendCompatibleLanguage
            )                                                                                     : IO[EmailResult] = IO.pure(EmailSent)
            def sendAdminNotification(notification: AdminNotification): IO[EmailResult] = IO.pure(EmailSent)
            def sendUserNotification (notification: UserNotification ): IO[EmailResult] = IO.pure(EmailSent)
            def sendEmail            (message     : EmailMessage     ): IO[EmailResult] = IO.pure(EmailSent)
        }

        (
            productRepo,
            customerRepo,
            purchaseIntentRepo,
            productMetadataRepo,
            authService,
            orderService,
            paymentService,
            emailService
        )
    }

    import java.util.Base64

    private def b64(s: String): String =
        Base64.getEncoder.encodeToString(s.getBytes("UTF-8"))

    /** A valid V7 project with a `SetInnerShapePreventSectionGeometryChangeAuto` in the FlueSlot. */
    private lazy val forbiddenYaml: String =
        val base = GenerateExampleProjectFixture.exampleFireCalcYaml.asInstanceOf[FireCalcYAML_V7]
        // Inject the forbidden variant at the head of the FlueSlot's descr.
        val forbiddenOp: FlowOnlyPipeDescr_15544 =
            SetFlowOnlyPipeProp_15544.SetInnerShapePreventSectionGeometryChangeAuto(
                PipeShape.Circle(0.2.meters)
            )
        val patchedSlots = base.post_firebox_pipes.slots.map {
            case s @ PostFireboxPipeDescrSlot_V7.FlueSlot(descr) =>
                PostFireboxPipeDescrSlot_V7.FlueSlot(forbiddenOp +: descr)
            case other                                           => other
        }
        val patched = base.copy(post_firebox_pipes = base.post_firebox_pipes.copy(slots = patchedSlots))
        FireCalcYAMLMigrations.encodeToYamlTry(patched).get

    private lazy val forbiddenYamlB64: String = b64(forbiddenYaml)

    /** A valid V7 project WITHOUT any forbidden variant (regression / negative guard). */
    private lazy val cleanYamlB64: String =
        b64(GenerateExampleProjectFixture.generateYamlContent())

    val tests = Tests {

        test("forbidden DTO raises ForbiddenDtoException") {
            val fireboxAvailability = FireboxAvailability.AllEnabled

            val repos = new SideEffectTrackingRepos()
            val (
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService
            )         = createMockServices(repos)

            val service = new PurchaseServiceImpl[IO](
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService,
                testProductCopyConfig,
                fireboxAvailability
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "forbidden.fcalc",
                mimeType = "application/x-yaml",
                content  = forbiddenYamlB64
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: ForbiddenDtoException) =>
                    assert(ex.errorCode == "backend_forbidden_dto"                                  )
                    assert(ex.context("dto_type") == "SetInnerShapePreventSectionGeometryChangeAuto")
                    assert(ex.context.contains("element_index")                                     )
                    assert(ex.context.size == 2                                                     )
                case Left(other)                     =>
                    throw new Exception(
                        s"Expected ForbiddenDtoException but got ${other.getClass}: ${other.getMessage}"
                    )
                case Right(_)                        =>
                    throw new Exception("Expected ForbiddenDtoException but got success")
            }
        }

        test("forbidden DTO produces zero side effects") {
            val fireboxAvailability = FireboxAvailability.AllEnabled

            val repos = new SideEffectTrackingRepos()
            val (
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService
            )         = createMockServices(repos)

            val service = new PurchaseServiceImpl[IO](
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService,
                testProductCopyConfig,
                fireboxAvailability
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "forbidden.fcalc",
                mimeType = "application/x-yaml",
                content  = forbiddenYamlB64
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            service.createPurchaseIntent(request).attempt.unsafeRunSync()

            // Verify zero side effects — checked before any DB write.
            assert(repos.customers.isEmpty      ) // No customer creation
            assert(repos.purchaseIntents.isEmpty) // No purchase intent creation
            assert(repos.emailsSent.isEmpty     ) // No email sent
            assert(repos.authCodesGenerated == 0) // No auth code generated
            assert(repos.productMetadata.isEmpty) // No metadata stored
            assert(repos.authCodes.isEmpty      ) // No auth code stored
            assert(repos.paymentLinks.isEmpty   ) // No payment links
        }

        test("clean project without forbidden DTO continues the existing flow") {
            // Regression / negative guard: a project with only plain SetInnerShape
            // must NOT be rejected. We don't assert the full downstream success
            // (the example project may trigger other validations), only that the
            // ForbiddenDtoException is NOT raised.
            val fireboxAvailability = FireboxAvailability.AllEnabled

            val repos = new SideEffectTrackingRepos()
            val (
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService
            )         = createMockServices(repos)

            val service = new PurchaseServiceImpl[IO](
                productRepo,
                customerRepo,
                purchaseIntentRepo,
                productMetadataRepo,
                authService,
                orderService,
                paymentService,
                emailService,
                testProductCopyConfig,
                fireboxAvailability
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "clean.fcalc",
                mimeType = "application/x-yaml",
                content  = cleanYamlB64
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(_: ForbiddenDtoException) =>
                    throw new Exception("Clean project must not be rejected as forbidden")
                case _                              =>
                    assert(true) // any non-forbidden outcome is acceptable for this guard
            }
        }
    }
}
