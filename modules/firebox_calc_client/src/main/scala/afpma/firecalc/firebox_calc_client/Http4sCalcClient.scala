/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_client

import cats.effect.Resource

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString

/**
 * http4s-based JVM adapter for the shared calculation client.
 *
 * Implements [[FireboxCalcClient]] using an http4s `Client[IO]`.
 * Calls only `GET /v1/kinds` and `POST /v1/calculate` as defined by the
 * OpenAPI contract.
 *
 * The adapter correctly encodes requests and decodes discovery, calculation,
 * documented error envelopes, and complete `AsResult` continuation state.
 *
 * Application-key propagation, connection refusal, timeout, retry exhaustion,
 * cancellation, and resource release are handled by the underlying http4s
 * client and middleware.
 *
 * This class and its dependency closure contain no Austrian model, constraint,
 * formula, transformer, registration, or engine implementation.
 */
class Http4sCalcClient private (
    client: Client[cats.effect.IO],
    config: FireboxCalcClientConfig
) extends FireboxCalcClient[cats.effect.IO]:

    private val baseUri: Uri =
        Uri.unsafeFromString(config.baseUri)

    private val appKeyHeader: Option[Header.Raw] =
        config.appKey.map(key => Header.Raw(CIString("X-Firecalc-App-Key"), key))

    /** Discover served firebox kinds (`GET /v1/kinds`). */
    def getKinds: cats.effect.IO[Either[CalculationFailure, DiscoveryResponse]] =
        val request = buildRequest(
            Method.GET,
            baseUri / "kinds",
            body = None
        )
        executeAndDecode[DiscoveryResponse](request)

    /**
     * Calculate firebox contributions, constraints, and report data
     * (`POST /v1/calculate`).
     */
    def calculate(request: FireboxCalcRequest): cats.effect.IO[Either[CalculationFailure, FireboxCalcResponse]] =
        val body = request.asJson.noSpaces
        val req  = buildRequest(
            Method.POST,
            baseUri / "calculate",
            body = Some(body)
        )
        executeAndDecode[FireboxCalcResponse](req)

    /** Release resources held by this client. */
    def close: cats.effect.IO[Unit] =
        cats.effect.IO.unit

    // -- private helpers --

    private def buildRequest(
        method: Method,
        uri   : Uri,
        body  : Option[String]
    ): Request[cats.effect.IO] =
        val base = Request[cats.effect.IO](method = method, uri = uri)
            .withHeaders     (
                `Content-Type`(MediaType.application.json)
            )
        val withAppKey = appKeyHeader.fold(base)(h => base.putHeaders(h))
        body.fold(withAppKey)(b => withAppKey.withEntity(b))

    private def executeAndDecode[A: Decoder](
        request: Request[cats.effect.IO]
    ): cats.effect.IO[Either[CalculationFailure, A]] =
        client
            .run(request)
            .use { response =>
                response.status.responseClass match {
                    case Status.Successful =>
                        response.as[String].flatMap { raw =>
                            cats.effect.IO.pure {
                                decode[A](raw) match
                                    case Right(value) => Right(value)
                                    case Left(err)    => Left(CalculationFailure.DecodeError(err, raw.take(2048)))
                            }
                        }

                    case Status.ClientError | Status.ServerError =>
                        response.as[String].flatMap { raw =>
                            cats.effect.IO.pure {
                                decode[FireboxCalcError](raw) match
                                    case Right(err) =>
                                        Left(
                                            CalculationFailure.ProtocolError (
                                                status  = response.status.code,
                                                error   = err.error,
                                                message = err.message,
                                                details = err.details
                                            )
                                        )
                                    case Left(_)    =>
                                        // Fall back to simple envelope
                                        decode[ErrorResponseEnvelope](raw) match
                                            case Right(env) =>
                                                Left(
                                                    CalculationFailure.ProtocolError (
                                                        status  = response.status.code,
                                                        error   = env.error,
                                                        message = env.message
                                                    )
                                                )
                                            case Left(_)    =>
                                                // Undecodable error body — still a protocol error
                                                Left(
                                                    CalculationFailure.ProtocolError (
                                                        status  = response.status.code,
                                                        error   = "unknown",
                                                        message = s"Undecodable error response: ${raw.take(512)}"
                                                    )
                                                )
                            }
                        }

                    case Status.Informational | Status.Redirection =>
                        // 1xx/3xx are unexpected from this API — treat as transport-level failure
                        cats.effect.IO.pure {
                            Left(
                                CalculationFailure.TransportError(
                                    new RuntimeException(
                                        s"Unexpected response class: ${response.status.responseClass} (${response.status.code})"
                                    )
                                )
                            )
                        }
                }
            }
            .handleError { t =>
                Left(CalculationFailure.TransportError(t))
            }

