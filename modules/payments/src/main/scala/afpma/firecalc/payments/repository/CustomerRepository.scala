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

trait CustomerRepository[F[_]]:
  def findByEmail(email: String): F[Option[Customer]]
  def findByEmailAndUpdate(email: String, newCustomerInfo: CustomerInfo): F[Option[Customer]]
  def findById(id: CustomerId): F[Option[Customer]]
  def create(customerInfo: CustomerInfo): F[Customer]
  def createFull(customer: Customer): F[Boolean]
  def updatePaymentProvider(customerId: CustomerId, paymentProviderId: String, paymentProvider: PaymentProvider): F[Boolean]

object CustomerRepository:
  def create[F[_]: Async: Logger](using conn: Conn, ec: ExecutionContext): F[CustomerRepository[F]] =
    Async[F].pure(new MoleculeCustomerRepository[F])
