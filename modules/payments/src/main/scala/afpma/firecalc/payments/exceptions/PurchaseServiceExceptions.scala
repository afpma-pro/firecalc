/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.exceptions

import java.util.UUID

import afpma.firecalc.payments.util.LogSanitizer

/**
 * Base sealed trait for all purchase service errors.
 * Following cats-effect best practices for typed error handling.
 */
sealed abstract class PurchaseServiceError(
    message: String,
    cause  : Option[Throwable] = None
) extends RuntimeException(message, cause.orNull)
    with Product
    with Serializable {

    /** Error code for API responses */
    def errorCode: String = this.getClass.getSimpleName.replace("Exception", "").toLowerCase

    /** Additional context for logging and debugging */
    def context: Map[String, String] = Map.empty
}

// Rate limiting errors
final case class TooManyAttemptsException(
    token: String
) extends PurchaseServiceError(
        "Too many failed verification attempts for this purchase token"
    ) {
    override def context: Map[String, String] = Map(
        "purchaseToken" -> token
    )
}

final case class TooManyIntentsForEmailException(
    email: String
) extends PurchaseServiceError(
        "Too many purchase intents created for this email in the last hour"
    ) {
    override def context: Map[String, String] = Map(
        "email" -> LogSanitizer.maskEmail(email)
    )
}

// Authentication related errors
// SEC-006: Removed `code` field — auth codes must never be stored in exception objects
final case class InvalidOrExpiredCodeException(
    token: String
) extends PurchaseServiceError(
        "Invalid or expired authentication code for purchase token"
    ) {
    override def context: Map[String, String] = Map(
        "purchaseToken" -> token
    )
}

final case class AuthenticationFailedException(
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Authentication failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> reason
    )
}

// Resource not found errors
// SEC-006: Removed `code` field — auth codes must never be stored in exception objects
final case class PurchaseIntentNotFoundException(
    token: String
) extends PurchaseServiceError(
        "Purchase intent not found for the provided token"
    ) {
    override def context: Map[String, String] = Map(
        "purchaseToken" -> token
    )
}

final case class CustomerNotFoundException(
    customerId: UUID
) extends PurchaseServiceError(
        "Customer not found"
    ) {
    override def context: Map[String, String] = Map(
        "customerId" -> customerId.toString
    )
}

final case class ProductNotFoundException(
    productId: String
) extends PurchaseServiceError(
        "Product not found"
    ) {
    override def context: Map[String, String] = Map(
        "productId" -> productId
    )
}

final case class OrderNotFoundException(
    orderId: String
) extends PurchaseServiceError(
        "Order not found"
    ) {
    override def context: Map[String, String] = Map(
        "orderId" -> orderId
    )
}

final case class PaymentIdMismatchException(
    orderId          : String,
    existingPaymentId: String,
    newPaymentId     : String
) extends PurchaseServiceError(
        "Payment ID mismatch for order"
    ) {
    override def context: Map[String, String] = Map(
        "orderId"           -> orderId,
        "existingPaymentId" -> existingPaymentId,
        "newPaymentId"      -> newPaymentId
    )
}

// Business logic errors
final case class OrderCreationFailedException(
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Order creation failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> reason
    )
}

final case class OrderUpdateFailedException(
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Order update failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> reason
    )
}

final case class PaymentLinkCreationFailedException(
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Payment link creation failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> reason
    )
}

final case class JWTGenerationFailedException(
    customerId: UUID,
    cause     : Option[Throwable] = None
) extends PurchaseServiceError(
        "JWT token generation failed",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "customerId" -> customerId.toString
    )
}

// Validation errors
final case class CustomerValidationException(
    validationErrors: List[String]
) extends PurchaseServiceError(
        s"Customer validation failed: ${validationErrors.mkString(", ")}"
    ) {
    override def context: Map[String, String] = Map(
        "validationErrors" -> validationErrors.mkString("; "),
        "errorCount"       -> validationErrors.length.toString
    )
}

final case class PurchaseIntentProcessingException(
    token : String,
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Purchase intent processing failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "purchaseToken" -> token,
        "reason"        -> reason
    )
}

// Report generation errors
final case class YAMLLoadingException(
    filename: String,
    reason  : String,
    cause   : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to load YAML from file: $filename",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "filename" -> filename,
        "reason"   -> reason
    )
}

final case class PDFGenerationFailedException(
    filename: String,
    reason  : String,
    cause   : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to generate PDF from file: $filename",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "filename" -> filename,
        "reason"   -> reason
    )
}

final case class InvoiceNumberGenerationFailedException(
    msg  : String,
    cause: Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to generate invoice number : $msg",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> msg
    )
}

final case class ReportFileAccessException(
    filename : String,
    operation: String,
    cause    : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to access report file: $filename during $operation",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "filename"  -> filename,
        "operation" -> operation
    )
}

// Configuration errors
final case class ConfigurationNotFoundException(
    configPath: String
) extends PurchaseServiceError(
        s"Configuration file not found: $configPath"
    ) {
    override def context: Map[String, String] = Map(
        "configPath" -> configPath
    )
}

