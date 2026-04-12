/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import java.util.UUID

import afpma.firecalc.payments.domain.CustomerId
import afpma.firecalc.payments.service.AuthenticationService

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.implicits.*
import org.http4s.{AuthScheme, Credentials}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import utest.*

object AuthMiddlewareTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    val testCustomerId = CustomerId(UUID.randomUUID())

    // Stub AuthenticationService: "valid-token" -> Some(testCustomerId), anything else -> None
    val stubAuthService: AuthenticationService[IO] = new AuthenticationService[IO]:
        def generateAuthCode(                      ): IO[String] = ???
        def generateJWT     (customerId: CustomerId): IO[String] = ???
        def validateJWT(token: String): IO[Option[CustomerId]] =
            IO.pure(if token == "valid-token" then Some(testCustomerId) else None)

    val middleware = AuthMiddleware[IO](stubAuthService)

    // A trivial authed route that returns 200 with the customer ID
    val authedRoutes = org.http4s.AuthedRoutes.of[AuthenticatedUser, IO] { case GET -> Root / "protected" as user =>
        Ok(user.customerId.value.toString)
    }

    val protectedRoutes: HttpRoutes[IO] = middleware(authedRoutes)

    val app: HttpApp[IO] = protectedRoutes.orNotFound

    /** Asserts that a 401 response has the expected WWW-Authenticate header and JSON error body. */
    def assert401Contract(resp: Response[IO]): Unit = {
        assert(resp.status == Status.Unauthorized)

        // WWW-Authenticate header must advertise Bearer realm
        val wwwAuth = resp.headers.get[`WWW-Authenticate`]
        assert(wwwAuth.isDefined)
        val challenges = wwwAuth.get.value
        assert(challenges.contains("Bearer")           )
        assert(challenges.contains("firecalc-payments"))

        // JSON error envelope must contain expected fields
        val json = resp.as[Json].unsafeRunSync()
        assert(json.hcursor.get[String]("error").contains("unauthorized")                 )
        assert(json.hcursor.get[String]("message").contains("Valid Bearer token required"))
    }

    val tests = Tests {

        test("valid Bearer token returns 200") {
            val req = Request[IO](Method.GET, uri"/protected")
                .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "valid-token")))
            val resp = app.run(req).unsafeRunSync()
            assert(resp.status == Status.Ok)
            val body = resp.as[String].unsafeRunSync()
            assert(body.contains(testCustomerId.value.toString))
        }

        test("invalid token returns 401 with WWW-Authenticate and JSON body") {
            val req = Request[IO](Method.GET, uri"/protected")
                .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "bad-token")))
            val resp = app.run(req).unsafeRunSync()
            assert401Contract(resp)
        }

        test("missing Authorization header returns 401 with WWW-Authenticate and JSON body") {
            val req  = Request[IO](Method.GET, uri"/protected")
            val resp = app.run(req).unsafeRunSync()
            assert401Contract(resp)
        }

        test("non-Bearer auth scheme returns 401 with WWW-Authenticate and JSON body") {
            val req = Request[IO](Method.GET, uri"/protected")
                .putHeaders(Authorization(Credentials.Token(AuthScheme.Basic, "some-value")))
            val resp = app.run(req).unsafeRunSync()
            assert401Contract(resp)
        }
    }
}
