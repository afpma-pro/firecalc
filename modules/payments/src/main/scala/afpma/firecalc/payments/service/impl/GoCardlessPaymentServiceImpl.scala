/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.repository.impl.CustomerSyntax.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.shared.*
import afpma.firecalc.payments.shared.api.BackendCompatibleLanguage
import afpma.firecalc.payments.shared.api.CountryCode_ISO_3166_1_ALPHA_2
import afpma.firecalc.payments.shared.api.CustomerInfo
import afpma.firecalc.payments.shared.api.OrderId
import afpma.firecalc.payments.shared.api.ProductCopyConfig
import afpma.firecalc.payments.shared.api.ProductCopyResolver
import afpma.firecalc.payments.shared.api.v1
import afpma.firecalc.payments.util.LogSanitizer

import cats.effect.Async
import cats.syntax.all.*

import scala.deriving.Mirror
import scala.util.Try

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.typelevel.ci.CIStringSyntax
import org.typelevel.log4cats.Logger

case class GoCardlessConfig private (
    accessToken  : String,
    baseUrl      : String, // = "https://api.gocardless.com",
    environment  : String, // "sandbox" or "live"
    redirectUri  : String,
    exitUri      : String,
    webhookSecret: String,
    adminEmail   : String
) {
    def withWebhookSecret(webhookSecret: String) = this.copy(
        webhookSecret = webhookSecret
    )

    def withRedirectUris(redirectUri: String, exitUri: String) = this.copy(
        redirectUri = redirectUri,
        exitUri     = exitUri
    )

    def withApplicationDomain(domain: String, protocol: "http" | "https") = this.copy(
        redirectUri = s"$protocol://$domain/v1/payment_complete",
        exitUri     = s"$protocol://$domain/v1/payment_cancelled"
    )
}

object GoCardlessConfig:
    def sandbox(accessToken: String, domain: String, protocol: "http" | "https", adminEmail: String) = GoCardlessConfig(
        accessToken   = accessToken,
        baseUrl       = "https://api-sandbox.gocardless.com",
        environment   = "sandbox",
        redirectUri   = s"$protocol://$domain/v1/payment_complete",
        exitUri       = s"$protocol://$domain/v1/payment_cancelled",
        webhookSecret = "sandbox_webhook_secret",
        adminEmail    = adminEmail
    )

    def live(
        accessToken        : String,
        webhookSecret      : String,
        domain             : String,
        adminEmail         : String
    ) = GoCardlessConfig(
        accessToken,
        baseUrl       = "https://api.gocardless.com",
        environment   = "live",
        redirectUri   = s"https://$domain/v1/payment_complete",
        exitUri       = s"https://$domain/v1/payment_cancelled",
        webhookSecret = webhookSecret,
        adminEmail    = adminEmail
    )

// GoCardless Language enum
enum GoCardlessLanguage(val code: String):
    case English    extends GoCardlessLanguage("en")
    case French     extends GoCardlessLanguage("fr")
    case German     extends GoCardlessLanguage("de")
    case Portuguese extends GoCardlessLanguage("pt")
    case Spanish    extends GoCardlessLanguage("es")
    case Italian    extends GoCardlessLanguage("it")
    case Dutch      extends GoCardlessLanguage("nl")
    case Danish     extends GoCardlessLanguage("da")
    case Norwegian  extends GoCardlessLanguage("nb")
    case Slovenian  extends GoCardlessLanguage("sl")
    case Swedish    extends GoCardlessLanguage("sv")

object GoCardlessLanguage:

    val DefaultLanguage: GoCardlessLanguage = BackendCompatibleLanguage.DefaultLanguage

    def fromCode(code: String): Option[GoCardlessLanguage] =
        GoCardlessLanguage.values.find(_.code == code)

    def fromCodeWithFallback(code: String): GoCardlessLanguage =
        fromCode(code).getOrElse(DefaultLanguage)

    /** Convert BackendCompatibleLanguage to GoCardlessLanguage manually */
    def fromBackendLanguage(lang: BackendCompatibleLanguage): GoCardlessLanguage = lang match
        case BackendCompatibleLanguage.English    => GoCardlessLanguage.English
        case BackendCompatibleLanguage.French     => GoCardlessLanguage.French
        case BackendCompatibleLanguage.German     => GoCardlessLanguage.German
        case BackendCompatibleLanguage.Portuguese => GoCardlessLanguage.Portuguese
        case BackendCompatibleLanguage.Spanish    => GoCardlessLanguage.Spanish
        case BackendCompatibleLanguage.Italian    => GoCardlessLanguage.Italian
        case BackendCompatibleLanguage.Dutch      => GoCardlessLanguage.Dutch
        case BackendCompatibleLanguage.Danish     => GoCardlessLanguage.Danish
        case BackendCompatibleLanguage.Norwegian  => GoCardlessLanguage.Norwegian
        case BackendCompatibleLanguage.Slovenian  => GoCardlessLanguage.Slovenian
        case BackendCompatibleLanguage.Swedish    => GoCardlessLanguage.Swedish

    /** Implicit conversion from BackendCompatibleLanguage to GoCardlessLanguage */
    given Conversion[BackendCompatibleLanguage, GoCardlessLanguage] = fromBackendLanguage

