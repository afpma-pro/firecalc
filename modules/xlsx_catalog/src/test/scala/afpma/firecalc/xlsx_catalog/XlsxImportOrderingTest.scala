/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog

import java.nio.file.Files

import munit.FunSuite

import org.apache.poi.xssf.usermodel.XSSFWorkbook

import afpma.firecalc.catalog.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.xlsx_catalog.importers.FlowResXlsxImporter
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.FlowResCols

class XlsxImportOrderingTest extends FunSuite:

    // Deliberately reverse-alphabetical to detect any accidental sorting
    private val orderedNames = Seq("Entry-E", "Entry-D", "Entry-C", "Entry-B", "Entry-A")
    private val zetaValues   = Seq(0.5, 1.0, 1.5, 2.0, 2.5)

    /** Create a minimal flow-resistance xlsx with entries in a known order. */
    private def createFlowResXlsx(): java.nio.file.Path =
        val wb    = new XSSFWorkbook()
        val sheet = wb.createSheet("Résistances - Flow Resist.")

        // Header rows (FR=0, EN=1, units=2) — importer skips rows 0..2
        for i <- 0 to 2 do sheet.createRow(i)

        // Data rows starting at row 3
        for (name, zeta, idx) <- orderedNames.zip(zetaValues).zipWithIndex.map((nz, i) => (nz._1, nz._2, i)) do
            val row = sheet.createRow(3 + idx)
            row.createCell(FlowResCols.Name).setCellValue(name)
            row.createCell(FlowResCols.Zeta).setCellValue(zeta)

        val tempFile = Files.createTempFile("flow-res-ordering-test-", ".xlsx")
        PoiHelpers.saveWorkbook(wb, tempFile)
        wb.close               (            )
        tempFile

    test("FlowResXlsxImporter preserves row ordering from xlsx"):
        val xlsxPath = createFlowResXlsx()
        try
            val entries = FlowResXlsxImporter.read(xlsxPath)
            assertEquals(entries.size, orderedNames.size, "Expected same number of entries as xlsx rows" )
            assertEquals(entries.map(_.name), orderedNames, "Entry ordering must match xlsx row ordering")
        finally Files.deleteIfExists(xlsxPath)

    test("full import pipeline preserves ordering through YAML round-trip"):
        import CatalogCategoryInstances.given

        val xlsxPath = createFlowResXlsx()
        try
            val entries = FlowResXlsxImporter.read(xlsxPath)

            // Build CatalogFile and serialize to YAML
            val builder = CatalogSections.Builder()
            builder.add(entries)
            val catalogFile = CatalogFile(
                catalog_version = CatalogMigrations.CURRENT_VERSION,
                catalog_name    = Map("fr" -> "Test ordre", "en" -> "Ordering test"),
                sections        = builder.build
            )
            val yaml        = CatalogWriter.toYaml(catalogFile)

            // Parse back from YAML
            val parsed = CatalogParser.parse(yaml)
            assert(parsed.isRight, s"YAML round-trip failed: $parsed")
            val roundTripped = parsed.toOption.get.entriesFor[FlowResistanceCatalogEntry]

            assertEquals(roundTripped.size, orderedNames.size, "Round-trip must preserve entry count"     )
            assertEquals(roundTripped.map(_.name), orderedNames, "Round-trip must preserve entry ordering")
        finally Files.deleteIfExists(xlsxPath)
