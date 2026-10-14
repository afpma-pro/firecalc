/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import utest.*

/**
 * Unit tests for the mode vocabulary owner (`BuildMode.fromString` /
 * `BuildMode.toModeString`).
 *
 * This enum is the single place the build-mode names are spelled out: Vite's
 * `MODE` reaches the UI through `ViteEnv.buildMode`, and consumers match on the
 * enum. Two directions have to agree — the strings Vite can hand over, and the
 * canonical names `toModeString` emits (the long forms, which are also the
 * `configs/<env>/` directory names and the `--mode` values in
 * `modules/ui/package.json`). A slip here is invisible at compile time: it shows
 * up as a startup `IllegalArgumentException`, or as a mode silently taking the
 * wrong display policy.
 *
 * Covered: the accepted input set (canonical names and the two documented short
 * aliases), case-insensitivity, the round-trip invariant over every enum case,
 * and the failure path for an unrecognised mode. Not covered: `ViteEnv.buildMode`
 * itself — it is a facade over Vite's `MODE`, pinned to `"development"` in the
 * Node test runtime by `src/main/js/vite-env.js`, so it cannot be varied here.
 */
object BuildModeSuite extends TestSuite:

    val tests = Tests {

        test("fromString — the three canonical mode names resolve to their case") {
            assert(BuildMode.fromString("development") == BuildMode.Development)
            assert(BuildMode.fromString("staging") == BuildMode.Staging        )
            assert(BuildMode.fromString("production") == BuildMode.Production  )
        }

        test("fromString — the documented short aliases resolve to the same case") {
            assert(BuildMode.fromString("dev") == BuildMode.Development)
            assert(BuildMode.fromString("prod") == BuildMode.Production)
        }

        test("fromString — matching ignores case") {
            assert(BuildMode.fromString("DEVELOPMENT") == BuildMode.Development)
            assert(BuildMode.fromString("Production") == BuildMode.Production  )
        }

        test("toModeString — emits the canonical long form, never an alias") {
            assert(BuildMode.toModeString(BuildMode.Development) == "development")
            assert(BuildMode.toModeString(BuildMode.Staging) == "staging"        )
            assert(BuildMode.toModeString(BuildMode.Production) == "production"  )
        }

        test("round-trip — every case survives toModeString back to fromString") {
            // Guards the two directions against each other: a name added to one
            // side only, or an alias leaking into toModeString, fails here.
            for mode <- BuildMode.values do assert(BuildMode.fromString(BuildMode.toModeString(mode)) == mode)
        }

        test("fromString — an unrecognised mode fails loudly, naming the value and the set") {
            val err = intercept[IllegalArgumentException] {
                BuildMode.fromString("stag")
            }
            // A near-miss alias must not be accepted: the alias set is exactly
            // {dev, prod}, so a typo'd mode is an error, not a silent fallback.
            assert(err.getMessage.contains("'stag'"))
            assert(err.getMessage.contains("development, staging, production"))

            intercept[IllegalArgumentException] {
                BuildMode.fromString("")
            }
        }
    }
