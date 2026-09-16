/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import afpma.firecalc.domain.FireboxAvailability
import afpma.firecalc.engine.alg.en15544.FireboxEngineSource
import afpma.firecalc.engine.alg.en15544.FireboxEngineSourceConfig

/**
 * Retry/backoff tunables for the firebox-calc pre-fetch.
 * Mirrors the `InvoiceRetryConfig` shape (`maxRetries`, `baseDelayMs`, `maxDelayMs`).
 */
final case class PrefetchRetryConfig(
    maxRetries : Int  = 5,
    baseDelayMs: Long = 1000,
    maxDelayMs : Long = 30000
):
    require(maxRetries >= 0, "maxRetries must be non-negative"            )
    require(baseDelayMs > 0, "baseDelayMs must be positive"               )
    require(maxDelayMs >= baseDelayMs, "maxDelayMs must be >= baseDelayMs")

/**
 * Central configuration for the FireCalc UI application
 *
 * Provides type-safe access to backend API endpoints and other configuration values.
 * All configuration is loaded from Vite environment variables at build time.
 *
 * Usage:
 * ```scala
 * // Get the healthcheck endpoint URL
 * val url = UIConfig.Endpoints.healthcheck
 *
 * // Get the base backend URL
 * val baseUrl = UIConfig.backendBaseUrl
 * ```
 */
object UIConfig {

    /**
     * Parse a VITE env var as a boolean feature flag.
     *
     * Missing (`None`) and empty both mean "the flag is absent", not
     * "disabled": an omitted var must not hide a firebox kind, so they
     * resolve to available. Only the explicit value `"false"` disables;
     * any other value is treated as available with a console warning
     * naming the var.
     *
     * `s` is an `Option[String]` because the var may be absent from the
     * vite environment entirely (the JS wrapper exports `undefined`,
     * mapped to `None`); a missing var must not cause a cast failure at
     * the facade boundary.
     */
    def parseBool(key: String, s: Option[String]): Boolean =
        s match
            case Some("true")  => true
            case Some("false") => false
            // Absent (None) or empty: the flag is absent, not disabled —
            // silent by design (absence is a legitimate build-mode choice).
            case None          => true
            case Some("")      => true
            case Some(v)       =>
                scala.scalajs.js.Dynamic.global.console
                    .warn(s"VITE env var $key has unexpected value '$v', treating as true")
                true

    /**
     * Parse a VITE env var as an Int, falling back to `default` when the var
     * is missing, empty, or non-numeric. Mirrors `parseBool`'s defensive
     * handling of build-time-absent env vars (the UI cannot read JVM HOCON).
     *
     * `s` is an `Option[String]` because the var may be absent from the vite
     * environment entirely (the JS wrapper exports `undefined`, mapped to
     * `None`); a missing var must yield `default`, not a cast failure at the
     * facade boundary.
     */
    def parseInt(key: String, s: Option[String], default: Int): Int =
        s match
            case Some(str) if str.nonEmpty =>
                scala.util.Try(str.toInt) match
                    case scala.util.Success(v) => v
                    case scala.util.Failure(_) =>
                        scala.scalajs.js.Dynamic.global.console.warn(
                            s"VITE env var $key has unexpected value '$str', using default $default"
                        )
                        default
            case _                         => default

    /**
     * Resolve a VITE env var to a String, falling back to `default` when the
     * var is missing or empty. A missing/empty var warns (naming the var) so
     * the fallback is visible in the console instead of silent.
     *
     * `s` is an `Option[String]` because the var may be absent from the vite
     * environment entirely (the JS wrapper exports `undefined`, mapped to
     * `None`); a missing var must yield `default`, not a cast failure at the
     * facade boundary.
     */
    def parseString(key: String, s: Option[String], default: String): String =
        s match
            case Some(v) if v.nonEmpty => v
            case _                     =>
                scala.scalajs.js.Dynamic.global.console
                    .warn(s"VITE env var $key is missing or empty, using default '$default'")
                default

    lazy val uiAvailability: FireboxAvailability = FireboxAvailability(
        traditional    = parseBool("VITE_FIREBOX_AVAIL_TRADITIONAL", ViteEnv.fireboxAvailTraditional),
        ecolabeled     = parseBool("VITE_FIREBOX_AVAIL_ECOLABELED", ViteEnv.fireboxAvailEcolabeled),
        afpmaPrse      = parseBool("VITE_FIREBOX_AVAIL_AFPMA_PRSE", ViteEnv.fireboxAvailAfpmaPrse),
        singleTested   = parseBool("VITE_FIREBOX_AVAIL_SINGLE_TESTED", ViteEnv.fireboxAvailSingleTested),
        door15aCatalog = parseBool("VITE_FIREBOX_AVAIL_DOOR15A_CATALOG", ViteEnv.fireboxAvailDoor15aCatalog)
    )

