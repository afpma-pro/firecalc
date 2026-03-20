/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.time.Instant
import java.util.UUID

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async
import cats.syntax.all.*

import org.typelevel.log4cats.Logger

class PurchaseServiceImpl[F[_]: Async](
    productRepo        : ProductRepository[F],
    customerRepo       : CustomerRepository[F],
    purchaseIntentRepo : PurchaseIntentRepository[F],
    productMetadataRepo: ProductMetadataRepository[F],
    authService        : AuthenticationService[F],
    orderService       : OrderService[F],
    paymentService     : PaymentService[F],
    emailService       : EmailService[F]
)                                     (implicit logger: Logger[F])
    extends PurchaseService[F]:

    def createPurchaseIntent(request: CreatePurchaseIntentRequest): F[PurchaseToken] =
        for
            _ <- logger.info(s"Creating purchase intent for email: ${request.customer.email}")

            // Validate email address at API entry point
            validatedEmail <- EmailAddress.fromString(request.customer.email) match {
                case Right(_)       => Async[F].pure(request.customer.email)
                case Left(errorMsg) =>
                    Async[F].raiseError(CustomerValidationException(List(s"Invalid email address: $errorMsg")))
            }

            // Validate customer information based on type
            _ <- request.customer.customerType match {
                case CustomerType.Individual =>
                    if (request.customer.givenName.isEmpty || request.customer.familyName.isEmpty)
                        Async[F].raiseError(
                            CustomerValidationException(
                                List("Given name and family name are required for individual customers")
                            )
                        )
                    else Async[F].unit
                case CustomerType.Business   =>
                    if (request.customer.companyName.isEmpty)
                        Async[F].raiseError(
                            CustomerValidationException(List("Company name is required for business customers"))
                        )
                    else Async[F].unit
            }

            productOpt <- productRepo.findById(request.productId)
            product    <- productOpt.liftTo[F](ProductNotFoundException(request.productId.value.toString))

            now      <- Async[F].delay(Instant.now())

            // Check per-email cooldown: max 10 intents per hour
            recentCount <- purchaseIntentRepo.countRecentByEmail(validatedEmail, since = now.minusSeconds(3600))
            _           <- Async[F].raiseError(TooManyIntentsForEmailException(validatedEmail))
                               .whenA(recentCount >= 10)

            authCode <- authService.generateAuthCode()

            // Create or findAndUpdate existing customer (using validated email)
            customerOpt <- customerRepo.findByEmailAndUpdate(validatedEmail, request.customer)
            customer    <- customerOpt match
                case Some(existingCustomer) =>
                    logger.info(s"Using existing customer: ${existingCustomer.id}") *>
                        Async[F].pure(existingCustomer)
                case None                   =>
                    logger.info(s"Creating new customer for email: ${validatedEmail}") *>
                        customerRepo.create(request.customer)

            // Store productMetadata if present and get productMetadataId
            productMetadataId <- request.productMetadata match
                case Some(metadata) =>
                    logger.info("Storing product metadata") *>
                        productMetadataRepo.create(metadata).map(Some(_))
                case None           =>
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
                email       = EmailAddress.unsafeFromString(validatedEmail),
                code        = authCode,
                isNewUser   = isNewUser,
                productName = Some(product.name),
                amount      = product.price
            )
            emailResult <- {
                given BackendCompatibleLanguage = request.customer.language
                emailService.sendUserAuthenticationCode(authCodeEmail)
            }
            _ <- emailResult match
                case EmailSent          => Async[F].unit
                case EmailFailed(error) =>
                    Async[F].raiseError(
                        EmailSendingFailedException  (
                            orderId   = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                            recipient = validatedEmail,
                            reason    = error
                        )
                    )

            _ <- logger.info(s"Purchase intent created: ${intent.token}")
        yield intent.token

    def verifyAndProcess(request: VerifyAndProcessRequest): F[VerifyAndProcessResponse] =
        (for
            _ <- logger.info(s"Processing verification for token: ${request.purchaseToken}")

            // Step 0: Validate email address at API entry point
            validatedEmail <- EmailAddress.fromString(request.email) match {
                case Right(_)       => Async[F].pure(request.email)
                case Left(errorMsg) =>
                    Async[F].raiseError(CustomerValidationException(List(s"Invalid email address: $errorMsg")))
            }

            // Step 1: Validate authentication code and retrieve intent
            intent <- validateAuthenticationCode(request.purchaseToken, request.code)

            // Step 2: Atomically mark as processed — concurrency gate (SEC-004)
            // Only the first request through wins; duplicates get 409.
            // DESIGN DECISION: if this succeeds but subsequent steps (order creation,
            // payment link) fail, the intent stays permanently locked. The user must
            // create a new purchase intent to retry. This is intentional — it prevents
            // retry abuse where an attacker replays the same token to create duplicate
            // orders or payment links.
            wasMarked <- purchaseIntentRepo.atomicMarkAsProcessed(request.purchaseToken)
            _         <- Async[F].raiseError(AlreadyProcessedException(request.purchaseToken.value.toString))
                             .whenA(!wasMarked)

            // Step 3: Find customer with typed error
            customer <- findCustomer(intent.customerId)

            // Step 4: Process the verified request with error recovery
            response <- processVerifiedRequest(request, intent, customer)
                .handleErrorWith(handleProcessingErrors(request.purchaseToken.value.toString, _))

            _ <- logger.info(s"Verification processed successfully for order: ${response.orderId}")
        yield response)
            .handleErrorWith(logAndRethrowError(request.purchaseToken.value.toString, _))

    private val MAX_ATTEMPTS = 10

    private def validateAuthenticationCode(token: PurchaseToken, code: String): F[PurchaseIntent] =
        for
            intent <- purchaseIntentRepo.findByToken(token)
                .flatMap(_.liftTo[F](PurchaseIntentNotFoundException(token.value.toString, code)))

            // Check lockout before doing anything else
            _ <- Async[F].raiseError(TooManyAttemptsException(token.value.toString))
                     .whenA(intent.failedAttempts >= MAX_ATTEMPTS)

            // Check expiry
            now <- Async[F].delay(Instant.now())
            _   <- Async[F].raiseError(InvalidOrExpiredCodeException(token.value.toString, code))
                       .whenA(now.isAfter(intent.expiresAt))

            // Constant-time comparison to prevent timing attacks
            isValid = java.security.MessageDigest.isEqual(
                intent.authCode.getBytes,
                code.getBytes
            )

            _ <- (purchaseIntentRepo.incrementFailedAttempts(token) *>
                     Async[F].raiseError(InvalidOrExpiredCodeException(token.value.toString, code)))
                     .whenA(!isValid)
        yield intent

    private def findCustomer(customerId: CustomerId): F[Customer] =
        customerRepo.findById(customerId).flatMap {
            case Some(customer) => Async[F].pure(customer)
            case None           =>
                logger.error(s"Customer not found with UUID: $customerId") *>
                    Async[F].raiseError(CustomerNotFoundException(customerId.value))
        }

    private def processVerifiedRequest(
        request : VerifyAndProcessRequest,
        intent  : PurchaseIntent,
        customer: Customer
    ): F[VerifyAndProcessResponse] = {
        // Email is already validated at API entry point before calling this method
        val customerCreated = request.email != customer.email

        for {
            jwtToken   <- generateJWTToken(customer.id)
            order      <- createOrderSafely(customer.id, intent, customer.language)
            paymentUrl <- createPaymentLinkSafely(order.id, order.amount, customer.toCustomerInfo)
        } yield VerifyAndProcessResponse    (
            success     = true,
            jwtToken    = jwtToken,
            userCreated = customerCreated,
            orderId     = order.id,
            paymentUrl  = paymentUrl
        )
    }

    private def generateJWTToken(customerId: CustomerId): F[String] =
        authService.generateJWT(customerId).handleErrorWith { error =>
            Async[F].raiseError(JWTGenerationFailedException(customerId.value, Some(error)))
        }

    private def createOrderSafely(
        customerId: CustomerId,
        intent    : PurchaseIntent,
        language  : BackendCompatibleLanguage
    ): F[ProductOrder] =
        orderService
            .createOrder(
                customerId,
                intent.productId,
                intent.amount,
                language,
                intent.productMetadataId
            )
            .handleErrorWith { error =>
                Async[F].raiseError(OrderCreationFailedException(error.getMessage, Some(error)))
            }

    private def createPaymentLinkSafely(
        orderId     : OrderId,
        amount      : BigDecimal,
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
                logger.error(
                    s"Purchase verification failed [${pse.errorCode}]: ${pse.getMessage}. Context: $context"
                ) *>
                    Async[F].raiseError(pse)
            case _ =>
                logger.error(error)(s"Unexpected error during purchase verification for token: $token") *>
                    Async[F].raiseError(PurchaseIntentProcessingException(token, "Unexpected error", Some(error)))
