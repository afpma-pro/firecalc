/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.time.LocalDate

/**
 * Dynamic parameters for invoice generation.
 * These parameters change for each invoice and are passed at generation time.
 * 
 * @param invoiceNumber Unique invoice identifier
 * @param recipient Company receiving the invoice
 * @param lineItems List of items/services on the invoice
 * @param paymentTerms Payment terms and conditions for this specific invoice
 * @param invoiceDate Date the invoice was issued
 * @param dueDate Date payment is due (if None, calculated from payment terms)
 * @param reference Optional reference number or description
 * @param billTo Optional separate billing address/company
 * @param currency Currency for all monetary amounts (default EUR)
 * @param notes Optional additional notes
 * @param discountPercentage Optional global discount percentage
 * @param status Current status of the invoice
 */
case class InvoiceParams(
  invoiceNumber: String,
  recipient: Company,
  lineItems: List[InvoiceLineItem],
  paymentTerms: PaymentTerms,
  invoiceDate: LocalDate = LocalDate.now(),
  dueDate: Option[LocalDate] = None,
  reference: Option[String] = None,
  billTo: Option[Company] = None,
  currency: String = "EUR",
  notes: Option[String] = None,
  discountPercentage: Option[BigDecimal] = None,
  status: InvoiceStatus = InvoiceStatus.Draft
):
  
  /**
   * Validates the invoice parameters.
   */
  def validate: List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()
    
    if (invoiceNumber.trim.isEmpty) errors += "Invoice number cannot be empty"
    if (lineItems.isEmpty) errors += "Invoice must have at least one line item"
    if (invoiceDate.isAfter(LocalDate.now().plusDays(1))) errors += "Invoice date cannot be in the future"
    
    val effectiveDueDate = dueDate.getOrElse(paymentTerms.calculateDueDate(invoiceDate))
    if (effectiveDueDate.isBefore(invoiceDate)) errors += "Due date cannot be before invoice date"
    
    // Validate line items
    lineItems.zipWithIndex.foreach { case (item, index) =>
      if (item.description.trim.isEmpty) errors += s"Line item ${index + 1}: Description cannot be empty"
      if (item.quantity <= 0) errors += s"Line item ${index + 1}: Quantity must be positive"
      if (item.unitPrice < 0) errors += s"Line item ${index + 1}: Unit price cannot be negative"
      if (item.taxRate < 0) errors += s"Line item ${index + 1}: Tax rate cannot be negative"
    }
    
    try {
      java.util.Currency.getInstance(currency)
    } catch {
      case _: IllegalArgumentException => errors += s"Invalid currency code: $currency"
    }
    
    errors.toList

  /**
   * Converts InvoiceParams to InvoiceData by combining with CompanyConfig.
   */
  def toInvoiceData(companyConfig: CompanyConfig): InvoiceData =
    InvoiceData(
      invoiceNumber = invoiceNumber,
      invoiceDate = invoiceDate,
      dueDate = dueDate,
      reference = reference,
      sender = companyConfig.sender,
      recipient = recipient,
      billTo = billTo,
      lineItems = lineItems,
      paymentTerms = paymentTerms,
      currency = currency,
      notes = notes,
      discountPercentage = discountPercentage,
      status = status
    )

object InvoiceParams:
  given Codec[InvoiceParams] = deriveCodec[InvoiceParams]
