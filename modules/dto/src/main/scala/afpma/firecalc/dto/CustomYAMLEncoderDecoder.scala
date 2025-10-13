/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

trait CustomYAMLEncoderDecoder[A]:

    given decoder: Decoder[A] = scala.compiletime.deferred
    given encoder: Encoder[A] = scala.compiletime.deferred

    def encodeToYaml(x: A): Try[String] =
        val jsonEncodedTry =
            try {
                val xj = x.asJson
                Success(xj)
            } catch 
                case e => 
                    Failure(e)
        jsonEncodedTry.map(yamlPrinter.print)

    def decodeFromYaml(y: String): Try[A] =
        yamlParser.parse(y) match
            case Left(pf)          => Failure(pf)
            case Right(parsedYaml) =>
                val j = parsedYaml.noSpaces
                try
                    decode[A](j) match
                        case Left(err)    => Failure(err)
                        case Right(value) => Success(value)
                catch case e => Failure(e)

object CustomYAMLEncoderDecoder:
    def apply[A](using ev: CustomYAMLEncoderDecoder[A]) = ev
