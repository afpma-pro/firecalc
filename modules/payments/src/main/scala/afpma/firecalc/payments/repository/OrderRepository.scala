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

trait OrderRepository[F[_]]:
    def create                                 (
        customerId       : CustomerId,
        productId        : ProductId,
        amount           : BigDecimal,
        currency         : Currency,
        language         : BackendCompatibleLanguage,
        productMetadataId: Option[Long]
    ): F[ProductOrder]
    def findById                               (id: OrderId                                                         ): F[Option[ProductOrder]]
    def updateStatus                           (id: OrderId, status       : OrderStatus                             ): F[Boolean]
    def updatePaymentId                        (id: OrderId, paymentId    : String, paymentProvider: PaymentProvider): F[Boolean]
    def updateInvoiceNumber                    (id: OrderId, invoiceNumber: String                                  ): F[Boolean]
    def findConfirmedOrdersWithoutInvoiceNumber(                                                                    ): F[List[ProductOrder]]

object OrderRepository:
    def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[OrderRepository[F]] =
        Async[F].pure(new MoleculeOrderRepository[F])
