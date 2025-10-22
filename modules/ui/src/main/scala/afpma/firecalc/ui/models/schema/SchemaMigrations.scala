/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1
import afpma.firecalc.ui.models.schema.common.AppStateSchema_Version
import afpma.firecalc.ui.models.AppStateSchemaHelper
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.{Decoder, Json}
import io.scalaland.chimney.dsl.*
import org.scalajs.dom
import scala.util.{Try, Success, Failure}

/**
 * Schema migration manager for AppState persistence.
 *
 * Handles version detection and migration of persisted YAML data to the latest schema version.
 * Uses manual JSON navigation for version detection and Chimney transformers for migrations.
 *
 * ## Version History
 * - V1 (current): Initial unified schema with engine_state, sensitive_data, and billing_data
 *
 * ## Migration Strategy
 * 1. Parse YAML to JSON using circe-yaml
 * 2. Navigate to version field manually using JSON cursor
 * 3. Apply version-specific migrations using Chimney transformers
 * 4. Validate the final schema
 *
 * ## Usage
 * {{{
 *   val maybeSchema = SchemaMigrations.migrateToLatest(rawYaml)
 *   maybeSchema match {
 *     case Some(schema) => // Use migrated schema
 *     case None => // Handle invalid/missing data
 *   }
 * }}}
 */
object SchemaMigrations:

    /** Current schema version - increment when adding new schema versions */
    val CURRENT_SCHEMA_VERSION = 1

    /**
     * Migrate raw YAML data to the latest schema version.
     *
     * Handles empty data, version detection, and applies necessary migrations.
     * Returns None if data is invalid or cannot be migrated.
     *
     * @param rawData YAML string from localStorage
     * @return Some(schema) if migration succeeds, None otherwise
     */
    def migrateToLatest(rawData: String): Option[AppStateSchema_V1] =
        if rawData.trim.isEmpty then
            dom.console.log("Empty data provided, returning None")
            return None

        detectVersion(rawData) match
            case None =>
                dom.console.log("Could not detect version, data may be corrupted")
                None

            case Some(1) =>
                // Current version - decode directly
                AppStateSchemaHelper.decodeFromYaml(rawData).toOption

            // Future migrations will be added here
            // case Some(2) =>
            //     // Decode V1, migrate to V2
            //     decodeV1(rawData).flatMap(migrateFromV1ToV2).toOption
            //
            // case Some(3) =>
            //     // Decode V2, migrate to V3
            //     decodeV2(rawData).flatMap(migrateFromV2ToV3).toOption

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
                        Some(versionNum)
                    case Left(decodeError) =>
                        dom.console.log(s"Failed to decode version field: ${decodeError.getMessage()}")
                        None

            case Left(parseError) =>
                dom.console.log(s"Failed to parse YAML: ${parseError.getMessage()}")
                None

    // Future migration functions using Chimney transformers
    //
    // /**
    //  * Migrate from V1 to V2 schema.
    //  *
    //  * Example migration adding a new field with a default value:
    //  * {{{
    //  *   schema
    //  *     .into[AppStateSchema_V2]
    //  *     .withFieldConst(_.newField, defaultValue)
    //  *     .transform
    //  * }}}
    //  */
    // private def migrateFromV1ToV2(schema: AppStateSchema_V1): Try[AppStateSchema_V2] =
    //     Try {
    //         schema
    //             .into[AppStateSchema_V2]
    //             .withFieldConst(_.version, AppStateSchema_Version(2))
    //             .transform
    //     }

    /**
     * Clear invalid data from localStorage.
     *
     * Called when schema validation fails or data is corrupted beyond repair.
     * Logs a warning before clearing to aid debugging.
     */
    def clearInvalidData(): Unit =
        dom.console.warn("Clearing invalid schema data from localStorage")
        dom.window.localStorage.removeItem(LocalStorageKeys.APP_STATE_SCHEMA)

    /**
     * Validate a schema by attempting to encode and decode it.
     *
     * Ensures the schema can be successfully serialized and deserialized,
     * which verifies structural integrity and codec compatibility.
     *
     * @param schema Schema to validate
     * @return true if validation succeeds, false otherwise
     */
    def validateSchema(schema: AppStateSchema_V1): Boolean =
        AppStateSchemaHelper.encodeToYaml(schema) match
            case Success(yaml) =>
                AppStateSchemaHelper.decodeFromYaml(yaml) match
                    case Success(_) =>
                        dom.console.log("Schema validation successful")
                        true
                    case Failure(e) =>
                        dom.console.error(s"Schema decode validation failed: ${e.getMessage()}")
                        false
            case Failure(e) =>
                dom.console.error(s"Schema encode validation failed: ${e.getMessage()}")
                false