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

trait ProductRepository[F[_]]:
    def findById    (id: ProductId): F[Option[Product]]
    def findOrCreate(
        name       : String,
        description: String,
        price      : BigDecimal,
        currency   : Currency,
        taxRate    : BigDecimal,
        taxExempt  : Boolean
    ): F[Product]

    def create(
        id         : ProductId,
        name       : String,
        description: String,
        price      : BigDecimal,
        currency   : Currency,
        active     : Boolean,
        taxRate    : BigDecimal,
        taxExempt  : Boolean
    ): F[Product]

    def update(
        id         : ProductId,
        name       : String,
        description: String,
        price      : BigDecimal,
        currency   : Currency,
        active     : Boolean,
        taxRate    : BigDecimal,
        taxExempt  : Boolean
    ): F[Product]

    def upsert(productInfo: afpma.firecalc.payments.shared.api.v1.ProductInfo): F[Product]

object ProductRepository:
    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[ProductRepository[F]] =
        Async[F].pure(new MoleculeProductRepository[F])
