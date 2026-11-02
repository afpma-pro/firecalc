/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
            Left(
                s"Invoice prefix contains invalid characters. Only letters, numbers, dashes (-), underscores (_), periods (.), and date placeholders [YYYY], [YY], [MM], [DD] are allowed: $prefix"
            )
        } else {
            validatePlaceholders(prefix).map(_ => prefix)
        }

    private def validatePlaceholders(prefix: String): Either[String, Unit] = {
        val foundPlaceholders   = PlaceholderPattern.findAllIn(prefix).toSet
        val invalidPlaceholders = foundPlaceholders -- ValidPlaceholders

        if (invalidPlaceholders.nonEmpty) {
            Left(
                s"Invalid date placeholders found: ${invalidPlaceholders.mkString(", ")}. Valid placeholders are: ${ValidPlaceholders.mkString(", ")}"
            )
        } else {
            Right(())
        }
    }

    def formatInvoiceNumber(prefix: String, number: Long, digits: Int): String =
        formatInvoiceNumberWithDate(prefix, number, digits, Instant.now(), ZoneOffset.of("+02:00"))

    def formatInvoiceNumberWithDate(
        prefix   : String,
        number   : Long,
        digits   : Int,
        timestamp: Instant = Instant.now(),
        timezone : ZoneId  = ZoneOffset.of("+02:00")
    ): String = {
        val leftPaddedNumber = String.format(s"%0${digits}d", number)
        val formattedPrefix  = formatDatePlaceholders(prefix, timestamp, timezone)
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

    // Derived from the same ValidPlaceholders set validatePlaceholders accepts — there is
    // deliberately no second spelling of the placeholder grammar. Bracketed text that is
    // not a valid placeholder stays literal: a validated prefix contains no such text, but
    // the matcher must not invent a wildcard for it either.
    private val ValidPlaceholderTokens =
        s"(?:${ValidPlaceholders.map(java.util.regex.Pattern.quote).mkString("|")})".r

    /**
     * Does a stored number carry this environment's static prefix? The literal
     * segments of the prefix must match exactly; date placeholders may legitimately
     * hold another year, month or day (clause 6 — "dev run 2025-0007 → 2026-0008"),
     * and the number part may have been written at another width. Owned here because
     * this is where the prefix grammar is defined; the startup guard asks.
     */
    def prefixApplies(prefix: String, number: String): Boolean = {
        val literals    = ValidPlaceholderTokens.split(prefix).toList
        val digitFields = ValidPlaceholderTokens.findAllIn(prefix).map(placeholderDigitField).toList
        val regex       = literals
            .map(java.util.regex.Pattern.quote)
            .zip(digitFields :+ "")
            .map((literal, digitField) => literal + digitField)
            .mkString("^", "", "\\d+$")
        java.util.regex.Pattern.compile(regex).matcher(number).matches()
    }

    /** A placeholder stands for exactly the digits it prints: [YYYY] four, [YY]/[MM]/[DD] two. */
    private def placeholderDigitField(token: String): String =
        s"\\d{${token.drop(1).dropRight(1).length}}"
}
