/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

import afpma.firecalc.payments.config.JwtConfig
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*

import cats.effect.Async
import cats.syntax.all.*

import scala.util.Try

import org.typelevel.log4cats.Logger
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtCirce
import pdi.jwt.JwtClaim

class AuthenticationServiceImpl[F[_]: Async](
    purchaseIntentRepo: PurchaseIntentRepository[F],
    jwtConfig         : JwtConfig
)                                           (implicit logger: Logger[F])
    extends AuthenticationService[F]:

    private val algorithm    = JwtAlgorithm.HS256
    private val secureRandom = new SecureRandom()

    def generateAuthCode(): F[String] =
        Async[F].delay {
            val code = 100000 + secureRandom.nextInt(900000)
            code.toString
        }

    def generateJWT(customerId: CustomerId): F[String] =
        for
            _   <- logger.debug(s"Generating JWT for customer: ${customerId.value}")
            now <- Async[F].delay(Instant.now())
            claim = JwtClaim(
                subject    = Some(customerId.value.toString),
                issuer     = Some(jwtConfig.issuer),
                issuedAt   = Some(now.getEpochSecond),
                expiration = Some(now.plusSeconds(jwtConfig.expirationMinutes * 60L).getEpochSecond)
            )
            token <- Async[F].delay(JwtCirce.encode(claim, jwtConfig.secret, algorithm))
        yield token

    def validateJWT(token: String): F[Option[CustomerId]] =
        for
            _       <- logger.debug("Validating JWT token")
            decoded <- Async[F].delay(JwtCirce.decode(token, jwtConfig.secret, Seq(algorithm)).toOption)
            result  <- decoded match
                case Some(claim) if claim.issuer != Some(jwtConfig.issuer) =>
                    logger
                        .debug(
                            s"JWT rejected: issuer mismatch (expected '${jwtConfig.issuer}', got '${claim.issuer.getOrElse("<none>")}')"
                        )
                        .as(None)
                case Some(claim)                                           =>
                    Async[F].delay {
                        for
                            sub  <- claim.subject
                            uuid <- Try(UUID.fromString(sub)).toOption
                        yield CustomerId(uuid)
                    }
                case None                                                  => Async[F].pure(None)
        yield result
