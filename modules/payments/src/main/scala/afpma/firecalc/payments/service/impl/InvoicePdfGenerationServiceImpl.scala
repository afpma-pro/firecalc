/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service.impl

import java.time.LocalDate

import afpma.firecalc.invoices.FireCalcInvoiceFactory
import afpma.firecalc.invoices.models.*

import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.service.*
import afpma.firecalc.payments.shared.api.*

import cats.effect.Async
import cats.syntax.all.*

import io.taig.babel.Locale
import org.typelevel.log4cats.Logger

class InvoicePdfGenerationServiceImpl[F[_]: Async](
    invoiceFactory   : FireCalcInvoiceFactory,
    paymentService   : PaymentService[F],
    productCopyConfig: ProductCopyConfig
)                                                 (implicit logger: Logger[F])
    extends InvoicePdfGenerationService[F] {

    def generateInvoicePdf(context: OrderCompletionContext, locale: Locale): F[Array[Byte]] = {
        for {
            _             <- logger.info(s"Generating PDF invoice for order ${context.order.id.value}")
            invoiceParams <- buildInvoiceParams(context, locale)
            result        <- Async[F].fromEither(
                invoiceFactory
                    .generateInvoice(invoiceParams, locale)
                    .left
                    .map(error => InvoiceGenerationFailedException(context.order.id.value, error))
            )
            _             <- logger.info(s"Successfully generated PDF invoice for order ${context.order.id.value}")
        } yield result.pdfBytes
    }

    def buildInvoiceParams(context: OrderCompletionContext, locale: Locale): F[InvoiceParams] = {
        for {
            _ <- logger.debug(s"Building invoice parameters for order ${context.order.id.value}")

            // Convert Customer to Company (recipient)
            recipientCompany = customerToCompany(context.customer)

            // Create line item from Product and Order
            lineItem = productToLineItem(context.product, context.order, locale)

            // Resolve payment terms — prepends a SEPA mandate line when the order's payment
            // provider surfaces one (provider-agnostic; PaymentService decides).
            paymentTerms <- resolvePaymentTerms(context)

            // Build invoice parameters
            invoiceParams = InvoiceParams(
                invoiceNumber      = context.order.invoiceNumber.getOrElse(
                    throw InvoiceNumberMissingException(context.order.id.value)
                ),
                recipient          = recipientCompany,
                lineItems          = List(lineItem),
                paymentTerms       = paymentTerms,
                invoiceDate        = LocalDate.now(),
                dueDate            = None,              // Will be calculated from payment terms
                reference          = Some(context.order.id.value.toString),
                billTo             = None,              // Same as recipient for now
                currency           = "EUR",
                notes              = context.productMetadata.flatMap { case FileDescriptionWithContent(filename, _, _) =>
                    // Some(s"Related file: $filename")
                    None // no notes for now
                },
                discountPercentage = None,
                status             = InvoiceStatus.Sent // Invoice is being sent via email
            )

            _ <- logger.debug(s"Built invoice parameters for ${invoiceParams.invoiceNumber}")
        } yield invoiceParams
    }

    private def resolvePaymentTerms(context: OrderCompletionContext): F[PaymentTerms] =
        val baseTerms = invoiceFactory.loadedPaymentTerms
        context.order.paymentId match
            case Some(paymentId) =>
                paymentService.getMandateForPayment(paymentId).attempt.flatMap {
                    case Right(Some(snap))
                        if snap.reference.isDefined
                            && snap.createdDate.isDefined
                            && snap.nextPossibleChargeDate.isDefined =>
                        val sepa = PaymentMethod.SepaMandate(
                            mandateReference       = snap.reference,
                            mandateDate            = snap.createdDate,
                            iban                   = None,
                            nextPossibleChargeDate = snap.nextPossibleChargeDate
                        )
                        Async[F].pure(baseTerms.copy(methods = sepa :: baseTerms.methods))
                    case Right(Some(partial)) =>
                        val sepa = PaymentMethod.SepaMandate(
                            mandateReference       = partial.reference,
                            mandateDate            = partial.createdDate,
                            iban                   = None,
                            nextPossibleChargeDate = partial.nextPossibleChargeDate
                        )
                        logger.warn(
                            s"Mandate snapshot incomplete for payment $paymentId (snapshot=$partial); rendering pending placeholder"
                        ) *> Async[F].pure(
                            baseTerms.copy(methods = sepa :: baseTerms.methods)
                        )
                    case Right(None)          =>
                        // Provider has no mandate to surface for this payment (e.g. card-only flow).
                        // Keep base terms unchanged — no SEPA line, no placeholder.
                        Async[F].pure(baseTerms)
                    case Left(err)            =>
                        logger.warn(
                            s"Mandate fetch failed for payment $paymentId: ${err.getMessage}; rendering pending placeholder"
                        ) *> Async[F].pure(
                            baseTerms.copy(methods = PaymentMethod.SepaMandate() :: baseTerms.methods)
                        )
                }
            case None            =>
                Async[F].pure(baseTerms)

    private def customerToCompany(customer: Customer): Company = {
        Company              (
            name               = customer.individualNameOrCompanyName,
            displayName        = customer.companyName,
            address            = Address(
                street      = customer.addressLine1.getOrElse(""),
                streetLine2 = customer.addressLine2,
                city        = customer.city.getOrElse(""),
                postalCode  = customer.postalCode.getOrElse(""),
                region      = customer.region.getOrElse(""),
                country     = customer.countryCode.map(_.code).getOrElse("")
            ),
            vatNumber          = None, // Customer VAT number not stored in current schema
            registrationNumber = None,
            email              = customer.email,
            phone              = customer.phoneNumber,
            website            = None,
            logo               = None
        )
    }

    private def productToLineItem(product: Product, order: ProductOrder, locale: Locale): InvoiceLineItem = {
        val copy = ProductCopyResolver.resolve(product.sku, locale)(using productCopyConfig)
        InvoiceLineItem       (
            description        = s"${copy.name} - ${copy.description}",
            quantity           = BigDecimal(1), // Assuming quantity of 1 for now
            unitPrice          = order.amount,
            taxRate            = product.taxRate,
            discountPercentage = None,
            taxExempt          = product.taxExempt,
            unit               = Some("service"),
            productCode        = Some(product.id.value.toString)
        )
    }

}

object InvoicePdfGenerationServiceImpl {
    def create[F[_]: Async](
        invoiceConfigPath: String,
        paymentService   : PaymentService[F],
        productCopyConfig: ProductCopyConfig
    )(implicit logger: Logger[F]): F[InvoicePdfGenerationService[F]] = {
        val configFile    = new java.io.File(invoiceConfigPath)
        val factoryEither = FireCalcInvoiceFactory.fromConfigFile(configFile)

        for {
            _              <- logger.info("Creating InvoicePdfGenerationService")
            invoiceFactory <- Async[F].fromEither(
                factoryEither.left.map(error => ConfigurationLoadException(invoiceConfigPath, error))
            )
            _              <- logger.info("Invoice factory loaded successfully")
        } yield new InvoicePdfGenerationServiceImpl[F](invoiceFactory, paymentService, productCopyConfig)
    }
}
