/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.Codec
import io.circe.generic.semiauto.*

/**
 * Represents a single line item on an invoice.
 * 
 * @param description Description of the item or service
 * @param quantity Quantity of items
 * @param unitPrice Price per unit
 * @param taxRate Tax rate as percentage (e.g., 20.0 for 20%)
 * @param taxExempt Whether this item is tax-exempt (overrides taxRate if true)
 * @param discountPercentage Optional discount percentage for this line item
 * @param unit Optional unit of measurement (e.g., "hours", "pieces", "kg")
 * @param productCode Optional product/service code
 */
case class InvoiceLineItem(
  description: String,
  quantity: BigDecimal,
  unitPrice: BigDecimal,
  taxRate: BigDecimal = BigDecimal(0),
  taxExempt: Boolean = false,
  discountPercentage: Option[BigDecimal] = None,
  unit: Option[String] = None,
  productCode: Option[String] = None
):
  /**
   * Calculates the subtotal before tax and discount.
   */
  def subtotal: BigDecimal = quantity * unitPrice

  /**
   * Calculates the discount amount if applicable.
   */
  def discountAmount: BigDecimal = 
    discountPercentage.map(discount => subtotal * discount / BigDecimal(100))
      .getOrElse(BigDecimal(0))

  /**
   * Calculates the net amount after discount but before tax.
   */
  def netAmount: BigDecimal = subtotal - discountAmount

  /**
   * Calculates the effective tax rate (0 if tax-exempt).
   */
  def effectiveTaxRate: BigDecimal = if (taxExempt) BigDecimal(0) else taxRate

  /**
   * Calculates the tax amount based on net amount.
   */
  def taxAmount: BigDecimal = netAmount * effectiveTaxRate / BigDecimal(100)

  /**
   * Calculates the total amount including tax.
   */
  def totalAmount: BigDecimal = netAmount + taxAmount

  /**
   * Checks if this line item has a discount applied.
   */
  def hasDiscount: Boolean = discountPercentage.exists(_ > 0)

  /**
   * Checks if this line item has tax applied.
   */
  def hasTax: Boolean = !taxExempt && taxRate > 0

  /**
   * Checks if this line item is tax-exempt.
   */
  def isTaxExempt: Boolean = taxExempt

  /**
   * Formats the quantity with unit if available.
   */
  def formattedQuantity: String = 
    unit.map(u => s"$quantity $u").getOrElse(quantity.toString)

  /**
   * Creates a copy with rounded monetary values to specified decimal places.
   */
  def withRoundedAmounts(scale: Int = 2): InvoiceLineItem =
    this.copy(
      unitPrice = unitPrice.setScale(scale, BigDecimal.RoundingMode.HALF_UP),
      discountPercentage = discountPercentage.map(_.setScale(scale, BigDecimal.RoundingMode.HALF_UP))
    )

object InvoiceLineItem:
  given Codec[InvoiceLineItem] = deriveCodec[InvoiceLineItem]

  /**
   * Creates a simple line item without tax or discount.
   */
  def simple(description: String, quantity: BigDecimal, unitPrice: BigDecimal): InvoiceLineItem =
    InvoiceLineItem(description, quantity, unitPrice)

  /**
   * Creates a line item for services (typically with "hours" as unit).
   */
  def service(description: String, hours: BigDecimal, hourlyRate: BigDecimal, taxRate: BigDecimal = BigDecimal(0)): InvoiceLineItem =
    InvoiceLineItem(
      description = description,
      quantity = hours,
      unitPrice = hourlyRate,
      taxRate = taxRate,
      unit = Some("hours")
    )

  /**
   * Creates a line item for products.
   */
  def product(description: String, quantity: BigDecimal, unitPrice: BigDecimal, taxRate: BigDecimal = BigDecimal(0), productCode: Option[String] = None): InvoiceLineItem =
    InvoiceLineItem(
      description = description,
      quantity = quantity,
      unitPrice = unitPrice,
      taxRate = taxRate,
      productCode = productCode,
      unit = Some("pieces")
    )

  /**
   * Creates a tax-exempt line item.
   */
  def taxExempt(description: String, quantity: BigDecimal, unitPrice: BigDecimal, unit: Option[String] = None): InvoiceLineItem =
    InvoiceLineItem(
      description = description,
      quantity = quantity,
      unitPrice = unitPrice,
      taxRate = BigDecimal(0),
      taxExempt = true,
      unit = unit
    )

  /**
   * Creates a tax-exempt service line item for non-profit organizations.
   */
  def taxExemptService(description: String, hours: BigDecimal, hourlyRate: BigDecimal): InvoiceLineItem =
    InvoiceLineItem(
      description = description,
      quantity = hours,
      unitPrice = hourlyRate,
      taxRate = BigDecimal(0),
      taxExempt = true,
      unit = Some("hours")
    )
