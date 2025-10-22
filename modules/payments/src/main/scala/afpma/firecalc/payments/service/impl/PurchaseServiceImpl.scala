/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import cats.effect.Async
import cats.syntax.all.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.exceptions.*
import java.util.UUID
import scala.util.Random
import org.typelevel.log4cats.Logger

class PurchaseServiceImpl[F[_]: Async](
  productRepo: ProductRepository[F],
  customerRepo: CustomerRepository[F],
  purchaseIntentRepo: PurchaseIntentRepository[F],
  productMetadataRepo: ProductMetadataRepository[F],
  authService: AuthenticationService[F],
  orderService: OrderService[F],
  paymentService: PaymentService[F],
  emailService: EmailService[F]
)(implicit logger: Logger[F]) extends PurchaseService[F]:

  def createPurchaseIntent(request: CreatePurchaseIntentRequest): F[PurchaseToken] =
    for
      _ <- logger.info(s"Creating purchase intent for email: ${request.customer.email}")

      // Validate email address at API entry point
      validatedEmail <- EmailAddress.fromString(request.customer.email) match {
        case Right(_) => Async[F].pure(request.customer.email)
        case Left(errorMsg) =>
          Async[F].raiseError(CustomerValidationException(List(s"Invalid email address: $errorMsg")))
      }

      // Validate customer information based on type
      _ <- request.customer.customerType match {
        case CustomerType.Individual =>
          if (request.customer.givenName.isEmpty || request.customer.familyName.isEmpty)
            Async[F].raiseError(CustomerValidationException(List("Given name and family name are required for individual customers")))
          else Async[F].unit
        case CustomerType.Business =>
          if (request.customer.companyName.isEmpty)
            Async[F].raiseError(CustomerValidationException(List("Company name is required for business customers")))
          else Async[F].unit
      }

      productOpt <- productRepo.findById(request.productId)
      product <- productOpt.liftTo[F](ProductNotFoundException(request.productId.value.toString))

      authCode <- authService.generateAuthCode()
      
      // Create or findAndUpdate existing customer (using validated email)
      customerOpt <- customerRepo.findByEmailAndUpdate(validatedEmail, request.customer)
      customer <- customerOpt match
        case Some(existingCustomer) =>
          logger.info(s"Using existing customer: ${existingCustomer.id}") *>
          Async[F].pure(existingCustomer)
        case None =>
          logger.info(s"Creating new customer for email: ${validatedEmail}") *>
          customerRepo.create(request.customer)

      // Store productMetadata if present and get productMetadataId
      productMetadataId <- request.productMetadata match
        case Some(metadata) => 
          logger.info("Storing product metadata") *>
          productMetadataRepo.create(metadata).map(Some(_))
        case None => 
          Async[F].pure(None)

      // Use the UUID-based method since we have the customer entity with its UUID
      intent <- purchaseIntentRepo.create(
        request.productId, 
        product.price, 
        product.currency,
        authCode,
        customer.id,
        productMetadataId
      )

      isNewUser = customerOpt.isEmpty

      authCodeEmail = AuthenticationCodeEmail(
        email = EmailAddress.unsafeFromString(validatedEmail),
        code = authCode,
        isNewUser = isNewUser,
        productName = Some(product.name),
        amount = product.price
      )
      emailResult <- {
        given BackendCompatibleLanguage = request.customer.language
        emailService.sendUserAuthenticationCode(authCodeEmail)
      }
      _ <- emailResult match
        case EmailSent => Async[F].unit
        case EmailFailed(error) =>
          Async[F].raiseError(
            EmailSendingFailedException(
              orderId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
              recipient = validatedEmail,
              reason = error
            )
          )

      _ <- logger.info(s"Purchase intent created: ${intent.token}")
    yield intent.token

  def verifyAndProcess(request: VerifyAndProcessRequest): F[VerifyAndProcessResponse] =
    (for
      _ <- logger.info(s"Processing verification for token: ${request.purchaseToken}")

      // Step 0: Validate email address at API entry point
      validatedEmail <- EmailAddress.fromString(request.email) match {
        case Right(_) => Async[F].pure(request.email)
        case Left(errorMsg) =>
          Async[F].raiseError(CustomerValidationException(List(s"Invalid email address: $errorMsg")))
      }

      // Step 1: Validate authentication code with typed error
      _ <- validateAuthenticationCode(request.purchaseToken, request.code)
      
      // Step 2: Find purchase intent with typed error  
      intent <- findPurchaseIntent(request.purchaseToken, request.code)
      
      // Step 3: Find customer with typed error
      customer <- findCustomer(intent.customerId)
      
      // Step 4: Process the verified request with error recovery
      response <- processVerifiedRequest(request, intent, customer)
        .handleErrorWith(handleProcessingErrors(request.purchaseToken.value.toString, _))
        
      _ <- logger.info(s"Verification processed successfully for order: ${response.orderId}")
    yield response)
      .handleErrorWith(logAndRethrowError(request.purchaseToken.value.toString, _))

  private def validateAuthenticationCode(token: PurchaseToken, code: String): F[Unit] =
    authService.validateCode(token, code).flatMap { isValid =>
      if (!isValid) 
        Async[F].raiseError(InvalidOrExpiredCodeException(token.value.toString, code))
      else 
        Async[F].unit
    }

  private def findPurchaseIntent(token: PurchaseToken, code: String): F[PurchaseIntent] =
    purchaseIntentRepo.findByTokenAndCode(token, code).flatMap {
      case Some(intent) => Async[F].pure(intent)
      case None => Async[F].raiseError(PurchaseIntentNotFoundException(token.value.toString, code))
    }

  private def findCustomer(customerId: CustomerId): F[Customer] =
    customerRepo.findById(customerId).flatMap {
      case Some(customer) => Async[F].pure(customer)
      case None => 
        logger.error(s"Customer not found with UUID: $customerId") *>
        Async[F].raiseError(CustomerNotFoundException(customerId.value))
    }

  private def processVerifiedRequest(
    request: VerifyAndProcessRequest,
    intent: PurchaseIntent,
    customer: Customer
  ): F[VerifyAndProcessResponse] = {
    // Email is already validated at API entry point before calling this method
    val customerCreated = request.email != customer.email
    
    for {
      jwtToken <- generateJWTToken(customer.id)
      order <- createOrderSafely(customer.id, intent, customer.language)
      paymentUrl <- createPaymentLinkSafely(order.id, order.amount, customer.toCustomerInfo)
      
      _ <- purchaseIntentRepo.markAsProcessed(request.purchaseToken)
        .handleErrorWith(error => 
          logger.warn(s"Failed to mark purchase intent as processed: ${error.getMessage}") *>
          Async[F].pure(false) // Continue even if this fails, return false
        ).void
    } yield VerifyAndProcessResponse(
      success = true,
      jwtToken = jwtToken,
      userCreated = customerCreated,
      orderId = order.id,
      paymentUrl = paymentUrl
    )
  }

  private def generateJWTToken(customerId: CustomerId): F[String] =
    authService.generateJWT(customerId).handleErrorWith { error =>
      Async[F].raiseError(JWTGenerationFailedException(customerId.value, Some(error)))
    }

  private def createOrderSafely(
    customerId: CustomerId, 
    intent: PurchaseIntent,
    language: BackendCompatibleLanguage
  ): F[ProductOrder] =
    orderService.createOrder(
      customerId, 
      intent.productId, 
      intent.amount, 
      language, 
      intent.productMetadataId
    ).handleErrorWith { error =>
      Async[F].raiseError(OrderCreationFailedException(error.getMessage, Some(error)))
    }

  private def createPaymentLinkSafely(
    orderId: OrderId,
    amount: BigDecimal,
    customerInfo: CustomerInfo
  ): F[String] =
    paymentService.createPaymentLink(orderId, amount, customerInfo).handleErrorWith { error =>
      Async[F].raiseError(PaymentLinkCreationFailedException(error.getMessage, Some(error)))
    }

  private def handleProcessingErrors(token: String, error: Throwable): F[VerifyAndProcessResponse] =
    error match
      case pse: PurchaseServiceError =>
        // Re-raise typed errors as-is
        Async[F].raiseError(pse)
      case _ =>
        // Wrap unexpected errors
        Async[F].raiseError(PurchaseIntentProcessingException(token, error.getMessage, Some(error)))

  private def logAndRethrowError(token: String, error: Throwable): F[VerifyAndProcessResponse] =
    error match
      case pse: PurchaseServiceError =>
        val context = pse.context.map { case (k, v) => s"$k=$v" }.mkString(", ")
        logger.error(s"Purchase verification failed [${pse.errorCode}]: ${pse.getMessage}. Context: $context") *>
        Async[F].raiseError(pse)
      case _ =>
        logger.error(error)(s"Unexpected error during purchase verification for token: $token") *>
        Async[F].raiseError(PurchaseIntentProcessingException(token, "Unexpected error", Some(error)))
