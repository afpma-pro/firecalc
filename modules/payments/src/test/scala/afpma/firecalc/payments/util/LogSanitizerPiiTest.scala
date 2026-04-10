/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import afpma.firecalc.payments.exceptions.*

import utest.*

object LogSanitizerPiiTest extends TestSuite {

    val tests = Tests {

        test("TooManyIntentsForEmailException masks email in context") {
            val ex = TooManyIntentsForEmailException("user@example.com")
            val emailCtx = ex.context("email")
            assert(!emailCtx.contains("user@"))
            assert(emailCtx.contains("***@example.com"))
        }

        test("InvoiceEmailFailedException masks customerEmail in context") {
            val ex = InvoiceEmailFailedException(
                orderId       = java.util.UUID.randomUUID(),
                customerEmail = "john.doe@company.org",
                reason        = "SMTP timeout"
            )
            val emailCtx = ex.context("customerEmail")
            assert(!emailCtx.contains("john.doe@"))
            assert(emailCtx.contains("***@company.org"))
        }

        test("EmailSendingFailedException masks recipient in context") {
            val ex = EmailSendingFailedException(
                orderId   = java.util.UUID.randomUUID(),
                recipient = "alice@test.fr",
                reason    = "Connection refused"
            )
            val recipientCtx = ex.context("recipient")
            assert(!recipientCtx.contains("alice@"))
            assert(recipientCtx.contains("***@test.fr"))
        }
    }
}
