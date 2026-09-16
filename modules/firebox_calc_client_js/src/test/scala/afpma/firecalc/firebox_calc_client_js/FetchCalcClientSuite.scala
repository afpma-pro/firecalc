/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_client_js

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given

import org.scalajs.dom
import org.scalajs.dom.{Response, ResponseInit, RequestInit, Headers}

import io.circe.syntax.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Contract tests for [[FetchCalcClient]] browser fetch adapter.
 *
 * Uses a mockable fetch function injected via the test-friendly factory.
 * All tests return Future[Assertion] for Scala.js async compatibility.
 */
class FetchCalcClientSuite extends AnyFlatSpec with Matchers {

    given ec: ExecutionContext = ExecutionContext.global

    // =====================================================================
    // Helpers
    // =====================================================================

    private def resolveResponse(
        responseStatus: Int,
        body          : String,
        contentType   : String = "application/json"
    ): js.Promise[Response] =
        val responseHeaders = new Headers()
        responseHeaders.append("Content-Type", contentType)
        val init = new ResponseInit:
            status  = responseStatus
            headers = responseHeaders
        js.Promise.resolve(new Response(body, init))

    private def rejectResponse(message: String): js.Promise[Response] =
        js.Promise.reject(new RuntimeException(message))

    private def neverResolve: js.Promise[Response] =
        new js.Promise[Response](
            (resolve: js.Function1[Response | js.Thenable[Response], ?], reject: js.Function1[Any, ?]) => {
                // Do nothing - never resolve or reject
            }
        )

    private def config(
        baseUri        : String                                   = "http://localhost:8080/v1",
        appKey         : Option[String]                           = None,
        timeout        : scala.concurrent.duration.FiniteDuration =
            scala.concurrent.duration.FiniteDuration(30, scala.concurrent.duration.SECONDS),
        retryPolicy    : RetryPolicy                              = RetryPolicy.none
    ): FireboxCalcClientConfig =
        FireboxCalcClientConfig(
            baseUri     = baseUri,
            appKey      = appKey,
            timeout     = timeout,
            retryPolicy = retryPolicy
        )

    // Pre-serialized JSON for responses
    private val discoveryJson: String =
        DiscoveryResponse(
            apiVersion = ApiVersion.v1,
            kinds      = List(WireFireboxKind.Traditional, WireFireboxKind.Ecolabeled)
        ).asJson.noSpaces

    // =====================================================================
    // Discovery
    // =====================================================================

