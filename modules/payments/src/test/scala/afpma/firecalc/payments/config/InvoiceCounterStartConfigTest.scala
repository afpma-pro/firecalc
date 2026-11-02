/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.config

import com.typesafe.config.ConfigFactory
import utest.*

/**
 * Configuration load of `invoice.counter-start`. The key was renamed from
 * the retired `invoice.counter-starting-number` and now also names the bounded repair the
 * boot may apply to an existing counter, so it is REQUIRED and there is deliberately no
 * compatibility branch: a config still carrying the old key fails at configuration load —
 * naming the key it wants and the replacement — exactly as one missing the key does.
 * (The deployment preflight (firecalc-docker) detects this at deploy time; the boot
 * itself stays strict.)
 */
object InvoiceCounterStartConfigTest extends TestSuite {

    private def invoiceSection(invoiceHocon: String) =
        ConfigFactory.parseString(s"""invoice { $invoiceHocon }""").resolve().getConfig("invoice")

    private def failureFor(invoiceHocon: String): String =
        scala.util.Try(ConfigLoader.loadInvoiceCounterStart(invoiceSection(invoiceHocon))).failed.get.getMessage

    val tests = Tests {
        test("the renamed key loads as the counter start") {
            ConfigLoader.loadInvoiceCounterStart(invoiceSection("""number-prefix = "FC", counter-start = 7""")) ==> 7L
        }

        test("a missing key fails at configuration load, naming the key it wants") {
            val msg = failureFor("""number-prefix = "FC"""")
            assert(msg.contains("invoice.counter-start"))
        }

        test("the old key present fails at configuration load: retired, naming the replacement") {
            val msg = failureFor("""number-prefix = "FC", counter-starting-number = 1""")
            assert(msg.contains("counter-starting-number")) // names the retired key
            assert(msg.contains("invoice.counter-start")  ) // names the key it wants
            assert(msg.toLowerCase.contains("retired")    ) // says it is retired
            assert(msg.toLowerCase.contains("replacement")) // …and points at the replacement
        }

        test("the old key alongside the new one still fails: no compatibility branch") {
            val msg = failureFor("""counter-start = 4, counter-starting-number = 1""")
            assert(msg.contains("counter-starting-number"))
        }
    }
}
