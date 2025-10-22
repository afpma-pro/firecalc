/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email.impl

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*

class EmailServiceMocked[F[_]: Async: Logger] extends EmailService[F] {

  private val logger = Logger[F]

  override def sendUserAuthenticationCode(authCode: AuthenticationCodeEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val subject = if (authCode.isNewUser) {
      authCode.productName.fold("Welcome! Complete your registration")(product => 
        s"Welcome! Complete your purchase - $product")
    } else {
      authCode.productName.fold("Your login code")(product => 
        s"Complete your purchase - $product")
    }

    for {
      _ <- logger.info(s"[MOCK] Sending user authentication code email")
      _ <- logger.info(s"  To: ${authCode.email.value}")
      _ <- logger.info(s"  Subject: $subject")
      _ <- logger.info(s"  Code: ${authCode.code}")
      _ <- logger.info(s"  Is new user: ${authCode.isNewUser}")
      _ <- authCode.productName.traverse_(product => logger.info(s"  Product: $product"))
    } yield EmailSent
  }

  override def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending user invoice email")
      _ <- logger.info(s"  To: ${invoice.email.value}")
      _ <- logger.info(s"  Subject: Your invoice ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Order ID: ${invoice.orderId}")
      _ <- logger.info(s"  Invoice Number: ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Product: ${invoice.productName}")
      _ <- logger.info(s"  Customer: ${invoice.customerName}")
      _ <- logger.info(s"  Amount: ${invoice.amount} ${invoice.currency}")
      _ <- logger.info(s"  Invoice PDF size: ${invoice.pdfBytes.length} bytes")
    } yield EmailSent
  }

  override def sendUserInvoiceWithReport(invoice: InvoiceEmail, pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending user invoice with report email")
      _ <- logger.info(s"  To: ${invoice.email.value}")
      _ <- logger.info(s"  Subject: Your invoice ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Order ID: ${invoice.orderId}")
      _ <- logger.info(s"  Invoice Number: ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Product: ${invoice.productName}")
      _ <- logger.info(s"  Customer: ${invoice.customerName}")
      _ <- logger.info(s"  Amount: ${invoice.amount} ${invoice.currency}")
      _ <- logger.info(s"  Invoice PDF size: ${invoice.pdfBytes.length} bytes")
      _ <- logger.info(s"  Report Name: ${pdfReport.reportName}")
      _ <- logger.info(s"  Report PDF size: ${pdfReport.pdfBytes.length} bytes")
    } yield EmailSent
  }

  override def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending admin invoice email (no PDF attachment)")
      _ <- logger.info(s"  To: ${invoice.email.value}")
      _ <- logger.info(s"  Subject: [ADMIN] Invoice ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Order ID: ${invoice.orderId}")
      _ <- logger.info(s"  Invoice Number: ${invoice.invoiceNumber}")
      _ <- logger.info(s"  Product: ${invoice.productName}")
      _ <- logger.info(s"  Customer: ${invoice.customerName}")
      _ <- logger.info(s"  Amount: ${invoice.amount} ${invoice.currency}")
    } yield EmailSent
  }

  override def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending user payment link email")
      _ <- logger.info(s"  To: ${paymentLink.email.value}")
      _ <- logger.info(s"  Subject: Complete your payment - ${paymentLink.productName}")
      _ <- logger.info(s"  Product: ${paymentLink.productName}")
      _ <- logger.info(s"  Amount: ${paymentLink.amount}")
      _ <- logger.info(s"  Payment URL: ${paymentLink.paymentUrl}")
    } yield EmailSent
  }

  override def sendUserPdfReport(pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending user PDF report email")
      _ <- logger.info(s"  To: ${pdfReport.email.value}")
      _ <- logger.info(s"  Subject: Your PDF Report: ${pdfReport.reportName}")
      _ <- logger.info(s"  Report Name: ${pdfReport.reportName}")
      _ <- logger.info(s"  Customer: ${pdfReport.customerName}")
      _ <- pdfReport.orderId.traverse_(orderId => logger.info(s"  Order ID: $orderId"))
      _ <- logger.info(s"  PDF size: ${pdfReport.pdfBytes.length} bytes")
    } yield EmailSent
  }

  override def sendAdminNotification(notification: AdminNotification): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending admin notification email")
      _ <- logger.info(s"  To: ${notification.adminEmail.value}")
      _ <- logger.info(s"  Subject: [ADMIN] ${notification.subject}")
      _ <- logger.info(s"  Message: ${notification.message}")
      _ <- notification.orderId.traverse_(orderId => logger.info(s"  Order ID: $orderId"))
    } yield EmailSent
  }

  override def sendUserNotification(notification: UserNotification): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending user notification email")
      _ <- logger.info(s"  To: ${notification.email.value}")
      _ <- logger.info(s"  Error Type: ${notification.error.getClass.getSimpleName}")
      _ <- logger.info(s"  Error Message: ${notification.error.getMessage}")
      _ <- logger.info(s"  Language: ${notification.language.code}")
      _ <- notification.orderId.traverse_(orderId => logger.info(s"  Order ID: $orderId"))
    } yield EmailSent
  }

  override def sendEmail(message: EmailMessage): F[EmailResult] = {
    for {
      _ <- logger.info(s"[MOCK] Sending generic email")
      _ <- logger.info(s"  To: ${message.to.value}")
      _ <- logger.info(s"  Subject: ${message.subject.value}")
      _ <- logger.info(s"  Content length: ${message.content.value.length} characters")
      _ <- logger.info(s"  Attachments: ${message.attachments.length}")
      _ <- message.attachments.traverse_ { att =>
        logger.info(s"    - ${att.filename} (${att.contentType}, ${att.content.length} bytes)")
      }
    } yield EmailSent
  }
}
