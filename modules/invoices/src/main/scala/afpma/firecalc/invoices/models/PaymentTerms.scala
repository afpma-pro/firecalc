/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.Codec
import io.circe.generic.semiauto.*
import java.time.LocalDate
import afpma.firecalc.invoices.i18n.I18nData_Invoices

/**
 * Represents payment terms and conditions for an invoice.
 * 
 * @param dueDays Number of days from invoice date until payment is due
 * @param description Human-readable description of payment terms
 * @param lateFeePercentage Optional percentage charged for late payments
 * @param discountPercentage Optional early payment discount percentage
 * @param discountDays Optional number of days to qualify for early payment discount
 * @param methods Accepted payment methods
 * @param notes Optional additional payment notes or instructions
 */
case class PaymentTerms(
  dueDays: Int,
  description: String,
  lateFeePercentage: Option[BigDecimal] = None,
  discountPercentage: Option[BigDecimal] = None,
  discountDays: Option[Int] = None,
  methods: List[PaymentMethod] = List.empty,
  notes: Option[String] = None
):
  /**
   * Calculates the due date based on invoice date and payment terms.
   */
  def calculateDueDate(invoiceDate: LocalDate): LocalDate =
    invoiceDate.plusDays(dueDays.toLong)

  /**
   * Calculates the early payment discount deadline if applicable.
   */
  def calculateDiscountDeadline(invoiceDate: LocalDate): Option[LocalDate] =
    discountDays.map(days => invoiceDate.plusDays(days.toLong))

  /**
   * Checks if early payment discount is available.
   */
  def hasEarlyPaymentDiscount: Boolean = 
    discountPercentage.isDefined && discountDays.isDefined

  /**
   * Calculates discounted amount if early payment conditions are met.
   */
  def calculateDiscountedAmount(originalAmount: BigDecimal): Option[BigDecimal] =
    discountPercentage.map(discount => 
      originalAmount * (BigDecimal(1) - discount / BigDecimal(100))
    )

object PaymentTerms:
  given Codec[PaymentTerms] = deriveCodec[PaymentTerms]

  /**
   * Common payment terms presets.
   */
  object Presets:
    val Net30 = PaymentTerms(
      dueDays = 30,
      description = "Net 30 days"
    )

    val Net15 = PaymentTerms(
      dueDays = 15,
      description = "Net 15 days"
    )

    val DueOnReceipt = PaymentTerms(
      dueDays = 0,
      description = "Due upon receipt"
    )

    val Net30With2Percent10 = PaymentTerms(
      dueDays = 30,
      description = "2/10 Net 30",
      discountPercentage = Some(BigDecimal(2)),
      discountDays = Some(10)
    )

/**
 * Represents different payment methods accepted.
 */
enum PaymentMethod:
  case BankTransfer(iban: Option[String] = None, bic: Option[String] = None)
  case CreditCard
  case Check
  case Cash
  case PayPal(email: Option[String] = None)
  case SepaMandate(mandateReference: Option[String] = None, mandateDate: Option[LocalDate] = None, iban: Option[String] = None)
  case Other(description: String)

  def displayName(using i18n: I18nData_Invoices): String = 
    this match
      case BankTransfer(_, _) => i18n.payment.bank_transfer
      case CreditCard => i18n.payment.credit_card
      case Check => i18n.payment.check
      case Cash => i18n.payment.cash
      case PayPal(_) => i18n.payment.paypal
      case SepaMandate(_, _, _) => i18n.payment.sepa_mandate
      case Other(desc) => desc

object PaymentMethod:
  given Codec[PaymentMethod] = deriveCodec[PaymentMethod]
