/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import cats.effect.IO
import java.time.Instant
import cats.effect.{Async, Resource}
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import org.typelevel.log4cats.Logger
import afpma.firecalc.payments.repository.impl.*

trait PurchaseIntentRepository[F[_]]:
  def create(
    productId: ProductId, 
    amount: BigDecimal, 
    currency: Currency,
    authCode: String,
    customerId: CustomerId,
    productMetadataId: Option[Long]
  ): F[PurchaseIntent]
  
  def createWithInternalCustomerId(
    productId: ProductId, 
    amount: BigDecimal, 
    currency: Currency,
    authCode: String,
    customerInternalId: Long,
    productMetadataId: Option[Long]
  ): F[PurchaseIntent]
  
  def findByToken(token: PurchaseToken): F[Option[PurchaseIntent]]
  def findByTokenAndCode(token: PurchaseToken, code: String): F[Option[PurchaseIntent]]
  def markAsProcessed(token: PurchaseToken): F[Boolean]
  def deleteExpired(): F[Int]

object PurchaseIntentRepository:
  def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[PurchaseIntentRepository[F]] =
    Async[F].pure(new MoleculePurchaseIntentRepository[F])
