/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.i18n

import afpma.firecalc.payments.i18n.I18nData_Payments.*

import io.taig.babel.StringFormat1

final case class I18nData_Payments(
    emails: Emails,
    pages: Pages,
    common: Common,
    errors: Errors,
)

object I18nData_Payments:

  final case class Emails(
      authentication: AuthenticationEmail,
      invoice: InvoiceEmail,
      payment_link: PaymentLinkEmail,
      pdf_report: PdfReportEmail,
      admin_notification: AdminNotificationEmail,
      common: EmailCommon
  )

  final case class AuthenticationEmail(
      subject_new_user: String,
      subject_existing_user: String,
      greeting: String,
      intro_new_user: String,
      intro_existing_user: String,
      code_label: String,
      product_info: StringFormat1, // "Product: {0}"
      amount_info: StringFormat1,  // "Amount: {0}"
      footer: String,
      signature: String
  )

  final case class InvoiceEmail(
      subject: StringFormat1, // "Invoice for order {0}"
      greeting: String,
      intro: String,
      details_heading: String, // "Invoice Details"
      invoice_number_label: String, // "Invoice Number:"
      customer_label: String, // "Customer:"
      order_details: OrderDetails,
      attachment_note: String,
      footer: String,
      signature: String
  )

  final case class PaymentLinkEmail(
      subject: StringFormat1, // "Payment link for {0}"
      greeting: String,
      intro: String,
      payment_button: String,
      amount_info: StringFormat1,
      expiry_note: String,
      footer: String,
      signature: String
  )


  final case class PdfReportEmail(
      subject: StringFormat1, // "Your PDF Report: {0}"
      greeting: StringFormat1, // "Dear {0},"
      intro: String, // "Please find attached your requested PDF report."
      details_heading: String, // "Report Details"
      report_name_label: String, // "Report Name:"
      customer_label: String, // "Customer:"
      date_label: String, // "Date:"
      order_id_label: String, // "Order ID:"
      attachment_note: String, // "The PDF report is attached to this email."
      footer: String, // "If you have any questions, please don't hesitate to contact us."
      signature: String // "FireCalc Team"
  )

  final case class AdminNotificationEmail(
      greeting: String, // "Admin,"
      order_id_label: String, // "Order ID:"
      signature: String // "FireCalc Backend"
  )

  final case class EmailCommon(
      company_name: String,
      support_email: String,
      website_url: String,
      unsubscribe: String,
      privacy_policy: String,
      report_details_heading: String, // "Report Details"
      report_name_label: String, // "Report Name:"
      report_for_label: String, // "Report for:"
      invoice_and_report_note: String // "Please find attached both your invoice and PDF report."
  )

  final case class OrderDetails(
      order_id_label: String, // "Order ID:"
      product_label: String, // "Product:"
      amount_label: String, // "Amount:"
      date_label: String // "Date:"
  )

  final case class Pages(
      payment_complete: PaymentCompletePage,
      payment_cancelled: PaymentCancelledPage
  )

  final case class PaymentCompletePage(
      title: String,
      heading: String,
      message: String,
      order_info: String,
      next_steps: String,
      support_contact: String,
      return_home: String
  )

  final case class PaymentCancelledPage(
      title: String,
      heading: String,
      message: String,
      reason_info: String,
      retry_payment: String,
      support_contact: String,
      return_home: String
  )

  final case class Common(
      currency_symbol: String,
      date_format: String,
      company_name: String,
      support_email: String,
      payment_description: io.taig.babel.StringFormat2 // "Payment for order {0}: {1}"
  )

  final case class Errors(
      email_send_failed: String,
      invalid_email: String,
      payment_processing_error: String,
      order_not_found: String,
      authentication_failed: String
  )
