/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import afpma.firecalc.payments.domain.CustomerId
import afpma.firecalc.payments.service.AuthenticationService
import afpma.firecalc.payments.shared.api.ErrorResponseEnvelope

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all.*

import io.circe.syntax.*
import org.http4s.*
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.AuthMiddleware as Http4sAuthMiddleware
import org.typelevel.log4cats.Logger

/** Authenticated user context extracted from a valid JWT Bearer token. */
case class AuthenticatedUser(customerId: CustomerId)

object AuthMiddleware:

    /**
     * Creates http4s AuthMiddleware that validates JWT Bearer tokens.
     *
     * Usage (for future protected routes):
     * {{{
     *   val authedRoutes = AuthedRoutes.of[AuthenticatedUser, IO] {
     *     case GET -> Root / "me" as user => Ok(s"Hello ${user.customerId}")
     *   }
     *   val protectedRoutes = jwtMiddleware(authedRoutes)
     * }}}
     */
    def apply[F[_]: Async](
        authService: AuthenticationService[F]
    )(implicit logger: Logger[F]): Http4sAuthMiddleware[F, AuthenticatedUser] =

        val dsl = Http4sDsl[F]
        import dsl.*

        val authUser: Kleisli[F, Request[F], Either[String, AuthenticatedUser]] =
            Kleisli { request =>
                request.headers.get[Authorization] match
                    case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
                        authService.validateJWT(token).map {
                            case Some(id) => Right(AuthenticatedUser(id))
                            case None     => Left("Invalid or expired token")
                        }
                    case _                                                                =>
                        Async[F].pure(Left("Missing Bearer token"))
            }

        val onFailure: AuthedRoutes[String, F] =
            Kleisli { _ =>
                OptionT.liftF(
                    logger.debug("JWT authentication failed") *>
                        Unauthorized(
                            `WWW-Authenticate`(Challenge("Bearer", "firecalc-payments")),
                            ErrorResponseEnvelope  (
                                error   = "unauthorized",
                                message = "Valid Bearer token required"
                            ).asJson
                        )
                )
            }

        Http4sAuthMiddleware(authUser, onFailure)