    "getKinds" should "return discovery response on 200 OK" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (url, init) =>
            Future {
                url shouldBe "http://localhost:8080/v1/kinds"
                init.method shouldBe dom.HttpMethod.GET
            }
            resolveResponse(200, discoveryJson)
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map { result =>
            result.isRight shouldBe true
            val kinds = result.toOption.get.kinds
            kinds should contain theSameElementsAs List(WireFireboxKind.Traditional, WireFireboxKind.Ecolabeled)
        }
    }

    it should "return ProtocolError on 404 with ErrorResponseEnvelope" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            val json = ErrorResponseEnvelope("unknown_kind", "Kind not found").asJson.noSpaces
            resolveResponse(404, json)
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map {
            case Left(CalculationFailure.ProtocolError(status, error, _, _)) =>
                status shouldBe 404
                error shouldBe "unknown_kind"
            case result                                                      =>
                fail(s"Expected ProtocolError, got $result")
        }
    }

    it should "return ProtocolError on 500 with FireboxCalcError" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            val json = FireboxCalcError("internal_error", "Server error", None).asJson.noSpaces
            resolveResponse(500, json)
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map {
            case Left(CalculationFailure.ProtocolError(status, error, _, _)) =>
                status shouldBe 500
                error shouldBe "internal_error"
            case result                                                      =>
                fail(s"Expected ProtocolError, got $result")
        }
    }

    it should "return DecodeError on malformed JSON response" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            resolveResponse(200, "not json at all")
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.DecodeError]
        }
    }

    it should "return TransportError on fetch exception" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            rejectResponse("Network Error")
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.TransportError]
        }
    }

    // =====================================================================
    // App Key Propagation
    // =====================================================================

    "app key" should "be sent as X-Firecalc-App-Key header when configured" in {
        val capturedHeader = Promise[Option[String]]()
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (url, init) =>
            capturedHeader.completeWith(Future {
                init.headers match {
                    case h: Headers =>
                        try Some(h.get("X-Firecalc-App-Key"))
                        catch { case _: Throwable => None }
                    case _ => None
                }
            })
            resolveResponse            (200, discoveryJson)
        }

        val client = FetchCalcClient(config(appKey = Some("my-app-key")), mockFetch)(using ec)
        client.getKinds.flatMap { _ =>
            capturedHeader.future.map { header =>
                header shouldBe Some("my-app-key")
            }
        }
    }

    it should "not send X-Firecalc-App-Key header when not configured" in {
        val capturedHeader = Promise[Option[String]]()
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (url, init) =>
            capturedHeader.completeWith(Future {
                init.headers match {
                    case h: Headers =>
                        try Some(h.get("X-Firecalc-App-Key"))
                        catch { case _: Throwable => None }
                    case _ => None
                }
            })
            resolveResponse            (200, discoveryJson)
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.flatMap { _ =>
            capturedHeader.future.map { header =>
                header shouldBe None
            }
        }
    }

    // =====================================================================
    // Resource Cleanup
    // =====================================================================

    "close" should "complete successfully" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            resolveResponse(200, "{}")
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.close.map { _ shouldBe (()) }
    }

    // =====================================================================
    // Timeout
    // =====================================================================

    "timeout" should "return TransportError when request exceeds timeout" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            neverResolve
        }

        val client = FetchCalcClient(
            config(timeout = scala.concurrent.duration.FiniteDuration(100, scala.concurrent.duration.MILLISECONDS)),
            mockFetch
        )(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.TransportError]
        }
    }

    // =====================================================================
    // Retry Behavior
    // =====================================================================

    "retry" should "succeed after transient transport errors" in {
        val attemptCounter = Promise[Int]()
        var attemptCount   = 0
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            attemptCount += 1
            if attemptCount < 3 then rejectResponse("Temporary network error")
            else
                attemptCounter.success(attemptCount      )
                resolveResponse       (200, discoveryJson)
        }

        val retryConfig = RetryPolicy(
            maxAttempts   = 3,
            initialDelay  = scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.MILLISECONDS),
            maxDelay      = scala.concurrent.duration.FiniteDuration(100, scala.concurrent.duration.MILLISECONDS),
            backOffFactor = 2.0
        )
        val client      = FetchCalcClient(config(retryPolicy = retryConfig), mockFetch)(using ec)
        client.getKinds.flatMap { result =>
            attemptCounter.future.map { count =>
                result.isRight shouldBe true
                count shouldBe 3
            }
        }
    }

    it should "not retry on protocol errors" in {
        val attemptCounter = Promise[Int]()
        var attemptCount   = 0
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            attemptCount += 1
            attemptCounter.success(attemptCount)
            val json = FireboxCalcError("not_found", "Resource not found", None).asJson.noSpaces
            resolveResponse(404, json)
        }

        val retryConfig = RetryPolicy(
            maxAttempts   = 3,
            initialDelay  = scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.MILLISECONDS),
            maxDelay      = scala.concurrent.duration.FiniteDuration(100, scala.concurrent.duration.MILLISECONDS),
            backOffFactor = 2.0
        )
        val client      = FetchCalcClient(config(retryPolicy = retryConfig), mockFetch)(using ec)
        client.getKinds.flatMap { result =>
            attemptCounter.future.map { count =>
                val failure = result.left.toOption.get
                failure shouldBe a[CalculationFailure.ProtocolError]
                count shouldBe 1
            }
        }
    }
    it should "make exactly maxAttempts total HTTP calls for transient transport errors" in {
        var attemptCount = 0
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            attemptCount += 1
            rejectResponse("Persistent network error")
        }

        val retryConfig = RetryPolicy(
            maxAttempts   = 6,
            initialDelay  = scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.MILLISECONDS),
            maxDelay      = scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.MILLISECONDS),
            backOffFactor = 1.0
        )
        val client      = FetchCalcClient(config(retryPolicy = retryConfig), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.TransportError]
            attemptCount shouldBe 6
        }
    }

    it should "not retry protocol errors regardless of maxAttempts" in {
        var attemptCount = 0
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            attemptCount += 1
            val json = FireboxCalcError("not_found", "Resource not found", None).asJson.noSpaces
            resolveResponse(404, json)
        }

        val retryConfig = RetryPolicy(
            maxAttempts   = 6,
            initialDelay  = scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.MILLISECONDS),
            maxDelay      = scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.MILLISECONDS),
            backOffFactor = 1.0
        )
        val client      = FetchCalcClient(config(retryPolicy = retryConfig), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.ProtocolError]
            attemptCount shouldBe 1
        }
    }

    it should "exhaust retries and return TransportError" in {
        val retryConfig = RetryPolicy(
            maxAttempts   = 3,
            initialDelay  = scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.MILLISECONDS),
            maxDelay      = scala.concurrent.duration.FiniteDuration(100, scala.concurrent.duration.MILLISECONDS),
            backOffFactor = 2.0
        )
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            rejectResponse("Persistent network error")
        }

        val client = FetchCalcClient(config(retryPolicy = retryConfig), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.TransportError]
        }
    }

    // =====================================================================
    // Cancellation
    // =====================================================================

    "cancel" should "abort in-flight requests" in {
        val abortPromise = Promise[Unit]()
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (url, init) =>
            // Listen for abort signal (signal is js.UndefOr, use get to access)
            init.signal.get.addEventListener(
                "abort",
                (_: dom.Event) => {
                    abortPromise.success(())
                }
            )
            neverResolve
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        val fut    = client.getKinds

        // Cancel the request
        client.cancel

        // Wait for abort to be triggered, then check the result
        abortPromise.future.flatMap { _ =>
            fut.map { result =>
                val failure = result.left.toOption.get
                failure shouldBe a[CalculationFailure.TransportError]
            }
        }
    }

    // =====================================================================
    // URL Construction
    // =====================================================================

    "URL" should "construct correct endpoints" in {
        val capturedUrl = Promise[String]()
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (url, init) =>
            capturedUrl.success(url               )
            resolveResponse    (200, discoveryJson)
        }

        val client = FetchCalcClient(config(baseUri = "https://api.example.com/calc/v1"), mockFetch)(using ec)
        client.getKinds.flatMap { _ =>
            capturedUrl.future.map { url =>
                url shouldBe "https://api.example.com/calc/v1/kinds"
            }
        }
    }

    // =====================================================================
    // Edge Cases
    // =====================================================================

    "empty response" should "return DecodeError on empty body" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            resolveResponse(200, "")
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map { result =>
            val failure = result.left.toOption.get
            failure shouldBe a[CalculationFailure.DecodeError]
        }
    }

    "422 error" should "return ProtocolError with validation details" in {
        val mockFetch: (String, RequestInit) => js.Promise[Response] = { (_, _) =>
            val json = FireboxCalcError(
                "validation_error",
                "Invalid firebox dimensions",
                Some(List(WireErrorItem("E001", "firebox_depth must be positive")))
            ).asJson.noSpaces
            resolveResponse(422, json)
        }

        val client = FetchCalcClient(config(), mockFetch)(using ec)
        client.getKinds.map {
            case Left(CalculationFailure.ProtocolError(status, _, _, details)) =>
                status shouldBe 422
                details.isDefined shouldBe true
            case result                                                        =>
                fail(s"Expected ProtocolError, got $result")
        }
    }
}
