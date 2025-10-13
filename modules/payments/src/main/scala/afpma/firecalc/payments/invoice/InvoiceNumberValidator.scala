/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.{Instant, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

object InvoiceNumberValidator {
  
  // Whitelist of safe characters for filesystem compatibility
  // Alphanumeric characters plus dash, underscore, period, and square brackets for date placeholders
  private val SafeFileSystemChars = """^[a-zA-Z0-9\-_.\[\]]+$""".r
  
  // Valid date placeholders
  private val ValidPlaceholders = Set("[YYYY]", "[YY]", "[MM]", "[DD]")
  
  // Pattern to match any placeholder-like structure
  private val PlaceholderPattern = """\[[^\]]+\]""".r
  
  def validatePrefix(prefix: String): Either[String, String] =
    if (prefix.isEmpty) {
      Left("Invoice prefix cannot be empty")
    } else if (!SafeFileSystemChars.matches(prefix)) {
      Left(s"Invoice prefix contains invalid characters. Only letters, numbers, dashes (-), underscores (_), periods (.), and date placeholders [YYYY], [YY], [MM], [DD] are allowed: $prefix")
    } else {
      validatePlaceholders(prefix).map(_ => prefix)
    }
  
  private def validatePlaceholders(prefix: String): Either[String, Unit] = {
    val foundPlaceholders = PlaceholderPattern.findAllIn(prefix).toSet
    val invalidPlaceholders = foundPlaceholders -- ValidPlaceholders
    
    if (invalidPlaceholders.nonEmpty) {
      Left(s"Invalid date placeholders found: ${invalidPlaceholders.mkString(", ")}. Valid placeholders are: ${ValidPlaceholders.mkString(", ")}")
    } else {
      Right(())
    }
  }
  
  def formatInvoiceNumber(prefix: String, number: Long, digits: Int): String =
    formatInvoiceNumberWithDate(prefix, number, digits, Instant.now(), ZoneOffset.of("+02:00"))
  
  def formatInvoiceNumberWithDate(
    prefix: String, 
    number: Long, 
    digits: Int, 
    timestamp: Instant = Instant.now(), 
    timezone: ZoneId = ZoneOffset.of("+02:00")
  ): String = {
    val leftPaddedNumber = String.format(s"%0${digits}d", number)
    val formattedPrefix = formatDatePlaceholders(prefix, timestamp, timezone)
    s"$formattedPrefix$leftPaddedNumber"
  }
  
  private def formatDatePlaceholders(prefix: String, timestamp: Instant, timezone: ZoneId): String = {
    val zonedDateTime = timestamp.atZone(timezone)
    
    prefix
      .replace("[YYYY]", zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy")))
      .replace("[YY]", zonedDateTime.format(DateTimeFormatter.ofPattern("yy")))
      .replace("[MM]", zonedDateTime.format(DateTimeFormatter.ofPattern("MM")))
      .replace("[DD]", zonedDateTime.format(DateTimeFormatter.ofPattern("dd")))
  }
}
