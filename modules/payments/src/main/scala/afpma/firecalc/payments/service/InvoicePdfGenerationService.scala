/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import cats.effect.Async
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.email.EmailService
import afpma.firecalc.invoices.models.InvoiceParams
import io.taig.babel.Locale

trait InvoicePdfGenerationService[F[_]]:
  /**
   * Generates a PDF invoice for the given order context and returns the PDF bytes
   */
  def generateInvoicePdf(context: OrderCompletionContext, locale: Locale): F[Array[Byte]]
  
  /**
   * Converts order completion context to invoice parameters
   */
  def buildInvoiceParams(context: OrderCompletionContext): F[InvoiceParams]
  
object InvoicePdfGenerationService:
  def create[F[_]: Async](
    invoiceConfigPath: String,
  )(implicit logger: org.typelevel.log4cats.Logger[F]): F[InvoicePdfGenerationService[F]] =
    import afpma.firecalc.payments.service.impl.InvoicePdfGenerationServiceImpl
    InvoicePdfGenerationServiceImpl.create[F](invoiceConfigPath)