// GoCardless API Models
case class CreateCustomerRequest private (
    email       : String,
    given_name  : Option[String],     //      = None,
    family_name : Option[String],     //      = None,
    company_name: Option[String],     //      = None,
    language    : String,
    metadata    : Map[String, String] // = Map.empty
)

object CreateCustomerRequest:
    private def validateEmail(email: String): Either[String, String] =
        if (email.nonEmpty && email.contains("@")) Right(email)
        else Left("Email must be non-empty and contain @ symbol")

    def forIndividual(
        email     : String,
        givenName : String,
        familyName: String,
        language  : GoCardlessLanguage,
        metadata  : Map[String, String] = Map.empty
    ): Either[String, CreateCustomerRequest] =
        for {
            validEmail <- validateEmail(email)
        } yield CreateCustomerRequest       (
            email        = validEmail,
            given_name   = Some(givenName.trim).filter(_.nonEmpty),
            family_name  = Some(familyName.trim).filter(_.nonEmpty),
            company_name = None,
            language     = language.code,
            metadata     = metadata
        )

    def forCompany(
        email      : String,
        companyName: String,
        language   : GoCardlessLanguage,
        metadata   : Map[String, String] = Map.empty
    ): Either[String, CreateCustomerRequest] =
        for {
            validEmail <- validateEmail(email)
        } yield CreateCustomerRequest       (
            email        = validEmail,
            given_name   = None,
            family_name  = None,
            company_name = Some(companyName.trim).filter(_.nonEmpty),
            language     = language.code,
            metadata     = metadata
        )

case class GoCardlessCustomer(
    id         : String,
    email      : String,
    given_name : Option[String],
    family_name: Option[String],
    metadata   : Map[String, String]
)

case class CreateBillingRequestRequest(
    mandate_request: MandateRequest,
    payment_request: Option[PaymentRequest]      = None,
    metadata       : Map[String, String]         = Map.empty,
    links          : Option[BillingRequestLinks] = None
)

case class BillingRequestLinks(
    customer: Option[String] = None
)

case class MandateRequest(
    currency: String              = "EUR",
    metadata: Map[String, String] = Map.empty,
    scheme  : Option[String]      = Some("sepa_core")
)

case class PaymentRequest(
    amount     : Int, // Amount in cents
    currency   : String              = "EUR",
    description: String,
    metadata   : Map[String, String] = Map.empty
)

case class BillingRequestFlow(
    id               : String,
    authorisation_url: String,
    redirect_uri     : Option[String]
)

case class CreateBillingRequestFlowRequest(
    redirect_uri      : String,
    exit_uri          : Option[String]            = None,
    language          : GoCardlessLanguage,
    prefilled_customer: Option[PrefilledCustomer] = None,
    links             : BillingRequestFlowLinks
)

case class PrefilledCustomer(
    email        : String,
    given_name   : Option[String]                         = None,
    family_name  : Option[String]                         = None,
    company_name : Option[String]                         = None,
    address_line1: Option[String]                         = None,
    address_line2: Option[String]                         = None,
    address_line3: Option[String]                         = None,
    city         : Option[String]                         = None,
    region       : Option[String]                         = None,
    postal_code  : Option[String]                         = None,
    country_code : Option[CountryCode_ISO_3166_1_ALPHA_2] = None,
    language     : Option[String]                         = None
)

object PrefilledCustomer:
    def fromCustomerInfo(customerInfo: CustomerInfo): PrefilledCustomer =
        PrefilledCustomer        (
            email         = customerInfo.email,
            given_name    = customerInfo.givenName.filter(_.trim.nonEmpty),
            family_name   = customerInfo.familyName.filter(_.trim.nonEmpty),
            company_name  = customerInfo.companyName.filter(_.trim.nonEmpty),
            address_line1 = customerInfo.addressLine1.filter(_.trim.nonEmpty),
            address_line2 = customerInfo.addressLine2.filter(_.trim.nonEmpty),
            address_line3 = customerInfo.addressLine3.filter(_.trim.nonEmpty),
            city          = customerInfo.city.filter(_.trim.nonEmpty),
            region        = customerInfo.region.filter(_.trim.nonEmpty),
            postal_code   = customerInfo.postalCode.filter(_.trim.nonEmpty),
            country_code  = customerInfo.countryCode
        )

case class BillingRequestFlowLinks(
    billing_request: String
)

case class BillingRequest(
    id      : String,
    status  : String,
    metadata: Map[String, String],
    links   : Option[Map[String, String]] = None
)

case class GoCardlessPayment(
    id      : String,
    metadata: Map[String, String],
    links   : Option[Map[String, String]] = None
)

