/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * ScalaJS facade for accessing Vite's import.meta.env
 *
 * Provides type-safe access to environment variables defined in .env files.
 * Vite automatically injects these variables at build time based on the mode:
 * - .env.development (when running `npm run dev`)
 * - .env.staging (when running `npm run dev:staging`)
 * - .env.production (when running `npm run build:production`)
 *
 * All environment variables must be prefixed with VITE_ to be exposed to the client.
 *
 * This imports from a JavaScript wrapper file that Vite can process.
 */
@js.native
@JSImport("/src/main/js/vite-env.js", JSImport.Namespace)
private object ImportMetaEnv extends js.Any {
  val VITE_BACKEND_PROTOCOL: String = js.native
  val VITE_BACKEND_HOST: String = js.native
  val VITE_BACKEND_PORT: String = js.native
  val VITE_BACKEND_BASE_PATH: String = js.native
  val MODE: String = js.native
}

/**
 * Type-safe accessor for Vite environment variables
 *
 * Usage:
 * ```scala
 * val protocol = ViteEnv.backendProtocol  // "http" or "https"
 * val host = ViteEnv.backendHost         // "localhost" or "api.firecalc.example.com"
 *
 * // Type-safe mode checking
 * ViteEnv.buildMode match {
 *   case BuildMode.Development => // dev logic
 *   case BuildMode.Staging => // staging logic
 *   case BuildMode.Production => // prod logic
 * }
 *
 * // Or use convenience methods
 * if (ViteEnv.isDevelopment) { ... }
 * if (ViteEnv.isStaging) { ... }
 * if (ViteEnv.isProduction) { ... }
 * ```
 */
object ViteEnv {
  /** Backend protocol: "http" or "https" */
  def backendProtocol: String = ImportMetaEnv.VITE_BACKEND_PROTOCOL
  
  /** Backend host: e.g., "localhost" or "api.firecalc.example.com" */
  def backendHost: String = ImportMetaEnv.VITE_BACKEND_HOST
  
  /** Backend port: e.g., "8181" or "443" */
  def backendPort: String = ImportMetaEnv.VITE_BACKEND_PORT
  
  /** Backend base path: e.g., "/v1" */
  def backendBasePath: String = ImportMetaEnv.VITE_BACKEND_BASE_PATH
  
  /**
   * Current build mode as a type-safe enum.
   *
   * This is the primary way to check the current environment mode.
   * Use pattern matching for exhaustive checks.
   */
  lazy val buildMode: BuildMode = BuildMode.fromString(ImportMetaEnv.MODE)
  
  /**
   * Raw mode string from Vite (for debugging/logging only).
   * Prefer using `buildMode` for type-safe access.
   */
  def modeString: String = ImportMetaEnv.MODE
  
  // Convenience methods for common checks
  
  /** True if running in development mode */
  def isDevelopment: Boolean = buildMode == BuildMode.Development
  
  /** True if running in staging mode */
  def isStaging: Boolean = buildMode == BuildMode.Staging
  
  /** True if running in production mode */
  def isProduction: Boolean = buildMode == BuildMode.Production
  
  /** True if running in any non-production mode (development or staging) */
  def isNonProduction: Boolean = !isProduction
  
  /**
   * Returns debug information about the current environment configuration
   */
  def debugInfo(): String =
    s"""ViteEnv Configuration:
       |  Build Mode: ${buildMode} (raw: "${modeString}")
       |  Backend Protocol: $backendProtocol
       |  Backend Host: $backendHost
       |  Backend Port: $backendPort
       |  Backend Base Path: $backendBasePath
       |""".stripMargin
}
