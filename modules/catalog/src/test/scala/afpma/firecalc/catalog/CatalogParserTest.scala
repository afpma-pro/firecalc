/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import munit.FunSuite
import afpma.firecalc.dto.all.*

class CatalogParserTest extends FunSuite:

    val minimalYaml = """
      |catalog_version: 4
      |catalog_name:
      |  fr: "Catalogue Test"
      |  en: "Test Catalog"
      |""".stripMargin

    val yamlWithEmptySections = """
      |catalog_version: 4
      |catalog_name:
      |  fr: "Catalogue Test"
      |  en: "Test Catalog"
      |door_15a_fireboxes: []
      |pipe_presets: []
      |""".stripMargin

    test("parse minimal catalog (no sections)"):
        val result = CatalogParser.parse(minimalYaml)
        assert(result.isRight, s"Expected Right but got: $result")
        val file = result.toOption.get
        assertEquals(file.sections, Map.empty[String, Seq[Any]])
        assertEquals(file.catalog_name.get("fr"), Some("Catalogue Test"))

    test("parse catalog with empty sections"):
        val result = CatalogParser.parse(yamlWithEmptySections)
        assert(result.isRight, s"Expected Right but got: $result")
        val file = result.toOption.get
        assertEquals(file.catalog_name.get("en"), Some("Test Catalog"))

    test("parse catalog with version too new"):
        val yaml = """
          |catalog_version: 999
          |catalog_name:
          |  fr: "Futur"
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft)
        assert(
            result.left.toOption.get.isInstanceOf[CatalogParseError.VersionTooNew],
            s"Expected VersionTooNew but got: ${result.left.toOption.get}"
        )

    test("parse catalog with missing version"):
        val yaml = """
          |catalog_name:
          |  fr: "Sans version"
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft)
        assertEquals(result.left.toOption.get, CatalogParseError.MissingVersion)

    test("parse catalog with invalid YAML"):
        val result = CatalogParser.parse("{ unclosed: [")
        assert(result.isLeft)
        assert(
            result.left.toOption.get.isInstanceOf[CatalogParseError.InvalidFile],
            s"Expected InvalidFile but got: ${result.left.toOption.get}"
        )

    test("parse catalog with version too old returns MigrationFailed"):
        val yaml = """
          |catalog_version: 1
          |catalog_name:
          |  fr: "Ancien"
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft)
        assert(
            result.left.toOption.get.isInstanceOf[CatalogParseError.MigrationFailed],
            s"Expected MigrationFailed but got: ${result.left.toOption.get}"
        )

    test("entriesFor returns empty seq for missing category"):
        val result = CatalogParser.parse(minimalYaml)
        assert(result.isRight)
        val file = result.toOption.get
        import CatalogCategoryInstances.given
        assertEquals(file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch].length, 0)

    test("parse catalog with malformed section entry returns DecodeError"):
        val yaml = """
          |catalog_version: 4
          |catalog_name:
          |  fr: "Test"
          |door_15a_fireboxes:
          |  - this_is_not_a_valid_entry: true
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft, s"Expected Left but got: $result")
        assert(
            result.left.toOption.get.isInstanceOf[CatalogParseError.DecodeError],
            s"Expected DecodeError but got: ${result.left.toOption.get}"
        )
        val err = result.left.toOption.get.asInstanceOf[CatalogParseError.DecodeError]
        assertEquals(err.category, "door_15a_fireboxes")

    test("parse sample catalog file"):
        val stream = getClass.getResourceAsStream("/sample-catalog.fcalc-db")
        assert(stream != null, "sample-catalog.fcalc-db resource not found")
        val yaml = scala.io.Source.fromInputStream(stream).mkString
        val result = CatalogParser.parse(yaml)
        assert(result.isRight, s"Expected Right but got: $result")
        val file = result.toOption.get
        assertEquals(file.catalog_version.unwrap, 4)
        assert(file.catalog_name.nonEmpty)
        import CatalogCategoryInstances.given
        val fireboxes = file.entriesFor[Firebox.Door15aFirebox_Catalog]
        assert(fireboxes.nonEmpty, "Expected at least one Door15aFirebox_Catalog entry")
        assertEquals(fireboxes.head.reference, "Door15aFirebox_Catalog_Example")
        val pipes = file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch]
        assert(pipes.nonEmpty, "Expected at least one pipe preset entry")
