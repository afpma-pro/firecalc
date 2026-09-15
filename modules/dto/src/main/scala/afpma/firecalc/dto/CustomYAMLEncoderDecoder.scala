/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.utils.yaml.YamlDocumentText
import afpma.firecalc.utils.yaml.YamlPrintGuard
import afpma.firecalc.utils.yaml.YamlStringRepair

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
trait CustomYAMLEncoderDecoder[A]:

    type Version
    val VERSION: Version

    given decoder: Decoder[A] = scala.compiletime.deferred
    given encoder: Encoder[A] = scala.compiletime.deferred

    def encodeToYaml(x: A): Try[String] =
        val jsonEncodedTry =
            try {
                val xj = PressureLossTableStorage.encode(x.asJson)
                Success(xj)
            } catch
                case e =>
                    Failure(e)
        jsonEncodedTry.map(j => YamlPrintGuard.guard(yamlPrinter.print(j)))

    def decodeFromYaml(y: String): Try[A] =
        yamlParser.parse(YamlDocumentText.withTrailingNewline(y)) match
            case Left(pf)          => Failure(pf)
            case Right(parsedYaml) =>
                val j = YamlStringRepair.repair(PressureLossTableStorage.decode(parsedYaml)).noSpaces
                try
                    decode[A](j) match
                        case Left(err)    => Failure(err)
                        case Right(value) => Success(value)
                catch case e => Failure(e)

object CustomYAMLEncoderDecoder:
    def apply[A](using ev: CustomYAMLEncoderDecoder[A]) = ev
