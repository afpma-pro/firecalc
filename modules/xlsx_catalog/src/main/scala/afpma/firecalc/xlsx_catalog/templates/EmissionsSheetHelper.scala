/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import afpma.firecalc.dto.all.*

import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*
import afpma.firecalc.xlsx_catalog.templates.TemplateHelpers.*
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress

/** Shared emissions sheet builder used by both FireboxTemplateWriter and SingleTestedTemplateWriter. */
object EmissionsSheetHelper:

    /** Build the emissions sheet. If `data` is Some, fills real values; if None, fills examples. */
    def buildEmissionsSheet(
        sheet: Sheet,
        styles: Styles.StyleBundle,
        data: Option[EmissionsAndEfficiencyValues_DTO] = None,
    ): Unit =
        import EmissionsRows.*
        setColumnWidths(sheet, Seq(22, 22, 20, 20))

        // Title
        val titleRow = sheet.createRow(Title)
        val tc = titleRow.createCell(0)
        tc.setCellValue("ÉMISSIONS ET RAPPORTS D'ESSAI / EMISSIONS AND TEST REPORTS")
        tc.setCellStyle(styles.title)
        sheet.addMergedRegion(new CellRangeAddress(Title, Title, 0, 3))

        // Identification
        writeSection(sheet, styles, SectionId, "Identification", 4)
        writeFormField(sheet, styles, FireboxName, "Nom du foyer", "Firebox name", "texte",
            Some(data.map(_.firebox_name).getOrElse("15a firebox")))
        writeFormField(sheet, styles, AccrBody, "Organisme accrédité", "Accredited/notified body", "texte",
            Some(data.map(_.accredited_or_notified_body).getOrElse("Test Laboratory - TU Vienna")))

        // Test Reports
        writeSection(sheet, styles, SectionRep, "Rapports d'essai / Test Reports (max 6)", 4)

        val exampleReports = Seq(
            (Some("PL-19075-1-P"), Some("9.6.2020")),
            (Some("PL-19075-2-P"), Some("9.6.2020")),
            (None, None), (None, None), (None, None), (None, None),
        )

        for i <- 0 until MaxReports do
            val num = i + 1
            val nameRow = FirstReport + i * 2
            val dateRow = nameRow + 1

            val (nameVal, dateVal) = data match
                case Some(d) if i < d.test_reports.size =>
                    val r = d.test_reports(i)
                    (Some(r.name), Some(r.date))
                case _ =>
                    val (en, ed) = exampleReports.lift(i).getOrElse((None, None))
                    (en, ed)

            writeFormField(sheet, styles, nameRow,
                s"Rapport $num - Nom", s"Report $num - Name", "texte", nameVal, optional = i >= 1)
            writeFormField(sheet, styles, dateRow,
                s"Rapport $num - Date", s"Report $num - Date", "texte", dateVal, optional = i >= 1)

        // Emissions sub-table
        writeSection(sheet, styles, SectionEmis, "Valeurs d'émissions / Emission Values", 4)

        val emisHeaderRow = sheet.createRow(EmisHeader)
        val subHeaders = Seq(
            "Polluant\n(Pollutant)", "Valeur\n(Value mg/m³)", "Méthode d'essai\n(Test method)", "O2 réf.\n(O2 ref %)",
        )
        for ((h, colIdx) <- subHeaders.zipWithIndex) do
            val c = emisHeaderRow.createCell(colIdx)
            c.setCellValue(h)
            c.setCellStyle(styles.frHeader)

        val pollutantAccessors: Seq[(String, EmissionValues_DTO => TestEmissionValue_DTO, Double, Int)] = Seq(
            ("CO",               _.co,   1154.0, CoRow),
            ("Poussières / Dust", _.dust, 25.0,   DustRow),
            ("COV / OGC",        _.ogc,  40.0,   OgcRow),
            ("NOx",              _.nox,  119.0,  NoxRow),
        )

        for ((label, accessor, exampleVal, rowIdx) <- pollutantAccessors) do
            val row = sheet.createRow(rowIdx)
            val nameCell = row.createCell(0)
            nameCell.setCellValue(label)
            nameCell.setCellStyle(styles.lockedGray)

            val emission = data.map(d => accessor(d.emissions_values))

            val valCell = row.createCell(1)
            emission.flatMap(_.valueO.map(_.value)) match
                case Some(v)              => valCell.setCellValue(v)
                case None if data.isEmpty => valCell.setCellValue(exampleVal); valCell.setCellStyle(styles.example)
                case _                    => ()

            val methodCell = row.createCell(2)
            emission.map(_.test_method).filter(_.nonEmpty).foreach(methodCell.setCellValue)
            methodCell.setCellStyle(styles.thinBorder)

            val o2Cell = row.createCell(3)
            emission.map(_.o2ref.value) match
                case Some(v) => o2Cell.setCellValue(v)
                case None    => o2Cell.setCellValue(13.0); if data.isEmpty then o2Cell.setCellStyle(styles.example)

        addComment(sheet, CoRow, 1,
            "Valeur d'émission mesurée en mg/m³.\nLaisser vide si non mesuré.\n\n" +
            "Measured emission value in mg/m³.\nLeave blank if not measured.")
