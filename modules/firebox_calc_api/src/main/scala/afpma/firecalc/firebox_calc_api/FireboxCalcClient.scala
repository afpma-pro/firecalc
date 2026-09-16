/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

import scala.concurrent.duration.FiniteDuration

/**
 * Configuration for the shared calculation client.
 *
 * Configures the base endpoint, optional application key, bounded timeout,
 * and bounded retry policy. Platform HTTP details (connection pool, TLS)
 * are adapter-specific and not exposed here.
 *
 * @param baseUri Base URI of the calculation API (e.g. `http://localhost:8080/v1`)
 * @param appKey Optional application key for rate-limit key composition (§8.2).
 *               Propagated as `X-Firecalc-App-Key` header.
 * @param timeout Bounded request timeout. The adapter MUST abort outstanding
 *                work when the timeout elapses.
 * @param retryPolicy Bounded retry policy for transient transport errors.
 *                    Protocol errors (4xx/5xx) are never retried.
 */
final case class FireboxCalcClientConfig(
    baseUri    : String,
    appKey     : Option[String] = None,
    timeout    : FiniteDuration = scala.concurrent.duration.FiniteDuration(30, scala.concurrent.duration.SECONDS),
    retryPolicy: RetryPolicy    = RetryPolicy.default
):
    require(baseUri.nonEmpty, "baseUri must not be empty" )
    require(timeout.length > 0, "timeout must be positive")

end FireboxCalcClientConfig

object FireboxCalcClientConfig:
    /** Default configuration pointing to localhost. */
    def localhost(port: Int = 8080): FireboxCalcClientConfig =
        FireboxCalcClientConfig(baseUri = s"http://localhost:$port/v1")

end FireboxCalcClientConfig

/**
 * Abstract calculation client for discovery and calculation through the
 * normative HTTP seam.
 *
 * Defines the conceptual interface any adapter (JVM/http4s, JS/fetch) must
 * implement. The client calls only `GET /v1/kinds` and `POST /v1/calculate`
 * as defined by the OpenAPI contract.
 *
 * The client's dependency closure contains no Austrian model, constraint,
 * formula, transformer, registration, or engine implementation and introduces
 * no MCE dependency to public callers.
 *
 * Adapters provide the effect type `F[_]` (e.g. `IO`, `Future`, `Promise`).
 *
 * @tparam F Effect type for asynchronous operations.
 */
trait FireboxCalcClient[F[_]]:

    /**
     * Discover served firebox kinds (`GET /v1/kinds`).
     *
     * Returns the list of kinds this deployment can calculate, derived from
     * the active served registry.
     */
    def getKinds: F[Either[CalculationFailure, DiscoveryResponse]]

    /**
     * Calculate firebox contributions, constraints, and report data
     * (`POST /v1/calculate`).
     *
     * @param request The calculation request.
     * @return Either a typed failure or the calculation response.
     */
    def calculate(request: FireboxCalcRequest): F[Either[CalculationFailure, FireboxCalcResponse]]

    /**
     * Release resources held by this client (connection pool, etc.).
     * After calling `close`, further operations may fail with [[CalculationFailure.TransportError]].
     */
    def close: F[Unit]

end FireboxCalcClient
