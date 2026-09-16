/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import scala.scalajs.js.Dynamic

import utest.*

/**
 * Unit tests for the firebox-calc prefetch retry env parsing
 * (`UIConfig.parseInt`), which consumes the three
 * `VITE_FIREBOX_PREFETCH_*` vars.
 *
 * The vars may be absent from the vite environment entirely, in which case
 * the JS wrapper exports `undefined` and the `ViteEnv` facade maps it to
 * `None`; the parse helper must treat that (and `Some("")`) as "use the
 * default" instead of letting the facade cast throw `ClassCastException`.
 */
object PrefetchRetryConfigSuite extends TestSuite:

    val tests = Tests {

        test("parseInt — missing var yields the default") {
            assert(UIConfig.parseInt("VITE_FIREBOX_PREFETCH_MAX_RETRIES", None, 5) == 5)
        }

        test("parseInt — empty var yields the default") {
            assert(UIConfig.parseInt("VITE_FIREBOX_PREFETCH_MAX_DELAY_MS", Some(""), 30000) == 30000)
        }

        test("parseInt — numeric var is parsed and applied") {
            assert(UIConfig.parseInt("VITE_FIREBOX_PREFETCH_MAX_RETRIES", Some("3"), 5) == 3             )
            assert(UIConfig.parseInt("VITE_FIREBOX_PREFETCH_BASE_DELAY_MS", Some("0"), 1000) == 0        )
            assert(UIConfig.parseInt("VITE_FIREBOX_PREFETCH_MAX_DELAY_MS", Some("12000"), 30000) == 12000)
        }

        test("parseInt — non-numeric var yields the default and warns, naming the var") {
            // Intercept `console.warn`: `parseInt` calls the JS global
            // directly (a call-time property lookup, no cached reference), so
            // overwriting `warn` on `globalThis.console` catches it. The stub
            // compiles to a real JS function that updates `warnings` by
            // reference (captured `var`), which the asserts below read back.
            // Caveat: the patch is process-global — any other code warning in
            // this window would pollute `warnings`, so the `finally` restore
            // is load-bearing and `size == 1` doubles as a check that nothing
            // else warned during the call.
            var warnings = List.empty[String]
            val original = Dynamic.global.console.warn
            Dynamic.global.console.warn         = (msg: String) => warnings = warnings :+ msg
            try
                val result = UIConfig.parseInt("VITE_FIREBOX_PREFETCH_MAX_RETRIES", Some("abc"), 5)
                assert(result == 5                                                )
                assert(warnings.size == 1                                         )
                assert(warnings.head.contains("VITE_FIREBOX_PREFETCH_MAX_RETRIES"))
            finally Dynamic.global.console.warn = original
        }

        test("PrefetchRetryConfig — documented defaults are 5 / 1000 / 30000") {
            val cfg = PrefetchRetryConfig()
            assert(cfg.maxRetries == 5     )
            assert(cfg.baseDelayMs == 1000L)
            assert(cfg.maxDelayMs == 30000L)
        }
    }