case class GoCardlessMandate(
    id                       : String,
    reference                : Option[String],
    created_at               : Option[String],
    next_possible_charge_date: Option[String]
)

case class MandateResponseEnvelope(mandates: GoCardlessMandate)

// Webhook models
case class WebhookEvent(
    id               : String,
    created_at       : String,
    action           : String,
    resource_type    : String,
    links            : Map[String, String],
    details          : WebhookEventDetails,
    metadata         : Option[Map[String, String]] = None,
    resource_metadata: Option[Map[String, String]] = None
)

case class WebhookEventDetails(
    origin         : String,
    cause          : String,
    description    : String,
    bank_account_id: Option[String] = None
)

case class WebhookPayload(
    events: List[WebhookEvent]
)

enum WebhookEventStatus:
    case Processed, Unknown, InvalidSignature

// inline final def deriveEncoder[A](using inline A: Mirror.Of[A]): Encoder.AsObject[A] =
//     Encoder.AsObject.derived[A]

final inline def deriveEncoder_andDeepDropNullValues[A](using inline A: Mirror.Of[A]): Encoder[A] =
    deriveEncoder[A].mapJson(_.deepDropNullValues)

// JSON codecs
given Encoder[CreateCustomerRequest] = deriveEncoder_andDeepDropNullValues
given [K: KeyEncoder, V: Encoder]: Encoder[Map[K, V]] = Encoder.encodeMap[K, V]
given Decoder[GoCardlessCustomer]          = deriveDecoder
given Encoder[MandateRequest]              = deriveEncoder_andDeepDropNullValues
given Encoder[PaymentRequest]              = deriveEncoder_andDeepDropNullValues
given Encoder[CreateBillingRequestRequest] = deriveEncoder_andDeepDropNullValues
given Decoder[BillingRequest]              = deriveDecoder
given Decoder[GoCardlessPayment]           = deriveDecoder

given Encoder[PrefilledCustomer] = deriveEncoder_andDeepDropNullValues

given Encoder[CreateBillingRequestFlowRequest] = deriveEncoder_andDeepDropNullValues

given Encoder[GoCardlessLanguage] = Encoder[String].contramap(_.code)
given Decoder[GoCardlessLanguage] =
    Decoder[String].emap(s => GoCardlessLanguage.values.find(_.code == s).toRight(s"Invalid GoCardless language: $s"))

given Encoder[CountryCode_ISO_3166_1_ALPHA_2] = Encoder[String].contramap(_.code)
given Decoder[CountryCode_ISO_3166_1_ALPHA_2] =
    Decoder[String].emap(s => CountryCode_ISO_3166_1_ALPHA_2.fromString(s).toRight(s"Invalid country code: $s"))

given Encoder[BillingRequestLinks]     = deriveEncoder_andDeepDropNullValues
given Encoder[BillingRequestFlowLinks] = deriveEncoder_andDeepDropNullValues
given Decoder[BillingRequestFlow]      = deriveDecoder
given Decoder[WebhookEventDetails]     = deriveDecoder
given Decoder[WebhookEvent]            = deriveDecoder
given Decoder[WebhookPayload]          = deriveDecoder

// Wrapper types for proper GoCardless API envelopes
case class CustomerEnvelope(customers: CreateCustomerRequest)
case class CustomerResponseEnvelope(customers: GoCardlessCustomer)
case class BillingRequestEnvelope(billing_requests: CreateBillingRequestRequest)
case class BillingRequestResponseEnvelope(billing_requests: BillingRequest)
case class BillingRequestFlowEnvelope(billing_request_flows: CreateBillingRequestFlowRequest)
case class BillingRequestFlowResponseEnvelope(billing_request_flows: BillingRequestFlow)
case class PaymentResponseEnvelope(payments: GoCardlessPayment)

// Encoders/Decoders for envelope types
given Encoder[CustomerEnvelope]                   = deriveEncoder_andDeepDropNullValues
given Decoder[CustomerResponseEnvelope]           = deriveDecoder
given Encoder[BillingRequestEnvelope]             = deriveEncoder_andDeepDropNullValues
given Decoder[BillingRequestResponseEnvelope]     = deriveDecoder
given Decoder[PaymentResponseEnvelope]            = deriveDecoder
given Encoder[BillingRequestFlowEnvelope]         = deriveEncoder_andDeepDropNullValues
given Decoder[BillingRequestFlowResponseEnvelope] = deriveDecoder
given Decoder[GoCardlessMandate]                  = deriveDecoder
given Decoder[MandateResponseEnvelope]            = deriveDecoder

