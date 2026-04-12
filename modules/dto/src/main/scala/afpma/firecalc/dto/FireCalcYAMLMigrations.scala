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
import afpma.firecalc.dto.v6.FireCalcYAML_V6

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.dsl.*

/**
 * Entry point for loading, migrating, and encoding FireCalcYAML project files (.fcalc).
 *
 * ## Version History & Migration Bug Context
 *
 * Before March 2026, the Chimney transformers for V2→V3, V3→V4, and V4→V5 did NOT
 * explicitly set the `version` field via `.withFieldConst`. Chimney auto-derived
 * transformers that silently copied the source version number instead of bumping it.
 * Only V1→V2 had a correct transformer from the start.
 *
 * This produced corrupted files in the wild where the `version` field says N but the
 * data is actually in V(N+1) format. For example, a file saved after a V4→V5 migration
 * would contain `version: 4` with V5 firebox fields (reinforcement_bars_offset_in_corners_R1/R2/R3
 * instead of the V4 field reinforcement_bars_offset_in_corners). Reloading such a file
 * selects the V4 decoder (based on the version marker), which fails on the V5 fields.
 *
 * ## Fix (March 2026)
 *
 * 1. **Compile-time prevention**: `FireCalc_Version.V[N]` opaque literal types make each
 *    version field a distinct type. Chimney can no longer auto-derive version-copying
 *    transformers — it fails to compile, forcing explicit `.withFieldConst`.
 *    See [[FireCalc_Version.V]] and [[transformers]].
 *
 * 2. **Runtime recovery**: `decodeWithFallback` handles legacy corrupted files by trying
 *    the next version's decoder (with a patched version field) when the primary decoder fails.
 *
 * ## Corrupted file landscape
 *
 * | Version marker | Actual data | Affected versions | Notes |
 * |---|---|---|---|
 * | `version: 2` | V3 data | V2→V3 migration | No user report, defensive fix |
 * | `version: 3` | V4 data | V3→V4 migration | Known issue, pre-existing fallback |
 * | `version: 4` | V5 data | V4→V5 migration | User-reported bug (March 2026) |
 */
