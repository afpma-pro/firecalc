/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import cats.effect.IO
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import cats.effect.Async
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.email.*
import org.typelevel.log4cats.Logger
import org.http4s.client.Client
import afpma.firecalc.payments.service.impl.*

trait AuthenticationService[F[_]]:
  def generateAuthCode(): F[String]
  def validateCode(token: PurchaseToken, code: String): F[Boolean]
  def generateJWT(customerId: CustomerId): F[String]
  def validateJWT(token: String): F[Option[CustomerId]]

object AuthenticationService:
  def create[F[_]: Async](
    purchaseIntentRepo: PurchaseIntentRepository[F]
  )(implicit logger: Logger[F]): F[AuthenticationService[F]] =
    Async[F].pure(new AuthenticationServiceImpl[F](purchaseIntentRepo))