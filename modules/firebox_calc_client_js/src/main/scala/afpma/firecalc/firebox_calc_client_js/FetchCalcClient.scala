/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_client_js

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.timers.SetTimeoutHandle

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import org.scalajs.dom
import org.scalajs.dom.fetch

/**
 * Browser fetch adapter for the shared calculation client.
 *
 * Implements [[FireboxCalcClient]] using the browser `fetch` API.
 * Calls only `GET /v1/kinds` and `POST /v1/calculate` as defined by the
 * OpenAPI contract.
 *
 * The adapter correctly encodes requests and decodes discovery, calculation,
 * documented error envelopes, and complete `AsResult` continuation state.
 *
 * Browser cancellation via [[AbortController]] aborts in-flight transport
 * and releases resources without turning cancellation into a successful
 * or stale response.
 *
 * This class and its dependency closure contain no Austrian model, constraint,
 * formula, transformer, registration, or engine implementation.
 */
class FetchCalcClient private (
    config : FireboxCalcClientConfig,
    fetchFn: (String, dom.RequestInit) => js.Promise[dom.Response] = fetch(_, _)
)                             (using ExecutionContext)
    extends FireboxCalcClient[Future]:

    private val baseUri     : String         = config.baseUri
    private val appKeyHeader: Option[String] = config.appKey

    // Track the current abort controller for cancellation
    private var currentController: dom.AbortController | Null = null

    /** Discover served firebox kinds (`GET /v1/kinds`). */
    def getKinds: Future[Either[CalculationFailure, DiscoveryResponse]] =
        executeAndDecode[DiscoveryResponse](
            httpMethod = "GET",
            path       = "/kinds",
            body       = None
        )

    /**
     * Calculate firebox contributions, constraints, and report data
     * (`POST /v1/calculate`).
     */
    def calculate(request: FireboxCalcRequest): Future[Either[CalculationFailure, FireboxCalcResponse]] =
        import io.circe.syntax.*
        val json = request.asJson.noSpaces
        executeAndDecode[FireboxCalcResponse](
            httpMethod = "POST",
            path       = "/calculate",
            body       = Some(json)
        )

    /** Release resources held by this client. No-op for the fetch adapter. */
    def close: Future[Unit] = Future.successful(())

    /**
     * Cancel the in-flight request (if any).
     *
     * Aborts the current transport operation and returns the failure
     * as a [[CalculationFailure.TransportError]] with an AbortError cause.
     */
    def cancel: Unit =
        val ctrl = currentController
        if ctrl != null then ctrl.abort()

    // -- private helpers --

    /**
     * Retry wrapper: retries transient transport errors up to the configured
     * maximum with exponential back-off. Protocol errors are never retried.
     */
    private def withRetry[A](attempt: => Future[A]): Future[A] =
        def loop(remaining: Int, delayMs: Long): Future[A] =
            attempt.flatMap { result =>
                result match
                    case Left(CalculationFailure.TransportError(_)) if remaining > 0 =>
                        sleep(delayMs).flatMap(_ =>
                            loop(
                                remaining - 1,
                                (delayMs * config.retryPolicy.backOffFactor).toLong
                                    .min(config.retryPolicy.maxDelay.toMillis)
                            )
                        )
                    case _                                                           =>
                        Future.successful(result)
            }

        if config.retryPolicy.enabled then
            loop(config.retryPolicy.maxAttempts - 1, config.retryPolicy.initialDelay.toMillis)
        else attempt

    private def sleep(ms: Long): Future[Unit] =
        val p = Promise[Unit]()
        js.timers.setTimeout(ms.toDouble) {
            p.success(())
        }
        p.future

    private def decodeResponse[A: io.circe.Decoder](
        response    : dom.Response,
        clearTimeout: () => Unit
    ): Future[Either[CalculationFailure, A]] =
        clearTimeout()
        response.status match
            case 200 | 201 | 202 | 204 | 205 | 206                               =>
                response.text().toFuture.flatMap { raw =>
                    Future.fromTry {
                        scala.util.Try {
                            io.circe.parser.decode[A](raw) match
                                case scala.util.Right(value) => Right(value)
                                case scala.util.Left(err)    =>
                                    Left(
                                        CalculationFailure.DecodeError(
                                            err,
                                            raw.take(2048)
                                        )
                                    )
                        }
                    }
                }
            case 400 | 401 | 403 | 404 | 413 | 422 | 429 | 500 | 502 | 503 | 504 =>
                response.text().toFuture.flatMap { raw =>
                    Future.successful {
                        io.circe.parser.decode[FireboxCalcError](raw) match
                            case scala.util.Right(err) =>
                                Left(
                                    CalculationFailure.ProtocolError (
                                        status  = response.status,
                                        error   = err.error,
                                        message = err.message,
                                        details = err.details
                                    )
                                )
                            case scala.util.Left(_)    =>
                                io.circe.parser.decode[ErrorResponseEnvelope](raw) match
                                    case scala.util.Right(env) =>
                                        Left(
                                            CalculationFailure.ProtocolError (
                                                status  = response.status,
                                                error   = env.error,
                                                message = env.message
                                            )
                                        )
                                    case scala.util.Left(_)    =>
                                        Left(
                                            CalculationFailure.ProtocolError (
                                                status  = response.status,
                                                error   = "unknown",
                                                message = s"Undecodable error response: ${raw.take(512)}"
                                            )
                                        )
                    }
                }
            case _                                                               =>
                Future.successful {
                    Left(
                        CalculationFailure.TransportError(
                            new RuntimeException(
                                s"Unexpected response status: ${response.status}"
                            )
                        )
                    )
                }

    private def transportError[A](t: Throwable, clearTimeout: () => Unit): Future[Either[CalculationFailure, A]] =
        clearTimeout()
        Future.successful {
            Left(CalculationFailure.TransportError(t))
        }

    private def executeAndDecode[A: io.circe.Decoder](
        httpMethod: String,
        path      : String,
        body      : Option[String]
    ): Future[Either[CalculationFailure, A]] =
        withRetry {
            val controller = new dom.AbortController()
            currentController = controller
            val abortSignal = controller.signal

            val timeoutHandle: Option[SetTimeoutHandle] =
                if config.timeout.length > 0 then
                    Some(js.timers.setTimeout(config.timeout.toMillis.toDouble) {
                        controller.abort()
                    })
                else None

            val clearTimeout: () => Unit = () => timeoutHandle.foreach(js.timers.clearTimeout)

            val requestHeaders = {
                val h = new dom.Headers()
                h.append            ("Content-Type", "application/json"        )
                appKeyHeader.foreach(key => h.append("X-Firecalc-App-Key", key))
                h
            }

            val init = new dom.RequestInit {
                signal       = abortSignal
                method       = httpMethod match
                    case "GET"    => dom.HttpMethod.GET
                    case "POST"   => dom.HttpMethod.POST
                    case "PUT"    => dom.HttpMethod.PUT
                    case "DELETE" => dom.HttpMethod.DELETE
                    case "PATCH"  => dom.HttpMethod.PATCH
                    case _        => dom.HttpMethod.GET
                this.headers = requestHeaders
            }
            body.foreach(b => init.body = b)

            val url = s"$baseUri$path"

            fetchFn(url, init).toFuture
                .flatMap(response => decodeResponse[A](response, clearTimeout))
                .recoverWith { case t =>
                    transportError[A](t, clearTimeout)
                }
        }

end FetchCalcClient

/** Factory for [[FetchCalcClient]]. */
object FetchCalcClient:

    /**
     * Create a new fetch-based calculation client.
     *
     * @param config Client configuration (base URI, app key, timeout, retry policy)
     * @param ec ExecutionContext for async operations
     * @return The client instance
     */
    def apply(
        config: FireboxCalcClientConfig
    )(using ec: ExecutionContext): FetchCalcClient =
        new FetchCalcClient(config)

    /** Create a client with a custom fetch function (for testing). */
    def apply(
        config : FireboxCalcClientConfig,
        fetchFn: (String, dom.RequestInit) => js.Promise[dom.Response]
    )(using ec: ExecutionContext): FetchCalcClient =
        new FetchCalcClient(config, fetchFn)

end FetchCalcClient
