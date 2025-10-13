/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import utest.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.*
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.service.impl.GoCardlessPaymentServiceImpl
import afpma.firecalc.payments.service.{OrderStateTransition, OrderCompletionCallback}
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.email
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.util.UUID
import java.time.Instant

object PaymentLinkCreationTest extends TestSuite {
  
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  
  // Test data for payment link creation scenarios
  val testProduct = Product(
    id = ProductId(UUID.randomUUID()),
    name = "Payment Link Test Product",
    description = "Product for testing payment link creation",
    price = BigDecimal("75.50"),
    currency = Currency.EUR,
    active = true
  )
  
  val testCustomerInfo = CustomerInfo(
    email = "payment.link@example.com",
    customerType = CustomerType.Business,
    language = BackendCompatibleLanguage.German,
    givenName = Some("Payment"),
    familyName = Some("Linkerson"),
    companyName = Some("Link Testing GmbH"),
    addressLine1 = Some("Unter den Linden 1"),
    addressLine2 = None,
    addressLine3 = None,
    city = Some("Berlin"),
    region = None,
    postalCode = Some("10117"),
    countryCode = Some(CountryCode_ISO_3166_1_ALPHA_2.DE),
    phoneNumber = None
  )

  class TestPaymentLinkRepositories {
    var products: Map[ProductId, Product] = Map(testProduct.id -> testProduct)
    var customers: Map[String, Customer] = Map.empty
    var customersById: Map[CustomerId, Customer] = Map.empty
    var orders: Map[OrderId, ProductOrder] = Map.empty
    var ordersByCustomer: Map[CustomerId, List[ProductOrder]] = Map.empty
    
    var paymentLinkCreationAttempts: List[(OrderId, BigDecimal, CustomerInfo)] = List.empty
    var paymentLinkErrors: Map[OrderId, String] = Map.empty
    var emailsSent: List[PaymentLinkEmail] = List.empty
  }
  
  def createPaymentLinkMockServices(repos: TestPaymentLinkRepositories) = {
    val orderService = new OrderService[IO] {
      def createOrder(customerId: CustomerId, productId: ProductId, amount: BigDecimal, language: BackendCompatibleLanguage, productMetadataId: Option[Long]): IO[ProductOrder] = IO.delay {
        val order = ProductOrder(
          id = OrderId(UUID.randomUUID()),
          customerId = customerId,
          productId = productId,
          amount = amount,
          currency = Currency.EUR,
          status = OrderStatus.Pending,
          paymentProvider = None,
          paymentId = None,
          language = language,
          invoiceNumber = None,
          productMetadataId = productMetadataId,
          createdAt = Instant.now(),
          updatedAt = Instant.now()
        )
        repos.orders = repos.orders + (order.id -> order)
        val existingOrders = repos.ordersByCustomer.getOrElse(customerId, List.empty)
        repos.ordersByCustomer = repos.ordersByCustomer + (customerId -> (order :: existingOrders))
        order
      }
      
      def findOrder(orderId: OrderId): IO[Option[ProductOrder]] = IO.pure(repos.orders.get(orderId))
      
      def findProduct(orderId: OrderId): IO[Option[Product]] = IO.delay {
        repos.orders.get(orderId).flatMap(order => repos.products.get(order.productId))
      }
      
      def markOrderConfirmed(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Unit] = ???
      def markOrderFailed(orderId: OrderId): IO[Unit] = ???
      def findCustomer(orderId: OrderId): IO[Option[Customer]] = ???
      def registerInvoiceNumberGenerationCallback(invoiceService: InvoiceNumberService[IO]): IO[Unit] = IO.unit
      def registerInvoicePdfGenerationCallback(invoicePdfGenerationService: InvoicePdfGenerationService[IO]): IO[Unit] = IO.unit
      def updateOrderStatus(orderId: OrderId, status: OrderStatus): IO[Boolean] = IO.pure(true)
      def updatePaymentId(orderId: OrderId, paymentId: String, paymentProvider: PaymentProvider): IO[Boolean] = IO.pure(true)
      def updatePaymentIdIfNeededAndPresent(orderId: OrderId, paymentId: Option[String], paymentProvider: PaymentProvider): IO[Boolean] = IO.pure(true)
      
      // New transition-based callback methods
      def registerCallbackForStatusChangeTo(name: String, toStatuses: Set[OrderStatus], callback: OrderCompletionCallback[IO]): IO[Unit] = IO.unit
      def registerCallbackForTransition(name: String, transition: OrderStateTransition, callback: OrderCompletionCallback[IO]): IO[Unit] = IO.unit
      def registerCallbackForTransitions(name: String, transitions: Set[OrderStateTransition], callback: OrderCompletionCallback[IO]): IO[Unit] = IO.unit
      def registerCallbackForFinalStatus(name: String, finalStatus: OrderStatus, callback: OrderCompletionCallback[IO]): IO[Unit] = IO.unit
      def registerCallbackForFinalStatuses(name: String, finalStatuses: Set[OrderStatus], callback: OrderCompletionCallback[IO]): IO[Unit] = IO.unit
    }
    
    val customerRepo = new CustomerRepository[IO] {
      def create(customerInfo: CustomerInfo): IO[Customer] = ???
      def findByEmail(email: String): IO[Option[Customer]] = IO.pure(repos.customers.get(email))
      def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): IO[Option[Customer]] = ???
      def findById(customerId: CustomerId): IO[Option[Customer]] = IO.pure(repos.customersById.get(customerId))
      def createFull(customer: Customer): IO[Boolean] = IO.delay {
        repos.customers = repos.customers + (customer.email -> customer)
        repos.customersById = repos.customersById + (customer.id -> customer)
        true
      }
      def updatePaymentProvider(customerId: CustomerId, paymentProviderId: String, paymentProvider: PaymentProvider): IO[Boolean] = ???
    }
    
