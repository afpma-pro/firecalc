/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import afpma.firecalc.dto.transformers.given
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2

import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.syntax.*

import scala.util.{Try, Success, Failure}


object FireCalcYAMLMigrations:

    // V1 → V2 Migration
    // The new `height_of_first_row_of_air_injectors` field has a default value (5.cm)
    // in the Firebox case classes, so Chimney can derive the transformer automatically
    // since the Circe decoder will use the default when deserializing V1 JSON.
    
    given Transformer[FireCalcYAML_V1, FireCalcYAML_V2] =
        Transformer.define[FireCalcYAML_V1, FireCalcYAML_V2]
            .withFieldConst(_.version, FireCalc_Version(2))
            .withFieldComputed(_.firebox, _.firebox.transformInto[v2.Firebox_V2])
            .buildTransformer

    def migrateV1ToV2(v1: FireCalcYAML_V1): FireCalcYAML_V2 =
        v1.transformInto[FireCalcYAML_V2]

    def upgradeToCurrent(dto: Any): Either[Throwable, FireCalcYAML] =
        dto match
            case fc: FireCalcYAML_V2 => Right(fc)
            case fcv1: FireCalcYAML_V1 =>
                Right(fcv1.transformInto[FireCalcYAML_V2])
            case other =>
                Left(new Exception(s"Unsupported file version: ${other.getClass().getName}"))

    /**
     * Decode YAML string and migrate to latest FireCalcYAML version.
     *
     * This is the recommended entry point for loading FireCalcYAML from files or API requests.
     * It automatically detects the version, decodes with the appropriate versioned decoder,
     * and migrates to the latest version.
     *
     * @param yaml YAML string containing FireCalcYAML data
     * @return Either[String, FireCalcYAML] - Left with error message, Right with migrated schema
     */
    def decodeAndMigrate(yaml: String): Either[String, FireCalcYAML] =
        yamlParser.parse(yaml) match
            case Left(parseError) =>
                Left(s"Failed to parse YAML: ${parseError.getMessage()}")
            case Right(json) =>
                decodeAndMigrateJson(json)

    /**
     * Decode JSON and migrate to latest FireCalcYAML version.
     *
     * @param json Circe JSON value
     * @return Either[String, FireCalcYAML] - Left with error message, Right with migrated schema
     */
    def decodeAndMigrateJson(json: Json): Either[String, FireCalcYAML] =
        detectVersion(json) match
            case None =>
                Left("Could not detect version field in YAML")
            case Some(1) =>
                decodeV1(json).map(migrateV1ToV2)
            case Some(2) =>
                decodeV2(json)
            case Some(version) =>
                Left(s"Unknown FireCalcYAML version: $version")

    /**
     * Detect version from JSON.
     */
    private def detectVersion(json: Json): Option[Int] =
        json.hcursor.downField("version").as[Int].toOption

    /**
     * Decode V1 from JSON using FireCalcYAML_V1 decoder.
     */
    private def decodeV1(json: Json): Either[String, FireCalcYAML_V1] =
        // Use the decoder from v1/FireCalcYAML_V1.scala
        import FireCalcYAML_V1.decoder
        json.as[FireCalcYAML_V1].left.map(e => s"Failed to decode V1: ${e.getMessage()}")

    /**
     * Decode V2 from JSON using FireCalcYAML_V2 decoder.
     */
    private def decodeV2(json: Json): Either[String, FireCalcYAML_V2] =
        // Use the decoder from v2/FireCalcYAML_V2.scala
        import FireCalcYAML_V2.decoder
        json.as[FireCalcYAML_V2].left.map(e => s"Failed to decode V2: ${e.getMessage()}")

    /**
     * Try-based wrapper for decodeAndMigrate for Scala.js compatibility.
     */
    def decodeAndMigrateTry(yaml: String): Try[FireCalcYAML] =
        decodeAndMigrate(yaml) match
            case Right(fc) => Success(fc)
            case Left(err) => Failure(new RuntimeException(err))

    /**
     * Encode FireCalcYAML to YAML string.
     *
     * @param fc FireCalcYAML to encode (always latest version)
     * @return Either[String, String] - Left with error message, Right with YAML string
     */
    def encodeToYaml(fc: FireCalcYAML): Either[String, String] =
        import FireCalcYAML_V2.encoder
        try
            val json = fc.asJson
            Right(yamlPrinter.print(json))
        catch
            case e: Exception =>
                Left(s"Failed to encode to YAML: ${e.getMessage()}")

    /**
     * Try-based wrapper for encodeToYaml for Scala.js compatibility.
     */
    def encodeToYamlTry(fc: FireCalcYAML): Try[String] =
        encodeToYaml(fc) match
            case Right(yaml) => Success(yaml)
            case Left(err) => Failure(new RuntimeException(err))

end FireCalcYAMLMigrations