    lazy val backendAvailability: FireboxAvailability = FireboxAvailability(
        traditional    = parseBool("VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND", ViteEnv.fireboxAvailTraditionalBackend),
        ecolabeled     = parseBool("VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND", ViteEnv.fireboxAvailEcolabeledBackend),
        afpmaPrse      = parseBool("VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND", ViteEnv.fireboxAvailAfpmaPrseBackend),
        singleTested   = parseBool("VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND", ViteEnv.fireboxAvailSingleTestedBackend),
        door15aCatalog =
            parseBool("VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND", ViteEnv.fireboxAvailDoor15aCatalogBackend)
    )

    /**
     * Resolve the per-kind engine source from the two remote-calc vars
     * (fix-plan Phase 1, §6), built via
     * [[FireboxEngineSourceConfig.fromRemoteKeys]] so unknown kind keys are
     * reported rather than silently dropped.
     *
     * A missing or empty kinds list or base URL resolves to `AllLocal`: a
     * deployment that omits the vars degrades to the historical all-local
     * behaviour instead of crashing startup.
     */
    def resolveEngineSource(
        rawKinds: Option[String],
        baseUrl : Option[String]
    ): FireboxEngineSourceConfig =
        (rawKinds, baseUrl) match
            case (Some(kinds), Some(base)) if kinds.nonEmpty && base.nonEmpty =>
                val (config, unknown) =
                    FireboxEngineSourceConfig.fromRemoteKeys(kinds.split(",").toSeq, base)
                if unknown.nonEmpty then
                    scala.scalajs.js.Dynamic.global.console.warn(
                        s"VITE_FIREBOX_REMOTE_KINDS: unknown kind key(s) ignored: ${unknown.mkString(", ")}"
                    )
                config
            case _ => FireboxEngineSourceConfig.AllLocal

    /**
     * Per-kind firebox engine source (fix-plan Phase 1, §6), built from
     * `VITE_FIREBOX_REMOTE_KINDS` (comma-separated kebab-case kind keys) and
     * `VITE_FIREBOX_CALC_BASE_URL` (shared remote base URL). Missing/empty
     * vars → `AllLocal` (see [[resolveEngineSource]]).
     */
    lazy val fireboxEngineSource: FireboxEngineSourceConfig =
        resolveEngineSource(ViteEnv.fireboxRemoteKinds, ViteEnv.fireboxCalcBaseUrl)

    /**
     * Retry/backoff tunables for the firebox-calc UI pre-fetch (fix-plan
     * Phase 5, deviation 8 / Q7.2-B). Mirrors the `InvoiceRetryConfig` shape.
     * `RemoteFireboxPrefetch` derives `FireboxCalcClientConfig` per-call from
     * these values and the `fireboxEngineSource`.
     */
    lazy val prefetchRetry: PrefetchRetryConfig =
        PrefetchRetryConfig (
            maxRetries  = parseInt("VITE_FIREBOX_PREFETCH_MAX_RETRIES", ViteEnv.fireboxPrefetchMaxRetries, 5),
            baseDelayMs =
                parseInt("VITE_FIREBOX_PREFETCH_BASE_DELAY_MS", ViteEnv.fireboxPrefetchBaseDelayMs, 1000).toLong,
            maxDelayMs  = parseInt("VITE_FIREBOX_PREFETCH_MAX_DELAY_MS", ViteEnv.fireboxPrefetchMaxDelayMs, 30000).toLong
        )

    private def logAvailability(label: String, fa: FireboxAvailability): Unit =
        scala.scalajs.js.Dynamic.global.console.log(
            s"$label — traditional=${fa.traditional} ecolabeled=${fa.ecolabeled} afpmaPrse=${fa.afpmaPrse} singleTested=${fa.singleTested} door15aCatalog=${fa.door15aCatalog}"
        )

