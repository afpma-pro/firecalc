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

trait PaymentService[F[_]]:
  def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): F[String]
  
  def processWebhook(body: String, signature: String): F[Either[String, WebhookEventStatus]]

object PaymentService:
  def create[F[_]: Async](
    httpClient: Client[F],
    config: GoCardlessConfig,
    emailService: EmailService[F],
    orderService: OrderService[F],
    customerRepo: CustomerRepository[F]
  )(implicit logger: Logger[F]): F[PaymentService[F]] =
    GoCardlessPaymentServiceImpl.create[F](httpClient, config, emailService, orderService, customerRepo)

  // Keep the mock for testing
  def createMock[F[_]: Async](implicit logger: Logger[F]): F[PaymentService[F]] =
    Async[F].pure(new MockPaymentServiceImpl[F])