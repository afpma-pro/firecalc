/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import cats.effect.Async
import cats.syntax.all.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.{CustomerInfo, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2, PurchaseToken}
import afpma.firecalc.payments.email.*
import java.util.UUID
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import scala.util.Random
import org.typelevel.log4cats.Logger

class AuthenticationServiceImpl[F[_]: Async](
  purchaseIntentRepo: PurchaseIntentRepository[F]
)(implicit logger: Logger[F]) extends AuthenticationService[F]:

  def generateAuthCode(): F[String] =
    Async[F].delay(Random.between(100000, 999999).toString)

  def validateCode(token: PurchaseToken, code: String): F[Boolean] =
    for
      _ <- logger.debug(s"Validating code for token: ${token.value}")
      intentOpt <- purchaseIntentRepo.findByTokenAndCode(token, code)
      now <- Async[F].delay(Instant.now())
      isValid = intentOpt.exists { intent =>
        val notExpired = now.isBefore(intent.expiresAt)
        if !notExpired then
          val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
          val nowFormatted = formatter.format(now)
          val expiresAtFormatted = formatter.format(intent.expiresAt)
          logger.debug(s"Auth code expired. Current time: $nowFormatted, Expires at: $expiresAtFormatted")
        notExpired
      }
      _ <- logger.debug(s"Code validation result: $isValid")
    yield isValid

  def generateJWT(customerId: CustomerId): F[String] =
    for
      _ <- logger.debug(s"Generating JWT for customer: ${customerId.value}")
      token <- Async[F].delay(s"jwt_${customerId.value}_${System.currentTimeMillis()}")
    yield token

  def validateJWT(token: String): F[Option[CustomerId]] =
    for
      _ <- logger.debug(s"Validating JWT token")
      customerIdOpt <- Async[F].delay {
        if token.startsWith("jwt_") then
          val parts = token.split("_")
          if parts.length >= 2 then
            try Some(CustomerId(UUID.fromString(parts(1))))
            catch case _ => None
          else None
        else None
      }
    yield customerIdOpt
