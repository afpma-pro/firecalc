/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.Instant
import java.time.ZoneOffset

import utest.*

object InvoiceNumberValidatorTest extends TestSuite {

    val tests = Tests {

        test("validatePrefix - accepts valid basic prefixes") {
            val result = InvoiceNumberValidator.validatePrefix("FCALC-")
            assert(result.isRight                  )
            assert(result.getOrElse("") == "FCALC-")
        }

        test("validatePrefix - accepts date placeholders") {
            val validPrefixes = List(
                "FCALC-[YYYY]-",
                "INVOICE-[YY][MM]-",
                "TEST-[YYYY][MM][DD]-",
                "PREFIX-[DD][MM][YYYY]-"
            )

            validPrefixes.foreach { prefix =>
                val result = InvoiceNumberValidator.validatePrefix(prefix)
                assert(result.isRight)
            }
        }

        test("validatePrefix - rejects invalid placeholders") {
            val invalidPrefixes = List(
                "FCALC-[INVALID]-",
                "INVOICE-[YYY]-", // Wrong format
                "TEST-[MONTH]-",  // Invalid placeholder
                "PREFIX-[DDDD]-"  // Wrong format
            )

            invalidPrefixes.foreach { prefix =>
                val result = InvoiceNumberValidator.validatePrefix(prefix)
                assert(result.isLeft)
            }
        }

        test("validatePrefix - rejects empty prefix") {
            val result = InvoiceNumberValidator.validatePrefix("")
            assert(result.isLeft                                        )
            assert(result.left.getOrElse("").contains("cannot be empty"))
        }

        test("validatePrefix - rejects invalid characters") {
            val result = InvoiceNumberValidator.validatePrefix("FCALC-@#$-")
            assert(result.isLeft                                           )
            assert(result.left.getOrElse("").contains("invalid characters"))
        }

        test("formatInvoiceNumberWithDate - handles YYYY placeholder") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[YYYY]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-2024-042")
        }

        test("formatInvoiceNumberWithDate - handles YY placeholder") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[YY]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-24-042")
        }

        test("formatInvoiceNumberWithDate - handles MM placeholder") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[MM]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-09-042")
        }

        test("formatInvoiceNumberWithDate - handles DD placeholder") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[DD]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-23-042")
        }

        test("formatInvoiceNumberWithDate - handles multiple placeholders") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[YYYY][MM][DD]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-20240923-042")
        }

        test("formatInvoiceNumberWithDate - handles different order") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[DD][MM][YY]-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-230924-042")
        }

        test("formatInvoiceNumberWithDate - handles no placeholders") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-DEV-",
                42,
                3,
                timestamp,
                timezone
            )

            assert(result == "FCALC-DEV-042")
        }

        test("formatInvoiceNumberWithDate - respects timezone") {
            val timestamp     = Instant.parse("2024-09-23T22:30:00Z") // Late at night UTC
            val utcTimezone   = ZoneOffset.UTC
            val parisTimezone = ZoneOffset.of("+02:00")

            val utcResult = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[DD]-",
                1,
                3,
                timestamp,
                utcTimezone
            )

            val parisResult = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[DD]-",
                1,
                3,
                timestamp,
                parisTimezone
            )

            assert(utcResult == "FCALC-23-001"  )
            assert(parisResult == "FCALC-24-001") // Next day in Paris timezone
        }

        test("formatInvoiceNumberWithDate - handles different digit counts") {
            val timestamp = Instant.parse("2024-09-23T10:30:00Z")
            val timezone  = ZoneOffset.of("+02:00")

            val result1 = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[YYYY]-",
                5,
                2,
                timestamp,
                timezone
            )

            val result2 = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                "FCALC-[YYYY]-",
                5,
                5,
                timestamp,
                timezone
            )

            assert(result1 == "FCALC-2024-05"   )
            assert(result2 == "FCALC-2024-00005")
        }

        test("formatInvoiceNumber - uses current time and default timezone") {
            val result = InvoiceNumberValidator.formatInvoiceNumber("TEST-", 1, 3)

            // Basic prefix without date placeholders should return expected format
            assert(result.startsWith("TEST-"))
            assert(result.endsWith("001")    )
            assert(result == "TEST-001"      ) // No date placeholders, so exact match
        }

        // prefixApplies (the startup guard's clause 6 question, owned with the grammar).
        test("prefixApplies - literals must match, placeholders may hold another date") {
            assert(InvoiceNumberValidator.prefixApplies("FCALC-STG-[YYYY]-", "FCALC-STG-2026-0004")    )
            // clause 6: a number written under a previous year still carries this prefix
            assert(InvoiceNumberValidator.prefixApplies("FCALC-STG-[YYYY]-", "FCALC-STG-2025-0007")    )
            assert(!InvoiceNumberValidator.prefixApplies("FCALC-STG-[YYYY]-", "OTHER-PREFIX-2025-0004"))
        }

        test("prefixApplies - each placeholder covers exactly its own digits, the number part any width") {
            // [YYYY] is four digits: a three-digit run is not a year.
            assert(!InvoiceNumberValidator.prefixApplies("FCALC-[YYYY]-", "FCALC-202-0004")          )
            // [MM] stands for exactly two digits.
            assert(InvoiceNumberValidator.prefixApplies("FCALC-[YYYY]-[MM]-", "FCALC-2026-11-0004")  )
            assert(!InvoiceNumberValidator.prefixApplies("FCALC-[YYYY]-[MM]-", "FCALC-2026-111-0004"))
            // the sequence may have been written at another width
            assert(InvoiceNumberValidator.prefixApplies("FCALC-[YYYY]-", "FCALC-2026-1234567")       )
        }

        test("prefixApplies - bracketed text outside the placeholder set stays literal") {
            // [WW] is not an accepted placeholder — the matcher must not invent a wildcard
            // for it (the alternation is derived from the same set validatePrefix accepts).
            assert(InvoiceNumberValidator.prefixApplies("X-[WW]-", "X-[WW]-5") )
            assert(!InvoiceNumberValidator.prefixApplies("X-[WW]-", "X-2026-5"))
        }
    }
}
