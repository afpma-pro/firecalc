/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.typst

import afpma.firecalc.invoices.models.*
import afpma.firecalc.invoices.i18n.I18nData_Invoices
import io.taig.babel.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import afpma.firecalc.invoices.typst.TypstShow.sanitized

/**
 * Typeclass for converting invoice data into Typst markup.
 * Similar to the reports module's TypShow but specialized for invoices.
 */
trait TypstShow[A]:
  def showAsTypst(a: A): TypstShow.SanitizedString

object TypstShow:
  opaque type SanitizedString = String
  given Conversion[SanitizedString, String] = identity
  
  extension (raw: String)
    def sanitized: SanitizedString = 
      raw
        .replaceAll("//", "\\\\/") // escape Typst comments
        .replaceAll("#", "\\\\#")   // escape Typst function calls
        .replaceAll("\\$", "\\\\$") // escape Typst math mode
        .replaceAll("@", "\\\\@")   // escape @ symbol to prevent label references (4 backslashes = 2 in output)
  
  def apply[A](using ev: TypstShow[A]): TypstShow[A] = ev
  
  def make[A](f: A => String): TypstShow[A] = new TypstShow[A]:
    def showAsTypst(a: A): SanitizedString = f(a).sanitized

  /**
   * Creates a TypstShow instance without sanitization.
   * Use this when combining already sanitized content to avoid double sanitization.
   */
  def makeRaw[A](f: A => String): TypstShow[A] = new TypstShow[A]:
    def showAsTypst(a: A): SanitizedString = f(a)

  /**
   * Creates a TypstShow instance for table rendering.
   */
  def mkForTable[A](
    title: Option[String],
    headers: Option[Seq[String]],
    toRows: A => Seq[Seq[String]],
    columnAlignment: Seq[String] = Seq("left"),
    maxWidthForFirstColumn: Boolean = true,
  ): TypstShow[A] =
    TypstShow.makeRaw { a =>
      val rows = toRows(a)
      require(rows.nonEmpty, "Table must have at least one row")

      val colCount = rows.head.length
      val alignments = if (columnAlignment.length >= colCount) columnAlignment else columnAlignment ++ Seq.fill(colCount - columnAlignment.length)("left")
      
      val columnsSpec = if (maxWidthForFirstColumn) 
        "1fr" +: Seq.fill(colCount - 1)("auto")
      else 
        Seq.fill(colCount)("auto")

      val tableTitleString = title.map { t =>
        s"""  table.cell(inset: (y: 10pt), colspan: $colCount)[
           |    #text(1.2em)[*${t.sanitized}*]
           |  ],""".stripMargin
      }.getOrElse("")

      val tableHeaderString = headers.map { h =>
        h.map(header => s"[*${header.sanitized}*]").mkString("  ", ",\n  ", ",")
      }.getOrElse("")

      val sanitizedRows = rows.map(_.map(_.sanitized))

      s"""|#table(
          |  columns: ${columnsSpec.mkString("(", ", ", ")")},
          |  inset: 4pt,
          |  align: (${alignments.mkString(", ")}),
          |$tableTitleString
          |$tableHeaderString
          |${sanitizedRows.map(_.mkString("  [", "], [", "],")).mkString("\n")}
          |)""".stripMargin
    }

  // Built-in instances for common types
  given TypstShow[String] = make(identity)
  
  given TypstShow[BigDecimal] = make(_.toString)
  
  given TypstShow[LocalDate] = make(_.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
  
  given TypstShow[Int] = make(_.toString)
  
  given [A: TypstShow]: TypstShow[Option[A]] = make(_.map(TypstShow[A].showAsTypst).getOrElse(""))
  
  given [A: TypstShow]: TypstShow[List[A]] = make(_.map(TypstShow[A].showAsTypst).mkString(", "))

extension [A: TypstShow](a: A)
  def toTypst: TypstShow.SanitizedString = TypstShow[A].showAsTypst(a)

/**
 * Specific TypstShow instances for invoice domain models.
 */
object InvoiceTypstInstances:
  
  given TypstShow[Address] = TypstShow.makeRaw { addr =>
    val lines = Seq(
      Some(addr.street.sanitized),
      addr.streetLine2.map(_.sanitized),
      Some(s"${addr.postalCode} ${addr.city}".sanitized),
      Some(s"${addr.region}, ${addr.country}".sanitized)
    ).flatten
    s"#block[${lines.mkString(" \\ \n")}]"
  }
  
  given (using i18n: I18nData_Invoices): TypstShow[Company] = TypstShow.makeRaw { company =>
    val effective_name_line = s"*${company.effectiveName.sanitized}*"

    val vat_line = company.vatNumber.map(vat => s"${i18n.company.vat_label} $vat".sanitized)
    val reg_number_line = company.registrationNumber.map(reg => s"${i18n.company.registration_label} $reg".sanitized)
    val email_line = s"${i18n.company.email_label} ${company.email}".sanitized
    val phone_line = company.phone.map(p => s"${i18n.company.phone_label} $p".sanitized)
    val website_line = company.website.map(w => s"${i18n.company.website_label} $w".sanitized)

    val lines = Seq(
        vat_line,
        reg_number_line,
        Some(email_line),
        phone_line,
        website_line
    ).flatten.mkString(" \\ \n")

    s"""
     |
     |#block[
     |$effective_name_line \\
     |${company.address.toTypst}
     |$lines
     |]
     |
     |""".stripMargin
  }
  
  given (using i18n: I18nData_Invoices): TypstShow[PaymentMethod] = TypstShow.make { method =>
    method match
      case PaymentMethod.BankTransfer(iban, bic) =>
        val details = Seq(iban.map(i => s"${i18n.payment.iban_label} $i"), bic.map(b => s"${i18n.payment.bic_label} $b")).flatten
        if (details.nonEmpty) s"${i18n.payment.bank_transfer} (${details.mkString(", ")})" else i18n.payment.bank_transfer
      case PaymentMethod.PayPal(email) =>
        email.map(e => s"${i18n.payment.paypal} ($e)").getOrElse(i18n.payment.paypal)
      case other => other.displayName
  }
  
  given (using i18n: I18nData_Invoices): TypstShow[PaymentTerms] = TypstShow.makeRaw { terms =>
    val lines_A = Seq(
      Some(i18n.payment.payment_due_within(terms.dueDays.toString).sanitized),
      terms.lateFeePercentage.map(fee => s"${i18n.payment.late_fee_label} ${fee}%".sanitized),
      terms.discountPercentage.zip(terms.discountDays).headOption.map { case (discount, days) =>
        s"${i18n.payment.early_discount_label} ${discount}% ${i18n.payment.if_paid_within} $days ${i18n.payment.days}".sanitized
      },
    ).flatten
    val lines_B = 
        if (terms.methods.nonEmpty)
            Some(s"${i18n.payment.payment_methods_label}")
            :: terms.methods.map(method => Some(s"- ${TypstShow[PaymentMethod].showAsTypst(method)}"))
        else 
            Seq(None)
    s"""#block[${lines_A.mkString(" \\ \n")}]
       |#block[${lines_B.flatten.mkString(" \\ \n")}]""".stripMargin
  }
  
  given TypstShow[InvoiceStatus] = TypstShow.make(_.displayName)
  
  given TypstShow[InvoiceLineItem] = TypstShow.make { item =>
    // This is typically used in tables, so we format it as table cells
    s"${item.description} | ${item.formattedQuantity} | ${item.unitPrice} | ${item.totalAmount}"
  }

/**
 * Utilities for formatting monetary amounts in invoices.
 */
object MoneyFormatting:
  
  def formatCurrency(amount: BigDecimal, currency: String = "EUR"): String =
    val formatted = amount.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString
    currency match
      case "EUR" => s"$formatted €"
      case "USD" => s"$$formatted"
      case "GBP" => s"£$formatted"
      case _     => s"$formatted $currency"
  
  given TypstShow[BigDecimal] = TypstShow.make(formatCurrency(_))
  
  def formatCurrencyWithCode(amount: BigDecimal, currency: String): String =
    s"${formatCurrency(amount, currency)} $currency"

/**
 * Utilities for creating common Typst document elements.
 */
object TypstElements:
  
  def heading(level: Int, text: String): String =
    val prefix = "=" * level
    s"$prefix $text"
  
  def pageBreak: String = "#pagebreak()"
  
  def horizontalRule: String = "#line(length: 100%)"
  
  def grid(columns: Int, content: String*): String =
    val columnSpec = Seq.fill(columns)("1fr").mkString(", ")
    val contentItems = content.map(c => s"[$c]").mkString(", ")
    s"#grid(columns: ($columnSpec), $contentItems)"
  
  def block(width: String = "100%", height: Option[String] = None, fill: Option[String] = None)(content: String): String =
    val heightAttr = height.map(h => s", height: $h").getOrElse("")
    val fillAttr = fill.map(f => s", fill: $f").getOrElse("")
    s"#block(width: $width$heightAttr$fillAttr)[$content]"
  
  def image(path: String, width: Option[String] = None): String =
    val widthAttr = width.map(w => s", width: $w").getOrElse("")
    s"#image(\"$path\"$widthAttr)"
  
  def align(alignment: String)(content: String): String =
    s"#align($alignment)[$content]"
  
  def text(size: Option[String] = None, weight: Option[String] = None, style: Option[String] = None)(content: String): String =
    val attrs = Seq(
      size.map(s => s"size: $s"),
      weight.map(w => s"weight: \"$w\""),
      style.map(st => s"style: \"$st\"")
    ).flatten
    
    if (attrs.nonEmpty)
      s"#text(${attrs.mkString(", ")})[$content]"
    else
      content
