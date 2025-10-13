/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.time.LocalDate
import java.util.Currency

/**
 * Represents a complete invoice with all necessary information.
 * 
 * @param invoiceNumber Unique invoice identifier
 * @param invoiceDate Date the invoice was issued
 * @param dueDate Date payment is due
 * @param reference Optional reference number or description
 * @param sender Company issuing the invoice
 * @param recipient Company receiving the invoice
 * @param billTo Optional separate billing address/company
 * @param lineItems List of items/services on the invoice
 * @param paymentTerms Payment terms and conditions
 * @param currency Currency for all monetary amounts (default EUR)
 * @param notes Optional additional notes
 * @param discountPercentage Optional global discount percentage
 * @param status Current status of the invoice
 */
case class InvoiceData(
  invoiceNumber: String,
  invoiceDate: LocalDate,
  dueDate: Option[LocalDate] = None,
  reference: Option[String] = None,
  sender: Company,
  recipient: Company,
  billTo: Option[Company] = None,
  lineItems: List[InvoiceLineItem],
  paymentTerms: PaymentTerms,
  currency: String = "EUR",
  notes: Option[String] = None,
  discountPercentage: Option[BigDecimal] = None,
  status: InvoiceStatus = InvoiceStatus.Draft
):
  /**
   * Gets the effective due date, either from explicit dueDate or calculated from payment terms.
   */
  def effectiveDueDate: LocalDate = dueDate.getOrElse(paymentTerms.calculateDueDate(invoiceDate))

  /**
   * Gets the company to bill (billTo if specified, otherwise recipient).
   */
  def effectiveBillTo: Company = billTo.getOrElse(recipient)

  /**
   * Calculates the subtotal of all line items before any global discount.
   */
  def subtotal: BigDecimal = lineItems.map(_.netAmount).sum

  /**
   * Calculates the global discount amount if applicable.
   */
  def globalDiscountAmount: BigDecimal = 
    discountPercentage.map(discount => subtotal * discount / BigDecimal(100))
      .getOrElse(BigDecimal(0))

  /**
   * Calculates the net amount after global discount but before tax.
   */
  def netAmount: BigDecimal = subtotal - globalDiscountAmount

  /**
   * Calculates the total tax amount across all line items.
   */
  def totalTaxAmount: BigDecimal = lineItems.map(_.taxAmount).sum

  /**
   * Calculates the final total amount including all discounts and taxes.
   */
  def totalAmount: BigDecimal = netAmount + totalTaxAmount

  /**
   * Groups line items by tax rate for tax breakdown display.
   */
  def taxBreakdown: Map[BigDecimal, BigDecimal] = 
    lineItems.groupBy(_.taxRate).view.mapValues(_.map(_.taxAmount).sum).toMap

  /**
   * Checks if the invoice has any discounts (global or line item).
   */
  def hasDiscounts: Boolean = 
    discountPercentage.exists(_ > 0) || lineItems.exists(_.hasDiscount)

  /**
   * Checks if the invoice has any tax applied.
   */
  def hasTax: Boolean = lineItems.exists(_.hasTax)

  /**
   * Checks if the invoice is overdue based on current date.
   */
  def isOverdue(currentDate: LocalDate = LocalDate.now()): Boolean = 
    status == InvoiceStatus.Sent && currentDate.isAfter(effectiveDueDate)

  /**
   * Creates a copy with all monetary amounts rounded to specified decimal places.
   */
  def withRoundedAmounts(scale: Int = 2): InvoiceData =
    this.copy(
      lineItems = lineItems.map(_.withRoundedAmounts(scale)),
      discountPercentage = discountPercentage.map(_.setScale(scale, BigDecimal.RoundingMode.HALF_UP))
    )

  /**
   * Validates the invoice data and returns any errors found.
   */
  def validate: List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()
    
    if (invoiceNumber.trim.isEmpty) errors += "Invoice number cannot be empty"
    if (lineItems.isEmpty) errors += "Invoice must have at least one line item"
    if (invoiceDate.isAfter(LocalDate.now().plusDays(1))) errors += "Invoice date cannot be in the future"
    if (effectiveDueDate.isBefore(invoiceDate)) errors += "Due date cannot be before invoice date"
    
    // Validate line items
    lineItems.zipWithIndex.foreach { case (item, index) =>
      if (item.description.trim.isEmpty) errors += s"Line item ${index + 1}: Description cannot be empty"
      if (item.quantity <= 0) errors += s"Line item ${index + 1}: Quantity must be positive"
      if (item.unitPrice < 0) errors += s"Line item ${index + 1}: Unit price cannot be negative"
      if (item.taxRate < 0) errors += s"Line item ${index + 1}: Tax rate cannot be negative"
    }
    
    try {
      Currency.getInstance(currency)
    } catch {
      case _: IllegalArgumentException => errors += s"Invalid currency code: $currency"
    }
    
    errors.toList

object InvoiceData:
  given Codec[InvoiceData] = deriveCodec[InvoiceData]

/**
 * Represents the current status of an invoice.
 */
enum InvoiceStatus:
  case Draft
  case Sent
  case Paid
  case Overdue
  case Cancelled
  case PartiallyPaid

  def displayName: String = this match
    case Draft => "Draft"
    case Sent => "Sent"
    case Paid => "Paid"
    case Overdue => "Overdue"
    case Cancelled => "Cancelled"
    case PartiallyPaid => "Partially Paid"

object InvoiceStatus:
  given Decoder[InvoiceStatus] = Decoder.decodeString.emap {
    case "Draft" => Right(InvoiceStatus.Draft)
    case "Sent" => Right(InvoiceStatus.Sent)
    case "Paid" => Right(InvoiceStatus.Paid)
    case "Overdue" => Right(InvoiceStatus.Overdue)
    case "Cancelled" => Right(InvoiceStatus.Cancelled)
    case "PartiallyPaid" => Right(InvoiceStatus.PartiallyPaid)
    case other => Left(s"Invalid invoice status: $other")
  }
  
  given Encoder[InvoiceStatus] = Encoder.encodeString.contramap {
    case InvoiceStatus.Draft => "Draft"
    case InvoiceStatus.Sent => "Sent"
    case InvoiceStatus.Paid => "Paid"
    case InvoiceStatus.Overdue => "Overdue"
    case InvoiceStatus.Cancelled => "Cancelled"
    case InvoiceStatus.PartiallyPaid => "PartiallyPaid"
  }
