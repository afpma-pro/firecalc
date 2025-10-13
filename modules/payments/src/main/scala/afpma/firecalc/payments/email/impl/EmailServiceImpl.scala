/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email.impl

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import emil.*
import emil.javamail.*
import emil.builder.Attach
import emil.javamail.syntax.MimeTypeTypeOps
import emil.builder.MailBuilder
import emil.builder.From
import emil.builder.To
import emil.builder.Subject
import emil.builder.HtmlBody
import emil.builder.AttachUrl
import emil.builder.AttachFile
import emil.builder.AttachStream
import emil.builder.Trans
import afpma.firecalc.payments.i18n.implicits.given
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*
import io.taig.babel.{Locale, Locales}

class EmailServiceImpl[F[_]: Async: Logger](config: EmailConfig) extends EmailService[F] {

  import BackendCompatibleLanguage.given

  private val logger = Logger[F]

  // Emil configuration
  private val smtpConfig = MailConfig(
    url = s"smtp://${config.smtpHost}:${config.smtpPort}",
    user = config.username,
    password = config.password,
    // Port 465 uses implicit SSL, port 587 uses explicit TLS (STARTTLS)
    sslType = if (config.smtpPort == 465) SSLType.SSL 
              else if (config.useTLS) SSLType.StartTLS 
              else SSLType.NoEncryption
  )

  private val emil = JavaMailEmil[F]()

  override def sendUserAuthenticationCode(authCode: AuthenticationCodeEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    
    val subject = if (authCode.isNewUser) {
      translations.emails.authentication.subject_new_user
    } else {
      translations.emails.authentication.subject_existing_user
    }

    val content = buildAuthCodeContent(authCode)

    val mail = createEmilMail(
      to            = authCode.email,
      subject       = subject,
      htmlContent   = content
    )

    for {
      _ <- logger.info(s"Sending authentication code to ${authCode.email.value}")
      result <- sendEmilMail(mail)
      _ <- logger.info(s"Authentication code email result: $result")
    } yield result
  }

  override def sendUserInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    val attachments = List(createInvoiceAttachment(invoice))
    
    val mail = createEmilMail(
      to = invoice.email,
      subject = translations.emails.invoice.subject(invoice.invoiceNumber),
      htmlContent = buildInvoiceContent(invoice),
      attachments = attachments
    )

