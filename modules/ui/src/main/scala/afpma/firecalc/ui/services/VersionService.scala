/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.FIRECALC_UI
import afpma.firecalc.ui.config.BuildMode
import afpma.firecalc.ui.config.ViteEnv

/**
 * Service for handling version information display and formatting.
 *
 * Implements environment-aware version display:
 * - Development: Shows full version with git hash in footer
 * - Staging: Shows full version with git hash in footer
 * - Production: Shows clean version in footer, git hash in tooltip only
 */
object VersionService {

    /**
     * Version information container
     *
     * @param displayVersion The version string to display in the footer
     * @param fullVersion The complete version string with all metadata
     * @param showTooltip Whether to show git hash in tooltip (true for production only)
     */
    final case class VersionInfo(
        displayVersion: String,
        fullVersion   : String,
        showTooltip   : Boolean
    )

    /**
     * Pure version-display rule: build the `VersionInfo` for a given mode
     * from explicit version strings.
     *
     * `getVersionInfo()` is the only production caller. The seam exists so
     * the mode-dependent display rule can be pinned by `VersionServiceSuite`:
     * the Node test runtime pins `MODE` to `"development"` (see
     * `src/main/js/vite-env.js`) and resolves no `firecalc-ui` module, so the
     * mode cannot be varied through `getVersionInfo()` there.
     *
     * The match is exhaustive over the enum, so a new `BuildMode` case is a
     * compile error here rather than a silent fall-through.
     *
     * @param mode The build mode whose display policy applies
     * @param base The clean UI base version (no git metadata)
     * @param engine The engine version appended in production
     * @param fullVersion The complete version string with all metadata
     * @return VersionInfo configured for that mode
     */
    def versionInfoFor(mode: BuildMode, base: String, engine: String, fullVersion: String): VersionInfo =
        mode match
            case BuildMode.Production =>
                VersionInfo(
                    displayVersion = s"$base+engine-$engine",
                    fullVersion    = fullVersion,
                    showTooltip    = true
                )

            case BuildMode.Development | BuildMode.Staging =>
                VersionInfo(
                    displayVersion = fullVersion,
                    fullVersion    = fullVersion,
                    showTooltip    = false
                )

    /**
     * Get version information for the current build mode.
     *
     * Reads the mode through `ViteEnv.buildMode` (the typed view of Vite's
     * `MODE`, whose vocabulary `BuildMode` owns) and the version strings from
     * the `firecalc-ui` facade, and hands them to [[versionInfoFor]]. It
     * holds no display policy of its own.
     *
     * @return VersionInfo configured for the current build mode
     */
    def getVersionInfo(): VersionInfo =
        versionInfoFor       (
            mode        = ViteEnv.buildMode,
            base        = FIRECALC_UI.UI_BASE_VERSION,
            engine      = FIRECALC_UI.ENGINE_VERSION,
            fullVersion = FIRECALC_UI.UI_FULL_VERSION
        )

    /**
     * Log version information to browser console
     *
     * Should be called once on application startup
     */
    def logVersionToConsole(): Unit = {
        val info = getVersionInfo()
        println(s"FireCalc UI Version: ${info.fullVersion}")
        println(s"Environment: ${ViteEnv.modeString}"      )
    }
}
