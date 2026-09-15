/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.dto.all.*

import munit.FunSuite

/**
 * The `.fcalc-db` route for `pressure_loss_table_raw`.
 *
 * Import heals whatever the old codec wrote (real separators, one escape layer, or the
 * doubled-backslash runs of a re-saved catalog), and export stores the table with printable
 * separators and no escapes — the form that reads back byte-identically.
 */
class CatalogPressureLossStorageSuite extends FunSuite:

    private val canonicalTable = "mb_in_kg/sb_in_cm\t1.6\t2.4\n10\t4\t3\n25\t22\t20"

    private def catalogYaml(table: String): String =
        s"""|catalog_version: 4
            |catalog_name:
            |  en: "Storage Form Test"
            |door_15a_fireboxes:
            |  - reference: "STORAGE-001"
            |    firebox_depth: { value: "0.39", unit: "meter" }
            |    firebox_width: { value: "0.29", unit: "meter" }
            |    firebox_height: { value: "1.0", unit: "meter" }
            |    sb: { value: "1.6", unit: "centimeter" }
            |    pressure_loss_table_raw: "$table"
            |    expectedAirIntakePipeShapes:
            |      - Circle: { diameter: { value: "0.2", unit: "meter" } }
            |    actualAirIntakePipeShape:
            |      Circle: { diameter: { value: "0.2", unit: "meter" } }
            |    co2_dry_nominal: { value: "12.0", unit: "percent" }
            |    emissions_values:
            |      firebox_name: "STORAGE-001"
            |      accredited_or_notified_body: "TestBody"
            |      emissions_values:
            |        co:   { polluant_name: "CO",   valueO: 1154.0, test_method: "", o2ref: { value: "13.0", unit: "percent" } }
            |        dust: { polluant_name: "Dust", valueO: 25.0,   test_method: "", o2ref: { value: "13.0", unit: "percent" } }
            |        ogc:  { polluant_name: "OGC",  valueO: 40.0,   test_method: "", o2ref: { value: "13.0", unit: "percent" } }
            |        nox:  { polluant_name: "NOx",  valueO: 119.0,  test_method: "", o2ref: { value: "13.0", unit: "percent" } }
            |    glass_area: { value: "0.05", unit: "meter^2" }
            |    height_of_lowest_opening: { value: "0.05", unit: "meter" }
            |""".stripMargin

    private def parse(yaml: String): CatalogFile =
        CatalogParser.parse(yaml) match
            case Right(file) => file
            case Left(err)   => fail(s"parse failed: $err")

    private def tableOf(file: CatalogFile): String =
        import CatalogCategoryInstances.given
        val entries = file.entriesFor[Firebox.Door15aFirebox_Catalog]
        assertEquals(entries.size, 1)
        entries.head.pressure_loss_table_raw

    private def tableScalar(yaml: String): String =
        yaml.linesIterator
            .find(_.contains("pressure_loss_table_raw"))
            .map(_.trim)
            .getOrElse(fail("field missing from export"))

    /** The table as a file carries it: separators escaped, `backslashes` deep. */
    private def escapeLayer(table: String, backslashes: Int): String =
        val run = "\\" * backslashes
        table.replace("\t", run + "t").replace("\n", run + "n")

    private val storedScalar = "pressure_loss_table_raw: \"mb_in_kg/sb_in_cm;1.6;2.4|10;4;3|25;22;20\""

    test("import restores the canonical table from a legacy escape layer"):
        assertEquals(tableOf(parse(catalogYaml(escapeLayer(canonicalTable, 1)))), canonicalTable)

    test("import heals the doubled runs of a catalog re-saved by the old codec"):
        assertEquals(tableOf(parse(catalogYaml(escapeLayer(canonicalTable, 2)))), canonicalTable)

    test("export stores the table escape-free with printable separators"):
        // Files carry the table escaped: the printer never writes a raw newline inside the scalar,
        // and YAML would fold one to a space.
        val exported = CatalogWriter.toYaml(parse(catalogYaml(escapeLayer(canonicalTable, 1))))
        val scalar   = tableScalar(exported)

        assert      (scalar.contains(";"), scalar  )
        assert      (!scalar.contains("\\"), scalar)
        assertEquals(scalar, storedScalar          )

    test("export is a fixed point across an export/import/export cycle"):
        val file     = parse(catalogYaml(escapeLayer(canonicalTable, 1)))
        val exported = CatalogWriter.toYaml(file)
        val reParsed = parse(exported)

        assertEquals(tableOf(reParsed), canonicalTable       )
        assertEquals(CatalogWriter.toYaml(reParsed), exported)

    test("the full writer/parser route round-trips a free-text field carrying a backslash"):
        // Issue 01/02 acceptance for the .fcalc-db route specifically: `reference` (a plain
        // free-text field) holds a Windows path with real backslashes. Writer -> parser must
        // return it byte-exact (the old codec would have doubled it), and the export must be a
        // fixed point. CatalogWriter/Parser now run the guard + repair passes.
        val pathRef = "C:\\Users\\poele"
        val yamlIn  = catalogYaml(canonicalTable).replace("STORAGE-001", pathRef)
        val file    = parse(yamlIn)

        import CatalogCategoryInstances.given
        val entryRef = file.entriesFor[Firebox.Door15aFirebox_Catalog].head.reference
        assertEquals(entryRef, pathRef)

        val exported = CatalogWriter.toYaml(file)
        val reParsed = parse(exported)
        assertEquals(reParsed.entriesFor[Firebox.Door15aFirebox_Catalog].head.reference, pathRef)
        assertEquals(CatalogWriter.toYaml(reParsed), exported                                   )

end CatalogPressureLossStorageSuite
