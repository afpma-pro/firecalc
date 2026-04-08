/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.common.AppStateSchema_Version
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1
import afpma.firecalc.ui.models.schema.v2.AppStateSchema_V2
import afpma.firecalc.ui.models.schema.v3.AppStateSchema_V3
import afpma.firecalc.ui.models.schema.v4.AppStateSchema_V4
import afpma.firecalc.ui.models.schema.v5.AppStateSchema_V5
import afpma.firecalc.ui.models.schema.v6.AppStateSchema_V6

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
 * - V2: engine_state upgraded to FireCalcYAML_V2 (adds height_of_first_row_of_air_injectors)
 * - V3: engine_state upgraded to FireCalcYAML_V3
 * - V4: engine_state upgraded to FireCalcYAML_V4
 * - V5: engine_state upgraded to FireCalcYAML_V5 (Firebox_V4 with ecolabel R1/R2/R3 fields)
 * - V6 (current): engine_state upgraded to FireCalcYAML_V6 (post_firebox_pipes replaces separate pipe fields)
 *
 * ## Adding a New Version (V7)
 * 1. Create AppStateSchema_V7 case class + companion with Decoder
 * 2. Add a Chimney Transformer[AppStateSchema_V6, AppStateSchema_V7] below
 * 3. Import the new Decoder (`import v7.AppStateSchema_V7.given`)
 * 4. Add `migrateFromV6` method and a `case Some(6)` branch in `migrateToLatest`
 * 5. Update the current-version decode case to point to V7
 *
 * ## Migration Strategy
 * 1. Parse YAML to JSON using circe-yaml
 * 2. Navigate to version field manually using JSON cursor
 * 3. Apply version-specific migrations using Chimney transformers
 * 4. Validate the final schema
 *
 * @see docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md for full versioning documentation
 */
