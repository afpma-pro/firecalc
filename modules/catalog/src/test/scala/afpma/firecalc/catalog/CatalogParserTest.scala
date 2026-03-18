/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import munit.FunSuite
import afpma.firecalc.dto.all.*

import scala.util.Using

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
        assert(file.sections.isEmpty)
        assertEquals(file.catalog_name.get("fr"), Some("Catalogue Test"))

    test("parse catalog with empty sections"):
        val result = CatalogParser.parse(yamlWithEmptySections)
        assert(result.isRight, s"Expected Right but got: $result")
        val file = result.toOption.get
        assertEquals(file.catalog_name.get("en"), Some("Test Catalog"))
        import CatalogCategoryInstances.given
        assertEquals(file.entriesFor[Firebox.Door15aFirebox_Catalog], Seq.empty)
        assertEquals(file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch], Seq.empty)

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

    test("parse catalog with non-array section returns DecodeError"):
        val yaml = """
          |catalog_version: 4
          |catalog_name:
          |  fr: "Test"
          |door_15a_fireboxes: "not an array"
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft, s"Expected Left but got: $result")
        val err = result.left.toOption.get
        assert(err.isInstanceOf[CatalogParseError.DecodeError], s"Expected DecodeError but got: $err")
        assertEquals(err.asInstanceOf[CatalogParseError.DecodeError].category, "door_15a_fireboxes")

    test("parse catalog with malformed catalog_name returns InvalidFile"):
        val yaml = """
          |catalog_version: 4
          |catalog_name: "just a string"
          |""".stripMargin
        val result = CatalogParser.parse(yaml)
        assert(result.isLeft, s"Expected Left but got: $result")
        assert(
            result.left.toOption.get.isInstanceOf[CatalogParseError.InvalidFile],
            s"Expected InvalidFile but got: ${result.left.toOption.get}"
        )

    test("parse sample catalog file"):
        val yaml = Using.resource(getClass.getResourceAsStream("/sample-catalog.fcalc-db")): stream =>
            scala.io.Source.fromInputStream(stream).mkString
        val result = CatalogParser.parse(yaml)
        assert(result.isRight, s"Expected Right but got: $result")
        val file = result.toOption.get
        assertEquals(file.catalog_version.unwrap, 4)
        assert(file.catalog_name.nonEmpty)
        import CatalogCategoryInstances.given
        val fireboxes = file.entriesFor[Firebox.Door15aFirebox_Catalog]
        assert(fireboxes.nonEmpty, "Expected at least one Door15aFirebox_Catalog entry")
        assertEquals(fireboxes.head.reference, "Door15aFirebox_Catalog_Example")
        val singleTested = file.entriesFor[Firebox.SingleTested]
        assert(singleTested.nonEmpty, "Expected at least one SingleTested entry")
        val st = singleTested.head
        assertEquals(st.reference, "SingleTested_Example")
        assert(st.efficiency_reduced.isDefined, "efficiency_reduced should be decoded")
        assert(st.minimum_fuel_mass.isDefined, "minimum_fuel_mass should be decoded")
        assert(st.air_fuel_ratio_lowest.isDefined, "air_fuel_ratio_lowest should be decoded")
        assert(st.co2_dry_lowest.isDefined, "co2_dry_lowest should be decoded")
        assert(st.pellets_load_burn_duration.isDefined, "pellets_load_burn_duration should be decoded")
        val pipes = file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch]
        assert(pipes.nonEmpty, "Expected at least one pipe preset entry")
        val casings = file.entriesFor[CasingPreset]
        assert(casings.nonEmpty, "Expected at least one casing preset entry")
        assertEquals(casings.head.unwrap.batch_name, "Boisseau terre cuite 20x20")
        val flowResistances = file.entriesFor[FlowResistanceCatalogEntry]
        assert(flowResistances.nonEmpty, "Expected at least one flow resistance preset entry")
        assertEquals(flowResistances.head.name, "Wire mesh screen")
