/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.payments.config.JwtConfig
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.impl.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async

import org.typelevel.log4cats.Logger

trait AuthenticationService[F[_]]:
    def generateAuthCode(                                       ): F[String]
    def validateCode    (token     : PurchaseToken, code: String): F[Boolean]
    def generateJWT     (customerId: CustomerId                 ): F[String]
    def validateJWT     (token     : String                     ): F[Option[CustomerId]]

object AuthenticationService:
    def create[F[_]: Async](
        purchaseIntentRepo: PurchaseIntentRepository[F],
        jwtConfig         : JwtConfig
    )(implicit logger: Logger[F]): F[AuthenticationService[F]] =
        Async[F].pure(new AuthenticationServiceImpl[F](purchaseIntentRepo, jwtConfig))
