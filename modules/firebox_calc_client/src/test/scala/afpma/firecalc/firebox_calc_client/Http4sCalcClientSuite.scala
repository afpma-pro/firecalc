/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_client

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.HeatOutputReduced
import afpma.firecalc.dto.v5.Firebox_V4

import afpma.firecalc.firebox_calc_api.*
import afpma.firecalc.firebox_calc_api.Codecs.given

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import coulomb.*
import coulomb.syntax.*

import io.circe.*
import io.circe.syntax.*
import cats.data.Kleisli

import org.http4s.*
import org.http4s.client.Client

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for [[Http4sCalcClient]] adapter behavior.
 *
 * Covers: discovery, calculation, error taxonomy, app-key propagation,
 * transport errors, and resource cleanup.
 *
 * Uses [[Client.fromHttpApp]] to avoid real network calls.
 */
class Http4sCalcClientSuite extends AnyWordSpec with Matchers {

    // =====================================================================
    // Fixtures
    // =====================================================================

    given org.typelevel.log4cats.Logger[IO] =
        new org.typelevel.log4cats.Logger[IO]:
            def info (msg: => String): IO[Unit] = IO.unit
            def error(msg: => String): IO[Unit] = IO.unit
            def warn (msg: => String): IO[Unit] = IO.unit
            def debug(msg: => String): IO[Unit] = IO.unit
            def trace(msg: => String): IO[Unit] = IO.unit
            def debug(t  : Throwable)(msg: => String): IO[Unit] = IO.unit
            def info (t  : Throwable)(msg: => String): IO[Unit] = IO.unit
            def warn (t  : Throwable)(msg: => String): IO[Unit] = IO.unit
            def error(t  : Throwable)(msg: => String): IO[Unit] = IO.unit
            def trace(t  : Throwable)(msg: => String): IO[Unit] = IO.unit
    private val validFirebox: Firebox_V4.Traditional = Firebox_V4.Traditional(
        heat_output_reduced                   = HeatOutputReduced.NotDefined,
        firebox_depth                         = 0.442.withUnit[Meter],
        firebox_width                         = 0.332.withUnit[Meter],
        firebox_height                        = 0.641.withUnit[Meter],
        height_of_lowest_opening              = 0.1.withUnit[Meter],
        pressure_loss_coefficient_from_door   = 1.0.withUnit[1],
        total_air_intake_surface_area_on_door = 0.01.withUnit[(Meter ^ 2)],
        glass_width                           = 0.3.withUnit[Meter],
        glass_height                          = 0.3.withUnit[Meter]
    )

    private val validRequest: FireboxCalcRequest = FireboxCalcRequest(
        apiVersion         = afpma.firecalc.firebox_calc_api.ApiVersion.v1,
        kind               = WireFireboxKind.Traditional,
        firebox            = validFirebox,
        params             = Standard15544Params(m_B = None, n_min = None),
        airIntakePipeShape = Some(WirePipeShape.Circle(WireQty("0.2", "meter"))),
        locale             = "en",
        standard           = WireStandardVariant.Strict
    )

    // Pre-serialized JSON for responses (avoids constructing complex response objects)
    private val validDiscoveryJson: String =
        DiscoveryResponse(
            apiVersion = afpma.firecalc.firebox_calc_api.ApiVersion.v1,
            kinds      = List(WireFireboxKind.Traditional, WireFireboxKind.Ecolabeled)
        ).asJson.noSpaces

    // =====================================================================
    // Helpers
    // =====================================================================

    private def makeClient(app: HttpApp[IO], config: FireboxCalcClientConfig): Http4sCalcClient =
        Http4sCalcClient(config, Client.fromHttpApp(app))

    private def defaultConfig: FireboxCalcClientConfig =
        FireboxCalcClientConfig.localhost()

    // =====================================================================
    // Discovery
    // =====================================================================

