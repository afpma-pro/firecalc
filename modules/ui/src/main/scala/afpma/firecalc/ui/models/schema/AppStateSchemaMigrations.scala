/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchemaLoader
import afpma.firecalc.ui.models.schema.common.AppStateSchema_Version
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1
import afpma.firecalc.ui.models.schema.v2.AppStateSchema_V2
import afpma.firecalc.ui.models.schema.v3.AppStateSchema_V3
import afpma.firecalc.ui.models.schema.v4.AppStateSchema_V4
import afpma.firecalc.ui.models.schema.v5.AppStateSchema_V5

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.circe.Decoder
import io.circe.yaml.scalayaml.parser as yamlParser
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.scalajs.dom


/**
 * Schema migration manager for AppState persistence.
 *
 * Handles version detection and migration of persisted YAML data to the latest schema version.
 * Uses manual JSON navigation for version detection and Chimney transformers for migrations.
 *
 * ## Versioning Rule (Option B - Composite Versioning)
 *
 * **The container schema version MUST be incremented when any component version changes.**
 *
 * When upgrading `FireCalcYAML_V1` → `FireCalcYAML_V2`, you MUST also create `AppStateSchema_V2`.
 * This ensures:
 * - Clear migration path when reading localStorage
 * - Compile-time guarantee about contained component versions
 * - No runtime ambiguity
 *
 * ## Version History
 * - V1: Initial unified schema with engine_state (FireCalcYAML_V1), sensitive_data, and billing_data
 * - V2 (current): engine_state upgraded to FireCalcYAML_V2 (adds height_of_first_row_of_air_injectors)
 *
 * ## Migration Strategy
 * 1. Parse YAML to JSON using circe-yaml
 * 2. Navigate to version field manually using JSON cursor
 * 3. Apply version-specific migrations using Chimney transformers
 * 4. Validate the final schema
 *
 * ## Usage
 * {{{
 *   val maybeSchema = AppStateSchemaMigrations.migrateToLatest(rawYaml)
 *   maybeSchema match {
 *     case Some(schema) => // Use migrated schema
 *     case None => // Handle invalid/missing data
 *   }
 * }}}
 *
 * @see docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md for full versioning documentation
 */
