/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_testkit

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.HeatOutputReduced
import afpma.firecalc.dto.v5.Firebox_V4

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given
import afpma.firecalc.firebox_calc_client.Http4sCalcClient

import coulomb.*
import coulomb.syntax.*

import com.comcast.ip4s.*
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*

import org.http4s.client.Client
import org.http4s.ember.server.EmberServerBuilder
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.CountDownLatch

/**
 * Black-box contract acceptance for the normative Firebox Calculation API
 * (ticket 17).
 *
 * These tests drive a conforming server over the real HTTP seam — the
 * production `Http4sCalcClient` adapter over an http4s `Client` — and assert
 * observable wire behavior: HTTP status, error codes, headers, and wire body
 * shape. The target is [[ReferenceConformingServer]], an Austrian-free
 * reference implementation, so the suite proves the public contract holds for
 * ANY conforming server without sharing compiled private classes, a local
 * Austrian core, or an in-process Austrian adapter.
 *
 * Numerical equivalence is deliberately NOT asserted here — it is a private
 * concern (`LocalRemoteEquivalenceSuite`).
 */
class FireboxCalcContractSuite extends AnyFreeSpec with Matchers:

    private val traditionalFixture: Firebox_V4.Traditional =
        Firebox_V4.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.5.withUnit[Meter],
            firebox_width                         = 0.5.withUnit[Meter],
            firebox_height                        = 0.6.withUnit[Meter],
            height_of_lowest_opening              = 0.1.withUnit[Meter],
            pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
            total_air_intake_surface_area_on_door = 0.025.withUnit[(Meter ^ 2)],
            glass_width                           = 0.3.withUnit[Meter],
            glass_height                          = 0.2.withUnit[Meter]
        )

    private def request(
        kind              : WireFireboxKind = WireFireboxKind.Traditional,
        locale            : String          = "en",
        apiVersion        : ApiVersion      = ApiVersion.v1,
        mB                : Option[WireQty] = Some(WireQty("15.0", "kilogram"))
    ): FireboxCalcRequest =
        FireboxCalcRequest(
            apiVersion         = apiVersion,
            kind               = kind,
            firebox            = traditionalFixture,
            params             = Standard15544Params(m_B = mB, n_min = None),
            airIntakePipeShape = None,
            locale             = locale,
            standard           = WireStandardVariant.Strict,
            operatingEnvelope  = None
        )

    /** A fresh client against a fresh reference server (isolated rate buckets). */
    private def withClient[A](f: Http4sCalcClient => A): A =
        val app = ReferenceConformingServer.routes[IO].map(_.orNotFound).unsafeRunSync()
        f(Http4sCalcClient(FireboxCalcClientConfig.localhost(), Client.fromHttpApp(app)))

    // ── Raw-HTTP path (findings 1–3) ──────────────────────────────────────
    //
    // The typed `Http4sCalcClient` cannot express request bodies outside
    // `FireboxCalcRequest` (an unknown `kind` string, a malformed nested
    // field, an oversized payload) and drops response headers
    // (`CalculationFailure.ProtocolError` carries none). These tests therefore
    // start a REAL Ember server around a fresh reference app and drive it with
    // `java.net.http.HttpClient` — a fully independent HTTP stack, as the
    // private `BlackBoxHttpContractSuite` does — so raw request bodies and raw
    // response headers are observable on the wire.

    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** A fresh reference server on an ephemeral port (isolated rate buckets). */
    private def withRawServer[A](f: String => A): A =
        val latch = new CountDownLatch(1)
        @volatile var base: String = ""
        val io =
            for
                app <- ReferenceConformingServer.app[IO]
                _   <- EmberServerBuilder
                    .default[IO]
                    .withHost(ipv4"127.0.0.1")
                    .withPort(port"0")
                    .withHttpApp(app)
                    .build
                    .use { server =>
                        IO {
                            base = s"http://127.0.0.1:${server.address.getPort}"
                            latch.countDown()
                        } *> IO.never
                    }
            yield ()
        io.start.unsafeRunSync()
        latch.await(    )
        f          (base)

    private def rawGet(base: String, path: String): HttpResponse[String] =
        httpClient.send(
            HttpRequest.newBuilder().uri(URI.create(s"$base$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private def rawPost(base: String, path: String, body: String): HttpResponse[String] =
        httpClient.send                 (
            HttpRequest
                .newBuilder()
                .uri (URI.create(s"$base$path")                )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build                        (),
            HttpResponse.BodyHandlers.ofString()
        )

    private def parseJson(body: String): Json =
        parse(body).getOrElse(fail(s"response body is not valid JSON: ${body.take(200)}"))

    "discovery — GET /v1/kinds" - {

        "advertises only the served kinds" in withClient { client =>
            val discovery = client.getKinds.unsafeRunSync().toOption.get
            discovery.kinds should contain theSameElementsAs
                List(WireFireboxKind.Traditional, WireFireboxKind.Ecolabeled)
            discovery.kinds should not contain WireFireboxKind.Door15aCatalog
        }
    }

    "calculation — POST /v1/calculate" - {

        "returns a complete AsResult grid for a served kind" in withClient { client =>
            val response = client.calculate(request()).unsafeRunSync().toOption.get
            response.kind shouldBe WireFireboxKind.Traditional
            response.locale shouldBe "en"
            response.fireboxContribution match
                case WireValidated.Valid(WireFireboxContribution.AsResult(results)) =>
                    results should have size 2
                    results.map(r => (r.draftCondition, r.loadQty)).distinct should have size 2
                case other                                                          =>
                    fail(s"expected a valid AsResult grid, got $other")
            response.constraints should not be empty
            response.reportData.fireboxDescription.rows should not be empty
        }

        "reports a MissingMB verdict when m_B is absent" in withClient { client =>
            val response = client.calculate(request(mB = None)).unsafeRunSync().toOption.get
            response.constraints.exists { v =>
                v.outcome == ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB)
            } shouldBe true
        }
    }

    "failure taxonomy — §7 error codes" - {

        "rejects an unsupported API version with 400" in withClient { client =>
            val failure = client.calculate(request(apiVersion = "v2")).unsafeRunSync().swap.toOption.get
            failure match
                case CalculationFailure.ProtocolError(status, error, _, _) =>
                    status shouldBe 400
                    error shouldBe "unsupported_api_version"
                case other                                                 =>
                    fail(s"expected a protocol error, got $other")
        }

        "rejects an unsupported locale with 400" in withClient { client =>
            val failure = client.calculate(request(locale = "ja")).unsafeRunSync().swap.toOption.get
            failure match
                case CalculationFailure.ProtocolError(status, error, _, _) =>
                    status shouldBe 400
                    error shouldBe "unsupported_locale"
                case other                                                 =>
                    fail(s"expected a protocol error, got $other")
        }

        "rejects an unserved kind with 404 kind_not_served" in withClient { client =>
            val failure =
                client.calculate(request(kind = WireFireboxKind.Door15aCatalog)).unsafeRunSync().swap.toOption.get
            failure match
                case CalculationFailure.ProtocolError(status, error, _, _) =>
                    status shouldBe 404
                    error shouldBe "kind_not_served"
                case other                                                 =>
                    fail(s"expected a protocol error, got $other")
        }
    }

    "request decode failures — unknown_kind vs malformed_request (raw HTTP)" - {

        "rejects an unknown kind string with 400 unknown_kind naming the valid wire values" in withRawServer { base =>
            val body    = request().asJson.deepMerge(Json.obj("kind" -> Json.fromString("nonexistent"))).noSpaces
            val resp    = rawPost(base, "/v1/calculate", body)
            resp.statusCode() shouldBe 400
            val json    = parseJson                 (resp.body()                                       )
            json.hcursor.downField("error").as[String] shouldBe Right("unknown_kind")
            // The message names the WIRE values (lowercase), not Scala enum names.
            val message = json.hcursor.downField("message").as[String].getOrElse("")
            message should include("traditional")
            message should not include "Traditional"
        }

        "rejects an absent kind field with 400 unknown_kind" in withRawServer { base =>
            val body = request().asJson.mapObject(_.remove("kind")).noSpaces
            val resp = rawPost(base, "/v1/calculate", body)
            resp.statusCode() shouldBe 400
            parseJson(resp.body()).hcursor.downField("error").as[String] shouldBe Right("unknown_kind")
        }

        "rejects a bad nested field with 400 malformed_request and a field-path detail" in withRawServer { base =>
            // With flat encoding, firebox fields are directly in the firebox object
            // (no wrapper key). Corrupt the `firebox_depth` quantity so the
            // derived decoder fails AT that field. The detail must then carry a
            // real field-path code naming the corrupted field.
            val encoded = request().asJson
            val body    = encoded
                .deepMerge(
                    Json.obj("firebox" -> Json.obj("firebox_depth" -> Json.fromString("not-a-quantity")))
                )
                .noSpaces
            val resp = rawPost(base, "/v1/calculate", body)
            resp.statusCode() shouldBe 400
            val json = parseJson(resp.body())
            json.hcursor.downField("error").as[String] shouldBe Right("malformed_request")
            val codes = json.hcursor.downField("details").as[List[WireErrorItem]].getOrElse(Nil).map(_.code)
            codes should have size 1
            val code = codes.head
            code should startWith("decoding_failure:")
            code should not be "decoding_failure:root"
            // The path is reconstructed from the circe cursor history (the same
            // `extractDecodeDetails` fold the real server uses), so it names the
            // corrupted field and its enclosing `firebox` field.
            code should include("firebox_depth")
            code should include("firebox")
            json.hcursor.downField("message").as[String].getOrElse("") should include("firebox_depth")
        }
    }

    "payload limit — 413 payload_too_large (raw HTTP)" - {

        "returns the JSON 413 envelope for an oversized body, not a 400 or plain text" in withRawServer { base =>
            val padding = "x" *                     (ReferenceConformingServer.maxBodySize.toInt + 100)
            val body    = request().asJson.deepMerge(Json.obj("padding" -> Json.fromString(padding))  ).noSpaces
            val resp    = rawPost(base, "/v1/calculate", body)
            resp.statusCode() shouldBe 413
            parseJson(resp.body()).hcursor.downField("error").as[String] shouldBe Right("payload_too_large")
        }
    }

    "rate limiting — GET /v1/kinds burst" - {

        "returns 429 rate_limited once the kinds bucket is exhausted" in withClient { client =>
            val results = (1 to (ReferenceConformingServer.kindsBurst + 3)).map { _ =>
                client.getKinds.unsafeRunSync()
            }
            val denied  = results.collect { case Left(CalculationFailure.ProtocolError(429, "rate_limited", _, _)) =>
                ()
            }
            denied should not be empty
        }

        "emits X-RateLimit-* on allowed responses and Retry-After on 429 (raw HTTP)" in withRawServer { base =>
            val responses = (1 to (ReferenceConformingServer.kindsBurst + 3)).map(_ => rawGet(base, "/v1/kinds")).toList

            // Allowed responses carry the rate-limit headers.
            val allowed = responses.filter(_.statusCode() == 200)
            allowed should not be empty
            allowed.head.headers().firstValue("X-RateLimit-Limit").isPresent shouldBe true
            allowed.head.headers().firstValue("X-RateLimit-Remaining").isPresent shouldBe true
            allowed.head.headers().firstValue("X-RateLimit-Reset").isPresent shouldBe true

            // The burst exhausts the bucket, so at least one 429 follows.
            val denied = responses.filter(_.statusCode() == 429)
            denied should not be empty
            val last   = denied.last
            parseJson(last.body()).hcursor.downField("error").as[String] shouldBe Right("rate_limited")

            // Retry-After is present on the 429.
            last.headers().firstValue("Retry-After").isPresent shouldBe true

            // X-RateLimit-Reset is an ABSOLUTE Unix epoch (seconds since epoch),
            // not a relative seconds count — it must be far larger than any
            // plausible wait and sit in the post-2023 epoch range.
            val reset = last.headers().firstValue("X-RateLimit-Reset").orElse("0").toLongOption.getOrElse(0L)
            reset should be > 1_700_000_000L
        }
    }

end FireboxCalcContractSuite