end Http4sCalcClient

/**
 * Builder for [[Http4sCalcClient]] with resource management.
 *
 * Creates an http4s Ember client with the configured timeout and retry policy,
 * wrapped in a [[cats.effect.Resource]] for safe lifecycle management.
 */
object Http4sCalcClient:

    /**
     * Create an [[Http4sCalcClient]] managed by a [[Resource]].
     *
     * The resource ensures the underlying http4s client is closed when the
     * scope ends. Cancellation interrupts outstanding work.
     *
     * @param config Client configuration (base URI, app key, timeout, retry policy)
     * @return Resource wrapping the client
     */
    def resource(
        config: FireboxCalcClientConfig
    ): Resource[cats.effect.IO, Http4sCalcClient] =
        val clientResource = buildEmberClient(config)
        clientResource.map { client =>
            new Http4sCalcClient(client, config)
        }

    /**
     * Create an [[Http4sCalcClient]] directly (caller responsible for lifecycle).
     *
     * @param config Client configuration
     * @param client Pre-built http4s client
     * @return The client instance
     */
    def apply(
        config: FireboxCalcClientConfig,
        client: Client[cats.effect.IO]
    ): Http4sCalcClient =
        new Http4sCalcClient(client, config)

    private def buildEmberClient(
        config: FireboxCalcClientConfig
    ): Resource[cats.effect.IO, Client[cats.effect.IO]] =
        import org.http4s.ember.client.EmberClientBuilder

        val baseClient: Resource[cats.effect.IO, Client[cats.effect.IO]] =
            EmberClientBuilder
                .default[cats.effect.IO]
                .withTimeout(config.timeout)
                .build

        // Apply retry middleware if policy is enabled
        if config.retryPolicy.enabled then
            val http4sRetryPolicy: RetryPolicy[cats.effect.IO] =
                (request: Request[cats.effect.IO], result: Either[Throwable, Response[cats.effect.IO]], attempt: Int) =>
                    result match
                        case Left(e)  =>
                            // Only retry on transport errors (not protocol errors)
                            if isRetryableTransportError(e) then
                                val delay = config.retryPolicy.delayForAttempt(attempt)
                                if attempt < config.retryPolicy.maxAttempts then Some(delay)
                                else None
                            else None
                        case Right(_) =>
                            // Protocol errors (4xx/5xx) never retried
                            None

            baseClient.map { client =>
                Retry.apply(http4sRetryPolicy)(client)
            }
        else baseClient

    private def isRetryableTransportError(t: Throwable): Boolean =
        t match
            // Retry on common transport errors
            case _: java.net.ConnectException       => true
            case _: java.net.SocketTimeoutException => true
            case _: java.net.UnknownHostException   => true
            case _: java.io.IOException             => true
            // Don't retry on cancellation or other fatal errors
            case _: InterruptedException            => false
            case _: RuntimeException                => false
            case _ => true // retry unknown I/O errors

    end isRetryableTransportError

end Http4sCalcClient