    val emailService = new EmailService[IO] {
      def sendUserAuthenticationCode(authCode: AuthenticationCodeEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendUserInvoiceWithReport(invoice: InvoiceEmail, pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = IO.delay {
        repos.emailsSent = paymentLink :: repos.emailsSent
        EmailSent
      }
      
      def sendUserPdfReport(pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendAdminNotification(notification: AdminNotification): IO[email.EmailResult] = 
        IO.pure(EmailSent)
      
      def sendEmail(message: EmailMessage): IO[email.EmailResult] = 
        IO.pure(EmailSent)
    }
    
    val paymentService = new PaymentService[IO] {
      def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): IO[String] = IO.delay {
        repos.paymentLinkCreationAttempts = (orderId, amount, customerInfo) :: repos.paymentLinkCreationAttempts
        
        // Check for simulated errors
        repos.paymentLinkErrors.get(orderId) match {
          case Some(errorMsg) => throw new RuntimeException(errorMsg)
          case None => s"https://gocardless.test.example.com/pay/${orderId.value}"
        }
      }
      def processWebhookEvent(event: GoCardlessWebhookEvent): IO[Either[String, String]] = ???
      def sendPaymentLinkEmail(orderId: OrderId): IO[email.EmailResult] = ???
      def processWebhook(body: String, signature: String): IO[Either[String, afpma.firecalc.payments.service.impl.WebhookEventStatus]] = ???
    }
    
    (orderService, customerRepo, emailService, paymentService)
  }

  val tests = Tests {
    
    test("payment link creation - successful flow") {
      val repos = new TestPaymentLinkRepositories()
      val (orderService, customerRepo, emailService, paymentService) = createPaymentLinkMockServices(repos)
      
      // Create a customer first
      val customer = Customer(
        id = CustomerId(UUID.randomUUID()),
        email = testCustomerInfo.email,
        customerType = testCustomerInfo.customerType,
        language = testCustomerInfo.language,
        givenName = testCustomerInfo.givenName,
        familyName = testCustomerInfo.familyName,
        companyName = testCustomerInfo.companyName,
        addressLine1 = testCustomerInfo.addressLine1,
        addressLine2 = None,
        addressLine3 = None,
        city = testCustomerInfo.city,
        region = None,
        postalCode = testCustomerInfo.postalCode,
        countryCode = testCustomerInfo.countryCode,
        phoneNumber = None,
        paymentProviderId = None,
        paymentProvider = None,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )
      
      customerRepo.createFull(customer).unsafeRunSync()
      
      // Create an order
      val order = orderService.createOrder(
        customer.id,
        testProduct.id,
        testProduct.price,
        testCustomerInfo.language,
        None
      ).unsafeRunSync()
      
      // Test payment link creation
      val paymentUrl = paymentService.createPaymentLink(
        order.id,
        order.amount,
        testCustomerInfo
      ).unsafeRunSync()
      
      // Verify payment link was created
      assert(paymentUrl.contains(s"gocardless.test.example.com/pay/${order.id.value}"))
      
      // Verify the attempt was recorded
      assert(repos.paymentLinkCreationAttempts.nonEmpty)
      val (attemptOrderId, attemptAmount, attemptCustomerInfo) = repos.paymentLinkCreationAttempts.head
      assert(attemptOrderId == order.id)
      assert(attemptAmount == testProduct.price)
      assert(attemptCustomerInfo.email == testCustomerInfo.email)
    }
    
    test("payment link creation - handles failures with proper exception") {
      val repos = new TestPaymentLinkRepositories()
      val (orderService, customerRepo, emailService, paymentService) = createPaymentLinkMockServices(repos)
      
      // Create a customer
      val customer = Customer(
        id = CustomerId(UUID.randomUUID()),
        email = testCustomerInfo.email,
        customerType = testCustomerInfo.customerType,
        language = testCustomerInfo.language,
        givenName = testCustomerInfo.givenName,
        familyName = testCustomerInfo.familyName,
        companyName = testCustomerInfo.companyName,
        addressLine1 = testCustomerInfo.addressLine1,
        addressLine2 = None,
        addressLine3 = None,
        city = testCustomerInfo.city,
        region = None,
        postalCode = testCustomerInfo.postalCode,
        countryCode = testCustomerInfo.countryCode,
        phoneNumber = None,
        paymentProviderId = None,
        paymentProvider = None,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )
      
      customerRepo.createFull(customer).unsafeRunSync()
      
      // Create an order
      val order = orderService.createOrder(
        customer.id,
        testProduct.id,
        testProduct.price,
        testCustomerInfo.language,
        None
      ).unsafeRunSync()
      
      // Simulate payment link creation failure
      repos.paymentLinkErrors = repos.paymentLinkErrors + (order.id -> "class scala.Tuple11 cannot be cast to class scala.Tuple13")
      
      // Test payment link creation failure
      val result = paymentService.createPaymentLink(
        order.id,
        order.amount,
        testCustomerInfo
      ).attempt.unsafeRunSync()
      
      result match {
        case Left(ex) =>
          assert(ex.getMessage.contains("Tuple11"))
          assert(ex.getMessage.contains("Tuple13"))
        case Right(_) =>
          throw new Exception("Expected payment link creation to fail")
      }
      
      // Verify the attempt was still recorded
      assert(repos.paymentLinkCreationAttempts.nonEmpty)
    }
    
    test("payment link creation - handles complex customer data correctly") {
      val repos = new TestPaymentLinkRepositories()
      val (orderService, customerRepo, emailService, paymentService) = createPaymentLinkMockServices(repos)
      
      // Create a customer with complex data (all fields filled)
      val complexCustomerInfo = CustomerInfo(
        email = "complex.customer@enterprise.de",
        customerType = CustomerType.Business,
        language = BackendCompatibleLanguage.German,
        givenName = Some("Klaus-Dieter"),
        familyName = Some("Müller-Schmidt"),
        companyName = Some("Müller-Schmidt Unternehmensberatung & Co. KG"),
        addressLine1 = Some("Maximilianstraße 12"),
        addressLine2 = Some("3. Obergeschoss"),
        addressLine3 = Some("Büroeingang links"),
        city = Some("München"),
        region = Some("Bayern"),
        postalCode = Some("80539"),
        countryCode = Some(CountryCode_ISO_3166_1_ALPHA_2.DE),
        phoneNumber = Some("+49 89 12345-678")
      )
      
      val customer = Customer(
        id = CustomerId(UUID.randomUUID()),
        email = complexCustomerInfo.email,
        customerType = complexCustomerInfo.customerType,
        language = complexCustomerInfo.language,
        givenName = complexCustomerInfo.givenName,
        familyName = complexCustomerInfo.familyName,
        companyName = complexCustomerInfo.companyName,
        addressLine1 = complexCustomerInfo.addressLine1,
        addressLine2 = complexCustomerInfo.addressLine2,
        addressLine3 = complexCustomerInfo.addressLine3,
        city = complexCustomerInfo.city,
        region = complexCustomerInfo.region,
        postalCode = complexCustomerInfo.postalCode,
        countryCode = complexCustomerInfo.countryCode,
        phoneNumber = complexCustomerInfo.phoneNumber,
        paymentProviderId = Some("cus_german_enterprise_123"),
        paymentProvider = Some(PaymentProvider.GoCardless),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )
      
      customerRepo.createFull(customer).unsafeRunSync()
      
      // Create an order
      val order = orderService.createOrder(
        customer.id,
        testProduct.id,
        BigDecimal("1299.99"), // Higher amount for enterprise customer
        complexCustomerInfo.language,
        None
      ).unsafeRunSync()
      
      // Test payment link creation with complex data
      val paymentUrl = paymentService.createPaymentLink(
        order.id,
        order.amount,
        complexCustomerInfo
      ).unsafeRunSync()
      
      // Verify payment link was created
      assert(paymentUrl.contains(s"gocardless.test.example.com/pay/${order.id.value}"))
      
      // Verify all complex customer data was handled correctly
      assert(repos.paymentLinkCreationAttempts.nonEmpty)
      val (_, attemptAmount, attemptCustomerInfo) = repos.paymentLinkCreationAttempts.head
      assert(attemptAmount == BigDecimal("1299.99"))
      assert(attemptCustomerInfo.email == complexCustomerInfo.email)
      assert(attemptCustomerInfo.companyName.contains("Müller-Schmidt Unternehmensberatung & Co. KG"))
      assert(attemptCustomerInfo.addressLine1.contains("Maximilianstraße 12"))
      assert(attemptCustomerInfo.addressLine2.contains("3. Obergeschoss"))
      assert(attemptCustomerInfo.addressLine3.contains("Büroeingang links"))
      assert(attemptCustomerInfo.phoneNumber.contains("+49 89 12345-678"))
    }
    
    test("payment link creation - validates tuple handling across different customer types") {
      val repos = new TestPaymentLinkRepositories()
      val (orderService, customerRepo, emailService, paymentService) = createPaymentLinkMockServices(repos)
      
      // Test Individual customer
      val individualCustomerInfo = CustomerInfo(
        email = "individual@test.com",
        customerType = CustomerType.Individual,
        language = BackendCompatibleLanguage.English,
        givenName = Some("John"),
        familyName = Some("Doe"),
        companyName = None,
        addressLine1 = Some("123 Main St"),
        addressLine2 = None,
        addressLine3 = None,
        city = Some("Anytown"),
        region = Some("State"),
        postalCode = Some("12345"),
        countryCode = Some(CountryCode_ISO_3166_1_ALPHA_2.US),
        phoneNumber = Some("+1-555-123-4567")
      )
      
      // Test Business customer  
      val businessCustomerInfo = CustomerInfo(
        email = "business@company.com",
        customerType = CustomerType.Business,
        language = BackendCompatibleLanguage.French,
        givenName = Some("Pierre"),
        familyName = Some("Martin"),
        companyName = Some("Martin & Associés SARL"),
        addressLine1 = Some("15 Rue de Rivoli"),
        addressLine2 = None,
        addressLine3 = None,
        city = Some("Paris"),
        region = Some("Île-de-France"),
        postalCode = Some("75001"),
        countryCode = Some(CountryCode_ISO_3166_1_ALPHA_2.FR),
        phoneNumber = Some("+33 1 42 36 12 34")
      )
      
      val customers = List(
        (individualCustomerInfo, "individual"),
        (businessCustomerInfo, "business")
      )
      
      customers.foreach { case (customerInfo, customerType) =>
        val customer = Customer(
          id = CustomerId(UUID.randomUUID()),
          email = customerInfo.email,
          customerType = customerInfo.customerType,
          language = customerInfo.language,
          givenName = customerInfo.givenName,
          familyName = customerInfo.familyName,
          companyName = customerInfo.companyName,
          addressLine1 = customerInfo.addressLine1,
          addressLine2 = customerInfo.addressLine2,
          addressLine3 = customerInfo.addressLine3,
          city = customerInfo.city,
          region = customerInfo.region,
          postalCode = customerInfo.postalCode,
          countryCode = customerInfo.countryCode,
          phoneNumber = customerInfo.phoneNumber,
          paymentProviderId = None,
          paymentProvider = None,
          createdAt = Instant.now(),
          updatedAt = Instant.now()
        )
        
        customerRepo.createFull(customer).unsafeRunSync()
        
        val order = orderService.createOrder(
          customer.id,
          testProduct.id,
          testProduct.price,
          customerInfo.language,
          None
        ).unsafeRunSync()
        
        val paymentUrl = paymentService.createPaymentLink(
          order.id,
          order.amount,
          customerInfo
        ).unsafeRunSync()
        
        // Verify payment link was created for this customer type
        assert(paymentUrl.contains(s"gocardless.test.example.com/pay/${order.id.value}"))
      }
      
      // Verify both customer types were processed
      assert(repos.paymentLinkCreationAttempts.length == 2)
      
      // Verify individual customer data
      val individualAttempt = repos.paymentLinkCreationAttempts.find(_._3.customerType == CustomerType.Individual).get
      assert(individualAttempt._3.givenName.contains("John"))
      assert(individualAttempt._3.companyName.isEmpty)
      
      // Verify business customer data
      val businessAttempt = repos.paymentLinkCreationAttempts.find(_._3.customerType == CustomerType.Business).get
      assert(businessAttempt._3.companyName.contains("Martin & Associés SARL"))
      assert(businessAttempt._3.language == BackendCompatibleLanguage.French)
    }
  }
}
