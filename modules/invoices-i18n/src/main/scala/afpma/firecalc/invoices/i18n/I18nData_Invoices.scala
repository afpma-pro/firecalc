/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.i18n

import afpma.firecalc.invoices.i18n.I18nData_Invoices.*

import io.taig.babel.{StringFormat1, StringFormat2}

final case class I18nData_Invoices(
    document: Document,
    fields: Fields,
    table: Table,
    totals: Totals,
    sections: Sections,
    formatting: Formatting,
    company: Company,
    payment: Payment,
    tax: Tax,
)

object I18nData_Invoices:

  final case class Document(
      title: String,
      subtitle: String,
      footer_generated: StringFormat2, // "Generated on {0} at {1}"
  )

  final case class Fields(
      from: String,
      to: String,
      invoice_number: String,
      invoice_date: String,
      due_date: String,
      reference: String,
      currency: String,
      status: String,
  )

  final case class Table(
      description: String,
      quantity: String,
      unit_price: String,
      tax_rate: String,
      amount: String,
      line_items_title: String,
  )

  final case class Totals(
      subtotal: String,
      discount: String,
      net_amount: String,
      tax_label: StringFormat1, // "Tax ({0}%)"
      total: String,
  )

  final case class Sections(
      payment_information: String,
      notes: String,
  )

  final case class Formatting(
      date_format: String, // e.g., "dd/MM/yyyy" or "MM/dd/yyyy"
  )

  final case class Company(
      vat_label: String,
      registration_label: String,
      email_label: String,
      phone_label: String,
      website_label: String,
  )

  final case class Payment(
      iban_label: String,
      bic_label: String,
      bank_transfer: String,
      credit_card: String,
      check: String,
      cash: String,
      paypal: String,
      sepa_mandate: String,
      mandate_reference: String,
      mandate_date: String,
      payment_due_within: StringFormat1, // "Payment due within {0} days"
      late_fee_label: String,
      early_discount_label: String,
      if_paid_within: String,
      days: String,
      payment_methods_label: String,
  )

  final case class Tax(
      exempt: String,
      exempt_label: String,
      non_profit_exemption: String,
  )
