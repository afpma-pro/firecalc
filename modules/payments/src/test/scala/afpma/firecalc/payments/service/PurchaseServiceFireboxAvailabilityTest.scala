/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.Instant
import java.util.UUID

import afpma.firecalc.domain.FireboxAvailability
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

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

object PurchaseServiceFireboxAvailabilityTest extends TestSuite {

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

    private val ecolabeledFireboxYaml = b64(
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Ecolabeled:
          |    heat_output_reduced: null
          |    version:
          |      type: "either"
          |      left_or_right: "left"
          |      value: "Version 1"
          |    air_intake_shape: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_first_row_of_air_injectors:
          |      value: "0.1"
          |      unit: meter
          |    door_opening_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |    ash_pit_height:
          |      value: "0.05"
          |      unit: meter
          |    air_manifold_height:
          |      value: "0.05"
          |      unit: meter
          |    firebox_floor_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_inner_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_outer_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    air_column_thickness:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_sides:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_rear:
          |      value: "0.05"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R1:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R2:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R3:
          |      value: "0"
          |      unit: meter
          |    injector_height:
          |      value: "0"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin
    )

    private val traditionalFireboxYaml = b64(
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Traditional:
          |    heat_output_reduced: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_lowest_opening:
          |      value: "0.05"
          |      unit: meter
          |    pressure_loss_coefficient_from_door:
          |      value: "0.3"
          |      unit: "1"
          |    total_air_intake_surface_area_on_door:
          |      value: "0.0097"
          |      unit: "meter^2"
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin
    )

    val tests = Tests {

        test("disabled firebox raises FireboxTypeDisabledException") {
            // Ecolabeled disabled, Traditional enabled
            val fireboxAvailability = FireboxAvailability(
                traditional    = true,
                ecolabeled     = false,
                afpmaPrse      = true,
                singleTested   = true,
                door15aCatalog = true
            )

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
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = ecolabeledFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: FireboxTypeDisabledException) =>
                    assert(ex.errorCode == "firebox_type_disabled"   )
                    assert(ex.context("firebox_type") == "Ecolabeled")
                case Left(other)                            =>
                    throw new Exception(
                        s"Expected FireboxTypeDisabledException but got ${other.getClass}: ${other.getMessage}"
                    )
                case Right(_)                               =>
                    throw new Exception("Expected FireboxTypeDisabledException but got success")
            }
        }

        test("disabled firebox produces zero side effects") {
            val fireboxAvailability = FireboxAvailability(
                traditional    = true,
                ecolabeled     = false,
                afpmaPrse      = true,
                singleTested   = true,
                door15aCatalog = true
            )

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
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = ecolabeledFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            service.createPurchaseIntent(request).attempt.unsafeRunSync()

            // Verify zero side effects
            assert(repos.customers.isEmpty      ) // No customer creation
            assert(repos.purchaseIntents.isEmpty) // No purchase intent creation
            assert(repos.emailsSent.isEmpty     ) // No email sent
            assert(repos.authCodesGenerated == 0) // No auth code generated
            assert(repos.productMetadata.isEmpty) // No metadata stored
            assert(repos.authCodes.isEmpty      ) // No auth code stored
            assert(repos.paymentLinks.isEmpty   ) // No payment links
        }

        test("exception context carries the offending firebox type name") {
            val fireboxAvailability = FireboxAvailability(
                traditional    = true,
                ecolabeled     = false,
                afpmaPrse      = true,
                singleTested   = true,
                door15aCatalog = true
            )

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
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = ecolabeledFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: FireboxTypeDisabledException) =>
                    assert(ex.context("firebox_type") == "Ecolabeled")
                    assert(ex.context.size == 1                      )
                case Left(other)                            =>
                    throw new Exception(s"Expected FireboxTypeDisabledException but got ${other.getClass}")
                case Right(_)                               =>
                    throw new Exception("Expected FireboxTypeDisabledException but got success")
            }
        }

        test("allowed firebox continues existing flow unchanged") {
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
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = traditionalFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()
            assert(result.value != null)

            // Verify side effects occurred
            assert(repos.customers.nonEmpty      )
            assert(repos.purchaseIntents.nonEmpty)
            assert(repos.emailsSent.nonEmpty     )
            assert(repos.authCodesGenerated == 1 )
        }

        test("when productMetadata is absent, allowed firebox check is skipped") {
            val fireboxAvailability = FireboxAvailability(
                traditional    = false,
                ecolabeled     = false,
                afpmaPrse      = false,
                singleTested   = false,
                door15aCatalog = false
            )

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

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()
            assert(result.value != null)
        }

        test("when firebox cannot be decoded from metadata, availability check is skipped") {
            val fireboxAvailability = FireboxAvailability(
                traditional    = false,
                ecolabeled     = false,
                afpmaPrse      = false,
                singleTested   = false,
                door15aCatalog = false
            )

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
                filename = "test-document.pdf",
                mimeType = "application/pdf",
                content  = "aW52YWxpZCB5YW1s"
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()
            assert(result.value != null)
        }
    }
}
