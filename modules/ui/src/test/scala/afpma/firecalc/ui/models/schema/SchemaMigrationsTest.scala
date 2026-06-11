/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.dto.FireCalcYAMLMigrations
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.units.coulombutils.meters
import afpma.firecalc.dto.v5.FireCalcYAML_V5
import afpma.firecalc.ui.instances.defaultable
import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.EngineState
import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1
import afpma.firecalc.ui.models.schema.v5.AppStateSchema_V5

import scala.util.Success

import io.circe.syntax.*
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Comprehensive tests for the SchemaMigrations system.
 *
 * Tests cover:
 * - Migration from empty/legacy/current data
 * - Schema validation and round-trip encoding
 * - Edge cases and error handling
 *
 * Note: detectVersion is a private method, so it's tested indirectly through migrateToLatest.
 */
class SchemaMigrationsTest extends AnyFlatSpec with Matchers {

    behavior of "SchemaMigrations.migrateToLatest"

    it should "return None for empty localStorage data" in {
        // Given - empty string simulating fresh localStorage
        val rawData = ""

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(rawData)

        // Then
        result `shouldBe` None
    }

    it should "return None for whitespace-only data" in {
        // Given - only whitespace
        val rawData = "   \n  \t  \n   "

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(rawData)

        // Then
        result.shouldBe(None)
    }

    it should "decode V1 data directly without migration" in {
        // Given - create a valid V1 schema and encode it
        val originalSchema = AppStateSchemaHelper.createInitialSchema()
        val yaml           = AppStateSchemaHelper.encodeToYaml(originalSchema).get

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(yaml)

        // Then
        result.shouldBe                   (defined                      )
        result.get.version.unwrap.shouldBe(AppStateSchema.LATEST_VERSION)
    }

    it should "return None for data without version field (legacy data)" in {
        // Given - legacy data format without version field
        val legacyYaml =
            """engine_state:
        |  locale: fr
        |  display_units: SI
        |sensitive_data:
        |  customer:
        |    name: "Test Customer"
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(legacyYaml)

        // Then
        // Should return None because detectVersion returns None for missing version
        result.shouldBe(None)
    }

    it should "return None for future schema version" in {
        // Given - data claiming to be from a future version
        val futureYaml =
            """version: 999
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(futureYaml)

        // Then
        // Should return None as version 999 is not supported
        result.shouldBe(None)
    }

    it should "handle malformed YAML gracefully" in {
        // Given - completely invalid YAML
        val malformedYaml = "{ invalid: yaml: structure: [[[[ }}"

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(malformedYaml)

        // Then
        result.shouldBe(None)
    }

    it should "return None for YAML with missing required fields" in {
        // Given - YAML with version but missing required schema fields
        val incompleteYaml =
            """version: 1
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(incompleteYaml)

        // Then - decoding as V1 fails because required fields are missing,
        // so migrateToLatest returns None
        result.shouldBe(None)
    }

    behavior of "SchemaMigrations.validateSchema"

    it should "validate a correctly formed schema" in {
        // Given - create a valid initial schema
        val schema = AppStateSchemaHelper.createInitialSchema()

        // When
        val isValid = AppStateSchemaMigrations.validateSchema(schema)

        // Then
        isValid.shouldBe(true)
    }

    it should "perform successful round-trip encode/decode" in {
        // Given - create initial schema
        val originalSchema = AppStateSchemaHelper.createInitialSchema()

        // When - encode to YAML
        val encodeTry = AppStateSchemaHelper.encodeToYaml(originalSchema)

        // Then - encoding should succeed
        encodeTry.shouldBe(a[Success[?]])

        // When - decode back to schema
        val decodeTry = AppStateSchemaHelper.decodeFromYaml(encodeTry.get)

        // Then - decoding should succeed
        decodeTry.shouldBe(a[Success[?]])

        // And - decoded schema should match original
        val decodedSchema = decodeTry.get
        decodedSchema.version.unwrap.shouldBe     (originalSchema.version.unwrap     )
        decodedSchema.engine_state.locale.shouldBe(originalSchema.engine_state.locale)
    }

    behavior of "SchemaMigrations edge cases"

    it should "handle empty string input" in {
        // Given
        val emptyString = ""

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(emptyString)

        // Then
        result.shouldBe(None)
    }

    it should "handle YAML with only whitespace" in {
        // Given
        val whitespaceYaml = "    \n    \n    "

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(whitespaceYaml)

        // Then
        result.shouldBe(None)
    }

    it should "handle YAML with null values" in {
        // Given - YAML with explicit null
        val nullYaml =
            """version: null
        |engine_state: null
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(nullYaml)

        // Then
        result.shouldBe(None)
    }

