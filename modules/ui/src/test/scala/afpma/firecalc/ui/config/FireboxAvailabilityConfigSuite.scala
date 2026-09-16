/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import scala.scalajs.js.Dynamic

import afpma.firecalc.domain.FireboxAvailability

import utest.*

/**
 * Unit tests for the firebox availability env parsing
 * (`UIConfig.parseBool`), which consumes the ten
 * `VITE_FIREBOX_AVAIL_*` vars via the `ViteEnv` facade.
 *
 * The vars may be absent from the vite environment entirely, in which case
 * the JS wrapper exports `undefined` and the facade maps it to `None`;
 * `parseBool` must treat that (and `Some("")`) as "available" instead of
 * letting the facade cast throw `ClassCastException` at first access.
 */
object FireboxAvailabilityConfigSuite extends TestSuite:

    val tests = Tests {

        test("parseBool — explicit \"true\" yields true") {
            assert(UIConfig.parseBool("key", Some("true")))
        }

        test("parseBool — explicit \"false\" yields false") {
            assert(!UIConfig.parseBool("key", Some("false")))
        }

        test("parseBool — empty string yields true") {
            assert(UIConfig.parseBool("key", Some("")))
        }

        test("parseBool — missing var (None) yields true") {
            assert(UIConfig.parseBool("key", None))
        }

        test("parseBool — garbage string yields true") {
            assert(UIConfig.parseBool("key", Some("garbage")))
            assert(UIConfig.parseBool("key", Some("maybe"))  )
            assert(UIConfig.parseBool("key", Some("0"))      )
            assert(UIConfig.parseBool("key", Some("FALSE"))  )
        }

        test("parseBool — garbage value warns, naming the var") {
            // Intercept `console.warn`: `parseBool` calls the JS global
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
                val result = UIConfig.parseBool("VITE_FIREBOX_AVAIL_ECOLABELED", Some("maybe"))
                assert(result == true                                         )
                assert(warnings.size == 1                                     )
                assert(warnings.head.contains("VITE_FIREBOX_AVAIL_ECOLABELED"))
                assert(warnings.head.contains("maybe")                        )
            finally Dynamic.global.console.warn = original
        }

        test("parseBool — missing var (None) does not warn") {
            // Absence is a legitimate build-mode configuration (a deployment
            // may omit the var entirely); the fallback to "available" is
            // silent by design, unlike the garbage-value warning.
            var warnings = List.empty[String]
            val original = Dynamic.global.console.warn
            Dynamic.global.console.warn         = (msg: String) => warnings = warnings :+ msg
            try
                assert(UIConfig.parseBool("VITE_FIREBOX_AVAIL_ECOLABELED", None))
                assert(warnings.isEmpty                                         )
            finally Dynamic.global.console.warn = original
        }

        test("FireboxAvailability — all \"true\" builds AllEnabled") {
            val fa = FireboxAvailability(
                traditional    = UIConfig.parseBool("key", Some("true")),
                ecolabeled     = UIConfig.parseBool("key", Some("true")),
                afpmaPrse      = UIConfig.parseBool("key", Some("true")),
                singleTested   = UIConfig.parseBool("key", Some("true")),
                door15aCatalog = UIConfig.parseBool("key", Some("true"))
            )
            assert(fa == FireboxAvailability.AllEnabled)
        }

        test("FireboxAvailability — all \"false\" builds all disabled") {
            val fa       = FireboxAvailability(
                traditional    = UIConfig.parseBool("key", Some("false")),
                ecolabeled     = UIConfig.parseBool("key", Some("false")),
                afpmaPrse      = UIConfig.parseBool("key", Some("false")),
                singleTested   = UIConfig.parseBool("key", Some("false")),
                door15aCatalog = UIConfig.parseBool("key", Some("false"))
            )
            val disabled = FireboxAvailability.disabledTypeNames(fa)
            assert(disabled == Set("Traditional", "Ecolabeled", "AFPMA_PRSE", "SingleTested", "Door15aFirebox_Catalog"))
        }
    }
