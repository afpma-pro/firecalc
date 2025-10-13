/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import cats.effect.Async
import cats.syntax.all.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.email.*
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.invoices.FireCalcInvoiceFactory
import afpma.firecalc.invoices.models.*
import afpma.firecalc.invoices.config.EnvironmentConfigLoader
import io.taig.babel.Locale
import org.typelevel.log4cats.Logger
import java.time.LocalDate
import java.util.UUID

class InvoicePdfGenerationServiceImpl[F[_]: Async](
  invoiceFactory: FireCalcInvoiceFactory,
)(implicit logger: Logger[F]) extends InvoicePdfGenerationService[F] {

  def generateInvoicePdf(context: OrderCompletionContext, locale: Locale): F[Array[Byte]] = {
    for {
      _ <- logger.info(s"Generating PDF invoice for order ${context.order.id.value}")
      invoiceParams <- buildInvoiceParams(context)
      result <- Async[F].fromEither(
        invoiceFactory.generateInvoice(invoiceParams, locale)
          .left.map(error => InvoiceGenerationFailedException(context.order.id.value, error))
      )
      _ <- logger.info(s"Successfully generated PDF invoice for order ${context.order.id.value}")
    } yield result.pdfBytes
  }

  def buildInvoiceParams(context: OrderCompletionContext): F[InvoiceParams] = {
    for {
      _ <- logger.debug(s"Building invoice parameters for order ${context.order.id.value}")
      
      // Convert Customer to Company (recipient)
      recipientCompany = customerToCompany(context.customer)
      
      // Create line item from Product and Order
      lineItem = productToLineItem(context.product, context.order)
      
      // Get payment terms (default 30 days)
      paymentTerms = PaymentTerms.Presets.Net30
      
      // Build invoice parameters
      invoiceParams = InvoiceParams(
        invoiceNumber = context.order.invoiceNumber.getOrElse(
          throw InvoiceNumberMissingException(context.order.id.value)
        ),
        recipient = recipientCompany,
        lineItems = List(lineItem),
        paymentTerms = paymentTerms,
        invoiceDate = LocalDate.now(),
        dueDate = None, // Will be calculated from payment terms
        reference = Some(s"Order ${context.order.id.value}"),
        billTo = None, // Same as recipient for now
        currency = "EUR",
        notes = context.productMetadata.flatMap {
          case FileDescriptionWithContent(filename, _, _) => 
            Some(s"Related file: $filename")
        },
        discountPercentage = None,
        status = InvoiceStatus.Sent // Invoice is being sent via email
      )
      
      _ <- logger.debug(s"Built invoice parameters for ${invoiceParams.invoiceNumber}")
    } yield invoiceParams
  }

  private def customerToCompany(customer: Customer): Company = {
    Company(
      name = customer.individualNameOrCompanyName,
      displayName = customer.companyName,
      address = Address(
        street = customer.addressLine1.getOrElse(""),
        streetLine2 = customer.addressLine2,
        city = customer.city.getOrElse(""),
        postalCode = customer.postalCode.getOrElse(""),
        region = customer.region.getOrElse(""),
        country = customer.countryCode.map(_.code).getOrElse("")
      ),
      vatNumber = None, // Customer VAT number not stored in current schema
      registrationNumber = None,
      email = customer.email,
      phone = customer.phoneNumber,
      website = None,
      logo = None
    )
  }

  private def productToLineItem(product: Product, order: ProductOrder): InvoiceLineItem = {
    InvoiceLineItem(
      description = s"${product.name} - ${product.description}",
      quantity = BigDecimal(1), // Assuming quantity of 1 for now
      unitPrice = order.amount,
      taxRate = BigDecimal(0), // No tax for now
      discountPercentage = None,
      unit = Some("service"),
      productCode = Some(product.id.value.toString)
    )
  }

  private def backendLanguageToLocale(language: BackendCompatibleLanguage): Locale = {
    language match {
      case BackendCompatibleLanguage.English => io.taig.babel.Locales.en
      case BackendCompatibleLanguage.French => io.taig.babel.Locales.fr
      case _ => io.taig.babel.Locales.en // Default to English
    }
  }
}

object InvoicePdfGenerationServiceImpl {
  def create[F[_]: Async](
    invoiceConfigPath: String
  )(implicit logger: Logger[F]): F[InvoicePdfGenerationService[F]] = {
    val configFile = new java.io.File(invoiceConfigPath)
    val factoryEither = FireCalcInvoiceFactory.fromConfigFile(configFile)
    
    for {
      _ <- logger.info("Creating InvoicePdfGenerationService")
      invoiceFactory <- Async[F].fromEither(
        factoryEither.left.map(error => ConfigurationLoadException(invoiceConfigPath, error))
      )
      _ <- logger.info("Invoice factory loaded successfully")
    } yield new InvoicePdfGenerationServiceImpl[F](invoiceFactory)
  }
}
