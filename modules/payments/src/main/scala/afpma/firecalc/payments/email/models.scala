/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import java.time.Instant

// Base email types
opaque type EmailAddress = String
object EmailAddress:
    // RFC 5322 simplified regex - rejects double @@
    private val EMAIL_REGEX = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
    
    def fromString(email: String): Either[String, EmailAddress] =
        if email.trim.isEmpty then
            Left("Email address cannot be empty")
        else if email.contains("@@") then
            Left("Email address contains double @ character")
        else if EMAIL_REGEX.matches(email.trim) then
            Right(email.trim)
        else
            Left(s"Invalid email format: $email")
    
    // Unsafe constructor for when validation already happened
    def unsafeFromString(email: String): EmailAddress = email
    
    extension (email: EmailAddress)
        def value: String = email

case class EmailSubject(value: String) extends AnyVal
case class EmailContent(value: String) extends AnyVal

// Email attachments
case class EmailAttachment(
  filename: String,
  contentType: String,
  content: Array[Byte]
)

// Base email message
case class EmailMessage(
  to: EmailAddress,
  subject: EmailSubject,
  content: EmailContent,
  attachments: List[EmailAttachment] = List.empty
)

// Specific email types based on your requirements
sealed trait EmailType

case class AuthenticationCodeEmail(
  email: EmailAddress,
  code: String,
  productName: Option[String] = None,
  isNewUser: Boolean,
  amount: BigDecimal,
) extends EmailType

case class InvoiceEmail(
  email: EmailAddress,
  orderId: String,
  invoiceNumber: String,
  productName: String,
  customerName: String,
  amount: BigDecimal,
  currency: String,
  pdfBytes: Array[Byte],
  language: BackendCompatibleLanguage
) extends EmailType

case class PaymentLinkEmail(
  email: EmailAddress,
  paymentUrl: String,
  productName: String,
  amount: BigDecimal,
  currency: String
) extends EmailType

case class PdfReportEmail(
  email: EmailAddress,
  reportName: String,
  customerName: String,
  orderId: Option[String] = None,
  pdfBytes: Array[Byte],
  language: BackendCompatibleLanguage
) extends EmailType

case class AdminNotification(
  adminEmail: EmailAddress,
  subject: String,
  message: String,
  orderId: Option[String] = None
) extends EmailType

// Email sending result
sealed trait EmailResult
case object EmailSent extends EmailResult
case class EmailFailed(error: String) extends EmailResult
