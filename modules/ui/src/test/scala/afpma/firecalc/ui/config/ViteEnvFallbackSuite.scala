/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import scala.scalajs.js.Dynamic

import afpma.firecalc.domain.FireboxAvailability
import afpma.firecalc.engine.alg.en15544.FireboxEngineSource
import afpma.firecalc.engine.alg.en15544.FireboxEngineSourceConfig
import afpma.firecalc.engine.alg.en15544.FireboxKind

import utest.*

/**
 * Unit tests for the optional env var fallbacks (`UIConfig.parseString`)
 * that consume the backend URL parts and the CGUV URL via the `ViteEnv`
 * facade, plus a facade-level check that first access to every remaining
 * optional `VITE_*` field is cast-safe in an env-less JS runtime.
 *
 * The vars may be absent from the vite environment entirely, in which case
 * the JS wrapper exports `undefined` and the facade maps it to `None`;
 * `parseString` must treat that (and `Some("")`) as "use the default"
 * instead of letting the facade cast throw `ClassCastException`.
 *
 * The Node test runtime loads the same in-repo JS wrapper the browser build
 * uses; there `import.meta.env` is absent, so the wrapper's documented
 * Node-test defaults apply: the optional vars are `undefined` (absence),
 * except the two remote-calc vars, which default to non-empty values so the
 * remote-prefetch suites project per-kind state. The facade-level checks
 * below therefore exercise the missing path for the backend / CGUV /
 * availability fields, while the remote-calc and backend-URL fallback
 * contracts are pinned by the pure `resolveEngineSource` / `buildBackendUrl`
 * tests.
 */
object ViteEnvFallbackSuite extends TestSuite:

    val tests = Tests {

        test("parseString — present value is used, no warning") {
            var warnings = List.empty[String]
            val original = Dynamic.global.console.warn
            Dynamic.global.console.warn         = (msg: String) => warnings = warnings :+ msg
            try
                val result = UIConfig.parseString("VITE_BACKEND_HOST", Some("api.example.com"), "localhost")
                assert(result == "api.example.com")
                assert(warnings.isEmpty           )
            finally Dynamic.global.console.warn = original
        }

        test("parseString — missing var yields the default and warns, naming the var") {
            // Intercept `console.warn`: `parseString` calls the JS global
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
                val result = UIConfig.parseString("VITE_BACKEND_HOST", None, "localhost")
                assert(result == "localhost"                      )
                assert(warnings.size == 1                         )
                assert(warnings.head.contains("VITE_BACKEND_HOST"))
                assert(warnings.head.contains("localhost")        )
            finally Dynamic.global.console.warn = original
        }

        test("parseString — empty var yields the default and warns, naming the var") {
            var warnings = List.empty[String]
            val original = Dynamic.global.console.warn
            Dynamic.global.console.warn         = (msg: String) => warnings = warnings :+ msg
            try
                val result = UIConfig.parseString("VITE_CGUV_URL", Some(""), "https://example.com/cguv")
                assert(result == "https://example.com/cguv"   )
                assert(warnings.size == 1                     )
                assert(warnings.head.contains("VITE_CGUV_URL"))
            finally Dynamic.global.console.warn = original
        }

        test("backend URL — every missing var resolves to the boring default") {
            // The documented fallback (http / localhost / 8181 / empty path)
            // mirrors vite.config.js's dev server. `parseString(None,
            // default)` is the missing-var path; `buildBackendUrl` assembles
            // the URL from the resolved parts.
            val protocol = UIConfig.parseString("VITE_BACKEND_PROTOCOL", None, "http")
            val host     = UIConfig.parseString("VITE_BACKEND_HOST", None, "localhost")
            val port     = UIConfig.parseString("VITE_BACKEND_PORT", None, "8181")
            val basePath = UIConfig.parseString("VITE_BACKEND_BASE_PATH", None, "")
            assert(UIConfig.buildBackendUrl(protocol, host, port, basePath) == "http://localhost:8181")
        }

        test("buildBackendUrl — default ports omitted, others kept") {
            assert(UIConfig.buildBackendUrl("http", "localhost", "80", "/v1") == "http://localhost/v1"               )
            assert(UIConfig.buildBackendUrl("https", "api.example.com", "443", "/v1") == "https://api.example.com/v1")
            assert(UIConfig.buildBackendUrl("https", "api.example.com", "8443", "") == "https://api.example.com:8443")
            assert(UIConfig.buildBackendUrl("http", "localhost", "8181", "") == "http://localhost:8181"              )
        }

        test("cguvUrl — env-less runtime resolves to the empty fallback, no ClassCastException") {
            assert(UIConfig.cguvUrl.isEmpty)
        }

        test("resolveEngineSource — missing or empty vars resolve to AllLocal") {
            assert(UIConfig.resolveEngineSource(None, None) == FireboxEngineSourceConfig.AllLocal                   )
            assert(UIConfig.resolveEngineSource(Some(""), Some("http://x/v1")) == FireboxEngineSourceConfig.AllLocal)
            assert(UIConfig.resolveEngineSource(Some("ecolabeled"), None) == FireboxEngineSourceConfig.AllLocal     )
            assert(UIConfig.resolveEngineSource(Some("ecolabeled"), Some("")) == FireboxEngineSourceConfig.AllLocal )
        }

        test("resolveEngineSource — valid vars resolve the remote kinds") {
            val cfg = UIConfig.resolveEngineSource(Some("ecolabeled, door15a-catalog"), Some("http://calc:9000/v1"))
            assert(cfg.isRemote(FireboxKind.Ecolabeled)                                                      )
            assert(cfg.isRemote(FireboxKind.Door15aCatalog)                                                  )
            assert(!cfg.isRemote(FireboxKind.Traditional)                                                    )
            assert(cfg.sourceFor(FireboxKind.Ecolabeled) == FireboxEngineSource.Remote("http://calc:9000/v1"))
        }

        test("resolveEngineSource — unknown keys warn and stay local") {
            var warnings = List.empty[String]
            val original = Dynamic.global.console.warn
            Dynamic.global.console.warn         = (msg: String) => warnings = warnings :+ msg
            try
                val cfg = UIConfig.resolveEngineSource(Some("ecolabeled,bogus"), Some("http://x/v1"))
                assert(cfg.isRemote(FireboxKind.Ecolabeled)  )
                assert(!cfg.isRemote(FireboxKind.Traditional))
                assert(warnings.size == 1                    )
                assert(warnings.head.contains("bogus")       )
            finally Dynamic.global.console.warn = original
        }

        test("fireboxEngineSource — first facade access is cast-safe") {
            // The Node test runtime's wrapper defaults pin
            // `VITE_FIREBOX_REMOTE_KINDS` / `VITE_FIREBOX_CALC_BASE_URL` to
            // non-empty values (so the remote-prefetch suites project
            // per-kind state), so the facade resolves a remote config here;
            // the AllLocal fallback contract is pinned by the pure
            // `resolveEngineSource` tests above. What this check proves:
            // first access of the two facade fields throws no
            // `ClassCastException`, and a kind the defaults do not list
            // (Traditional) resolves local.
            val cfg = UIConfig.fireboxEngineSource
            assert(cfg.sourceFor(FireboxKind.Traditional) == FireboxEngineSource.Local)
        }

        test("availability — env-less runtime resolves to all enabled, no ClassCastException") {
            // First access of the ten `VITE_FIREBOX_AVAIL_*` facade fields.
            assert(UIConfig.uiAvailability == FireboxAvailability.AllEnabled     )
            assert(UIConfig.backendAvailability == FireboxAvailability.AllEnabled)
        }
    }