object AppStateSchemaMigrations:

    import afpma.firecalc.dto.transformers.given

    // ─── Chimney Transformers ─────────────────────────────────────────────────
    //
    // Each transformer MUST use .withFieldConst(_.version, ...) to bump the schema version.
    // Without these, Chimney auto-derives transformers that copy the version field from the
    // source schema, resulting in the migrated schema retaining the old version number.

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

    given Transformer[AppStateSchema_V5, AppStateSchema_V6] =
        Transformer
            .define[AppStateSchema_V5, AppStateSchema_V6]
            .withFieldConst(_.version, AppStateSchema_V6.VERSION)
            .buildTransformer


    // ─── Generic decode + migrate helpers ─────────────────────────────────────

    /** Decode a versioned schema from YAML using its circe Decoder. */
    private def decodeVersion[V: Decoder](yaml: String, versionLabel: String): Try[V] =
        yamlParser.parse(yaml) match
            case Right(json) =>
                json.as[V] match
                    case Right(schema) => Success(schema)
                    case Left(err)     => Failure(new RuntimeException(s"Failed to decode $versionLabel: ${err.getMessage()}"))
            case Left(err)   => Failure(new RuntimeException(s"Failed to parse $versionLabel YAML: ${err.getMessage()}"))

    /** Migrate one schema version to the next using a Chimney transformer. */
    private def migrate[From, To](schema: From, fromLabel: String, toLabel: String)(using Transformer[From, To]): Try[To] =
        Try {
            dom.console.log(s"Migrating AppStateSchema from $fromLabel to $toLabel")
            schema.transformInto[To]
        }


    // Import all version-specific Decoders so decodeVersion[VN] can resolve them
    import v1.AppStateSchema_V1.given
    import v2.AppStateSchema_V2.given
    import v3.AppStateSchema_V3.given
    import v4.AppStateSchema_V4.given
    import v5.AppStateSchema_V5.given
    import v6.AppStateSchema_V6.given

    // ─── Migration chain ──────────────────────────────────────────────────────
    //
    // Each migration step: decode + chain of migrate calls to reach latest.
    // The V2 fallback (decode as V1 then migrate V1→V2) is preserved.

    private def migrateFromV1(yaml: String): Option[AppStateSchema] =
        val result =
            for
                v1 <- decodeVersion[AppStateSchema_V1](yaml, "V1")
                v2 <- migrate[AppStateSchema_V1, AppStateSchema_V2](v1, "V1", "V2")
                v3 <- migrate[AppStateSchema_V2, AppStateSchema_V3](v2, "V2", "V3")
                v4 <- migrate[AppStateSchema_V3, AppStateSchema_V4](v3, "V3", "V4")
                v5 <- migrate[AppStateSchema_V4, AppStateSchema_V5](v4, "V4", "V5")
                v6 <- migrate[AppStateSchema_V5, AppStateSchema_V6](v5, "V5", "V6")
            yield v6
        result match
            case Success(latest) => Some(latest)
            case Failure(e)      =>
                dom.console.error(s"Failed to migrate V1 to V${AppStateSchema.LATEST_VERSION}: ${e.getMessage()}")
                None

    private def migrateFromV2(yaml: String): Option[AppStateSchema] =
        // Fallback: old V2 data may use V1 Firebox structure (before Firebox_V2 was created).
        // Decode as V1 then migrate V1→V2 (Chimney adds height_of_first_row_of_air_injectors = 5.cm).
        val result =
            for
                v2 <- decodeVersion[AppStateSchema_V2](yaml, "V2")
                        .orElse {
                            dom.console.warn("V2 decode failed, falling back to V1 decode + migration")
                            for
                                v1 <- decodeVersion[AppStateSchema_V1](yaml, "V1")
                                v2 <- migrate[AppStateSchema_V1, AppStateSchema_V2](v1, "V1", "V2")
                            yield v2
                        }
                v3 <- migrate[AppStateSchema_V2, AppStateSchema_V3](v2, "V2", "V3")
                v4 <- migrate[AppStateSchema_V3, AppStateSchema_V4](v3, "V3", "V4")
                v5 <- migrate[AppStateSchema_V4, AppStateSchema_V5](v4, "V4", "V5")
                v6 <- migrate[AppStateSchema_V5, AppStateSchema_V6](v5, "V5", "V6")
            yield v6
        result match
            case Success(latest) => Some(latest)
            case Failure(e)      =>
                dom.console.error(s"Failed to migrate V2 to V${AppStateSchema.LATEST_VERSION}: ${e.getMessage()}")
                None

    private def migrateFromV3(yaml: String): Option[AppStateSchema] =
        val result =
            for
                v3 <- decodeVersion[AppStateSchema_V3](yaml, "V3")
                v4 <- migrate[AppStateSchema_V3, AppStateSchema_V4](v3, "V3", "V4")
                v5 <- migrate[AppStateSchema_V4, AppStateSchema_V5](v4, "V4", "V5")
                v6 <- migrate[AppStateSchema_V5, AppStateSchema_V6](v5, "V5", "V6")
            yield v6
        result match
            case Success(latest) => Some(latest)
            case Failure(e)      =>
                dom.console.error(s"Failed to migrate V3 to V${AppStateSchema.LATEST_VERSION}: ${e.getMessage()}")
                None

    private def migrateFromV4(yaml: String): Option[AppStateSchema] =
        val result =
            for
                v4 <- decodeVersion[AppStateSchema_V4](yaml, "V4")
                v5 <- migrate[AppStateSchema_V4, AppStateSchema_V5](v4, "V4", "V5")
                v6 <- migrate[AppStateSchema_V5, AppStateSchema_V6](v5, "V5", "V6")
            yield v6
        result match
            case Success(latest) => Some(latest)
            case Failure(e)      =>
                dom.console.error(s"Failed to migrate V4 to V${AppStateSchema.LATEST_VERSION}: ${e.getMessage()}")
                None

    private def migrateFromV5(yaml: String): Option[AppStateSchema] =
        val result =
            for
                v5 <- decodeVersion[AppStateSchema_V5](yaml, "V5")
                v6 <- migrate[AppStateSchema_V5, AppStateSchema_V6](v5, "V5", "V6")
            yield v6
        result match
            case Success(latest) => Some(latest)
            case Failure(e)      =>
                dom.console.error(s"Failed to migrate V5 to V${AppStateSchema.LATEST_VERSION}: ${e.getMessage()}")
                None


    // ─── Public API ───────────────────────────────────────────────────────────

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

        detectVersion(rawData) match
            case None          =>
                dom.console.log("Could not detect version, data may be corrupted")
                None
            case Some(1)       => migrateFromV1(rawData)
            case Some(2)       => migrateFromV2(rawData)
            case Some(3)       => migrateFromV3(rawData)
            case Some(4)       => migrateFromV4(rawData)
            case Some(5)       => migrateFromV5(rawData)
            case Some(6)       =>
                // Current version - decode directly
                decodeVersion[AppStateSchema_V6](rawData, "V6").toOption
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
