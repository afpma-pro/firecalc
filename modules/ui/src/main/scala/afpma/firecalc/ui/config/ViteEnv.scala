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
    // All optional VITE_ exports are typed `js.UndefOr[String]` (not `String`):
    // a var that is legitimately absent in a build mode is exported as
    // `undefined` by the JS wrapper in both runtimes (the browser wrapper
    // reads Vite's `import.meta.env`; the Node test runtime uses the
    // wrapper's documented defaults), and a `String`-typed field would throw
    // `ClassCastException` on first access. Accessors map absence to `None`;
    // consumers fall back to a documented default (see `UIConfig.parseBool`,
    // `UIConfig.parseString` and `UIConfig.parseInt`).
    val VITE_BACKEND_PROTOCOL                     : js.UndefOr[String] = js.native
    val VITE_BACKEND_HOST                         : js.UndefOr[String] = js.native
    val VITE_BACKEND_PORT                         : js.UndefOr[String] = js.native
    val VITE_BACKEND_BASE_PATH                    : js.UndefOr[String] = js.native
    val VITE_CGUV_URL                             : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_TRADITIONAL            : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_ECOLABELED             : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_AFPMA_PRSE             : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_SINGLE_TESTED          : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_DOOR15A_CATALOG        : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND    : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND     : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND     : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND  : js.UndefOr[String] = js.native
    val VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND: js.UndefOr[String] = js.native
    val VITE_FIREBOX_REMOTE_KINDS                 : js.UndefOr[String] = js.native
    val VITE_FIREBOX_CALC_BASE_URL                : js.UndefOr[String] = js.native
    val VITE_FIREBOX_PREFETCH_MAX_RETRIES         : js.UndefOr[String] = js.native
    val VITE_FIREBOX_PREFETCH_BASE_DELAY_MS       : js.UndefOr[String] = js.native
    val VITE_FIREBOX_PREFETCH_MAX_DELAY_MS        : js.UndefOr[String] = js.native
    // `MODE` is always defined by Vite (never omitted in a build), so it
    // stays a plain `String`.
    val MODE                                      : String             = js.native
}

/**
 * Type-safe accessor for Vite environment variables
 *
 * Usage:
 * ```scala
 * val protocol = ViteEnv.backendProtocol  // Some("http") | Some("https") | None
 * val host = ViteEnv.backendHost         // Some("localhost") | ... | None
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

    /** Backend protocol: "http" or "https". `None` if the var is unset. */
    def backendProtocol: Option[String] = envToOption(ImportMetaEnv.VITE_BACKEND_PROTOCOL)

    /** Backend host: e.g., "localhost" or "api.firecalc.afpma.pro". `None` if the var is unset. */
    def backendHost: Option[String] = envToOption(ImportMetaEnv.VITE_BACKEND_HOST)

    /** Backend port: e.g., "8181" or "443". `None` if the var is unset. */
    def backendPort: Option[String] = envToOption(ImportMetaEnv.VITE_BACKEND_PORT)

    /** Backend base path: e.g., "/v1". `None` if the var is unset. */
    def backendBasePath: Option[String] = envToOption(ImportMetaEnv.VITE_BACKEND_BASE_PATH)

    /** URL to Conditions Générales de Vente (CGUV / Terms and Conditions of Sale). `None` if the var is unset. */
    def cguvUrl: Option[String] = envToOption(ImportMetaEnv.VITE_CGUV_URL)

    /** `VITE_FIREBOX_AVAIL_*` switches (UI + backend mirror). `None` if a var is unset. */
    def fireboxAvailTraditional          : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_TRADITIONAL)
    def fireboxAvailEcolabeled           : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_ECOLABELED)
    def fireboxAvailAfpmaPrse            : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_AFPMA_PRSE)
    def fireboxAvailSingleTested         : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_SINGLE_TESTED)
    def fireboxAvailDoor15aCatalog       : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG)
    def fireboxAvailTraditionalBackend   : Option[String] = envToOption(
        ImportMetaEnv.VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND
    )
    def fireboxAvailEcolabeledBackend    : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND)
    def fireboxAvailAfpmaPrseBackend     : Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND)
    def fireboxAvailSingleTestedBackend  : Option[String] = envToOption(
        ImportMetaEnv.VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND
    )
    def fireboxAvailDoor15aCatalogBackend: Option[String] = envToOption(
        ImportMetaEnv.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND
    )

    /** Comma-separated kebab-case kind keys to compute remotely. `None` if the var is unset. */
    def fireboxRemoteKinds: Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_REMOTE_KINDS)

    /** Shared base URL for the remote Firebox Calculation API. `None` if the var is unset. */
    def fireboxCalcBaseUrl: Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_CALC_BASE_URL)

    /**
     * Map a raw VITE env export to `Option[String]`.
     *
     * Absence is `undefined` in both runtimes: the browser wrapper exports
     * `undefined` for vars not set in the vite environment, and the Node
     * test runtime uses the same wrapper, whose fallback exports
     * `undefined` for vars without a documented Node-test default. So
     * `js.UndefOr[String].toOption` does the whole job.
     */
    private def envToOption(v: js.UndefOr[String]): Option[String] = v.toOption

    /** Max retry attempts for the firebox-calc UI pre-fetch. `None` if the var is unset. */
    def fireboxPrefetchMaxRetries: Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_PREFETCH_MAX_RETRIES)

    /** Base backoff delay (ms) for the firebox-calc UI pre-fetch. `None` if the var is unset. */
    def fireboxPrefetchBaseDelayMs: Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_PREFETCH_BASE_DELAY_MS)

    /** Cap (ms) for the firebox-calc UI pre-fetch backoff. `None` if the var is unset. */
    def fireboxPrefetchMaxDelayMs: Option[String] = envToOption(ImportMetaEnv.VITE_FIREBOX_PREFETCH_MAX_DELAY_MS)

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

    /** Returns debug information about the current environment configuration */
    def debugInfo(): String =
        s"""ViteEnv Configuration:
       |  Build Mode: ${buildMode} (raw: "${modeString}")
       |  Backend Protocol: ${backendProtocol.getOrElse("<unset>")}
       |  Backend Host: ${backendHost.getOrElse("<unset>")}
       |  Backend Port: ${backendPort.getOrElse("<unset>")}
       |  Backend Base Path: ${backendBasePath.getOrElse("<unset>")}
       |  Terms and Conditions URL: ${cguvUrl.getOrElse("<unset>")}
       |""".stripMargin
}
