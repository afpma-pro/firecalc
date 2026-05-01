/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service
import afpma.firecalc.payments.domain.MandateSnapshot
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.service.impl.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async

import org.http4s.client.Client
import org.typelevel.log4cats.Logger

trait PaymentService[F[_]]:
    def createPaymentLink(orderId: OrderId, amount: BigDecimal, customerInfo: CustomerInfo): F[String]

    def processWebhook(body: String, signature: String): F[Either[String, WebhookEventStatus]]

    /** Fetch the active mandate associated with a completed payment, if any. */
    def getMandateForPayment(paymentId: String): F[Option[MandateSnapshot]]

object PaymentService:
    def create[F[_]: Async](
        httpClient       : Client[F],
        config           : GoCardlessConfig,
        emailService     : EmailService[F],
        orderService     : OrderService[F],
        customerRepo     : CustomerRepository[F],
        productCopyConfig: ProductCopyConfig
    )(implicit logger: Logger[F]): F[PaymentService[F]] =
        GoCardlessPaymentServiceImpl
            .create[F](httpClient, config, emailService, orderService, customerRepo, productCopyConfig)

    // Keep the mock for testing
    def createMock[F[_]: Async](implicit logger: Logger[F]): F[PaymentService[F]] =
        Async[F].pure(new MockPaymentServiceImpl[F])
