/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import afpma.firecalc.ui.models.AppStateSchemaHelper
import afpma.firecalc.ui.models.schema.AppStateSchema

import scala.util.Success

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
    result shouldBe None
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
    val yaml = AppStateSchemaHelper.encodeToYaml(originalSchema).get

    // When
    val result = AppStateSchemaMigrations.migrateToLatest(yaml)

    // Then
    result.shouldBe(defined)
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
    decodedSchema.version.unwrap.shouldBe(originalSchema.version.unwrap)
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
    val schema = AppStateSchemaHelper.createInitialSchema()
    val largeYaml = AppStateSchemaHelper.encodeToYaml(schema).get

    // When
    val result = AppStateSchemaMigrations.migrateToLatest(largeYaml)

    // Then
    result.shouldBe(defined)
    result.get.version.unwrap.shouldBe(AppStateSchema.LATEST_VERSION)
  }

  it should "handle YAML with special characters in strings" in {
    // Given - create a valid schema with special characters
    val schema = AppStateSchemaHelper.createInitialSchema()
    val modifiedSchema = schema.copy(
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
    result.shouldBe(defined)
    result.get.engine_state.project_description.reference.shouldBe("Test with special: chars & symbols!")
  }

  it should "handle YAML with Unicode characters" in {
    // Given - create a valid schema with Unicode characters
    val schema = AppStateSchemaHelper.createInitialSchema()
    val modifiedSchema = schema.copy(
      engine_state = schema.engine_state.copy(
        project_description = schema.engine_state.project_description.copy(
          reference = "Projet français avec accents éèêà"
        )
      )
    )
    val unicodeYaml = AppStateSchemaHelper.encodeToYaml(modifiedSchema).get

    // When
    val result = AppStateSchemaMigrations.migrateToLatest(unicodeYaml)

    // Then - migration should work with Unicode
    result.shouldBe(defined)
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
    val schema = AppStateSchemaHelper.createInitialSchema()
    val modifiedSchema = schema.copy(
      engine_state = schema.engine_state.copy(
        project_description = schema.engine_state.project_description.copy(
          reference = "MODIFIED-TEST-001"
        )
      )
    )

    // When - perform round-trip
    val yaml = AppStateSchemaHelper.encodeToYaml(modifiedSchema).get
    val decodedSchema = AppStateSchemaHelper.decodeFromYaml(yaml).get

    // Then - modification should be preserved
    decodedSchema.engine_state.project_description.reference.shouldBe("MODIFIED-TEST-001")
  }

  behavior of "SchemaMigrations V4 to V5 migration (version bumping)"

  /**
   * Reproduces the bug where V4→V5 migration does not bump version fields.
   *
   * Strategy: take a valid V5 schema (with Traditional firebox — identical between V4 and V5),
   * downgrade the version markers to 4 in the YAML, then feed to migrateToLatest.
   * The migration should produce a schema with version=5 and engine_state.version=5.
   */
  it should "bump AppStateSchema version from 4 to 5" in {
    // Given - create a valid V5 schema, encode to YAML, downgrade version markers to 4
    val v5Schema = AppStateSchemaHelper.createInitialSchema()
    val v5Yaml   = AppStateSchemaHelper.encodeToYaml(v5Schema).get
    val v4Yaml   = v5Yaml.replaceAll("version: 5", "version: 4")

    // When - migrate from V4 to latest
    val result = AppStateSchemaMigrations.migrateToLatest(v4Yaml)

    // Then - schema version should be bumped to 5
    result shouldBe defined
    result.get.version.unwrap shouldBe AppStateSchema.LATEST_VERSION
  }

  it should "bump engine_state version from 4 to 5" in {
    // Given
    val v5Schema = AppStateSchemaHelper.createInitialSchema()
    val v5Yaml   = AppStateSchemaHelper.encodeToYaml(v5Schema).get
    val v4Yaml   = v5Yaml.replaceAll("version: 5", "version: 4")

    // When
    val result = AppStateSchemaMigrations.migrateToLatest(v4Yaml)

    // Then - engine_state version should also be bumped to 5
    result shouldBe defined
    result.get.engine_state.version.unwrap shouldBe 5
  }
}