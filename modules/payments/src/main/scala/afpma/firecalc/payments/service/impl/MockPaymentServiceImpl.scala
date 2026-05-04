/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import afpma.firecalc.payments.domain.MandateSnapshot
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.service.impl.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async
import cats.syntax.all.*

import org.typelevel.log4cats.Logger

class MockPaymentServiceImpl[F[_]: Async](implicit logger: Logger[F]) extends PaymentService[F]:

    def createPaymentLink(
        orderId     : OrderId,
        amount      : BigDecimal,
        customerInfo: CustomerInfo
    ): F[String] =
        for
            _          <- logger.info(
                s"Creating payment link for order: ${orderId.value} for ${customerInfo.customerType} customer: ${customerInfo.email}"
            )
            paymentUrl <- Async[F].delay(s"https://pay.gocardless.com/billing_requests/BR${orderId.value}")
            _          <- logger.info(s"Payment link created: $paymentUrl")
        yield paymentUrl

    def processWebhook(body: String, signature: String): F[Either[String, WebhookEventStatus]] =
        for
            _ <- logger.info(s"Mock: Processing webhook with signature: $signature")
            _ <- logger.debug(s"Mock: Webhook body: $body")
        yield Right(WebhookEventStatus.Processed)

    def getMandateForPayment(paymentId: String): F[Option[MandateSnapshot]] =
        Async[F].raiseError(new NotImplementedError(s"MockPaymentServiceImpl.getMandateForPayment($paymentId)"))
