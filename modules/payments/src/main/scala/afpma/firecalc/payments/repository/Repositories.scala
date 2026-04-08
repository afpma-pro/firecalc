/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import cats.effect.Async
import cats.syntax.all.*

import scala.concurrent.ExecutionContext

import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger

/**
 * Aggregated repository container — constructs all repositories in a single call.
 *
 * Individual repository traits are preserved (they document contracts).
 * This replaces the 6 sequential `Repository.create[F]` calls in BackendMain
 * with one `Repositories.create[F]`.
 */
case class Repositories[F[_]](
    product        : ProductRepository[F],
    customer       : CustomerRepository[F],
    order          : OrderRepository[F],
    purchaseIntent : PurchaseIntentRepository[F],
    productMetadata: ProductMetadataRepository[F],
    invoiceCounter : InvoiceCounterRepository[F]
)

object Repositories:

    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[Repositories[F]] =
        (
            ProductRepository.create[F],
            CustomerRepository.create[F],
            OrderRepository.create[F],
            PurchaseIntentRepository.create[F],
            ProductMetadataRepository.create[F],
            InvoiceCounterRepository.create[F]
        ).mapN(Repositories.apply)
