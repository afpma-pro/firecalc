/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import afpma.firecalc.ui.models.schema.v1.AppStateSchema_V1
import afpma.firecalc.ui.models.AppStateSchemaHelper
import scala.util.{Success, Failure}

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
    val result = SchemaMigrations.migrateToLatest(rawData)

    // Then
    result shouldBe None
  }

  it should "return None for whitespace-only data" in {
    // Given - only whitespace
    val rawData = "   \n  \t  \n   "

    // When
    val result = SchemaMigrations.migrateToLatest(rawData)

    // Then
    result.shouldBe(None)
  }

  it should "decode V1 data directly without migration" in {
    // Given - create a valid V1 schema and encode it
    val originalSchema = AppStateSchemaHelper.createInitialSchema()
    val yaml = AppStateSchemaHelper.encodeToYaml(originalSchema).get

    // When
    val result = SchemaMigrations.migrateToLatest(yaml)

    // Then
    result.shouldBe(defined)
    result.get.version.unwrap.shouldBe(1)
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
    val result = SchemaMigrations.migrateToLatest(legacyYaml)

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
    val result = SchemaMigrations.migrateToLatest(futureYaml)

    // Then
    // Should return None as version 999 is not supported
    result.shouldBe(None)
  }

  it should "handle malformed YAML gracefully" in {
    // Given - completely invalid YAML
    val malformedYaml = "{ invalid: yaml: structure: [[[[ }}"

    // When
    val result = SchemaMigrations.migrateToLatest(malformedYaml)

    // Then
    result.shouldBe(None)
  }

  it should "handle YAML with missing required fields by returning default schema" in {
    // Given - YAML with version but missing required schema fields
    val incompleteYaml =
      """version: 1
        |engine_state:
        |  locale: fr
        |""".stripMargin

    // When
    val result = SchemaMigrations.migrateToLatest(incompleteYaml)

    // Then
    // AppStateSchemaHelper.decodeFromYaml is fault-tolerant and returns default schema
    result.shouldBe(defined)
    // The returned schema should be the default initial schema
    val defaultSchema = AppStateSchemaHelper.createInitialSchema()
    result.get.version.unwrap.shouldBe(defaultSchema.version.unwrap)
  }

  behavior of "SchemaMigrations.validateSchema"

  it should "validate a correctly formed schema" in {
    // Given - create a valid initial schema
    val schema = AppStateSchemaHelper.createInitialSchema()

    // When
    val isValid = SchemaMigrations.validateSchema(schema)

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
    val result = SchemaMigrations.migrateToLatest(emptyString)

    // Then
    result.shouldBe(None)
  }

  it should "handle YAML with only whitespace" in {
    // Given
    val whitespaceYaml = "    \n    \n    "

    // When
    val result = SchemaMigrations.migrateToLatest(whitespaceYaml)

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
    val result = SchemaMigrations.migrateToLatest(nullYaml)

    // Then
    result.shouldBe(None)
  }

  it should "handle YAML with version as string by falling back to default schema" in {
    // Given
    val stringVersionYaml =
      """version: "1"
        |engine_state:
        |  locale: fr
        |""".stripMargin

    // When
    val result = SchemaMigrations.migrateToLatest(stringVersionYaml)

    // Then
    // detectVersion will fail (returns None), but decodeFromYaml is fault-tolerant
    // and returns default schema
    result.shouldBe(defined)
    val defaultSchema = AppStateSchemaHelper.createInitialSchema()
    result.get.version.unwrap.shouldBe(defaultSchema.version.unwrap)
  }

  it should "handle YAML with version as float" in {
    // Given
    val floatVersionYaml =
      """version: 1.5
        |engine_state:
        |  locale: fr
        |""".stripMargin

    // When
    val result = SchemaMigrations.migrateToLatest(floatVersionYaml)

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
    val migrationResult = SchemaMigrations.migrateToLatest(negativeVersionYaml)

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
    val migrationResult = SchemaMigrations.migrateToLatest(zeroVersionYaml)

    // Then
    // migrateToLatest will return None as 0 is not a supported version
    migrationResult.shouldBe(None)
  }

  it should "handle very large YAML documents" in {
    // Given - create a valid schema (which is reasonably large)
    val schema = AppStateSchemaHelper.createInitialSchema()
    val largeYaml = AppStateSchemaHelper.encodeToYaml(schema).get

    // When
    val result = SchemaMigrations.migrateToLatest(largeYaml)

    // Then
    result.shouldBe(defined)
    result.get.version.unwrap.shouldBe(1)
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
    val result = SchemaMigrations.migrateToLatest(specialCharsYaml)

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
    val result = SchemaMigrations.migrateToLatest(unicodeYaml)

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
    val isValid = SchemaMigrations.validateSchema(schema)

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
}