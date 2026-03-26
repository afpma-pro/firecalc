/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.transformers.given
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.v3.FireCalcYAML_V3
import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

object FireCalcYAMLMigrations:

    def migrateV1ToV2(v1: FireCalcYAML_V1): FireCalcYAML_V2 =
        v1.transformInto[FireCalcYAML_V2]

    def migrateV2ToV3(v2: FireCalcYAML_V2): FireCalcYAML_V3 =
        v2.transformInto[FireCalcYAML_V3]

    def migrateV3ToV4(v3: FireCalcYAML_V3): FireCalcYAML_V4 =
        v3.transformInto[FireCalcYAML_V4]

    def migrateV4ToV5(v4: FireCalcYAML_V4): FireCalcYAML_V5 =
        v4.transformInto[FireCalcYAML_V5]

    def upgradeToCurrent(dto: Any): Either[Throwable, FireCalcYAML] =
        dto match
            case fcv5: FireCalcYAML_V5 =>
                Right(fcv5)
            case fcv4: FireCalcYAML_V4 =>
                Right((migrateV4ToV5)(fcv4))
            case fcv3: FireCalcYAML_V3 =>
                Right((migrateV3ToV4 andThen migrateV4ToV5)(fcv3))
            case fcv2: FireCalcYAML_V2 =>
                Right((migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5)(fcv2))
            case fcv1: FireCalcYAML_V1 =>
                Right((migrateV1ToV2 andThen migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5)(fcv1))
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
            case Right(json)      =>
                decodeAndMigrateJson(json)

    /**
     * Decode JSON and migrate to latest FireCalcYAML version.
     *
     * @param json Circe JSON value
     * @return Either[String, FireCalcYAML] - Left with error message, Right with migrated schema
     */
    def decodeAndMigrateJson(json: Json): Either[String, FireCalcYAML] =
        detectVersion(json) match
            case None          =>
                Left("Could not detect version field in YAML")
            case Some(1)       =>
                decodeV1(json).map(migrateV1ToV2 andThen migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5)
            case Some(2)       =>
                decodeV2(json).map(migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5)
            case Some(3)       =>
                decodeV3(json) match
                    case Left(errV3) =>
                        decodeV4(json) match // try to bypass relying on the 'version' key because a failed migration made V4 projects be stored with a 'version = 3' key
                            case Left(errV4) => 
                                Left(
                                    s"""|Could not decode FireCalcYAML version: got 'version = 3' (error=$errV3))
                                        |
                                        |Attempt to decode as 'version = 4' also failed (error=$errV4)""".stripMargin
                                )
                            case Right(fcv4) => Right(migrateV4ToV5(fcv4))
                    case Right(fcv3) =>
                        Right((migrateV3ToV4 andThen migrateV4ToV5)(fcv3))
            case Some(4)       =>
                decodeV4(json).map(migrateV4ToV5)
            case Some(5)       =>
                decodeV5(json)
            case Some(version) =>
                Left(s"Unknown FireCalcYAML version: $version")

    /** Detect version from JSON. */
    private def detectVersion(json: Json): Option[Int] =
        json.hcursor.downField("version").as[Int].toOption

    /** Decode V1 from JSON using FireCalcYAML_V1 decoder. */
    private def decodeV1(json: Json): Either[String, FireCalcYAML_V1] =
        // Use the decoder from v1/FireCalcYAML_V1.scala
        import FireCalcYAML_V1.decoder
        json.as[FireCalcYAML_V1].left.map(e => s"Failed to decode V1: ${e.getMessage()}")

    /** Decode V2 from JSON using FireCalcYAML_V2 decoder. */
    private def decodeV2(json: Json): Either[String, FireCalcYAML_V2] =
        // Use the decoder from v2/FireCalcYAML_V2.scala
        import FireCalcYAML_V2.decoder
        json.as[FireCalcYAML_V2].left.map(e => s"Failed to decode V2: ${e.getMessage()}")

    /** Decode V3 from JSON using FireCalcYAML_V3 decoder. */
    private def decodeV3(json: Json): Either[String, FireCalcYAML_V3] =
        // Use the decoder from V3/FireCalcYAML_V3.scala
        import FireCalcYAML_V3.decoder
        json.as[FireCalcYAML_V3].left.map(e => s"Failed to decode V3: ${e.getMessage()}")

    /** Decode V4 from JSON using FireCalcYAML_V4 decoder. */
    private def decodeV4(json: Json): Either[String, FireCalcYAML_V4] =
        // Use the decoder from V4/FireCalcYAML_V4.scala
        import FireCalcYAML_V4.decoder
        json.as[FireCalcYAML_V4].left.map(e => s"Failed to decode V4: ${e.getMessage()}")

    /** Decode V5 from JSON using FireCalcYAML_V5 decoder. */
    private def decodeV5(json: Json): Either[String, FireCalcYAML_V5] =
        // Use the decoder from V5/FireCalcYAML_V5.scala
        import FireCalcYAML_V5.decoder
        json.as[FireCalcYAML_V5].left.map(e => s"Failed to decode V5: ${e.getMessage()}")

    /** Try-based wrapper for decodeAndMigrate for Scala.js compatibility. */
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
        try
            val json = fc.asJson
            Right(yamlPrinter.print(json))
        catch
            case e: Exception =>
                Left(s"Failed to encode to YAML: ${e.getMessage()}")

    /** Try-based wrapper for encodeToYaml for Scala.js compatibility. */
    def encodeToYamlTry(fc: FireCalcYAML): Try[String] =
        encodeToYaml(fc) match
            case Right(yaml) => Success(yaml)
            case Left(err)   => Failure(new RuntimeException(err))

end FireCalcYAMLMigrations