final case class ConfigurationLoadException(
    configPath: String,
    reason    : String,
    cause     : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to load configuration from: $configPath",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "configPath" -> configPath,
        "reason"     -> reason
    )
}

final case class ProductMetadataMissingException(
    orderId: UUID
) extends PurchaseServiceError(
        "Product metadata missing for order"
    ) {
    override def context: Map[String, String] = Map(
        "orderId" -> orderId.toString
    )
}

// Invoice errors
final case class InvoiceNumberMissingException(
    orderId: UUID
) extends PurchaseServiceError(
        "Invoice number missing for order"
    ) {
    override def context: Map[String, String] = Map(
        "orderId" -> orderId.toString
    )
}

final case class InvoiceGenerationFailedException(
    orderId: UUID,
    reason : String,
    cause  : Option[Throwable] = None
) extends PurchaseServiceError(
        "Invoice generation failed for order",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "orderId" -> orderId.toString,
        "reason"  -> reason
    )
}

final case class InvoiceEmailFailedException(
    orderId      : UUID,
    customerEmail: String,
    reason       : String,
    cause        : Option[Throwable] = None
) extends PurchaseServiceError(
        "Failed to send invoice email for order",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "orderId"       -> orderId.toString,
        "customerEmail" -> LogSanitizer.maskEmail(customerEmail),
        "reason"        -> reason
    )
}

final case class EmailSendingFailedException(
    orderId  : UUID,
    recipient: String,
    reason   : String,
    cause    : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Failed to send email for order ${orderId.toString}",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "orderId"   -> orderId.toString,
        "recipient" -> LogSanitizer.maskEmail(recipient),
        "reason"    -> reason
    )
}

// Webhook errors
final case class WebhookSignatureMissingException() extends PurchaseServiceError("Missing webhook signature header")

final case class WebhookSignatureInvalidException(
    signature: String
) extends PurchaseServiceError(
        "Invalid webhook signature"
    ) {
    override def context: Map[String, String] = Map(
        "signatureLength" -> signature.length.toString
    )
}

final case class WebhookProcessingException(
    reason: String,
    cause : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Webhook processing failed: $reason",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "reason" -> reason
    )
}

// Idempotency errors
final case class AlreadyProcessedException(
    token: String
) extends PurchaseServiceError(
        "This purchase has already been processed"
    ) {
    override def context: Map[String, String] = Map(
        "purchaseToken" -> token
    )
}

// Repository/Service errors
final case class EntityNotFoundException[T](
    entityType: String,
    entityId  : String
) extends PurchaseServiceError(
        s"$entityType not found"
    ) {
    override def context: Map[String, String] = Map(
        "entityType" -> entityType,
        "entityId"   -> entityId
    )
}

final case class DatabaseException(
    operation: String,
    reason   : String,
    cause    : Option[Throwable] = None
) extends PurchaseServiceError(
        s"Database operation failed: $operation",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "operation" -> operation,
        "reason"    -> reason
    )
}

final case class ExternalServiceException(
    service  : String,
    operation: String,
    reason   : String,
    cause    : Option[Throwable] = None
) extends PurchaseServiceError(
        s"External service $service failed during $operation",
        cause
    ) {
    override def context: Map[String, String] = Map(
        "service"   -> service,
        "operation" -> operation,
        "reason"    -> reason
    )
}

final case class ProductFireboxMismatchException(
    productId: String,
    message  : String
) extends PurchaseServiceError(
        message
    ) {
    override def errorCode: String              = "PRODUCT_FIREBOX_MISMATCH"
    override def context  : Map[String, String] = Map(
        "productId" -> productId
    )
}

final case class FireboxTypeDisabledException(
    typeName: String
) extends PurchaseServiceError(
        s"Firebox type '$typeName' is currently disabled for purchase"
    ) {
    override def errorCode: String              = "firebox_type_disabled"
    override def context  : Map[String, String] = Map(
        "firebox_type" -> typeName
    )
}

/**
 * Raised when a decoded FireCalc project DTO tree contains an `IsBackendForbidden`
 * instance — i.e. a dev-only DSL escape hatch (such as
 * `SetInnerShapePreventSectionGeometryChangeAuto`) was sent to the payments backend.
 *
 * Checked early, in `createPurchaseIntent`, before any database side effect, by
 * decoding+migrating the uploaded project YAML and running `BackendForbiddenDtoChecker`.
 * Mirrors the `FireboxTypeDisabledException` guard pattern. Mapped to HTTP 403
 * Forbidden by `PurchaseRoutes.handlePurchaseServiceError`.
 */
final case class ForbiddenDtoException(
    typeName: String
) extends PurchaseServiceError(
        s"Backend-forbidden DTO type '$typeName' is not allowed in a project submitted to the backend"
    ) {
    override def errorCode: String              = "backend_forbidden_dto"
    override def context  : Map[String, String] = Map(
        "dto_type" -> typeName
    )
}
