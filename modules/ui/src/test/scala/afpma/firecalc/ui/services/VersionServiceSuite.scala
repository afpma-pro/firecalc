/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.config.BuildMode

import utest.*

/**
 * Unit tests for the environment-aware version display rule
 * (`VersionService.versionInfoFor`), pinned per `BuildMode` case:
 *
 * - Production: footer shows the clean base version with the engine version
 *   appended, the git-hash metadata stays in `fullVersion` for the tooltip,
 *   and `showTooltip` is on.
 * - Development / Staging: footer shows the full version verbatim and
 *   `showTooltip` is off.
 *
 * What is NOT covered here: `getVersionInfo()` and `logVersionToConsole()`.
 * Both read their inputs through JS facades, and the Node test runtime pins
 * `MODE` to `"development"` (`src/main/js/vite-env.js`) while resolving no
 * `firecalc-ui` module, so the mode cannot be varied through them. Nothing
 * asserts the footer a browser renders: `Footer` consumes these values and the
 * browser build only compiles that wiring. This suite loads in the Node
 * runtime because Scala.js DCE drops the unreachable facade reads from
 * `VersionService`; a test that called `getVersionInfo()` would need that
 * module to resolve. `BuildMode.fromString` / `toModeString` are out of scope
 * for this suite.
 *
 * The sentinel version strings are mutually distinct, so any swap of `base`
 * and `fullVersion` inside the implementation fails the assertions.
 */
object VersionServiceSuite extends TestSuite:

    private val base        = "1.2.3-BASESENTINEL"
    private val engine      = "9.8.7-ENGINESENTINEL"
    private val fullVersion = "1.2.3-dev.20250101-abcdef01-FULLSENTINEL"

    private def infoFor(mode: BuildMode): VersionService.VersionInfo =
        VersionService.versionInfoFor       (
            mode        = mode,
            base        = base,
            engine      = engine,
            fullVersion = fullVersion
        )

    val tests = Tests {

        test("production — clean version plus engine in footer, tooltip on") {
            val info = infoFor(BuildMode.Production)

            // The footer format is the documented contract
            // (docs/dev/guides/VERSIONING_SYSTEM.md): pinned as a literal so a
            // change to the separator or the appended parts fails here.
            assert(info.displayVersion == "1.2.3-BASESENTINEL+engine-9.8.7-ENGINESENTINEL")
            assert(info.fullVersion == fullVersion                                        )
            assert(info.showTooltip                                                       )
        }

        test("development — full version in footer, tooltip off") {
            val info = infoFor(BuildMode.Development)

            assert(info.displayVersion == fullVersion)
            assert(info.fullVersion == fullVersion   )
            assert(!info.showTooltip                 )
        }

        test("staging — full version in footer, tooltip off") {
            val info = infoFor(BuildMode.Staging)

            assert(info.displayVersion == fullVersion)
            assert(info.fullVersion == fullVersion   )
            assert(!info.showTooltip                 )
        }
    }
