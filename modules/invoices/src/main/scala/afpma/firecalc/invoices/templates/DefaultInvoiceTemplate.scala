/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.templates

import afpma.firecalc.invoices.models.{InvoiceData, Company, InvoiceLineItem, PaymentTerms, InvoiceStatus}
import afpma.firecalc.invoices.typst.{TypstShow, InvoiceTypstInstances, MoneyFormatting, TypstElements}
import afpma.firecalc.invoices.typst.toTypst
import afpma.firecalc.invoices.i18n.I18nData_Invoices
import afpma.firecalc.invoices.i18n.implicits.given
import io.taig.babel.Locale
import java.time.{LocalDateTime, LocalDate}
import java.time.format.DateTimeFormatter

/**
 * Default professional invoice template implementation.
 * This serves as a reference implementation and can be extended or customized.
 */
class DefaultInvoiceTemplate(val config: TemplateConfig = TemplateConfig()) 
    extends ConfigurableTemplate[InvoiceData]:

  import InvoiceTypstInstances.given
  import MoneyFormatting.given
  import TypstElements.*

  def render(invoice: InvoiceData)(using locale: Locale): String =
    applyConfig(buildInvoiceDocument(invoice))

  def withConfig(newConfig: TemplateConfig): DefaultInvoiceTemplate =
    new DefaultInvoiceTemplate(newConfig)

  override def validate(invoice: InvoiceData): Option[String] =
    val errors = invoice.validate
    if (errors.nonEmpty) Some(errors.mkString("; ")) else None

  private def buildInvoiceDocument(invoice: InvoiceData)(using locale: Locale): String =
    import afpma.firecalc.invoices.i18n.implicits.I18Ns
    given i18n: I18nData_Invoices = I18Ns(locale)
    Seq(
      documentHeader,
      invoiceHeader(invoice),
      companyInformation(invoice),
      invoiceDetails(invoice),
      lineItemsTable(invoice),
      totalsSection(invoice),
      paymentInformation(invoice),
      footer(invoice)
    ).filter(_.trim.nonEmpty).mkString("\n\n")

  private def documentHeader(using i18n: I18nData_Invoices): String =
    s"""
    |#import "@preview/fancy-units:0.1.1": num, unit, qty, fancy-units-configure, add-macros
    |
    |#fancy-units-configure(
    |  per-mode: "slash",
    |  unit-separator: sym.dot,
    |)
    |
    |#set document(
    |  title: "${i18n.document.subtitle}",
    |  author: "Invoice System",
    |)
    |
    |#show heading: smallcaps
    |#show heading: set align(center)
    |#show heading: set text(weight: "regular", size: 1em)
    |""".stripMargin

  private def invoiceHeader(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val logoSection = if (invoice.sender.logo.isDefined) 
      image(invoice.sender.logo.get, Some(config.logoWidth))
    else ""

    val titleSection = text(Some("2em"), Some("bold"))(s"${i18n.document.title} ${invoice.invoiceNumber}")

    config.logoPosition match
      case LogoPosition.TopLeft => 
        if (logoSection.nonEmpty) grid(2, logoSection, align("right")(titleSection))
        else align("center")(titleSection)
      case LogoPosition.TopRight => 
        if (logoSection.nonEmpty) grid(2, align("left")(titleSection), logoSection)
        else align("center")(titleSection)
      case LogoPosition.TopCenter => 
        if (logoSection.nonEmpty) align("center")(s"$logoSection\n$titleSection")
        else align("center")(titleSection)
      case LogoPosition.None => 
        align("center")(titleSection)

  private def companyInformation(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    grid(2, 
      block()(s"*${i18n.fields.from}*\n${invoice.sender.toTypst}"),
      block()(s"*${i18n.fields.to}*\n${invoice.effectiveBillTo.toTypst}")
    )

  private def invoiceDetails(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val formatter = DateTimeFormatter.ofPattern(i18n.formatting.date_format)
    
    s"""
    |#table(
    |  columns: (1fr, 1fr),
    |  inset: 8pt,
    |  [*${i18n.fields.invoice_number}*], [${invoice.invoiceNumber}],
    |  [*${i18n.fields.invoice_date}*], [${invoice.invoiceDate.format(formatter)}],
    |  [*${i18n.fields.due_date}*], [${invoice.effectiveDueDate.format(formatter)}],
    |  ${invoice.reference.map(ref => s"[*${i18n.fields.reference}*], [$ref],").getOrElse("")}
    |  [*${i18n.fields.currency}*], [${invoice.currency}],
    |  [*${i18n.fields.status}*], [${invoice.status.displayName}],
    |)
    |""".stripMargin

  private def lineItemsTable(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val headers = Seq(
      i18n.table.description, 
      i18n.table.quantity, 
      i18n.table.unit_price, 
      i18n.table.tax_rate, 
      i18n.table.amount
    )
    
    val rows = invoice.lineItems.map { item =>
      Seq(
        item.description,
        item.formattedQuantity,
        MoneyFormatting.formatCurrency(item.unitPrice, invoice.currency),
        formatTaxRate(item),
        MoneyFormatting.formatCurrency(item.totalAmount, invoice.currency)
      )
    }

    TypstShow.mkForTable(
      title = Some(i18n.table.line_items_title),
      headers = Some(headers),
      toRows = (_: Unit) => rows,
      columnAlignment = Seq("left", "center", "right", "center", "right"),
      maxWidthForFirstColumn = true
    ).showAsTypst(())

  private def formatTaxRate(item: InvoiceLineItem)(using i18n: I18nData_Invoices): String =
    if (item.isTaxExempt) {
      i18n.tax.exempt_label
    } else if (item.effectiveTaxRate > 0) {
      s"${item.effectiveTaxRate}%"
    } else {
      "0%"
    }

  private def totalsSection(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val subtotalRow = s"[*${i18n.totals.subtotal}*], [${MoneyFormatting.formatCurrency(invoice.subtotal, invoice.currency)}],"
    
    val discountRow = if (invoice.hasDiscounts) 
      s"[*${i18n.totals.discount}*], [-${MoneyFormatting.formatCurrency(invoice.globalDiscountAmount, invoice.currency)}],"
    else ""
    
    val netAmountRow = if (invoice.hasDiscounts)
      s"[*${i18n.totals.net_amount}*], [${MoneyFormatting.formatCurrency(invoice.netAmount, invoice.currency)}],"
    else ""
    
    // Check if there are any tax-exempt items in the invoice
    val hasTaxExemptItems = invoice.lineItems.exists(_.isTaxExempt)
    
    // Only show tax rows if there are no tax-exempt items and there are actual taxes
    val taxRows = if (invoice.hasTax && !hasTaxExemptItems) {
      invoice.taxBreakdown.map { case (rate, amount) =>
        s"[*${i18n.totals.tax_label(rate.toString)}*], [${MoneyFormatting.formatCurrency(amount, invoice.currency)}],"
      }.mkString("\n  ")
    } else ""
    
    val totalRow = s"[*${i18n.totals.total}*], [*${MoneyFormatting.formatCurrency(invoice.totalAmount, invoice.currency)}*],"

    s"""
    |#align(right)[
    |  #table(
    |    columns: (auto, auto),
    |    inset: 8pt,
    |    stroke: none,
    |    $subtotalRow
    |    $discountRow
    |    $netAmountRow
    |    $taxRows
    |    table.hline(),
    |    $totalRow
    |  )
    |]
    |""".stripMargin

  private def paymentInformation(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    s"""
    |${heading(2, i18n.sections.payment_information)}
    |
    |${invoice.paymentTerms.toTypst}
    |""".stripMargin

  private def footer(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val notesSection = invoice.notes.map { notes =>
      s"""
      |${heading(2, i18n.sections.notes)}
      |$notes
      |""".stripMargin
    }.getOrElse("")

    // Add tax exemption legal notices if applicable
    val taxExemptionNotices = buildTaxExemptionNotices(invoice)

    // Format datetime in Scala instead of Typst, using locale-aware formatting
    val now = LocalDateTime.now()
    val dateFormatter = DateTimeFormatter.ofPattern(i18n.formatting.date_format)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val generatedDate = now.format(dateFormatter)
    val generatedTime = now.format(timeFormatter)

    val generationInfo = 
      s"""
      |${horizontalRule}
      |
      |#align(center)[
      |  #text(size: 8pt, style: "italic")[
      |    ${i18n.document.footer_generated(generatedDate, generatedTime)}
      |  ]
      |]
      |""".stripMargin

    s"$notesSection$taxExemptionNotices\n$generationInfo"

  private def buildTaxExemptionNotices(invoice: InvoiceData)(using i18n: I18nData_Invoices): String =
    val taxExemptItems = invoice.lineItems.filter(_.isTaxExempt)
    
    if (taxExemptItems.nonEmpty) {
        s"""
        |
        |#align(center, text(size: 9pt)[${i18n.tax.exempt} - ${i18n.tax.non_profit_exemption}])
        |
        |""".stripMargin
      
    } else {
      ""
    }

object DefaultInvoiceTemplate:
  /**
   * Creates a default template instance with standard configuration.
   */
  def apply(): DefaultInvoiceTemplate = new DefaultInvoiceTemplate()

  /**
   * Creates a default template with custom configuration.
   */
  def withConfig(config: TemplateConfig): DefaultInvoiceTemplate = 
    new DefaultInvoiceTemplate(config)

  /**
   * Provides the default given instance for InvoiceTemplate[InvoiceData].
   */
  given defaultInvoiceTemplate: InvoiceTemplate[InvoiceData] = DefaultInvoiceTemplate()
