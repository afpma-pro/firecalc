/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import cats.effect.IO
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.domain.*
import cats.effect.Async
import afpma.firecalc.payments.repository.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.email.*
import org.typelevel.log4cats.Logger
import org.http4s.client.Client
import afpma.firecalc.payments.service.impl.*

trait PurchaseService[F[_]]:
  def createPurchaseIntent(request: CreatePurchaseIntentRequest): F[PurchaseToken]
  def verifyAndProcess(request: VerifyAndProcessRequest): F[VerifyAndProcessResponse]

object PurchaseService:
  def create[F[_]: Async](
    productRepo: ProductRepository[F],
    customerRepo: CustomerRepository[F],
    purchaseIntentRepo: PurchaseIntentRepository[F],
    productMetadataRepo: ProductMetadataRepository[F],
    authService: AuthenticationService[F],
    orderService: OrderService[F],
    paymentService: PaymentService[F],
    emailService: EmailService[F]
  )(implicit logger: Logger[F]): F[PurchaseService[F]] =
    Async[F].pure(new PurchaseServiceImpl[F](
      productRepo,
      customerRepo,
      purchaseIntentRepo,
      productMetadataRepo,
      authService,
      orderService,
      paymentService,
      emailService
    ))