    it should "return None for YAML with version as string" in {
        // Given
        val stringVersionYaml =
            """version: "1"
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(stringVersionYaml)

        // Then - detectVersion fails to decode string as Int, returns None
        result.shouldBe(None)
    }

    it should "handle YAML with version as float" in {
        // Given
        val floatVersionYaml =
            """version: 1.5
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(floatVersionYaml)

        // Then
        // Should return None as version must be an integer, not a float
        result.shouldBe(None)
    }

    it should "handle YAML with negative version number" in {
        // Given
        val negativeVersionYaml =
            """version: -1
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val migrationResult = AppStateSchemaMigrations.migrateToLatest(negativeVersionYaml)

        // Then
        // migrateToLatest will return None as -1 is not a valid version
        migrationResult.shouldBe(None)
    }

    it should "handle YAML with version zero" in {
        // Given
        val zeroVersionYaml =
            """version: 0
        |engine_state:
        |  locale: fr
        |""".stripMargin

        // When
        val migrationResult = AppStateSchemaMigrations.migrateToLatest(zeroVersionYaml)

        // Then
        // migrateToLatest will return None as 0 is not a supported version
        migrationResult.shouldBe(None)
    }

    it should "handle very large YAML documents" in {
        // Given - create a valid schema (which is reasonably large)
        val schema    = AppStateSchemaHelper.createInitialSchema()
        val largeYaml = AppStateSchemaHelper.encodeToYaml(schema).get

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(largeYaml)

        // Then
        result.shouldBe                   (defined                      )
        result.get.version.unwrap.shouldBe(AppStateSchema.LATEST_VERSION)
    }

    it should "handle YAML with special characters in strings" in {
        // Given - create a valid schema with special characters
        val schema           = AppStateSchemaHelper.createInitialSchema()
        val modifiedSchema   = schema.copy(
            engine_state = schema.engine_state.copy(
                project_description = schema.engine_state.project_description.copy(
                    reference = "Test with special: chars & symbols!"
                )
            )
        )
        val specialCharsYaml = AppStateSchemaHelper.encodeToYaml(modifiedSchema).get

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(specialCharsYaml)

        // Then - migration should work with special characters
        result.shouldBe                                               (defined                              )
        result.get.engine_state.project_description.reference.shouldBe("Test with special: chars & symbols!")
    }

    it should "handle YAML with Unicode characters" in {
        // Given - create a valid schema with Unicode characters
        val schema         = AppStateSchemaHelper.createInitialSchema()
        val modifiedSchema = schema.copy(
            engine_state = schema.engine_state.copy(
                project_description = schema.engine_state.project_description.copy(
                    reference = "Projet français avec accents éèêà"
                )
            )
        )
        val unicodeYaml    = AppStateSchemaHelper.encodeToYaml(modifiedSchema).get

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(unicodeYaml)

        // Then - migration should work with Unicode
        result.shouldBe                                               (defined                            )
        result.get.engine_state.project_description.reference.shouldBe("Projet français avec accents éèêà")
    }

    behavior of "SchemaMigrations.validateSchema with encoded data"

    it should "validate schema after encoding to YAML" in {
        // Given
        val schema = AppStateSchemaHelper.createInitialSchema()

        // When - encode to YAML
        val yamlTry = AppStateSchemaHelper.encodeToYaml(schema)

        // Then - encoding should succeed
        yamlTry.shouldBe(a[Success[?]])

        // When - validate the schema
        val isValid = AppStateSchemaMigrations.validateSchema(schema)

        // Then
        isValid.shouldBe(true)
    }

    it should "successfully round-trip a modified schema" in {
        // Given - create and modify a schema
        val schema         = AppStateSchemaHelper.createInitialSchema()
        val modifiedSchema = schema.copy(
            engine_state = schema.engine_state.copy(
                project_description = schema.engine_state.project_description.copy(
                    reference = "MODIFIED-TEST-001"
                )
            )
        )

        // When - perform round-trip
        val yaml          = AppStateSchemaHelper.encodeToYaml(modifiedSchema).get
        val decodedSchema = AppStateSchemaHelper.decodeFromYaml(yaml).get

        // Then - modification should be preserved
        decodedSchema.engine_state.project_description.reference.shouldBe("MODIFIED-TEST-001")
    }

    // ─── Helper: build a minimal AppStateSchema_V5 with Traditional firebox ────
    //
    // We construct V5 directly rather than downgrading from V6, because there is no
    // backward transformer V6→V5. The Traditional firebox variant is structurally
    // identical between Firebox_V3 (V4) and Firebox_V4 (V5), so the same YAML can
    // be decoded by both the V4 and V5 decoders — enabling the version-replacement
    // strategy for V4→V5 tests.

    private lazy val minimalV5Schema: AppStateSchema_V5 = {
        import afpma.firecalc.ui.models.StoveParamsUI

        val engineV5 = FireCalcYAML_V5(
            locale                         = Locale(Languages.Fr),
            display_units                  = DisplayUnits.SI,
            standard_or_computation_method = StandardOrComputationMethod.EN_15544_2023,
            project_description            = ProjectDescr.empty,
            local_conditions               = LocalConditions.default,
            stove_params                   = StoveParamsUI.default_StoveParams.default,
            air_intake_descr               = Seq.empty,
            firebox                        = defaultable.firebox_traditional_empty.default,
            flue_pipe_descr                = Seq(AddFlowOnlyPipeElement_15544_V3.AddSectionSlopped("flue-s1", 1.0.meters)),
            connector_pipe_descr           = Seq(AddThermalPipeElement_13384_V3.AddSectionSlopped("conn-s1", 2.5.meters)),
            chimney_pipe_descr             = Seq(AddThermalPipeElement_13384_V3.AddSectionSlopped("chim-s1", 5.0.meters))
        )

        AppStateSchema_V5  (
            engine_state   = engineV5,
            sensitive_data = ClientProjectData_V1.empty,
            billing_data   = defaultable.default_BillingInfo.default
        )
    }

    private lazy val minimalV5Yaml: String = {
        import AppStateSchema_V5.given
        yamlPrinter.print(minimalV5Schema.asJson)
    }

    /** Engine-state-only V5 YAML (no AppStateSchema wrapper — simulates legacy .fcalc files). */
    private lazy val engineStateOnlyV5Yaml: String = {
        import FireCalcYAML_V5.given
        yamlPrinter.print(minimalV5Schema.engine_state.asJson)
    }

    /** Engine-state-only V4 YAML (version downgraded from V5, Traditional firebox is identical). */
    private lazy val engineStateOnlyV4Yaml: String =
        engineStateOnlyV5Yaml.replaceAll("version: 5", "version: 4")

    behavior of "SchemaMigrations V4 to V5 migration (version bumping)"

    /**
     * Tests V4→V5→V6 migration by constructing real V5 YAML (with a Traditional firebox
     * that is structurally identical in V4 and V5), replacing version markers 5→4,
     * then feeding to migrateToLatest.
     */
    it should "bump AppStateSchema version from 4 to latest" in {
        // Given - downgrade both outer and inner version markers from 5 to 4
        val v4Yaml = minimalV5Yaml.replaceAll("version: 5", "version: 4")

        // Sanity: the YAML must actually contain "version: 4" (not still "version: 5")
        v4Yaml should include("version: 4")
        v4Yaml should not include "version: 5"

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(v4Yaml)

        // Then - migrated to latest (V6)
        result `shouldBe` defined
        result.get.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }

    it should "bump engine_state version from 4 to latest" in {
        // Given
        val v4Yaml = minimalV5Yaml.replaceAll("version: 5", "version: 4")

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(v4Yaml)

        // Then
        result `shouldBe` defined
        result.get.engine_state.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }

    behavior of "SchemaMigrations V5 to V6 migration"

    it should "bump AppStateSchema version from 5 to 6" in {
        // Given - real V5 YAML
        val v5Yaml = minimalV5Yaml

        // Sanity: the YAML must contain version: 5
        v5Yaml should include("version: 5")

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(v5Yaml)

        // Then - migrated to latest (V6)
        result `shouldBe` defined
        result.get.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }

    it should "bump engine_state version from 5 to 6" in {
        // Given
        val v5Yaml = minimalV5Yaml

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(v5Yaml)

        // Then
        result `shouldBe` defined
        result.get.engine_state.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }

    it should "restructure V5 separate pipe fields into V6 PostFireboxPipeDescrSlot sequence" in {
        // Given - V5 schema with distinguishable pipe sections (length 1m / 2.5m / 5m)
        val v5Yaml = minimalV5Yaml

        // When
        val result = AppStateSchemaMigrations.migrateToLatest(v5Yaml)

        // Then - post_firebox_pipes should contain 3 slots (flue, connector, chimney)
        result `shouldBe` defined
        val pipes = result.get.engine_state.post_firebox_pipes
        pipes.slots should have size 3

        // Verify each slot carries the correct payload (catches cross-wiring bugs)
        pipes.slots(0) match
            case PostFireboxPipeDescrSlot_V7.FlueSlot(descrs) =>
                descrs should have size 1
                descrs.head match
                    case AddFlowOnlyPipeElement_15544_V4.AddSectionSlopped(_, length) =>
                        length `shouldBe` 1.0.meters
                    case other                                                        => fail(s"unexpected flue descriptor: $other")
            case other                                        => fail(s"expected FlueSlot at index 0, got: $other")

        pipes.slots(1) match
            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(descrs) =>
                descrs should have size 1
                descrs.head match
                    case AddThermalPipeElement_13384_V4.AddSectionSlopped(_, length) =>
                        length `shouldBe` 2.5.meters
                    case other                                                       => fail(s"unexpected connector descriptor: $other")
            case other                                             => fail(s"expected ConnectorSlot at index 1, got: $other")

        pipes.slots(2) match
            case PostFireboxPipeDescrSlot_V7.ChimneySlot(descrs) =>
                descrs should have size 1
                descrs.head match
                    case AddThermalPipeElement_13384_V4.AddSectionSlopped(_, length) =>
                        length `shouldBe` 5.0.meters
                    case other                                                       => fail(s"unexpected chimney descriptor: $other")
            case other                                           => fail(s"expected ChimneySlot at index 2, got: $other")
    }

    // ─── .fcalc file format: full AppStateSchema round-trip ─────────────────────

    behavior of ".fcalc file format (AppStateSchema round-trip)"

    it should "round-trip full AppStateSchema preserving sensitive_data and billing_data" in {
        // Given - a schema with non-empty sensitive_data
        val schema = AppStateSchemaHelper
            .createInitialSchema()
            .copy(
                sensitive_data = ClientProjectData_V1.empty.copy(
                    customer = ClientProjectData_V1.empty.customer.copy(
                        first_name = "Jean",
                        last_name  = "Dupont"
                    )
                )
            )

        // When - encode and reload via the file import path
        val yaml   = AppStateSchemaHelper.encodeToYaml(schema).get
        val loaded = AppStateSchemaHelper.decodeFromFile(yaml)

        // Then - all fields preserved
        loaded.isSuccess `shouldBe` true
        loaded.get.engine_state.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
        loaded.get.sensitive_data.customer.first_name `shouldBe` "Jean"
        loaded.get.sensitive_data.customer.last_name `shouldBe` "Dupont"
    }

    it should "load legacy engine-state-only .fcalc files via fallback" in {
        // Given - an old-format .fcalc containing only FireCalcYAML (no sensitive_data wrapper)
        val engineState = EngineState.empty
        val legacyYaml  = FireCalcYAMLMigrations.encodeToYamlTry(engineState).get

        // Sanity: legacy format should NOT contain sensitive_data
        legacyYaml should not include "sensitive_data"

        // When - load via the file import path (falls back to engine-state-only decoding)
        val loaded = AppStateSchemaHelper.decodeFromFile(legacyYaml)

        // Then - loads successfully with default sensitive_data
        loaded.isSuccess `shouldBe` true
        loaded.get.engine_state.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }

    it should "not include sensitive_data in engine-state-only encoding" in {
        // Given - encode only engine_state (the format sent to backend for PDF generation)
        val engineState = EngineState.empty
        val engineYaml  = FireCalcYAMLMigrations.encodeToYamlTry(engineState).get

        // Then - engine-only YAML must not contain sensitive_data or billing_data fields
        engineYaml should not include "sensitive_data"
        engineYaml should not include "billing_data"
        engineYaml should not include "first_name"
        engineYaml should not include "last_name"

        // And - it should still be a valid FireCalcYAML
        val decoded = FireCalcYAMLMigrations.decodeAndMigrateTry(engineYaml)
        decoded.isSuccess `shouldBe` true
    }

    it should "load a V4 engine-state-only .fcalc file via decodeFromFile fallback" in {
        // Given - a V4 engine-state-only YAML (no AppStateSchema wrapper, simulates legacy .fcalc)
        val v4Yaml = engineStateOnlyV4Yaml

        // Sanity: this is engine-state-only (no sensitive_data wrapper) with version 4
        v4Yaml should include("version: 4")
        v4Yaml should not include "sensitive_data"
        v4Yaml should not include "engine_state"

        // When - load via decodeFromFile (should fail AppStateSchema decode, then fallback to FireCalcYAML)
        val loaded = AppStateSchemaHelper.decodeFromFile(v4Yaml)

        // Then - should succeed via the FireCalcYAMLMigrations fallback
        withClue(s"decodeFromFile failed: ${loaded.failed.toOption.map(_.getMessage)}\n") {
            loaded.isSuccess `shouldBe` true
        }
        loaded.get.engine_state.version.unwrap `shouldBe` AppStateSchema.LATEST_VERSION
    }
}
