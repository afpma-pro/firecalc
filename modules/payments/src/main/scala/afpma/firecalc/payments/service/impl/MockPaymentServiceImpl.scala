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
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*
import java.util.UUID
import scala.util.Random
import org.typelevel.log4cats.Logger
import afpma.firecalc.payments.service.impl.*

class MockPaymentServiceImpl[F[_]: Async](implicit logger: Logger[F]) extends PaymentService[F]:

  def createPaymentLink(
    orderId: OrderId, 
    amount: BigDecimal, 
    customerInfo: CustomerInfo
  ): F[String] =
    for
      _ <- logger.info(s"Creating payment link for order: ${orderId.value} for ${customerInfo.customerType} customer: ${customerInfo.email}")
      paymentUrl <- Async[F].delay(s"https://pay.gocardless.com/billing_requests/BR${orderId.value}")
      _ <- logger.info(s"Payment link created: $paymentUrl")
    yield paymentUrl

  def processWebhook(body: String, signature: String): F[Either[String, WebhookEventStatus]] =
    for
      _ <- logger.info(s"Mock: Processing webhook with signature: $signature")
      _ <- logger.debug(s"Mock: Webhook body: $body")
    yield Right(WebhookEventStatus.Processed)
