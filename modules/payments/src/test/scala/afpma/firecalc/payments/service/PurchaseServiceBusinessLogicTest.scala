/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.Instant
import java.util.UUID

import afpma.firecalc.domain.FireboxAvailability
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.email.AdminNotification
import afpma.firecalc.payments.email.AuthenticationCodeEmail
import afpma.firecalc.payments.email.EmailAddress
import afpma.firecalc.payments.email.EmailMessage
import afpma.firecalc.payments.email.EmailResult
import afpma.firecalc.payments.email.EmailSent
import afpma.firecalc.payments.email.EmailService
import afpma.firecalc.payments.email.InvoiceEmail
import afpma.firecalc.payments.email.PaymentLinkEmail
import afpma.firecalc.payments.email.PdfReportEmail
import afpma.firecalc.payments.email.UserNotification
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.OrderCompletionCallback
import afpma.firecalc.payments.service.OrderStateTransition
import afpma.firecalc.payments.service.impl.PurchaseServiceImpl
import afpma.firecalc.payments.shared.api.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

object PurchaseServiceBusinessLogicTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    // Test data — product IDs match production catalog UUIDs so Sku.isLicenseFeeProduct resolves correctly
    val testProduct = Product(
        id        = ProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        sku       = "test",
        price     = BigDecimal("29.99"),
        currency  = Currency.EUR,
        active    = true,
        taxRate   = BigDecimal("20.0"),
        taxExempt = false
    )

    val testProductWithFee = Product(
        id        = ProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
        sku       = "test_WITH_FIREBOX_LICENSE_FEE",
        price     = BigDecimal("39.99"),
        currency  = Currency.EUR,
        active    = true,
        taxRate   = BigDecimal("20.0"),
        taxExempt = false
    )

    val testProductCopyConfig: ProductCopyConfig = ProductCopyConfig(
        entries = Map(
            "test"                                              -> Map(
                "default" -> ProductCopy(name = "Test Product", description = "Test product for unit tests")
            ),
            "test_WITH_FIREBOX_LICENSE_FEE"                     -> Map(
                "default" -> ProductCopy(name = "Test Product With Fee", description = "Test product for unit tests")
            ),
            "pdf_report_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE" -> Map(
                "default" -> ProductCopy(name = "Report With Fee", description = "Report with firebox license")
            ),
            "pdf_report_EN_15544_2023"                          -> Map(
                "default" -> ProductCopy(name = "Report", description = "Calculation report")
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

    val testCustomer = Customer(
        id                = CustomerId(UUID.randomUUID()),
        email             = testCustomerInfo.email,
        customerType      = testCustomerInfo.customerType,
        language          = testCustomerInfo.language,
        givenName         = testCustomerInfo.givenName,
        familyName        = testCustomerInfo.familyName,
        companyName       = testCustomerInfo.companyName,
        addressLine1      = testCustomerInfo.addressLine1,
        addressLine2      = testCustomerInfo.addressLine2,
        addressLine3      = testCustomerInfo.addressLine3,
        city              = testCustomerInfo.city,
        region            = testCustomerInfo.region,
        postalCode        = testCustomerInfo.postalCode,
        countryCode       = testCustomerInfo.countryCode,
        phoneNumber       = testCustomerInfo.phoneNumber,
        paymentProviderId = None,
        paymentProvider   = None,
        createdAt         = Instant.now(),
        updatedAt         = Instant.now()
    )

    class TestRepositories {
        var products       : Map[ProductId, Product]             = Map(
            testProduct.id        -> testProduct,
            testProductWithFee.id -> testProductWithFee
        )
        var customers      : Map[String, Customer]               = Map.empty
        var customersById  : Map[CustomerId, Customer]           = Map.empty
        var purchaseIntents: Map[PurchaseToken, PurchaseIntent]  = Map.empty
        var orders         : Map[CustomerId, List[ProductOrder]] = Map.empty
        var productMetadata: Map[Long, ProductMetadata]          = Map.empty
        var nextMetadataId = 1L

        var emailsSent  : List[AuthenticationCodeEmail]             = List.empty
        var authCodes   : Map[String, String]                       = Map.empty // token -> code
        var paymentLinks: List[(OrderId, BigDecimal, CustomerInfo)] = List.empty
    }

    def createMockServices(repos: TestRepositories) = {
        val productRepo = new ProductRepository[IO] {
            def findById(id: ProductId): IO[Option[Product]] = IO.pure(repos.products.get(id))
            def findOrCreate(
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("findOrCreate not needed in this test"))
            def create(
                id       : ProductId,
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                active   : Boolean,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("create not needed in this test"))
            def update(
                id       : ProductId,
                sku      : String,
                price    : BigDecimal,
                currency : Currency,
                active   : Boolean,
                taxRate  : BigDecimal,
                taxExempt: Boolean
            ): IO[Product] =
                IO.raiseError(new NotImplementedError("update not needed in this test"))
            def upsert(productInfo: v1.ProductInfo): IO[Product] =
                IO.raiseError(new NotImplementedError("upsert not needed in this test"))
        }

        val customerRepo = new CustomerRepository[IO] {
            def create(customerInfo: CustomerInfo)                                : IO[Customer]         = IO.delay {
                val customer = testCustomer.copy(
                    id           = CustomerId(UUID.randomUUID()),
                    email        = customerInfo.email,
                    customerType = customerInfo.customerType,
                    language     = customerInfo.language,
                    givenName    = customerInfo.givenName,
                    familyName   = customerInfo.familyName,
                    companyName  = customerInfo.companyName
                )
                repos.customers = repos.customers + (customerInfo.email -> customer)
                repos.customersById = repos.customersById + (customer.id -> customer)
                customer
            }
            def findByEmail(email: String): IO[Option[Customer]] = IO.pure(repos.customers.get(email))
            def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): IO[Option[Customer]] = IO.delay {
                repos.customers.get(email).map { existing =>
                    val updated = existing.copy(
                        customerType = newCustomerInfo.customerType,
                        language     = newCustomerInfo.language,
                        givenName    = newCustomerInfo.givenName,
                        familyName   = newCustomerInfo.familyName,
                        companyName  = newCustomerInfo.companyName,
                        addressLine1 = newCustomerInfo.addressLine1,
                        addressLine2 = newCustomerInfo.addressLine2,
                        addressLine3 = newCustomerInfo.addressLine3,
                        city         = newCustomerInfo.city,
                        region       = newCustomerInfo.region,
                        postalCode   = newCustomerInfo.postalCode,
                        countryCode  = newCustomerInfo.countryCode,
                        phoneNumber  = newCustomerInfo.phoneNumber,
                        updatedAt    = Instant.now()
                    )
                    repos.customers = repos.customers + (email -> updated)
                    repos.customersById = repos.customersById + (updated.id -> updated)
                    updated
                }
            }
            def findById(customerId: CustomerId): IO[Option[Customer]] = IO.pure(repos.customersById.get(customerId))
            def createFull(customer: Customer): IO[Boolean] = ???
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
                repos.authCodes = repos.authCodes + (intent.token.value.toString -> authCode)
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
            def findByTokenAndCode(token: PurchaseToken, code: String): IO[Option[PurchaseIntent]] = IO.delay {
                repos.purchaseIntents.get(token).filter(_.authCode == code)
            }
            def atomicMarkAsProcessed(token: PurchaseToken)           : IO[Boolean]                = IO.delay {
                repos.purchaseIntents.get(token) match {
                    case Some(intent) if !intent.processed =>
                        repos.purchaseIntents = repos.purchaseIntents + (token -> intent.copy(processed = true))
                        true
                    case _                                 => false
                }
            }
            def deleteExpired(): IO[Int] = ???
            def incrementFailedAttempts(token: PurchaseToken)         : IO[Unit]                   = IO.delay {
                repos.purchaseIntents.get(token).foreach { intent =>
                    repos.purchaseIntents =
                        repos.purchaseIntents + (token -> intent.copy(failedAttempts = intent.failedAttempts + 1))
                }
            }
            def countRecentByEmail(email: String, since: Instant): IO[Int] = IO.pure(0)
        }

        val productMetadataRepo = new ProductMetadataRepository[IO] {
            def create(metadata: ProductMetadata): IO[Long] = IO.delay {
                val id = repos.nextMetadataId
                repos.nextMetadataId += 1
                repos.productMetadata = repos.productMetadata + (id -> metadata)
                id
            }
            def findById(id: Long): IO[Option[ProductMetadata]] = IO.pure(repos.productMetadata.get(id))
        }

        val authService = new AuthenticationService[IO] {
            def generateAuthCode(                      ): IO[String]             = IO.pure("123456") // Fixed for testing
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
            ): IO[ProductOrder] = IO.delay {
                val order          = ProductOrder(
                    id                = OrderId(UUID.randomUUID()),
                    customerId        = customerId,
                    productId         = productId,
                    amount            = amount,
                    currency          = Currency.EUR,
                    status            = OrderStatus.Pending,
                    paymentProvider   = None,
                    paymentId         = None,
                    language          = language,
                    invoiceNumber     = None,
                    productMetadataId = productMetadataId,
                    createdAt         = Instant.now(),
                    updatedAt         = Instant.now()
                )
                val existingOrders = repos.orders.getOrElse(customerId, List.empty)
                repos.orders = repos.orders + (customerId -> (order :: existingOrders))
                order
            }
            def findOrder(orderId: OrderId): IO[Option[ProductOrder]] = ???
            def findProduct(orderId: OrderId): IO[Option[Product]] = ???
            def markOrderConfirmed(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Unit] =
                ???
            def markOrderFailed(orderId: OrderId): IO[Unit]             = ???
            def findCustomer   (orderId: OrderId): IO[Option[Customer]] = ???

            def updateOrderStatus(orderId: OrderId, status: OrderStatus): IO[Boolean] = IO.pure(true)
            def updatePaymentId(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Boolean] =
                IO.pure(true)
            def updatePaymentIdIfNeededAndPresent(
                orderId        : OrderId,
                paymentId      : Option[String],
                paymentProvider: PaymentProvider
            )                                                                                         : IO[Boolean] = IO.pure(true)

            // New transition-based callback methods
            def registerCallbackForStatusChangeTo(
                name      : String,
                toStatuses: Set[OrderStatus],
                callback  : OrderCompletionCallback[IO]
            ): IO[Unit] = IO.unit
            def registerCallbackForTransition(
                name      : String,
                transition: OrderStateTransition,
                callback  : OrderCompletionCallback[IO]
            ): IO[Unit] = IO.unit
            def registerCallbackForTransitions(
                name       : String,
                transitions: Set[OrderStateTransition],
                callback   : OrderCompletionCallback[IO]
            ): IO[Unit] = IO.unit
            def registerCallbackForFinalStatus(
                name       : String,
                finalStatus: OrderStatus,
                callback   : OrderCompletionCallback[IO]
            ): IO[Unit] = IO.unit
            def registerCallbackForFinalStatuses(
                name         : String,
                finalStatuses: Set[OrderStatus],
                callback     : OrderCompletionCallback[IO]
            ): IO[Unit] = IO.unit
        }

        val paymentService = new PaymentService[IO] {
            def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): IO[String] =
                IO.delay {
                    repos.paymentLinks = (orderId, amount, customerInfo) :: repos.paymentLinks
                    s"https://gocardless.example.com/pay/${orderId.value}"
                }
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
            )(using language: BackendCompatibleLanguage): IO[EmailResult] = IO.delay {
                repos.emailsSent = authCode :: repos.emailsSent
                EmailSent
            }

            def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendUserInvoiceWithReport(
                invoice  : InvoiceEmail,
                pdfReport: PdfReportEmail,
                bcc      : List[EmailAddress] = List.empty
            )(using language: BackendCompatibleLanguage): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using
                language: BackendCompatibleLanguage
            ): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendUserPdfReport(pdfReport: PdfReportEmail)(using
                language: BackendCompatibleLanguage
            ): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendAdminNotification(notification: AdminNotification): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendUserNotification(notification: UserNotification): IO[EmailResult] =
                IO.pure(EmailSent)

            def sendEmail(message: EmailMessage): IO[EmailResult] =
                IO.pure(EmailSent)
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

    val tests = Tests {

        test("createPurchaseIntent - new customer flow") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()

            // Verify purchase token was generated
            assert(result.value != null)

            // Verify customer was created
            assert(repos.customers.contains(testCustomerInfo.email))
            val createdCustomer = repos.customers(testCustomerInfo.email)
            assert(createdCustomer.email == testCustomerInfo.email              )
            assert(createdCustomer.customerType == testCustomerInfo.customerType)
            assert(createdCustomer.language == testCustomerInfo.language        )

            // Verify purchase intent was created
            assert(repos.purchaseIntents.contains(result))
            val intent = repos.purchaseIntents(result)
            assert(intent.productId == testProduct.id     )
            assert(intent.amount == testProduct.price     )
            assert(intent.customerId == createdCustomer.id)
            assert(!intent.processed                      )

            // Verify auth code was generated and stored
            assert(repos.authCodes.contains(result.value.toString)   )
            assert(repos.authCodes(result.value.toString) == "123456")

            // Verify email was sent
            assert(repos.emailsSent.nonEmpty)
            val sentEmail = repos.emailsSent.head
            assert(sentEmail.email.value == testCustomerInfo.email)
            assert(sentEmail.code == "123456"                     )
            assert(sentEmail.isNewUser == true                    ) // New customer
            assert(sentEmail.productName.contains("Test Product") )
            assert(sentEmail.amount == testProduct.price          )
        }

        test("createPurchaseIntent - existing customer flow") {
            val repos = new TestRepositories()
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

            // Pre-populate with existing customer
            repos.customers     = repos.customers +     (testCustomerInfo.email -> testCustomer)
            repos.customersById = repos.customersById + (testCustomer.id        -> testCustomer)

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
                FireboxAvailability.AllEnabled
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )

            service.createPurchaseIntent(request).unsafeRunSync()

            // Verify existing customer was reused (only one customer in repos)
            assert(repos.customers.size == 1    )
            assert(repos.customersById.size == 1)

            // Verify email indicates existing user
            val sentEmail = repos.emailsSent.head
            assert(sentEmail.isNewUser == false) // Existing customer
        }

        test("createPurchaseIntent - with ProductMetadata") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "test-document.pdf",
                mimeType = "application/pdf",
                content  = "dGVzdCBjb250ZW50" // base64 for "test content"
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()

            // Verify ProductMetadata was stored
            assert(repos.productMetadata.nonEmpty           )
            assert(repos.productMetadata.contains(1L)       )
            assert(repos.productMetadata(1L) == fileMetadata)

            // Verify PurchaseIntent references the metadata
            val intent = repos.purchaseIntents(result)
            assert(intent.productMetadataId.contains(1L))
        }

        test("verifyAndProcess - successful flow") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            // Setup: Create purchase intent first
            val createRequest = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )
            val token         = service.createPurchaseIntent(createRequest).unsafeRunSync()

            // Test: Verify and process
            val verifyRequest = VerifyAndProcessRequest(
                purchaseToken = token,
                email         = testCustomerInfo.email,
                code          = "123456"
            )

            val response = service.verifyAndProcess(verifyRequest).unsafeRunSync()

            // Verify successful response structure
            assert(response.success == true                              )
            assert(response.jwtToken.startsWith("jwt-")                  )
            assert(response.userCreated == false                         ) // Same email as customer, so not a "new" user in this context
            assert(response.paymentUrl.contains("gocardless.example.com"))

            // Verify order was created
            val customer = repos.customers(testCustomerInfo.email)
            assert(repos.orders.contains(customer.id))
            val orders = repos.orders(customer.id)
            assert(orders.nonEmpty)
            val order = orders.head
            assert(order.customerId == customer.id            )
            assert(order.productId == testProduct.id          )
            assert(order.amount == testProduct.price          )
            assert(order.status == OrderStatus.Pending        )
            assert(order.language == testCustomerInfo.language)

            // Verify payment link was created
            assert(repos.paymentLinks.nonEmpty)
            val (linkOrderId, linkAmount, linkCustomerInfo) = repos.paymentLinks.head
            assert(linkOrderId == order.id                         )
            assert(linkAmount == testProduct.price                 )
            assert(linkCustomerInfo.email == testCustomerInfo.email)

            // Verify purchase intent was marked as processed
            val processedIntent = repos.purchaseIntents(token)
            assert(processedIntent.processed == true)
        }

        test("verifyAndProcess - invalid code throws typed exception") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            // Setup: Create purchase intent first
            val createRequest = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )
            val token         = service.createPurchaseIntent(createRequest).unsafeRunSync()

            // Test: Verify with wrong code
            val verifyRequest = VerifyAndProcessRequest(
                purchaseToken = token,
                email         = testCustomerInfo.email,
                code          = "wrong-code"
            )

            val result = service.verifyAndProcess(verifyRequest).attempt.unsafeRunSync()
            result match {
                case Left(ex: InvalidOrExpiredCodeException) =>
                    assert(ex.errorCode == "invalidorexpiredcode"             )
                    assert(ex.context("purchaseToken") == token.value.toString)
                case Left(other)                             =>
                    throw new Exception(s"Expected InvalidOrExpiredCodeException but got ${other.getClass}")
                case Right(_)                                =>
                    throw new Exception("Expected exception but got success")
            }
        }

        test("verifyAndProcess - nonexistent token throws typed exception") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val verifyRequest = VerifyAndProcessRequest(
                purchaseToken = PurchaseToken(UUID.randomUUID()),
                email         = testCustomerInfo.email,
                code          = "123456"
            )

            val result = service.verifyAndProcess(verifyRequest).attempt.unsafeRunSync()
            result match {
                case Left(ex: PurchaseIntentNotFoundException) =>
                    // Nonexistent token fails at findByToken before code comparison
                    assert(ex.errorCode == "purchaseintentnotfound")
                    assert(ex.context.contains("purchaseToken")    )
                case Left(other)                               =>
                    throw new Exception(s"Expected PurchaseIntentNotFoundException but got ${other.getClass}")
                case Right(_)                                  =>
                    throw new Exception("Expected exception but got success")
            }
        }

        test("verifyAndProcess - second call with same token returns AlreadyProcessedException") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            // Setup: Create purchase intent
            val createRequest = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = testCustomerInfo
            )
            val token         = service.createPurchaseIntent(createRequest).unsafeRunSync()

            // First call: should succeed
            val verifyRequest = VerifyAndProcessRequest(
                purchaseToken = token,
                email         = testCustomerInfo.email,
                code          = "123456"
            )
            val firstResult   = service.verifyAndProcess(verifyRequest).unsafeRunSync()
            assert(firstResult.success == true)

            // Second call with same token+code: should fail with AlreadyProcessedException
            val secondResult = service.verifyAndProcess(verifyRequest).attempt.unsafeRunSync()
            secondResult match {
                case Left(ex: AlreadyProcessedException) =>
                    assert(ex.errorCode == "alreadyprocessed"                 )
                    assert(ex.context("purchaseToken") == token.value.toString)
                case Left(other)                         =>
                    throw new Exception(
                        s"Expected AlreadyProcessedException but got ${other.getClass}: ${other.getMessage}"
                    )
                case Right(_)                            =>
                    throw new Exception("Expected AlreadyProcessedException but got success")
            }
        }

        test("customer validation - Individual requires givenName and familyName") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val invalidCustomer = testCustomerInfo.copy(
                customerType = CustomerType.Individual,
                givenName    = None, // Missing required field
                familyName   = None  // Missing required field
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = invalidCustomer
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: RuntimeException) =>
                    assert(ex.getMessage.contains("Given name and family name are required"))
                case Left(other)                =>
                    throw new Exception(s"Expected RuntimeException but got ${other.getClass}")
                case Right(_)                   =>
                    throw new Exception("Expected validation error but got success")
            }
        }

        test("customer validation - Business requires companyName") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val invalidCustomer = testCustomerInfo.copy(
                customerType = CustomerType.Business,
                companyName  = None // Missing required field
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProduct.id,
                productMetadata = None,
                customer        = invalidCustomer
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: RuntimeException) =>
                    assert(ex.getMessage.contains("Company name is required"))
                case Left(other)                =>
                    throw new Exception(s"Expected RuntimeException but got ${other.getClass}")
                case Right(_)                   =>
                    throw new Exception("Expected validation error but got success")
            }
        }

        test("product-firebox mismatch - license fee product with Traditional firebox (fee not required)") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = traditionalFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProductWithFee.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).attempt.unsafeRunSync()
            result match {
                case Left(ex: ProductFireboxMismatchException) =>
                    assert(ex.errorCode == "PRODUCT_FIREBOX_MISMATCH"            )
                    assert(ex.getMessage.contains("not subject to a license fee"))
                case Left(other)                               =>
                    throw new Exception(
                        s"Expected ProductFireboxMismatchException but got ${other.getClass}: ${other.getMessage}"
                    )
                case Right(_)                                  =>
                    throw new Exception("Expected ProductFireboxMismatchException but got success")
            }
        }

        test("product-firebox match - Traditional firebox with base product (success)") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
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
        }

        test("product-firebox match - Ecolabeled firebox with license fee product (success)") {
            val repos = new TestRepositories()
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
                FireboxAvailability.AllEnabled
            )

            val fileMetadata = FileDescriptionWithContent(
                filename = "test.fcalc",
                mimeType = "application/x-yaml",
                content  = ecolabeledFireboxYaml
            )

            val request = CreatePurchaseIntentRequest(
                productId       = testProductWithFee.id,
                productMetadata = Some(fileMetadata),
                customer        = testCustomerInfo
            )

            val result = service.createPurchaseIntent(request).unsafeRunSync()
            assert(result.value != null)
        }
    }
}
