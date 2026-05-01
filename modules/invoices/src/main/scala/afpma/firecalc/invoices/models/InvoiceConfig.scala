/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import afpma.firecalc.payments.shared.api.ProductCopyConfig

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

/**
 * Invoice configuration — defines static org-level defaults only.
 * Per-invoice runtime data (lineItems, recipient, invoiceNumber, etc.) is
 * provided at generation time via InvoiceParams / InvoiceData and is not
 * part of this config.
 *
 * @param invoice  org-level sender config (sender identity, currency, payment terms, notes)
 * @param template template styling
 */
final case class InvoiceConfig(
    invoice : InvoiceSenderConfig,
    template: TemplateConfig = TemplateConfig()
)

/** Org-level static config fields that appear on every invoice from this sender. */
final case class InvoiceSenderConfig(
    sender        : Company,
    currency      : String                    = "EUR",
    paymentTerms  : PaymentTerms,
    notes         : Option[String]            = None,
    productCatalog: Option[ProductCopyConfig] = None
):
    /** Extract the product copy map, defaulting to empty when the YAML omits it. */
    def productCopyConfig: ProductCopyConfig = productCatalog.getOrElse(ProductCopyConfig.empty)

    /**
     * Ensure every active SKU has at least a `"default"` locale entry in productCatalog.
     * Returns Left with a descriptive message listing the missing SKUs on failure.
     */
    def validateProductCopyForSkus(activeSkus: Iterable[String]): Either[String, ProductCopyConfig] =
        val copy    = productCopyConfig
        val missing = activeSkus.filterNot(sku => copy.entries.get(sku).exists(_.contains("default"))).toList
        if missing.isEmpty then Right(copy)
        else
            Left                     (
                s"Missing product copy in invoice-config.yaml for SKU(s): " +
                    s"${missing.mkString(", ")}. Each active SKU requires at least " +
                    s"productCatalog.<sku>.default.{name, description}."
            )

final case class TemplateConfig(
    logoPosition: LogoPosition = LogoPosition.TopLeft,
    primaryColor: String       = "#2563eb",
    fontFamily  : String       = "Liberation Sans"
)

enum LogoPosition:
    case TopLeft, TopRight, TopCenter

object LogoPosition:
    given Decoder[LogoPosition] = Decoder.decodeString.emap {
        case "top-left"   => Right(LogoPosition.TopLeft)
        case "top-right"  => Right(LogoPosition.TopRight)
        case "top-center" => Right(LogoPosition.TopCenter)
        case other        => Left(s"Invalid logo position: $other")
    }

    given Encoder[LogoPosition] = Encoder.encodeString.contramap {
        case LogoPosition.TopLeft   => "top-left"
        case LogoPosition.TopRight  => "top-right"
        case LogoPosition.TopCenter => "top-center"
    }

object TemplateConfig:
    given Decoder[TemplateConfig] = semiauto.deriveDecoder[TemplateConfig]
    given Encoder[TemplateConfig] = semiauto.deriveEncoder[TemplateConfig]

object InvoiceSenderConfig:
    given Decoder[InvoiceSenderConfig] = semiauto.deriveDecoder[InvoiceSenderConfig]
    given Encoder[InvoiceSenderConfig] = semiauto.deriveEncoder[InvoiceSenderConfig]

object InvoiceConfig:
    given decoder: Decoder[InvoiceConfig] = semiauto.deriveDecoder[InvoiceConfig]
    given encoder: Encoder[InvoiceConfig] = semiauto.deriveEncoder[InvoiceConfig]

    def encodeToYaml(x: InvoiceConfig): Try[String] =
        val jsonEncodedTry =
            try {
                val xj = x.asJson
                Success(xj)
            } catch case e => Failure(e)
        jsonEncodedTry.map(yamlPrinter.print)

    def decodeFromYaml(y: String): Try[InvoiceConfig] =
        yamlParser.parse(y) match
            case Left(pf)          => Failure(pf)
            case Right(parsedYaml) =>
                val j = parsedYaml.noSpaces
                try
                    decode[InvoiceConfig](j) match
                        case Left(err)    => Failure(err)
                        case Right(value) => Success(value)
                catch case e => Failure(e)