class GoCardlessPaymentServiceImpl[F[_]: Async](
    httpClient       : Client[F],
    config           : GoCardlessConfig,
    emailService     : EmailService[F],
    orderService     : OrderService[F],
    customerRepo     : CustomerRepository[F],
    productCopyConfig: ProductCopyConfig
)                                              (implicit logger: Logger[F])
    extends PaymentService[F]:

    private def gcHeaders: Headers =
        Headers(
            Header.Raw(ci"Authorization", s"Bearer ${config.accessToken}"),
            Header.Raw(ci"Content-Type", "application/json"              ),
            Header.Raw(ci"GoCardless-Version", "2015-07-06"              )
        )

    private def makeRequest[A: Encoder, B: Decoder](
        method: Method,
        path  : String,
        body  : Option[A] = None
    ): F[B] =
        for {
            uri             <- Async[F].fromEither(
                Uri.fromString(s"${config.baseUrl}$path")
            )
            request = Request[F](
                method  = method,
                uri     = uri,
                headers = gcHeaders
            )
            requestWithBody <- body.fold          (Async[F].pure(request)                                   ) { b =>
                val jsonBody = b.asJson
                // SEC-006: Demote to debug, no JSON body in logs
                logger.debug(s"Making GoCardless request: $method $path") *>
                    Async[F].pure(request.withEntity(jsonBody))
            }
            _               <- body.fold          (logger.debug(s"Making GoCardless request: $method $path"))(_ => Async[F].unit)
            // SEC-006: Do not include raw response body in error messages (may contain PII)
            response        <- httpClient
                .expectOr[Json](requestWithBody) { resp =>
                    Async[F].pure(
                        new RuntimeException(
                            s"GoCardless API error: ${resp.status}"
                        )
                    )
                }
                .handleErrorWith { error =>
                    logger.warn(s"GoCardless HTTP request failed: $method $path, error: ${error.getMessage}") *>
                        Async[F].raiseError(error)
                }
            // _               <- response match {
            //     case _ =>
            //         // Log API errors if they occurred (this will be caught by expectOr if status was not successful)
            //         Async[F].unit
            // }
            // SEC-006: Demote response logging to debug, no full JSON
            _               <- logger.debug(s"GoCardless response received for: $method $path")
            result          <- Async[F].fromEither(response.as[B]).handleErrorWith { error =>
                logger.warn(
                    s"GoCardless JSON decode failed for $method $path: ${error.getMessage}"
                ) *>
                    Async[F].raiseError(error)
            }
        } yield result

    private def createBillingRequest(
        orderId                     : OrderId,
        amount                      : BigDecimal,
        customerEmail               : String,
        product                     : Product,
        existingGoCardlessCustomerId: Option[String] // = None
    )(using lang: BackendCompatibleLanguage): F[BillingRequest] =
        val amountInCents = (amount * 100).toInt
        val copy          = ProductCopyResolver.resolve(v1.Sku(product.sku), lang)(using productCopyConfig)
        val request       = CreateBillingRequestRequest(
            mandate_request = MandateRequest(
                currency = "EUR",
                metadata = Map("order_id" -> orderId.value.toString)
            ),
            payment_request = Some(
                PaymentRequest     (
                    amount      = amountInCents,
                    currency    = "EUR",
                    description = s"${copy.name} - ${copy.description}",
                    metadata    = Map("order_id" -> orderId.value.toString)
                )
            ),
            metadata        = Map(
                "order_id"       -> orderId.value.toString,
                "customer_email" -> customerEmail
            ),
            links           = existingGoCardlessCustomerId.map(customerId => BillingRequestLinks(customer = Some(customerId)))
        )

        val envelope = BillingRequestEnvelope(request)
        makeRequest[BillingRequestEnvelope, BillingRequestResponseEnvelope](
            Method.POST,
            "/billing_requests",
            Some(envelope)
        ).map(_.billing_requests)

    private def createBillingRequestFlow(
        billingRequestId: String,
        customerInfo    : CustomerInfo,
        language        : GoCardlessLanguage
    ): F[BillingRequestFlow] =
        // Add language parameter to redirect URI
        val redirectUriWithLang = s"${config.redirectUri}?lang=${language.code}"
        val exitUriWithLang     = s"${config.exitUri}?lang=${language.code}"

        val prefilledCustomer = PrefilledCustomer.fromCustomerInfo(customerInfo)

        val request = CreateBillingRequestFlowRequest(
            redirect_uri       = redirectUriWithLang,
            exit_uri           = Some(exitUriWithLang),
            language           = language,
            prefilled_customer = Some(prefilledCustomer),
            links              = BillingRequestFlowLinks(billing_request = billingRequestId)
        )

        val envelope = BillingRequestFlowEnvelope(request)
        makeRequest[BillingRequestFlowEnvelope, BillingRequestFlowResponseEnvelope](
            Method.POST,
            "/billing_request_flows",
            Some(envelope)
        ).map(_.billing_request_flows)

    def verifyWebhookSignature(body: String, signature: String): F[Boolean] =
        val isValid = Try {
            val mac       = Mac.getInstance("HmacSHA256")
            val secretKey = new SecretKeySpec(config.webhookSecret.getBytes("UTF-8"), "HmacSHA256")
            mac.init(secretKey)
            val computedSignature    = mac.doFinal(body.getBytes("UTF-8"))
            val computedSignatureHex = computedSignature.map("%02x".format(_)).mkString

            // Timing-safe comparison — prevents side-channel attacks
            java.security.MessageDigest.isEqual(
                computedSignatureHex.getBytes("UTF-8"),
                signature.getBytes           ("UTF-8")
            )
        }.getOrElse(false)

        (if isValid then logger.info(s"Webhook signature verification passed (env: ${config.environment})")
         else logger.error(s"Webhook signature verification FAILED (env: ${config.environment})")).map(_ => isValid)

    def getPayment(paymentId: String): F[GoCardlessPayment] =
        makeRequest[Unit, PaymentResponseEnvelope](
            Method.GET,
            s"/payments/$paymentId"
        ).map(_.payments)

    def getMandateForPayment(paymentId: String): F[Option[MandateSnapshot]] =
        for
            payment <- getPayment(paymentId)
            mandateId = payment.links.flatMap(_.get("mandate"))
            snapshotOpt <- mandateId.traverse(getMandateById)
        yield snapshotOpt

    def getMandateById(mandateId: String): F[MandateSnapshot] =
        makeRequest[Unit, MandateResponseEnvelope](Method.GET, s"/mandates/$mandateId")
            .map(extractMandateFromEnvelope)
            .map(_.getOrElse(MandateSnapshot(reference = None, createdDate = None, nextPossibleChargeDate = None)))

    def extractMandateFromEnvelope(mre: MandateResponseEnvelope): Option[MandateSnapshot] =
        val m = mre.mandates
        Some(
            MandateSnapshot             (
                reference              = m.reference,
                createdDate            = m.created_at.flatMap(parseIsoDate),
                nextPossibleChargeDate = m.next_possible_charge_date.flatMap(parseLocalDate)
            )
        )

    private def parseIsoDate(s: String): Option[java.time.LocalDate] =
        Try(java.time.OffsetDateTime.parse(s).toLocalDate).toOption

    private def parseLocalDate(s: String): Option[java.time.LocalDate] =
        Try(java.time.LocalDate.parse(s)).toOption

    // Helper method to extract OrderId from webhook event resource_metadata
    private def extractOrderId(event: WebhookEvent): F[Option[OrderId]] =
        extractGoCardlessPaymentId(event) match {
            case Some(paymentId) =>
                getPayment(paymentId).map { payment =>
                    payment.metadata.get("order_id").flatMap { orderIdStr =>
                        Try(UUID.fromString(orderIdStr)).toOption.map(OrderId.apply)
                    }
                }
            case None            => Async[F].pure(None)
        }

    // Helper method to extract GoCardlessPaymentId from webhook event links
    private def extractGoCardlessPaymentId(event: WebhookEvent): Option[String] =
        event.links.get("payment")

    // Helper method to update order status with error handling
    private def updatePaymentIdIfNeededAndPresent(orderId: OrderId, paymentId: Option[String]): F[Unit] =
        orderService
            .updatePaymentIdIfNeededAndPresent(orderId, paymentId, PaymentProvider.GoCardless)
            .flatMap { success =>
                if (success) {
                    logger.info(s"Updated order ${orderId.value} with paymentId $paymentId")
                } else {
                    logger.error(
                        s"Failed/Already done: Skipped update order ${orderId.value} with paymentId $paymentId"
                    )
                }
            }
            .handleErrorWith { error =>
                logger.error(s"Error updating order ${orderId.value}: ${error.getMessage}")
            }

    // Helper method to update order status with error handling
    private def updateOrderStatus(orderId: OrderId, status: OrderStatus): F[Unit] =
        orderService
            .updateOrderStatus(orderId, status)
            .flatMap { success =>
                if (success) {
                    logger.info(s"Updated order ${orderId.value} to status $status")
                } else {
                    logger.error(s"Failed to update order ${orderId.value} to status $status")
                }
            }
            .handleErrorWith { error =>
                logger.error(s"Error updating order ${orderId.value}: ${error.getMessage}")
            }

    // Helper method to send admin notifications with error handling
    private def sendAdminNotification(subject: String, message: String, orderId: Option[OrderId] = None)(using
        lang: BackendCompatibleLanguage
    ): F[Unit] =
        val notification = AdminNotification(
            adminEmail = EmailAddress.unsafeFromString(config.adminEmail),
            subject    = subject,
            message    = message,
            orderId    = orderId.map(_.value.toString)
        )

        emailService
            .sendAdminNotification(notification)
            .flatMap {
                case EmailSent          => logger.info(s"Admin notification sent: $subject")
                case EmailFailed(error) => logger.error(s"Failed to send admin notification: $error")
            }
            .handleErrorWith { error =>
                logger.error(s"Error sending admin notification: ${error.getMessage}")
            }

    def processWebhookEvent(event: WebhookEvent): F[WebhookEventStatus] =
        // Helper function to get language from order or use default
        def getLanguageForOrder(orderId: OrderId): F[BackendCompatibleLanguage] =
            orderService.findOrder(orderId).map {
                case Some(order) => order.language
                case None        => BackendCompatibleLanguage.DefaultLanguage
            }

        (event.resource_type, event.action) match {

            case ("payments", "created") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _ <- logger.info(s"Payment created for order ${orderId.value}")
                            _ <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        logger
                            .error(s"Payment created event ${event.id} missing or invalid order_id in metadata")
                            .map(_ => WebhookEventStatus.Processed)
                }

            case ("payments", "submitted") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _ <- logger.info(s"Payment submitted for order ${orderId.value}")
                            _ <- updateOrderStatus(orderId, OrderStatus.Processing)
                            _ <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        logger
                            .error(s"Payment submitted event ${event.id} missing or invalid order_id in metadata")
                            .map(_ => WebhookEventStatus.Processed)
                }

            case ("payments", "confirmed") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _ <- logger.info(s"Payment confirmed for order ${orderId.value}")
                            _ <- updateOrderStatus(orderId, OrderStatus.Confirmed)
                            _ <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        logger
                            .error(s"Payment confirmed event ${event.id} missing or invalid order_id in metadata")
                            .map(_ => WebhookEventStatus.Processed)
                }

            case ("payments", "paid_out") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _    <- logger.info(s"Payment paid out for order ${orderId.value}")
                            _    <- updateOrderStatus(orderId, OrderStatus.PaidOut)
                            lang <- getLanguageForOrder(orderId)
                            _    <- sendAdminNotification(
                                subject = "GoCardless Payment Paid Out",
                                message =
                                    s"Payment for order ${orderId.value} has been paid out. Event ID: ${event.id}, Description: ${event.details.description}",
                                orderId = Some(orderId)
                            )(using lang)
                            _    <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        for {
                            _ <- logger.error(
                                s"Payment paid out event ${event.id} missing or invalid order_id in metadata"
                            )
                            _ <- sendAdminNotification(
                                subject = "GoCardless Payment Paid Out (No Order ID)",
                                message =
                                    s"Payment paid out event received but no valid order_id found. Event ID: ${event.id}, Description: ${event.details.description}"
                            )(using BackendCompatibleLanguage.DefaultLanguage)
                        } yield WebhookEventStatus.Processed
                }

            case ("payments", "cancelled") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _    <- logger.error(
                                s"Payment cancelled for order ${orderId.value}: ${event.details.description}"
                            )
                            _    <- updateOrderStatus(orderId, OrderStatus.Cancelled)
                            lang <- getLanguageForOrder(orderId)
                            _    <- sendAdminNotification(
                                subject = "GoCardless Payment Cancelled",
                                message =
                                    s"Payment for order ${orderId.value} has been cancelled. Event ID: ${event.id}, Description: ${event.details.description}",
                                orderId = Some(orderId)
                            )(using lang)
                            _    <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        for {
                            _ <- logger.error(
                                s"Payment cancelled event ${event.id} missing or invalid order_id in metadata"
                            )
                            _ <- sendAdminNotification(
                                subject = "GoCardless Payment Cancelled (No Order ID)",
                                message =
                                    s"Payment cancelled event received but no valid order_id found. Event ID: ${event.id}, Description: ${event.details.description}"
                            )(using BackendCompatibleLanguage.DefaultLanguage)
                        } yield WebhookEventStatus.Processed
                }

            case ("payments", "failed") =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _    <- logger.error(
                                s"Payment failed for order ${orderId.value}: ${event.details.description}"
                            )
                            _    <- updateOrderStatus(orderId, OrderStatus.Failed)
                            lang <- getLanguageForOrder(orderId)
                            _    <- sendAdminNotification(
                                subject = "GoCardless Payment Failed",
                                message =
                                    s"Payment for order ${orderId.value} has failed. Event ID: ${event.id}, Description: ${event.details.description}",
                                orderId = Some(orderId)
                            )(using lang)
                            _    <- updatePaymentIdIfNeededAndPresent(orderId, extractGoCardlessPaymentId(event))
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        for {
                            _ <- logger.error(
                                s"Payment failed event ${event.id} missing or invalid order_id in metadata"
                            )
                            _ <- sendAdminNotification(
                                subject = "GoCardless Payment Failed (No Order ID)",
                                message =
                                    s"Payment failed event received but no valid order_id found. Event ID: ${event.id}, Description: ${event.details.description}"
                            )(using BackendCompatibleLanguage.DefaultLanguage)
                        } yield WebhookEventStatus.Processed
                }

            case ("mandates", _) =>
                for {
                    _ <- logger.info(s"Mandate event: ${event.action} - ${event.details.description}")
                    _ <- sendAdminNotification(
                        subject = s"GoCardless Mandate ${event.action.capitalize}",
                        message =
                            s"Mandate ${event.action} event received. Event ID: ${event.id}, Description: ${event.details.description}, Mandate ID: ${event.links.get("mandate").getOrElse("N/A")}"
                    )(using BackendCompatibleLanguage.DefaultLanguage)
                } yield WebhookEventStatus.Processed

            case ("billing_requests", _) =>
                extractOrderId(event).flatMap {
                    case Some(orderId) =>
                        for {
                            _    <- logger.info(s"Billing request ${event.action} for order ${orderId.value}")
                            lang <- getLanguageForOrder(orderId)
                            _    <- sendAdminNotification(
                                subject = s"GoCardless Billing Request ${event.action.capitalize}",
                                message =
                                    s"Billing request ${event.action} event received for order ${orderId.value}. Event ID: ${event.id}, Description: ${event.details.description}",
                                orderId = Some(orderId)
                            )(using lang)
                        } yield WebhookEventStatus.Processed
                    case None          =>
                        for {
                            _ <- logger.info(
                                s"Billing request ${event.action}: ${event.links.get("billing_request").getOrElse("N/A")}"
                            )
                            _ <- sendAdminNotification(
                                subject = s"GoCardless Billing Request ${event.action.capitalize}",
                                message =
                                    s"Billing request ${event.action} event received. Event ID: ${event.id}, Description: ${event.details.description}, Billing Request ID: ${event.links.get("billing_request").getOrElse("N/A")}"
                            )(using BackendCompatibleLanguage.DefaultLanguage)
                        } yield WebhookEventStatus.Processed
                }

            case _ =>
                for {
                    _ <- logger.info(s"Unhandled webhook event: ${event.resource_type}/${event.action}")
                    _ <- sendAdminNotification(
                        subject = s"GoCardless Unhandled Event: ${event.resource_type}/${event.action}",
                        message =
                            s"Received unhandled webhook event. Resource Type: ${event.resource_type}, Action: ${event.action}, Event ID: ${event.id}, Description: ${event.details.description}"
                    )(using BackendCompatibleLanguage.DefaultLanguage)
                } yield WebhookEventStatus.Unknown
        }

    def processWebhook(body: String, signature: String): F[Either[String, WebhookEventStatus]] =
        verifyWebhookSignature(body, signature)
            .flatMap { isValid =>
                if (isValid) {
                    for {
                        _                 <- logger.info("Processing GoCardless webhook")
                        parseResult       <- Async[F].fromEither(
                            io.circe.parser.decode[WebhookPayload](body)
                        )
                        whEventStatusList <- parseResult.events.traverse(processWebhookEvent)
                        _                 <- logger.info(s"Successfully processed ${parseResult.events.length} webhook events")
                    } yield Right(
                        if (whEventStatusList.forall(_ == WebhookEventStatus.Unknown)) WebhookEventStatus.Unknown
                        else WebhookEventStatus.Processed
                    )
                } else {
                    Async[F].pure(Right(WebhookEventStatus.InvalidSignature))
                }
            }
            .handleErrorWith { error =>
                logger.error(s"Error processing webhook: ${error.getMessage}") *>
                    Async[F].pure(Left(s"Error processing webhook: ${error.getMessage}"))
            }

    def createPaymentLink(
        orderId     : OrderId,
        amount      : BigDecimal,
        customerInfo: CustomerInfo
    ): F[String] =
        for {
            _ <- logger.info(
                s"Creating GoCardless payment link for order ${orderId.value} for ${customerInfo.customerType} customer: ${LogSanitizer.maskEmail(customerInfo.email)}"
            )

            gcLanguage = GoCardlessLanguage.fromBackendLanguage(customerInfo.language)

            // Get order and product first to access language
            order <- orderService.findOrder(orderId).flatMap { orderOpt =>
                Async[F].fromOption(orderOpt, new RuntimeException(s"Unexpected: could not find order ${orderId}"))
            }

            product <- orderService.findProduct(orderId).flatMap { productOpt =>
                Async[F].fromOption(
                    productOpt,
                    new RuntimeException(s"Unexpected: could not find product associated with order ${orderId}")
                )
            }

            // Step 1: Check if customer already exists in our database
            existingCustomer <- customerRepo.findByEmail(customerInfo.email)

            // Step 2: Create billing request with mandate and payment (using order language)
            // If customer has GoCardless ID, pass it to the billing request
            billingRequest <- createBillingRequest(
                orderId,
                amount,
                customerInfo.email,
                product,
                existingCustomer.flatMap(c =>
                    if (c.paymentProvider.contains(PaymentProvider.GoCardless)) c.paymentProviderId else None
                )
            )(using order.language)
            _              <- logger.info(s"Created billing request: ${billingRequest.id}")

            // Step 2.5: Extract and store GoCardless customer ID if not already stored
            gcCustomerIdOpt = billingRequest.links.flatMap(_.get("customer"))
            _ <- gcCustomerIdOpt match {
                case Some(gcCustomerId) =>

                    // Customer doesn't exist in our DB, or exists but doesn't have GoCardless info, so create or update
                    existingCustomer match {

                        // Customer exists but invalid provider or gcCustomerId
                        case Some(customer) =>

                            if (
                                customer.paymentProvider.contains(
                                    PaymentProvider.GoCardless
                                ) && customer.paymentProviderId.contains(gcCustomerId)
                            )
                                // Customer exists with valid provider and gcCustomerId
                                logger.info(s"Customer already has GoCardless ID: $gcCustomerId, skipping update")
                            else
                                // Update existing customer with GoCardless info
                                customerRepo
                                    .updatePaymentProvider(customer.id, gcCustomerId, PaymentProvider.GoCardless)
                                    .flatMap { success =>
                                        if (success) {
                                            logger.info(
                                                s"Updated existing customer ${customer.id.value} with GoCardless ID: $gcCustomerId"
                                            )
                                        } else {
                                            logger.warn(
                                                s"Failed to update customer ${customer.id.value} with GoCardless ID: $gcCustomerId"
                                            )
                                        }
                                    }

                        // Customer does not exist
                        case None =>
                            // Create new customer with GoCardless info
                            val newCustomerId = CustomerId(UUID.randomUUID())
                            val now           = Instant.now()
                            val newCustomer   = customerInfo
                                .toDomainCustomer(newCustomerId.value, now)
                                .copy  (
                                    paymentProvider   = Some(PaymentProvider.GoCardless),
                                    paymentProviderId = Some(gcCustomerId)
                                )
                            customerRepo.createFull(newCustomer).flatMap { success =>
                                if (success) {
                                    logger.info(
                                        s"Created new customer ${newCustomerId.value} with GoCardless ID: $gcCustomerId"
                                    )
                                } else {
                                    logger.warn(
                                        s"Failed to create customer ${newCustomerId.value} with GoCardless ID: $gcCustomerId"
                                    )
                                }
                            }
                    }

                case None =>
                    logger.warn("No customer ID found in billing request response")
                    Async[F].unit
            }

            // Step 3: Create billing request flow (hosted payment page)
            billingRequestFlow <- createBillingRequestFlow(
                billingRequest.id,
                customerInfo,
                gcLanguage
            )
            _                  <- logger.info(
                s"Created billing request flow with authorization URL: ${billingRequestFlow.authorisation_url}"
            )

            // Step 4: Send payment link via email
            _ <- sendPaymentLinkEmail(
                customerInfo.email,
                billingRequestFlow.authorisation_url,
                orderId,
                amount
            )(using order.language)

            _ <- logger.info(
                s"Payment link created and sent for order ${orderId.value}: ${billingRequestFlow.authorisation_url}"
            )
        } yield billingRequestFlow.authorisation_url

    private def sendPaymentLinkEmail(
        customerEmail: String,
        paymentUrl   : String,
        orderId      : OrderId,
        amount       : BigDecimal
    )(using lang: BackendCompatibleLanguage): F[Unit] =
        for {
            // Retrieve order to get language and verify it matches the context parameter
            orderOpt <- orderService.findOrder(orderId)
            order    <- Async[F].fromOption(
                orderOpt,
                new RuntimeException(s"Order ${orderId.value} not found when sending payment link email")
            )

            // Retrieve product to get name and description
            productOpt <- orderService.findProduct(orderId)
            product    <- Async[F].fromOption(
                productOpt,
                new RuntimeException(s"Product for order ${orderId.value} not found when sending payment link email")
            )

            // Use the language from the order to ensure consistency
            languageFromOrder = order.language

            productCopy = ProductCopyResolver.resolve(v1.Sku(product.sku), languageFromOrder)(using productCopyConfig)

            paymentLinkEmail = PaymentLinkEmail(
                email       = EmailAddress.unsafeFromString(customerEmail),
                paymentUrl  = paymentUrl,
                productName = productCopy.name,
                amount      = amount,
                currency    = product.currency.toString
            )

            // Send email using the order's language for proper translations
            _ <- emailService.sendUserPaymentLink(paymentLinkEmail)(using languageFromOrder)

            _ <- logger.info(
                s"Payment link email sent to $customerEmail for order ${orderId.value} with product: ${product.sku} in language: ${languageFromOrder.code}"
            )
        } yield ()

object GoCardlessPaymentServiceImpl:
    def create[F[_]: Async](
        httpClient       : Client[F],
        config           : GoCardlessConfig,
        emailService     : EmailService[F],
        orderService     : OrderService[F],
        customerRepo     : CustomerRepository[F],
        productCopyConfig: ProductCopyConfig
    )(implicit logger: Logger[F]): F[PaymentService[F]] =
        Async[F].pure(
            new GoCardlessPaymentServiceImpl[F](
                httpClient,
                config,
                emailService,
                orderService,
                customerRepo,
                productCopyConfig
            )
        )
