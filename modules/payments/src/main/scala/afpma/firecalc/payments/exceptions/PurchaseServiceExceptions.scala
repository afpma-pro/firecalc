/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.exceptions

import java.util.UUID

/**
 * Base sealed trait for all purchase service errors.
 * Following cats-effect best practices for typed error handling.
 */
sealed abstract class PurchaseServiceError(
  message: String, 
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with Product with Serializable {
  
  /** Error code for API responses */
  def errorCode: String = this.getClass.getSimpleName.replace("Exception", "").toLowerCase
  
  /** Additional context for logging and debugging */
  def context: Map[String, String] = Map.empty
}

// Authentication related errors
final case class InvalidOrExpiredCodeException(
  token: String, 
  code: String
) extends PurchaseServiceError(
  s"Invalid or expired authentication code for purchase token"
) {
  override def context: Map[String, String] = Map(
    "purchaseToken" -> token,
    "codeLength" -> code.length.toString
  )
}

final case class AuthenticationFailedException(
  reason: String, 
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Authentication failed: $reason", 
  cause
) {
  override def context: Map[String, String] = Map(
    "reason" -> reason
  )
}

// Resource not found errors
final case class PurchaseIntentNotFoundException(
  token: String, 
  code: String
) extends PurchaseServiceError(
  s"Purchase intent not found for the provided token and code"
) {
  override def context: Map[String, String] = Map(
    "purchaseToken" -> token,
    "codeLength" -> code.length.toString
  )
}

final case class CustomerNotFoundException(
  customerId: UUID
) extends PurchaseServiceError(
  s"Customer not found"
) {
  override def context: Map[String, String] = Map(
    "customerId" -> customerId.toString
  )
}

final case class ProductNotFoundException(
  productId: String
) extends PurchaseServiceError(
  s"Product not found"
) {
  override def context: Map[String, String] = Map(
    "productId" -> productId
  )
}

final case class OrderNotFoundException(
  orderId: String
) extends PurchaseServiceError(
  s"Order not found"
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId
  )
}

final case class PaymentIdMismatchException(
  orderId: String,
  existingPaymentId: String,
  newPaymentId: String
) extends PurchaseServiceError(
  s"Payment ID mismatch for order"
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId,
    "existingPaymentId" -> existingPaymentId,
    "newPaymentId" -> newPaymentId
  )
}

// Business logic errors
final case class OrderCreationFailedException(
  reason: String, 
  cause: Option[Throwable] = None
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
  cause: Option[Throwable] = None
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
  cause: Option[Throwable] = None
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
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"JWT token generation failed", 
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
    "errorCount" -> validationErrors.length.toString
  )
}

final case class PurchaseIntentProcessingException(
  token: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Purchase intent processing failed: $reason",
  cause
) {
  override def context: Map[String, String] = Map(
    "purchaseToken" -> token,
    "reason" -> reason
  )
}

// Report generation errors
final case class YAMLLoadingException(
  filename: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to load YAML from file: $filename",
  cause
) {
  override def context: Map[String, String] = Map(
    "filename" -> filename,
    "reason" -> reason
  )
}

final case class PDFGenerationFailedException(
  filename: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to generate PDF from file: $filename",
  cause
) {
  override def context: Map[String, String] = Map(
    "filename" -> filename,
    "reason" -> reason
  )
}

final case class InvoiceNumberGenerationFailedException(
  msg: String,
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
  filename: String,
  operation: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to access report file: $filename during $operation",
  cause
) {
  override def context: Map[String, String] = Map(
    "filename" -> filename,
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
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to load configuration from: $configPath",
  cause
) {
  override def context: Map[String, String] = Map(
    "configPath" -> configPath,
    "reason" -> reason
  )
}

final case class ProductMetadataMissingException(
  orderId: UUID
) extends PurchaseServiceError(
  s"Product metadata missing for order"
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId.toString
  )
}

// Invoice errors
final case class InvoiceNumberMissingException(
  orderId: UUID
) extends PurchaseServiceError(
  s"Invoice number missing for order"
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId.toString
  )
}

final case class InvoiceGenerationFailedException(
  orderId: UUID,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Invoice generation failed for order",
  cause
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId.toString,
    "reason" -> reason
  )
}

final case class InvoiceEmailFailedException(
  orderId: UUID,
  customerEmail: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to send invoice email for order",
  cause
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId.toString,
    "customerEmail" -> customerEmail,
    "reason" -> reason
  )
}

final case class EmailSendingFailedException(
  orderId: UUID,
  recipient: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Failed to send email for order ${orderId.toString}",
  cause
) {
  override def context: Map[String, String] = Map(
    "orderId" -> orderId.toString,
    "recipient" -> recipient,
    "reason" -> reason
  )
}

// Webhook errors
final case class WebhookSignatureMissingException() 
  extends PurchaseServiceError("Missing webhook signature header")

final case class WebhookSignatureInvalidException(
  signature: String
) extends PurchaseServiceError(
  s"Invalid webhook signature"
) {
  override def context: Map[String, String] = Map(
    "signatureLength" -> signature.length.toString
  )
}

final case class WebhookProcessingException(
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Webhook processing failed: $reason",
  cause
) {
  override def context: Map[String, String] = Map(
    "reason" -> reason
  )
}

// Repository/Service errors
final case class EntityNotFoundException[T](
  entityType: String,
  entityId: String
) extends PurchaseServiceError(
  s"$entityType not found"
) {
  override def context: Map[String, String] = Map(
    "entityType" -> entityType,
    "entityId" -> entityId
  )
}

final case class DatabaseException(
  operation: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"Database operation failed: $operation",
  cause
) {
  override def context: Map[String, String] = Map(
    "operation" -> operation,
    "reason" -> reason
  )
}

final case class ExternalServiceException(
  service: String,
  operation: String,
  reason: String,
  cause: Option[Throwable] = None
) extends PurchaseServiceError(
  s"External service $service failed during $operation",
  cause
) {
  override def context: Map[String, String] = Map(
    "service" -> service,
    "operation" -> operation,
    "reason" -> reason
  )
}