    sendMailWithLogging(
      mail = mail,
      operationDescription = s"invoice ${invoice.invoiceNumber}",
      recipient = invoice.email.value
    )
  }

  override def sendUserInvoiceWithReport(invoice: InvoiceEmail, pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    val attachments = List(
      createInvoiceAttachment(invoice),
      createReportAttachment(pdfReport)
    )
    
    val mail = createEmilMail(
      to = invoice.email,
      subject = translations.emails.invoice.subject(invoice.invoiceNumber),
      htmlContent = buildInvoiceWithReportContent(invoice, pdfReport),
      attachments = attachments
    )

    sendMailWithLogging(
      mail = mail,
      operationDescription = s"invoice ${invoice.invoiceNumber} with report ${pdfReport.reportName}",
      recipient = invoice.email.value
    )
  }

  override def sendUserPaymentLink(paymentLink: PaymentLinkEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    
    val mail = createEmilMail(
      to = paymentLink.email,
      subject = translations.emails.payment_link.subject(paymentLink.productName),
      htmlContent = buildPaymentLinkContent(paymentLink)
    )

    for {
      _ <- logger.info(s"Sending payment link to ${paymentLink.email.value}")
      result <- sendEmilMail(mail)
      _ <- logger.info(s"Payment link email result: $result")
    } yield result
  }

  override def sendAdminInvoice(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    
    // Admin invoice with PDF attachment
    val mail = createEmilMail(
      to = invoice.email,
      subject = s"[ADMIN] ${translations.emails.invoice.subject(invoice.invoiceNumber)}",
      htmlContent = buildInvoiceContent(invoice),
      attachments = List(createInvoiceAttachment(invoice))
    )

    for {
      _ <- logger.info(s"Sending admin invoice ${invoice.invoiceNumber} to ${invoice.email.value}")
      result <- sendEmilMail(mail)
      _ <- logger.info(s"Admin invoice email result: $result")
    } yield result
  }

  override def sendUserPdfReport(pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): F[EmailResult] = {
    val translations = I18N_Payments
    val attachments = List(createReportAttachment(pdfReport))

    val mail = createEmilMail(
      to = pdfReport.email,
      subject = translations.emails.pdf_report.subject(pdfReport.reportName),
      htmlContent = buildPdfReportContent(pdfReport),
      attachments = attachments
    )

    sendMailWithLogging(
      mail = mail,
      operationDescription = s"PDF report ${pdfReport.reportName}",
      recipient = pdfReport.email.value
    )
  }

  override def sendAdminNotification(notification: AdminNotification): F[EmailResult] = {
    val mail = createEmilMail(
      to = notification.adminEmail,
      subject = s"[ADMIN] ${notification.subject}",
      htmlContent = buildAdminNotificationContent(notification)
    )

    for {
      _ <- logger.info(s"Sending admin notification: ${notification.subject}")
      result <- sendEmilMail(mail)
      _ <- logger.info(s"Admin notification result: $result")
    } yield result
  }

  override def sendEmail(message: EmailMessage): F[EmailResult] = {
    val attachments = message.attachments.map { att =>
      AttachStream[F](
        filename = Some(att.filename),
        mimeType = MimeType.parse(att.contentType).getOrElse(MimeType.octetStream),
        data     = fs2.Stream.emits(att.content)
      )
    }

    val mail = createEmilMail(
      to            = message.to,
      subject       = message.subject.value,
      htmlContent   = message.content.value,
      attachments   = attachments
    )

    sendEmilMail(mail)
  }

  // Private helper methods using Emil

  private def createEmilMail(
    to: EmailAddress,
    subject: String,
    htmlContent: String,
    attachments: List[Attach[F]] = List.empty
  ): Mail[F] = {

    val parts = Seq[Trans[F]](
        From(config.fromAddress.value),
        To(to.value),
        Subject(subject),
        HtmlBody(htmlContent),
    ) ++ attachments.toSeq

    MailBuilder.build(parts*)
  }

  private def sendEmilMail(mail: Mail[F]): F[EmailResult] = {
    emil(smtpConfig).send(mail).map(_ => EmailSent).handleErrorWith { error =>
      for {
        _ <- logger.error(error)(s"Failed to send email")
      } yield EmailFailed(error.getMessage)
    }
  }

  // DRY helper methods for common operations

  private def createInvoiceAttachment(invoice: InvoiceEmail) = {
    AttachStream[F](
      filename = Some(s"invoice-${invoice.invoiceNumber}.pdf"),
      mimeType = MimeType.pdf,
      data     = fs2.Stream.emits(invoice.pdfBytes)
    )
  }

  private def createReportAttachment(pdfReport: PdfReportEmail) = {
    AttachStream[F](
      filename = Some(s"${pdfReport.reportName}.pdf"),
      mimeType = MimeType.pdf,
      data     = fs2.Stream.emits(pdfReport.pdfBytes)
    )
  }

  private def sendMailWithLogging(
    mail: Mail[F],
    operationDescription: String,
    recipient: String
  ): F[EmailResult] = {
    for {
      _ <- logger.info(s"Sending $operationDescription to $recipient")
      result <- sendEmilMail(mail)
      _ <- logger.info(s"Email result for $operationDescription: $result")
    } yield result
  }

  // Content builders with translations
  private def buildAuthCodeContent(authCode: AuthenticationCodeEmail)(using language: BackendCompatibleLanguage): String = {
    val translations = I18N_Payments
    
    val introText = if (authCode.isNewUser) {
      translations.emails.authentication.intro_new_user
    } else {
      translations.emails.authentication.intro_existing_user
    }
    
    val productText = authCode.productName.fold("")(name => 
      s"<p>${translations.emails.authentication.product_info(name)}</p>"
    )

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.authentication.greeting}</h2>
    |  <p>$introText</p>
    |  <p>${translations.emails.authentication.code_label}: <strong>${authCode.code}</strong></p>
    |  $productText
    |  <p>${translations.emails.authentication.footer}</p>
    |  <p><em>${translations.emails.authentication.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }

  private def buildInvoiceContent(invoice: InvoiceEmail)(using language: BackendCompatibleLanguage): String = {
    val translations = I18N_Payments
    val currentDate = java.time.LocalDate.now().toString

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.invoice.greeting}</h2>
    |  <p>${translations.emails.invoice.intro}</p>
    |  <h3>${translations.emails.invoice.details_heading}</h3>
    |  <ul>
    |    <li><strong>${translations.emails.invoice.order_details.order_id_label}</strong> ${invoice.orderId}</li>
    |    <li><strong>${translations.emails.invoice.invoice_number_label}</strong> ${invoice.invoiceNumber}</li>
    |    <li><strong>${translations.emails.invoice.order_details.product_label}</strong> ${invoice.productName}</li>
    |    <li><strong>${translations.emails.invoice.customer_label}</strong> ${invoice.customerName}</li>
    |    <li><strong>${translations.emails.invoice.order_details.amount_label}</strong> ${invoice.amount} ${invoice.currency}</li>
    |    <li><strong>${translations.emails.invoice.order_details.date_label}</strong> $currentDate</li>
    |  </ul>
    |  <p>${translations.emails.invoice.attachment_note}</p>
    |  <p>${translations.emails.invoice.footer}</p>
    |  <p><em>${translations.emails.invoice.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }

  private def buildPaymentLinkContent(paymentLink: PaymentLinkEmail)(using language: BackendCompatibleLanguage): String = {
    val translations = I18N_Payments

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.payment_link.greeting}</h2>
    |  <p>${translations.emails.payment_link.intro}</p>
    |  <p><strong>${translations.emails.authentication.product_info(paymentLink.productName)}</strong></p>
    |  <p>${translations.emails.payment_link.amount_info(s"${translations.common.currency_symbol}${paymentLink.amount}")}</p>
    |  <p><a href="${paymentLink.paymentUrl}" style="background-color: #4CAF50; color: white; padding: 15px 32px; text-decoration: none; display: inline-block; border-radius: 4px;">${translations.emails.payment_link.payment_button}</a></p>
    |  <p><em>${translations.emails.payment_link.expiry_note}</em></p>
    |  <p>${translations.emails.payment_link.footer}</p>
    |  <p><em>${translations.emails.payment_link.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }

  private def buildAdminNotificationContent(notification: AdminNotification): String = {
    // Admin notifications don't use language parameter - they're always in English for internal use
    val translations = I18N_Payments(using Locales.en)
    val orderInfo = notification.orderId.fold("")(id => s"<p><strong>${translations.emails.admin_notification.order_id_label}</strong> $id</p>")

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.admin_notification.greeting}</h2>
    |  $orderInfo
    |  <p>${notification.message}</p>
    |  <p><em>${translations.emails.admin_notification.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }

  private def buildInvoiceWithReportContent(invoice: InvoiceEmail, pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): String = {
    val translations = I18N_Payments
    val currentDate = java.time.LocalDate.now().toString

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.invoice.greeting}</h2>
    |  <p>${translations.emails.invoice.intro}</p>
    |  <h3>${translations.emails.invoice.details_heading}</h3>
    |  <ul>
    |    <li><strong>${translations.emails.invoice.order_details.order_id_label}</strong> ${invoice.orderId}</li>
    |    <li><strong>${translations.emails.invoice.invoice_number_label}</strong> ${invoice.invoiceNumber}</li>
    |    <li><strong>${translations.emails.invoice.order_details.product_label}</strong> ${invoice.productName}</li>
    |    <li><strong>${translations.emails.invoice.customer_label}</strong> ${invoice.customerName}</li>
    |    <li><strong>${translations.emails.invoice.order_details.amount_label}</strong> ${invoice.amount} ${invoice.currency}</li>
    |    <li><strong>${translations.emails.invoice.order_details.date_label}</strong> $currentDate</li>
    |  </ul>
    |  <h3>${translations.emails.common.report_details_heading}</h3>
    |  <ul>
    |    <li><strong>${translations.emails.common.report_name_label}</strong> ${pdfReport.reportName}</li>
    |    <li><strong>${translations.emails.common.report_for_label}</strong> ${pdfReport.customerName}</li>
    |  </ul>
    |  <p>${translations.emails.common.invoice_and_report_note}</p>
    |  <p>${translations.emails.invoice.footer}</p>
    |  <p><em>${translations.emails.invoice.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }

  private def buildPdfReportContent(pdfReport: PdfReportEmail)(using language: BackendCompatibleLanguage): String = {
    val translations = I18N_Payments
    val currentDate = java.time.LocalDate.now().toString
    val orderInfo = pdfReport.orderId.fold("")(id => s"<p><strong>${translations.emails.pdf_report.order_id_label}</strong> $id</p>")

    s"""
    |<html>
    |<body>
    |  <h2>${translations.emails.pdf_report.greeting(pdfReport.customerName)}</h2>
    |  <p>${translations.emails.pdf_report.intro}</p>
    |  <h3>${translations.emails.pdf_report.details_heading}</h3>
    |  <ul>
    |    <li><strong>${translations.emails.pdf_report.report_name_label}</strong> ${pdfReport.reportName}</li>
    |    <li><strong>${translations.emails.pdf_report.customer_label}</strong> ${pdfReport.customerName}</li>
    |    <li><strong>${translations.emails.pdf_report.date_label}</strong> $currentDate</li>
    |  </ul>
    |  $orderInfo
    |  <p>${translations.emails.pdf_report.attachment_note}</p>
    |  <p>${translations.emails.pdf_report.footer}</p>
    |  <p><em>${translations.emails.pdf_report.signature}</em></p>
    |</body>
    |</html>
    """.stripMargin
  }
}
