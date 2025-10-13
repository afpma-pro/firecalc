/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email

import cats.effect.Async
import org.typelevel.log4cats.Logger
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.impl.*

trait EmailService[F[_]] {

  /** Send authentication code email to user */
  def sendUserAuthenticationCode(authCode: AuthenticationCodeEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send invoice only to user */
  def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send invoice with PDF report attachment to user */
  def sendUserInvoiceWithReport(invoice: InvoiceEmail, pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send invoice only to admin (no PDF report) */
  def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send payment link email to user */
  def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send PDF report to user */
  def sendUserPdfReport(pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult]

  /** Send notification to administrators */
  def sendAdminNotification(notification: AdminNotification): F[EmailResult]

  /** Generic email sending method */
  def sendEmail(message: EmailMessage): F[EmailResult]
}

object EmailService {

  /** Create an EmailService instance with the provided configuration */
  def create[F[_]: Async: Logger](config: EmailConfig): F[EmailService[F]] = 
    Async[F].pure(new EmailServiceImpl[F](config))

  def createMocked[F[_]: Async: Logger]: F[EmailService[F]] = 
    Async[F].pure(new EmailServiceMocked[F])
}
