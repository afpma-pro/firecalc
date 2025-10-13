/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import afpma.firecalc.ui.FIRECALC_UI
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
    fullVersion: String,
    showTooltip: Boolean
  )

  /**
   * Get version information based on current environment
   *
   * @return VersionInfo configured for the current environment
   */
  def getVersionInfo(): VersionInfo = {
    val mode = ViteEnv.modeString
    val base = FIRECALC_UI.UI_BASE_VERSION
    val engine = FIRECALC_UI.ENGINE_VERSION
    val git = FIRECALC_UI.GIT_HASH
    val fullVersion = FIRECALC_UI.UI_FULL_VERSION

    mode match {
      case "production" =>
        VersionInfo(
          displayVersion = s"$base+engine-$engine",
          fullVersion = fullVersion,
          showTooltip = true
        )
      
      case "staging" | "dev" | _ =>
        VersionInfo(
          displayVersion = fullVersion,
          fullVersion = fullVersion,
          showTooltip = false
        )
    }
  }

  /**
   * Log version information to browser console
   *
   * Should be called once on application startup
   */
  def logVersionToConsole(): Unit = {
    val info = getVersionInfo()
    println(s"FireCalc UI Version: ${info.fullVersion}")
    println(s"Environment: ${ViteEnv.modeString}")
  }
}