    /**
     * Report the resolved engine source per firebox kind at boot (Q4).
     *
     * Mirrors [[logAvailability]]'s single-line `label — key=value ...` shape so
     * the two configuration lines read alike in the console. Kinds are listed in
     * `FireboxEngineSourceConfig.kindKey` order via its kebab-case keys, so the
     * console names match what an operator writes in
     * `VITE_FIREBOX_REMOTE_KINDS` / the HOCON `firebox-engine-source` block.
     *
     * A `Remote` kind prints its base URI, so a misconfigured host is visible
     * without opening the network tab.
     */
    private def logEngineSource(label: String, cfg: FireboxEngineSourceConfig): Unit =
        val rendered = FireboxEngineSourceConfig.kindKey.toList
            .sortBy(_._2)
            .map: (kind, key) =>
                val source = cfg.sourceFor(kind) match
                    case FireboxEngineSource.Local           => "local"
                    case FireboxEngineSource.Remote(baseUri) => s"remote($baseUri)"
                s"$key=$source"
            .mkString(" ")
        scala.scalajs.js.Dynamic.global.console.log(s"$label — $rendered")

    /**
     * Constructs the base backend URL from environment configuration
     *
     * Format: {protocol}://{host}:{port}{basePath}
     * Example (dev): http://localhost:8181/v1
     * Example (prod): https://api.firecalc.afpma.pro:443/v1
     *
     * Each missing or empty var falls back to a documented default
     * (http / localhost / 8181 / empty path — the same trio vite.config.js
     * uses for its dev server) with a console warning naming the var.
     */
    lazy val backendBaseUrl: String = {
        val protocol = parseString("VITE_BACKEND_PROTOCOL", ViteEnv.backendProtocol, "http")
        val host     = parseString("VITE_BACKEND_HOST", ViteEnv.backendHost, "localhost")
        val port     = parseString("VITE_BACKEND_PORT", ViteEnv.backendPort, "8181")
        val basePath = parseString("VITE_BACKEND_BASE_PATH", ViteEnv.backendBasePath, "")

        // Log resolved configuration on first access for debugging
        scala.scalajs.js.Dynamic.global.console.log("UIConfig initialized:"         )
        scala.scalajs.js.Dynamic.global.console.log(s"  Mode: ${ViteEnv.modeString}")
        scala.scalajs.js.Dynamic.global.console.log(s"  Protocol: $protocol"        )
        scala.scalajs.js.Dynamic.global.console.log(s"  Host: $host"                )
        scala.scalajs.js.Dynamic.global.console.log(s"  Port: $port"                )
        scala.scalajs.js.Dynamic.global.console.log(s"  Base Path: $basePath"       )

        logAvailability("UI availability", uiAvailability           )
        logAvailability("Backend availability", backendAvailability )
        logEngineSource("Firebox engine source", fireboxEngineSource)

        buildBackendUrl(protocol, host, port, basePath)
    }

    /**
     * Assemble the backend base URL from its resolved parts.
     *
     * The port is omitted when it is the protocol's default (http/80, https/443).
     */
    def buildBackendUrl(
        protocol: String,
        host    : String,
        port    : String,
        basePath: String
    ): String =
        val portSuffix = (protocol, port) match
            case ("http", "80"  ) => ""
            case ("https", "443") => ""
            case _ => s":$port"
        s"$protocol://$host$portSuffix$basePath"

    /**
     * Backend API endpoints
     *
     * All endpoints are fully qualified URLs ready to use with fetch/XHR
     */
    object Endpoints {

        /** Health check endpoint for connectivity testing */
        lazy val healthcheck: String = s"$backendBaseUrl/healthcheck"

        /** Create purchase intent endpoint */
        lazy val createPurchaseIntent: String = s"$backendBaseUrl/purchase/create-intent"

        /** Verify and process purchase endpoint */
        lazy val verifyAndProcess: String = s"$backendBaseUrl/purchase/verify-and-process"
    }

    /**
     * URL to Conditions Générales de Vente (CGUV / Terms and Conditions of Sale).
     *
     * A missing or empty var falls back to an empty URL (the link target
     * degrades to the current page) with a console warning naming the var.
     */
    lazy val cguvUrl: String = parseString("VITE_CGUV_URL", ViteEnv.cguvUrl, "")

    /**
     * Returns debug information about the current configuration
     * Useful for troubleshooting connectivity issues
     */
    def debugInfo(): String =
        s"""UIConfig:
       |  Environment: ${ViteEnv.modeString}
       |  Base URL: $backendBaseUrl
       |  Terms and Conditions URL: $cguvUrl
       |
       |Endpoints:
       |  Healthcheck: ${Endpoints.healthcheck}
       |  Create Purchase Intent: ${Endpoints.createPurchaseIntent}
       |  Verify and Process: ${Endpoints.verifyAndProcess}
       |
       |${ViteEnv.debugInfo()}
       |""".stripMargin
}