object FireCalcYAMLMigrations:

    def migrateV1ToV2(v1: FireCalcYAML_V1): FireCalcYAML_V2 =
        v1.transformInto[FireCalcYAML_V2]

    def migrateV2ToV3(v2: FireCalcYAML_V2): FireCalcYAML_V3 =
        v2.transformInto[FireCalcYAML_V3]

    def migrateV3ToV4(v3: FireCalcYAML_V3): FireCalcYAML_V4 =
        v3.transformInto[FireCalcYAML_V4]

    def migrateV4ToV5(v4: FireCalcYAML_V4): FireCalcYAML_V5 =
        v4.transformInto[FireCalcYAML_V5]

    def migrateV5ToV6(v5: FireCalcYAML_V5): FireCalcYAML_V6 =
        v5.transformInto[FireCalcYAML_V6]

    def upgradeToCurrent(dto: Any): Either[Throwable, FireCalcYAML] =
        dto match
            case fcv6: FireCalcYAML_V6 =>
                Right(fcv6)
            case fcv5: FireCalcYAML_V5 =>
                Right(migrateV5ToV6(fcv5))
            case fcv4: FireCalcYAML_V4 =>
                Right((migrateV4ToV5 andThen migrateV5ToV6)(fcv4))
            case fcv3: FireCalcYAML_V3 =>
                Right((migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6)(fcv3))
            case fcv2: FireCalcYAML_V2 =>
                Right((migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6)(fcv2))
            case fcv1: FireCalcYAML_V1 =>
                Right(
                    (migrateV1ToV2 andThen migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6)(
                        fcv1
                    )
                )
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
     * For versions 2, 3, and 4: if the primary decoder fails, a fallback is attempted
     * using the next version's decoder. This handles legacy corrupted files where the
     * version field was not bumped during migration (see class-level scaladoc).
     *
     * @param json Circe JSON value
     * @return Either[String, FireCalcYAML] - Left with error message, Right with migrated schema
     */
    def decodeAndMigrateJson(json: Json): Either[String, FireCalcYAML] =
        detectVersion(json) match
            case None          =>
                Left("Could not detect version field in YAML")
            case Some(1)       =>
                // V1→V2 always had a correct transformer — no corrupted V1-marker files exist
                decodeV1(json).map(
                    migrateV1ToV2 andThen migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6
                )
            case Some(2)       =>
                decodeWithFallback(
                    json,
                    2,
                    decodeV2,
                    migrateV2ToV3 andThen migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6,
                    3,
                    decodeV3,
                    migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6
                )
            case Some(3)       =>
                decodeWithFallback(
                    json,
                    3,
                    decodeV3,
                    migrateV3ToV4 andThen migrateV4ToV5 andThen migrateV5ToV6,
                    4,
                    decodeV4,
                    migrateV4ToV5 andThen migrateV5ToV6
                )
            case Some(4)       =>
                decodeWithFallback(json, 4, decodeV4, migrateV4ToV5 andThen migrateV5ToV6, 5, decodeV5, migrateV5ToV6)
            case Some(5)       =>
                decodeWithFallback(json, 5, decodeV5, migrateV5ToV6, 6, decodeV6, identity)
            case Some(6)       =>
                decodeV6(json)
            case Some(version) =>
                Left(s"Unknown FireCalcYAML version: $version")

    /**
     * Try the primary decoder; if it fails, patch the JSON version field and try the
     * fallback decoder. This handles legacy files where a buggy migration wrote V(N+1)
     * data with a `version: N` marker.
     *
     * The JSON version field must be patched because `Decoder[FireCalc_Version.V[N]]`
     * validates that the version number matches N exactly.
     *
     * @param json            original JSON with the version marker as found in the file
     * @param primaryVersion  the version number found in the file (N)
     * @param primaryDecode   decoder for version N
     * @param primaryMigrate  migration chain from version N to current
     * @param fallbackVersion the next version number to try (N+1)
     * @param fallbackDecode  decoder for version N+1
     * @param fallbackMigrate migration chain from version N+1 to current
     */
    private def decodeWithFallback[A <: FireCalcYAML_Format, B <: FireCalcYAML_Format](
        json           : Json,
        primaryVersion : Int,
        primaryDecode  : Json => Either[String, A],
        primaryMigrate : A => FireCalcYAML,
        fallbackVersion: Int,
        fallbackDecode : Json => Either[String, B],
        fallbackMigrate: B => FireCalcYAML
    ): Either[String, FireCalcYAML] =
        primaryDecode(json) match
            case Right(decoded)   =>
                Right(primaryMigrate(decoded))
            case Left(primaryErr) =>
                // Fallback: patch the version field so the stricter V[N+1] decoder accepts it
                val patchedJson = json.mapObject(_.add("version", Json.fromInt(fallbackVersion)))
                fallbackDecode(patchedJson) match
                    case Right(decoded)    =>
                        Right(fallbackMigrate(decoded))
                    case Left(fallbackErr) =>
                        Left(
                            s"""|Could not decode FireCalcYAML version: got 'version = $primaryVersion' (error=$primaryErr))
                                |
                                |Attempt to decode as 'version = $fallbackVersion' also failed (error=$fallbackErr)""".stripMargin
                        )

    /** Detect version from JSON. */
    private def detectVersion(json: Json): Option[Int] =
        json.hcursor.downField("version").as[Int].toOption

    /** Decode V1 from JSON using FireCalcYAML_V1 decoder. */
    private def decodeV1(json: Json): Either[String, FireCalcYAML_V1] =
        import FireCalcYAML_V1.decoder
        json.as[FireCalcYAML_V1].left.map(e => s"Failed to decode V1: ${e.getMessage()}")

    /** Decode V2 from JSON using FireCalcYAML_V2 decoder. */
    private def decodeV2(json: Json): Either[String, FireCalcYAML_V2] =
        import FireCalcYAML_V2.decoder
        json.as[FireCalcYAML_V2].left.map(e => s"Failed to decode V2: ${e.getMessage()}")

    /** Decode V3 from JSON using FireCalcYAML_V3 decoder. */
    private def decodeV3(json: Json): Either[String, FireCalcYAML_V3] =
        import FireCalcYAML_V3.decoder
        json.as[FireCalcYAML_V3].left.map(e => s"Failed to decode V3: ${e.getMessage()}")

    /** Decode V4 from JSON using FireCalcYAML_V4 decoder. */
    private def decodeV4(json: Json): Either[String, FireCalcYAML_V4] =
        import FireCalcYAML_V4.decoder
        json.as[FireCalcYAML_V4].left.map(e => s"Failed to decode V4: ${e.getMessage()}")

    /** Decode V5 from JSON using FireCalcYAML_V5 decoder. */
    private def decodeV5(json: Json): Either[String, FireCalcYAML_V5] =
        import FireCalcYAML_V5.decoder
        json.as[FireCalcYAML_V5].left.map(e => s"Failed to decode V5: ${e.getMessage()}")

    /** Decode V6 from JSON using FireCalcYAML_V6 decoder. */
    private def decodeV6(json: Json): Either[String, FireCalcYAML_V6] =
        import FireCalcYAML_V6.decoder
        json.as[FireCalcYAML_V6].left.map(e => s"Failed to decode V6: ${e.getMessage()}")

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
