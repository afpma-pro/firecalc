/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import utest.*

object LogSanitizerTest extends TestSuite:

    val tests = Tests {

        test("maskEmail - normal email") {
            assert(LogSanitizer.maskEmail("user@example.com") == "u***@example.com")
        }

        test("maskEmail - single char local part") {
            assert(LogSanitizer.maskEmail("u@example.com") == "u***@example.com")
        }

        test("maskEmail - long local part") {
            assert(LogSanitizer.maskEmail("guillaume.augais@afpma.org") == "g***@afpma.org")
        }

        test("maskEmail - malformed email") {
            assert(LogSanitizer.maskEmail("not-an-email") == "***@***")
        }

        test("maskEmail - empty string") {
            assert(LogSanitizer.maskEmail("") == "***@***")
        }

        test("maskToken - long token") {
            val token  = "sk_live_abcdefghijklmnop"
            val masked = LogSanitizer.maskToken(token)
            assert(masked == "sk_live_...mnop")
        }

        test("maskToken - short token") {
            assert(LogSanitizer.maskToken("short") == "****")
        }

        test("maskToken - exactly 12 chars") {
            assert(LogSanitizer.maskToken("123456789012") == "****")
        }

        test("maskToken - 13 chars") {
            assert(LogSanitizer.maskToken("1234567890123") == "12345678...0123")
        }

        test("redactJson - redacts email field") {
            val json     = """{"email": "user@example.com", "name": "John"}"""
            val redacted = LogSanitizer.redactJson(json)
            assert(!redacted.contains("user@example.com")       )
            assert(redacted.contains(""""email":"[REDACTED]""""))
            assert(redacted.contains(""""name": "John"""")      )
        }

        test("redactJson - redacts multiple sensitive fields") {
            val json     =
                """{"email":"a@b.com","given_name":"John","family_name":"Doe","address_line1":"123 Main St"}"""
            val redacted = LogSanitizer.redactJson(json)
            assert(!redacted.contains("a@b.com")    )
            assert(!redacted.contains("John")       )
            assert(!redacted.contains("Doe")        )
            assert(!redacted.contains("123 Main St"))
        }

        test("redactJson - redacts password field") {
            val json     = """{"password": "s3cret!"}"""
            val redacted = LogSanitizer.redactJson(json)
            assert(!redacted.contains("s3cret!")                   )
            assert(redacted.contains(""""password":"[REDACTED]""""))
        }

        test("redactJson - redacts access_token field") {
            val json     = """{"access_token": "sk_live_abc123"}"""
            val redacted = LogSanitizer.redactJson(json)
            assert(!redacted.contains("sk_live_abc123")                )
            assert(redacted.contains(""""access_token":"[REDACTED]""""))
        }

        test("redactJson - preserves non-sensitive fields") {
            val json     = """{"status": "active", "amount": 1000}"""
            val redacted = LogSanitizer.redactJson(json)
            assert(redacted == json)
        }
    }
