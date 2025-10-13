/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import scala.util.{Try, Success, Failure}

final case class InvoiceConfig(
    invoice: InvoiceData,
    template: TemplateConfig = TemplateConfig()
)

final case class TemplateConfig(
    logoPosition: LogoPosition = LogoPosition.TopLeft,
    primaryColor: String = "#2563eb",
    fontFamily: String = "Liberation Sans"
)

enum LogoPosition:
    case TopLeft, TopRight, TopCenter

object LogoPosition:
    given Decoder[LogoPosition] = Decoder.decodeString.emap {
        case "top-left" => Right(LogoPosition.TopLeft)
        case "top-right" => Right(LogoPosition.TopRight)
        case "top-center" => Right(LogoPosition.TopCenter)
        case other => Left(s"Invalid logo position: $other")
    }
    
    given Encoder[LogoPosition] = Encoder.encodeString.contramap {
        case LogoPosition.TopLeft => "top-left"
        case LogoPosition.TopRight => "top-right"
        case LogoPosition.TopCenter => "top-center"
    }

object TemplateConfig:
    given Decoder[TemplateConfig] = semiauto.deriveDecoder[TemplateConfig]
    given Encoder[TemplateConfig] = semiauto.deriveEncoder[TemplateConfig]

object InvoiceConfig:
    given decoder: Decoder[InvoiceConfig] = semiauto.deriveDecoder[InvoiceConfig]
    given encoder: Encoder[InvoiceConfig] = semiauto.deriveEncoder[InvoiceConfig]

    def encodeToYaml(x: InvoiceConfig): Try[String] = 
        val jsonEncodedTry = try {
            val xj = x.asJson 
            Success(xj)
        } catch
            case e => Failure(e)
        jsonEncodedTry.map(yamlPrinter.print)

    def decodeFromYaml(y: String): Try[InvoiceConfig] = 
        yamlParser.parse(y) match
            case Left(pf) => Failure(pf)
            case Right(parsedYaml) =>
                val j = parsedYaml.noSpaces
                try
                    decode[InvoiceConfig](j) match
                        case Left(err)    => Failure(err)
                        case Right(value) => Success(value)
                catch
                    case e => Failure(e)
