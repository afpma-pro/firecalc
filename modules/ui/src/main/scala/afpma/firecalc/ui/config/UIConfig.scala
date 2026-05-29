/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.config

import afpma.firecalc.domain.FireboxAvailability

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

    def parseBool(key: String, s: String): Boolean =
        if s == null || s.isEmpty then true
        else if s == "true" then true
        else if s == "false" then false
        else
            scala.scalajs.js.Dynamic.global.console
                .warn(s"VITE env var $key has unexpected value '$s', treating as true")
            true

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

    private def logAvailability(label: String, fa: FireboxAvailability): Unit =
        scala.scalajs.js.Dynamic.global.console.log(
            s"$label — traditional=${fa.traditional} ecolabeled=${fa.ecolabeled} afpmaPrse=${fa.afpmaPrse} singleTested=${fa.singleTested} door15aCatalog=${fa.door15aCatalog}"
        )

    /**
     * Constructs the base backend URL from environment configuration
     *
     * Format: {protocol}://{host}:{port}{basePath}
     * Example (dev): http://localhost:8181/v1
     * Example (prod): https://api.firecalc.afpma.pro:443/v1
     */
    lazy val backendBaseUrl: String = {
        // Log configuration on first access for debugging
        scala.scalajs.js.Dynamic.global.console.log("UIConfig initialized:"                   )
        scala.scalajs.js.Dynamic.global.console.log(s"  Mode: ${ViteEnv.modeString}"          )
        scala.scalajs.js.Dynamic.global.console.log(s"  Protocol: ${ViteEnv.backendProtocol}" )
        scala.scalajs.js.Dynamic.global.console.log(s"  Host: ${ViteEnv.backendHost}"         )
        scala.scalajs.js.Dynamic.global.console.log(s"  Port: ${ViteEnv.backendPort}"         )
        scala.scalajs.js.Dynamic.global.console.log(s"  Base Path: ${ViteEnv.backendBasePath}")

        logAvailability("UI availability", uiAvailability          )
        logAvailability("Backend availability", backendAvailability)

        val protocol = ViteEnv.backendProtocol
        val host     = ViteEnv.backendHost
        val port     = ViteEnv.backendPort
        val basePath = ViteEnv.backendBasePath

        // Only include port if it's not the default for the protocol
        val portSuffix = (protocol, port) match {
            case ("http", "80"  ) => ""
            case ("https", "443") => ""
            case _ => s":$port"
        }

        s"$protocol://$host$portSuffix$basePath"
    }

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

    /** URL to Conditions Générales de Vente (CGUV / Terms and Conditions of Sale) */
    lazy val cguvUrl: String = ViteEnv.cguvUrl

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