object AppStateSchemaMigrations:

    import afpma.firecalc.dto.transformers.given

    /**
     * Chimney transformer from AppStateSchema_V1 to AppStateSchema_V2.
     * Uses the FireCalcYAML_V1 -> FireCalcYAML_V2 transformer from dto module.
     *
     * NOTE: We must explicitly transform engine_state using the imported transformer
     * to ensure the FireCalcYAML version is also upgraded from 1 to 2.
     */
    given Transformer[AppStateSchema_V1, AppStateSchema_V2] =
        Transformer
            .define[AppStateSchema_V1, AppStateSchema_V2]
            .withFieldConst(_.version, AppStateSchema_Version(2))
            .withFieldComputed(
                _.engine_state,
                v1 => v1.engine_state.transformInto[afpma.firecalc.dto.v2.FireCalcYAML_V2]
            )
            .buildTransformer


    // ─── Explicit AppStateSchema version-bumping transformers ────────────────────
    //
    // Each transformer MUST use .withFieldConst(_.version, ...) to bump the schema version.
    // Without these, Chimney auto-derives transformers that copy the version field from the
    // source schema, resulting in the migrated schema retaining the old version number.
    //
    // Before March 2026, only V1→V2 had an explicit transformer. V2→V3, V3→V4, and V4→V5
    // relied on Chimney auto-derivation which silently copied the version. Additionally,
    // the case Some(4) branch in migrateToLatest had a stray
    // `AppStateSchemaHelper.decodeFromYaml(rawData).toOption` line that was the last expression
    // in the block — making the entire V4→V5 migration dead code (the migration ran but its
    // result was discarded in favor of a direct V5 decode of the raw V4 data).

    given Transformer[AppStateSchema_V2, AppStateSchema_V3] =
        Transformer
            .define[AppStateSchema_V2, AppStateSchema_V3]
            .withFieldConst(_.version, AppStateSchema_Version(3))
            .buildTransformer

    given Transformer[AppStateSchema_V3, AppStateSchema_V4] =
        Transformer
            .define[AppStateSchema_V3, AppStateSchema_V4]
            .withFieldConst(_.version, AppStateSchema_Version(4))
            .buildTransformer

    given Transformer[AppStateSchema_V4, AppStateSchema_V5] =
        Transformer
            .define[AppStateSchema_V4, AppStateSchema_V5]
            .withFieldConst(_.version, AppStateSchema_V5.VERSION)
            .buildTransformer

    /**
     * Migrate raw YAML data to the latest schema version.
     *
     * Handles empty data, version detection, and applies necessary migrations.
     * Returns None if data is invalid or cannot be migrated.
     *
     * @param rawData YAML string from localStorage
     * @return Some(schema) if migration succeeds, None otherwise
     */
    def migrateToLatest(rawData: String): Option[AppStateSchema] =
        if rawData.trim.isEmpty then
            dom.console.log("Empty data provided, returning None")
            return None

        val V_LATEST = s"V${AppStateSchema.LATEST_VERSION}"

        detectVersion(rawData) match
            case None =>
                dom.console.log("Could not detect version, data may be corrupted")
                None

            case Some(1) =>
                // V1 - decode and migrate to V4
                decodeV1(rawData)
                    .flatMap(migrateFromV1ToV2)
                    .flatMap(migrateFromV2ToV3)
                    .flatMap(migrateFromV3ToV4)
                    .flatMap(migrateFromV4ToV5) match
                        case Success(v_latest) => Some(v_latest)
                        case Failure(e)  =>
                            dom.console.error(s"Failed to migrate V1 to $V_LATEST: ${e.getMessage()}")
                            None

            case Some(2) =>
                // V2 - decode and migrate to V4
                // Fallback: old V2 data may use V1 Firebox structure (before Firebox_V2 was created).
                // Decode as V1 then migrate V1→V2 (Chimney adds height_of_first_row_of_air_injectors = 5.cm).
                decodeV2(rawData)
                    .orElse {
                        dom.console.warn("V2 decode failed, falling back to V1 decode + migration")
                        decodeV1(rawData).flatMap(migrateFromV1ToV2)
                    }
                    .flatMap(migrateFromV2ToV3)
                    .flatMap(migrateFromV3ToV4)
                    .flatMap(migrateFromV4ToV5) match
                        case Success(v_latest) => Some(v_latest)
                        case Failure(e)  =>
                            dom.console.error(s"Failed to migrate V2 to $V_LATEST: ${e.getMessage()}")
                            None

            case Some(3) =>
                // V3 - decode and migrate to V4
                decodeV3(rawData) 
                    .flatMap(migrateFromV3ToV4)
                    .flatMap(migrateFromV4ToV5) match
                        case Success(v_latest) => Some(v_latest)
                        case Failure(e)  =>
                            dom.console.error(s"Failed to migrate V3 to $V_LATEST: ${e.getMessage()}")
                            None

            case Some(4) =>
                // V4 - decode and migrate to V5
                decodeV4(rawData)
                    .flatMap(migrateFromV4ToV5) match
                        case Success(v_latest) => Some(v_latest)
                        case Failure(e)  =>
                            dom.console.error(s"Failed to migrate V4 to $V_LATEST: ${e.getMessage()}")
                            None

            case Some(5) =>
                // Current version - decode directly (bypass decodeFromYaml's silent fallback)
                AppStateSchemaLoader.loadFromYaml(rawData).toOption

            case Some(version) =>
                dom.console.log(s"Unknown schema version: $version")
                None

    /**
     * Detect schema version from YAML using manual JSON navigation.
     *
     * Parses YAML to JSON, then navigates to the "version" field using cursor operations.
     * This approach is more robust than regex and handles YAML structure properly.
     *
     * @param yaml YAML string to parse
     * @return Some(version) if detected, None if parsing fails or version not found
     */
    private def detectVersion(yaml: String): Option[Int] =
        yamlParser.parse(yaml) match
            case Right(jsonValue) =>
                // Navigate to version field using cursor
                val versionCursor = jsonValue.hcursor.downField("version")

                // Try to decode as Int
                versionCursor.as[Int] match
                    case Right(versionNum) =>
                        dom.console.log(s"Detected schema version: $versionNum")
                        Some           (versionNum                             )
                    case Left(decodeError) =>
                        dom.console.log(s"Failed to decode version field: ${decodeError.getMessage()}")
                        None

            case Left(parseError) =>
                dom.console.log(s"Failed to parse YAML: ${parseError.getMessage()}")
                None

    /** Decode V4 schema from YAML string. */
    private def decodeV4(yaml: String): Try[AppStateSchema_V4] =
        import afpma.firecalc.ui.models.schema.v4.AppStateSchema_V4.given
        yamlParser.parse(yaml) match
            case Right(json) =>
                json.as[AppStateSchema_V4] match
                    case Right(schema) => Success(schema)
                    case Left(err)     => Failure(new RuntimeException(s"Failed to decode V4: ${err.getMessage()}"))
            case Left(err)   => Failure(new RuntimeException(s"Failed to parse V4 YAML: ${err.getMessage()}"))

    /** Decode V3 schema from YAML string. */
    private def decodeV3(yaml: String): Try[AppStateSchema_V3] =
        import afpma.firecalc.ui.models.schema.v3.AppStateSchema_V3.given
        yamlParser.parse(yaml) match
            case Right(json) =>
                json.as[AppStateSchema_V3] match
                    case Right(schema) => Success(schema)
                    case Left(err)     => Failure(new RuntimeException(s"Failed to decode V3: ${err.getMessage()}"))
            case Left(err)   => Failure(new RuntimeException(s"Failed to parse V3 YAML: ${err.getMessage()}"))

    /** Decode V2 schema from YAML string. */
    private def decodeV2(yaml: String): Try[AppStateSchema_V2] =
        import afpma.firecalc.ui.models.schema.v2.AppStateSchema_V2.given
        yamlParser.parse(yaml) match
            case Right(json) =>
                json.as[AppStateSchema_V2] match
                    case Right(schema) => Success(schema)
                    case Left(err)     => Failure(new RuntimeException(s"Failed to decode V2: ${err.getMessage()}"))
            case Left(err)   => Failure(new RuntimeException(s"Failed to parse V2 YAML: ${err.getMessage()}"))

    /** Decode V1 schema from YAML string. */
    private def decodeV1(yaml: String): Try[AppStateSchema_V1] =
        import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1.given
        yamlParser.parse(yaml) match
            case Right(json) =>
                json.as[AppStateSchema_V1] match
                    case Right(schema) => Success(schema)
                    case Left(err)     => Failure(new RuntimeException(s"Failed to decode V1: ${err.getMessage()}"))
            case Left(err)   => Failure(new RuntimeException(s"Failed to parse V1 YAML: ${err.getMessage()}"))

    /**
     * Migrate from V4 to V5 schema.
     *
     * Uses Chimney transformer to convert engine_state from FireCalcYAML_V4 to FireCalcYAML_V5.
     */
    private def migrateFromV4ToV5(schema: AppStateSchema_V4): Try[AppStateSchema_V5] =
        Try {
            dom.console.log("Migrating AppStateSchema from V4 to V5")
            schema.transformInto[AppStateSchema_V5]
        }
     
    /**
     * Migrate from V3 to V4 schema.
     *
     * Uses Chimney transformer to convert engine_state from FireCalcYAML_V3 to FireCalcYAML_V4.
     */
    private def migrateFromV3ToV4(schema: AppStateSchema_V3): Try[AppStateSchema_V4] =
        Try {
            dom.console.log("Migrating AppStateSchema from V3 to V4")
            schema.transformInto[AppStateSchema_V4]
        }

    /**
     * Migrate from V2 to V3 schema.
     *
     * Uses Chimney transformer to convert engine_state from FireCalcYAML_V2 to FireCalcYAML_V3.
     */
    private def migrateFromV2ToV3(schema: AppStateSchema_V2): Try[AppStateSchema_V3] =
        Try {
            dom.console.log("Migrating AppStateSchema from V2 to V3")
            schema.transformInto[AppStateSchema_V3]
        }

    /**
     * Migrate from V1 to V2 schema.
     *
     * Uses Chimney transformer to convert engine_state from FireCalcYAML_V1 to FireCalcYAML_V2.
     * The FireCalcYAML migration adds height_of_first_row_of_air_injectors with default value.
     */
    private def migrateFromV1ToV2(schema: AppStateSchema_V1): Try[AppStateSchema_V2] =
        Try {
            dom.console.log("Migrating AppStateSchema from V1 to V2")
            schema.transformInto[AppStateSchema_V2]
        }

    /**
     * Clear invalid data from localStorage.
     *
     * Called when schema validation fails or data is corrupted beyond repair.
     * Logs a warning before clearing to aid debugging.
     */
    def clearInvalidData(): Unit =
        dom.console.warn                  ("Clearing invalid schema data from localStorage")
        dom.window.localStorage.removeItem(LocalStorageKeys.APP_STATE_SCHEMA               )

    /**
     * Validate a schema by attempting to encode and decode it.
     *
     * Ensures the schema can be successfully serialized and deserialized,
     * which verifies structural integrity and codec compatibility.
     *
     * @param schema Schema to validate
     * @return true if validation succeeds, false otherwise
     */
    def validateSchema(schema: AppStateSchema): Boolean =
        AppStateSchemaHelper.encodeToYaml(schema) match
            case Success(yaml) =>
                AppStateSchemaHelper.decodeFromYaml(yaml) match
                    case Success(_) =>
                        dom.console.log("Schema validation successful")
                        true
                    case Failure(e) =>
                        dom.console.error(s"Schema decode validation failed: ${e.getMessage()}")
                        false
            case Failure(e)    =>
                dom.console.error(s"Schema encode validation failed: ${e.getMessage()}")
                false