    "getKinds" should {

        "return discovery response on 200 OK" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.Ok).withEntity(validDiscoveryJson)))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result shouldBe a[Right[?, ?]]
            result.toOption.get.kinds should contain theSameElementsAs List(
                WireFireboxKind.Traditional,
                WireFireboxKind.Ecolabeled
            )
        }

        "return ProtocolError on 404 with ErrorResponseEnvelope" in {
            val errorBody = ErrorResponseEnvelope("unknown_kind", "Kind not found").asJson.noSpaces
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.NotFound).withEntity(errorBody)))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.ProtocolError]
            val pe     = result.left.toOption.get.asInstanceOf[CalculationFailure.ProtocolError]
            pe.status shouldBe 404
            pe.error shouldBe "unknown_kind"
        }

        "return ProtocolError on 500 with FireboxCalcError envelope" in {
            val errorBody = FireboxCalcError("internal_error", "Server error", None).asJson.noSpaces
            val app: HttpApp[IO] =
                Kleisli.liftF(IO.pure(Response[IO](Status.InternalServerError).withEntity(errorBody)))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.ProtocolError]
            val pe     = result.left.toOption.get.asInstanceOf[CalculationFailure.ProtocolError]
            pe.status shouldBe 500
            pe.error shouldBe "internal_error"
        }

        "return DecodeError on malformed JSON response" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.Ok).withEntity("not json at all")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.DecodeError]
        }

        "return TransportError on client exception" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.raiseError(new java.net.ConnectException("connection refused")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.TransportError]
            val te     = result.left.toOption.get.asInstanceOf[CalculationFailure.TransportError]
            te.cause shouldBe a[java.net.ConnectException]
        }
    }

    // =====================================================================
    // Calculation
    // =====================================================================

    "calculate" should {

        "return calculation response on 200 OK" in {
            // Build a minimal valid response JSON
            val responseJson = FireboxCalcResponse(
                apiVersion                = afpma.firecalc.firebox_calc_api.ApiVersion.v1,
                kind                      = WireFireboxKind.Traditional,
                locale                    = "en",
                fireboxContribution       = WireValidated.Valid(WireFireboxContribution.Without),
                combustionAirContribution = WireValidated.Valid(WireFireboxContribution.Without),
                constraints               = List.empty,
                reportData                = FireboxReportData(
                    fireboxDescription     = WireTable(header = None, colHeaders = None, rows = List.empty),
                    emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = List.empty),
                    combustionAirPipeRows  = List.empty,
                    fireboxPipeRows        = List.empty,
                    ecolabeledOutputs      = None
                )
            ).asJson.noSpaces

            val app: HttpApp[IO] = Kleisli.liftF(
                IO.pure(
                    Response[IO](Status.Ok)
                        .withEntity(responseJson)
                        .putHeaders(Header.Raw(org.typelevel.ci.CIString("Content-Type"), "application/json"))
                )
            )
            val client = makeClient(app, defaultConfig)

            val result = client.calculate(validRequest).unsafeRunSync()
            result shouldBe a[Right[?, ?]]
            result.toOption.get.kind shouldBe WireFireboxKind.Traditional
        }

        "return ProtocolError on 422 with detailed FireboxCalcError" in {
            val errorBody = FireboxCalcError(
                "malformed_request",
                "Request validation failed",
                Some(List(WireErrorItem("field1", "invalid value")))
            ).asJson.noSpaces
            val app: HttpApp[IO] =
                Kleisli.liftF(IO.pure(Response[IO](Status.UnprocessableEntity).withEntity(errorBody)))
            val client = makeClient(app, defaultConfig)

            val result = client.calculate(validRequest).unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.ProtocolError]
            val pe     = result.left.toOption.get.asInstanceOf[CalculationFailure.ProtocolError]
            pe.status shouldBe 422
            pe.error shouldBe "malformed_request"
            pe.details.isDefined shouldBe true
        }

        "return ProtocolError with ErrorResponseEnvelope fallback" in {
            val errorBody = ErrorResponseEnvelope("unsupported_locale", "Locale not supported").asJson.noSpaces
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.BadRequest).withEntity(errorBody)))
            val client = makeClient(app, defaultConfig)

            val result = client.calculate(validRequest).unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.ProtocolError]
            val pe     = result.left.toOption.get.asInstanceOf[CalculationFailure.ProtocolError]
            pe.status shouldBe 400
            pe.error shouldBe "unsupported_locale"
            pe.details shouldBe None
        }

        "return ProtocolError with unknown error code for undecodable body" in {
            val app: HttpApp[IO] =
                Kleisli.liftF(IO.pure(Response[IO](Status.BadRequest).withEntity("some random text")))
            val client = makeClient(app, defaultConfig)

            val result = client.calculate(validRequest).unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.ProtocolError]
            val pe     = result.left.toOption.get.asInstanceOf[CalculationFailure.ProtocolError]
            pe.error shouldBe "unknown"
            pe.message should include("Undecodable error response")
        }
    }

    // =====================================================================
    // App-key propagation
    // =====================================================================

    "app-key propagation" should {

        "include X-Firecalc-App-Key header when configured" in {
            var capturedHeader: Option[String] = None
            val app           : HttpApp[IO]    = Kleisli { (req: Request[IO]) =>
                capturedHeader = req.headers.get(org.typelevel.ci.CIString("X-Firecalc-App-Key")).map(_.head.value)
                IO.pure(Response[IO](Status.Ok).withEntity(validDiscoveryJson))
            }

            val config = FireboxCalcClientConfig.localhost().copy(appKey = Some("my-app-key"))
            val client = makeClient(app, config)

            client.getKinds.unsafeRunSync()
            capturedHeader shouldBe Some("my-app-key")
        }

        "not include X-Firecalc-App-Key header when not configured" in {
            var capturedHeader: Option[String] = None
            val app           : HttpApp[IO]    = Kleisli { (req: Request[IO]) =>
                capturedHeader = req.headers.get(org.typelevel.ci.CIString("X-Firecalc-App-Key")).map(_.head.value)
                IO.pure(Response[IO](Status.Ok).withEntity(validDiscoveryJson))
            }

            val client = makeClient(app, defaultConfig)
            client.getKinds.unsafeRunSync()
            capturedHeader shouldBe None
        }
    }
    "close" should {

        "succeed without side effects" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.Ok).withEntity(validDiscoveryJson)))
            val client = makeClient(app, defaultConfig)

            val result = client.close.unsafeRunSync()
            result shouldBe (())
        }
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    "edge cases" should {

        "treat 3xx redirects as transport errors" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status.TemporaryRedirect).withEntity("")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.TransportError]
        }

        "treat 1xx informational as transport errors" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.pure(Response[IO](Status(100, "Continue")).withEntity("")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.TransportError]
        }

        "return TransportError for socket timeout" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.raiseError(new java.net.SocketTimeoutException("read timed out")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.TransportError]
        }

        "return TransportError for unknown host" in {
            val app: HttpApp[IO] = Kleisli.liftF(IO.raiseError(new java.net.UnknownHostException("unknown.host")))
            val client = makeClient(app, defaultConfig)

            val result = client.getKinds.unsafeRunSync()
            result.left.toOption.get shouldBe a[CalculationFailure.TransportError]
        }
    }
}
