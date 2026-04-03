/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.impl.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger

trait PurchaseIntentRepository[F[_]]:

    def create(
        productId        : ProductId,
        amount           : BigDecimal,
        currency         : Currency,
        authCode         : String,
        customerId       : CustomerId,
        productMetadataId: Option[Long]
    ): F[PurchaseIntent]

    def createWithInternalCustomerId(
        productId         : ProductId,
        amount            : BigDecimal,
        currency          : Currency,
        authCode          : String,
        customerInternalId: Long,
        productMetadataId : Option[Long]
    ): F[PurchaseIntent]

    def findByToken       (token: PurchaseToken              ): F[Option[PurchaseIntent]]
    def findByTokenAndCode(token: PurchaseToken, code: String): F[Option[PurchaseIntent]]
    def markAsProcessed   (token: PurchaseToken              ): F[Boolean]
    def deleteExpired     (                                  ): F[Int]

object PurchaseIntentRepository:

    final val DEFAULT_AUTH_CODE_EXPIRATION_DURATION_MINUTES = 30
    final val DEFAULT_AUTH_CODE_EXPIRATION_DURATION_SECONDS = DEFAULT_AUTH_CODE_EXPIRATION_DURATION_MINUTES * 60

    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[PurchaseIntentRepository[F]] =
        Async[F].pure(new MoleculePurchaseIntentRepository[F])
