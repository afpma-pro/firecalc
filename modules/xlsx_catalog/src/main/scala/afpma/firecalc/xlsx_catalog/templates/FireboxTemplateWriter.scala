/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import java.nio.file.Path

import afpma.firecalc.xlsx_catalog.PoiHelpers
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*
import afpma.firecalc.xlsx_catalog.templates.TemplateHelpers.*
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object FireboxTemplateWriter:

    def generate(outputPath: Path): Unit =
        val wb     = new XSSFWorkbook()
        val styles = Styles.create(wb)

        buildMainSheet                          (wb.createSheet("Foyer - Firebox"), styles      )
        buildPressureLossSheet                  (wb.createSheet("Pertes de charge"), styles     )
        EmissionsSheetHelper.buildEmissionsSheet(wb.createSheet("Émissions - Emissions"), styles)

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close               (              )

    // ---- Sheet 1: Firebox main data (vertical form) ----

    private def buildMainSheet(sheet: Sheet, styles: Styles.StyleBundle): Unit =
        import FireboxRows.*
        setColumnWidths(sheet, Seq(22, 22, 12, 20))

        // Title
        val titleRow  = sheet.createRow(Title)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("FICHE FOYER 15a / 15a FIREBOX DATA SHEET")
        titleCell.setCellStyle(styles.title                              )
        sheet.addMergedRegion (new CellRangeAddress(Title, Title, 0, 3)  )

        writeSection  (sheet, styles, SectionId, "Identification", 4                                     )
        writeFormField(sheet, styles, Reference, "Référence", "Reference", "texte", Some("MON-FOYER-001"))

        writeSection  (sheet, styles, SectionDim, "Dimensions du foyer / Firebox Dimensions", 4     )
        writeFormField(sheet, styles, Depth, "Profondeur du foyer", "Firebox depth", "m", Some(0.39))
        writeFormField(sheet, styles, Width, "Largeur du foyer", "Firebox width", "m", Some(0.29)   )
        writeFormField(sheet, styles, Height, "Hauteur du foyer", "Firebox height", "m", Some(1.00) )

        writeSection  (sheet, styles, SectionComb, "Paramètres de combustion / Combustion Parameters", 4          )
        writeFormField(sheet, styles, NominalLoad, "Charge nominale", "Nominal load size", "kg", optional = true  )
        addComment    (sheet, NominalLoad, 0, "Optionnel. Charge nominale testée.\nOptional. Tested nominal load.")
        writeFormField(sheet, styles, Sb, "Largeur fente SB", "SB slot width", "cm", Some(1.6)                    )
        writeFormField(sheet, styles, SbMin, "SB min", "Min. SB", "cm", Some(1.6), optional               = true  )
        writeFormField(sheet, styles, SbMax, "SB max", "Max. SB", "cm", Some(4.0), optional               = true  )
        writeFormField(
            sheet,
            styles,
            MbMin,
            "Masse combustible min",
            "Min. fuel mass",
            "kg",
            Some(10.0),
            optional = true
        )
        writeFormField(
            sheet,
            styles,
            MbMax,
            "Masse combustible max",
            "Max. fuel mass",
            "kg",
            Some(25.0),
            optional = true
        )

        writeSection(sheet, styles, SectionAir, "Formes du conduit d'air attendues / Expected Air Intake Shapes", 4)

        // Shape 1 (required)
        writeFormField(sheet, styles, AirShape1, "Forme attendue 1", "Expected shape 1", "—", Some("Circle"))
        addDropdown   (sheet, AirShape1, AirShape1, ValueCol, Shapes                                        )
        addComment    (
            sheet,
            AirShape1,
            0,
            "Forme 1 obligatoire, formes 2 et 3 optionnelles.\n" +
                "Cercle : remplir Dim.1 (diamètre). Carré : Dim.1 (côté). Rectangle : Dim.1 + Dim.2.\n\n" +
                "Shape 1 required, shapes 2 and 3 optional.\n" +
                "Circle: fill Dim.1 (diameter). Square: Dim.1 (side). Rectangle: both dimensions."
        )
        writeFormField(sheet, styles, AirDim1_1, "Dimension 1", "Dimension 1", "m", Some(0.20)              )
        writeFormField(sheet, styles, AirDim2_1, "Dimension 2", "Dimension 2", "m", optional = true         )

        // Shape 2 (optional)
        writeFormField(sheet, styles, AirShape2, "Forme attendue 2", "Expected shape 2", "—", optional = true)
        addDropdown   (sheet, AirShape2, AirShape2, ValueCol, Shapes                                         )
        writeFormField(sheet, styles, AirDim1_2, "Dimension 1", "Dimension 1", "m", optional           = true)
        writeFormField(sheet, styles, AirDim2_2, "Dimension 2", "Dimension 2", "m", optional           = true)

        // Shape 3 (optional)
        writeFormField(sheet, styles, AirShape3, "Forme attendue 3", "Expected shape 3", "—", optional = true)
        addDropdown   (sheet, AirShape3, AirShape3, ValueCol, Shapes                                         )
        writeFormField(sheet, styles, AirDim1_3, "Dimension 1", "Dimension 1", "m", optional           = true)
        writeFormField(sheet, styles, AirDim2_3, "Dimension 2", "Dimension 2", "m", optional           = true)

        writeSection  (sheet, styles, SectionCo2, "CO2 et vitrage / CO2 and Glazing", 4                   )
        writeFormField(sheet, styles, Co2Nominal, "CO2 sec nominal", "Nominal dry CO2", "%", Some(12.0)   )
        writeFormField(sheet, styles, Co2Lowest, "CO2 sec minimal", "Lowest dry CO2", "%", optional = true)
        writeFormField(sheet, styles, GlassArea, "Surface vitrée", "Glass area", "m²", Some(0.05)         )
        writeFormField(
            sheet,
            styles,
            LowestOpen,
            "Hauteur plus basse ouverture",
            "Height of lowest opening",
            "m",
            Some(0.05)
        )

        writeSection  (sheet, styles, SectionHeat, "Puissance réduite / Reduced Heat Output", 4                  )
        writeFormField(
            sheet,
            styles,
            HeatMode,
            "Mode puissance réduite",
            "Heat output reduced mode",
            "—",
            Some("NotDefined")
        )
        addDropdown   (sheet, HeatMode, HeatMode, ValueCol, HeatOutputModes                                      )
        addComment    (
            sheet,
            HeatMode,
            0,
            "NotDefined = pas défini\n" +
                "HalfOfNominal = moitié de la puissance nominale\n" +
                "FromTypeTest = d'après essai de type (remplir valeur ci-dessous)\n\n" +
                "NotDefined = not defined\n" +
                "HalfOfNominal = half of nominal power\n" +
                "FromTypeTest = from type test (fill in power value below)"
        )
        writeFormField(sheet, styles, HeatPower, "Puissance réduite", "Reduced power value", "W", optional = true)
        addComment    (sheet, HeatPower, 0, "Uniquement si mode = FromTypeTest.\nOnly if mode = FromTypeTest."   )

        // ── Image ──
        writeSection(sheet, styles, HeatPower + 2, "Image / Photo (optionnel / optional)", 4)
        addComment  (
            sheet,
            HeatPower + 2,
            0,
            "Optionnel : insérer une photo du foyer dans cette zone.\n" +
                "Utiliser Insertion > Image. L'image sera importée automatiquement.\n\n" +
                "Optional: insert a photo of the firebox in this area.\n" +
                "Use Insert > Picture. The image will be imported automatically."
        )

    // ---- Sheet 2: Pressure loss table (16x9 grid) ----

    private def buildPressureLossSheet(sheet: Sheet, styles: Styles.StyleBundle): Unit =
        import PressureRows.*
        setColumnWidths(sheet, Seq(18, 12, 12, 12, 12, 12, 12, 12, 12))

        // Title
        val titleRow = sheet.createRow(Title)
        val tc       = titleRow.createCell(0)
        tc.setCellValue      ("TABLE DE PERTE DE CHARGE / PRESSURE LOSS TABLE")
        tc.setCellStyle      (styles.title                                    )
        sheet.addMergedRegion(new CellRangeAddress(Title, Title, 0, 8)        )

        // Subtitle
        val subRow = sheet.createRow(Subtitle)
        val sc     = subRow.createCell(0)
        sc.setCellValue      ("Valeurs en Pascal / Values in Pascal"        )
        sc.setCellStyle      (styles.example                                )
        sheet.addMergedRegion(new CellRangeAddress(Subtitle, Subtitle, 0, 8))

        // Grid header row
        val headerRow = sheet.createRow(HeaderRow)
        val corner    = headerRow.createCell(0)
        corner.setCellValue("mB (kg) \\ sB (cm)")
        corner.setCellStyle(styles.frHeader     )

        // SB column headers (yellow, user fills)
        for col <- SbFirstCol to SbLastCol do
            val c = headerRow.createCell(col)
            c.setCellValue(s"sB ${col - SbFirstCol + 1}")
            c.setCellStyle(styles.yellowFill            )

        addComment(
            sheet,
            HeaderRow,
            SbFirstCol,
            "Remplacez 'sB 1', 'sB 2', etc. par les valeurs SB en cm.\n" +
                "Ex : 1.6, 2.4, 3.2, 4.0\n\n" +
                "Replace 'sB 1', 'sB 2', etc. with SB values in cm.\n" +
                "E.g.: 1.6, 2.4, 3.2, 4.0"
        )

        // Instruction row
        val instrRow = sheet.createRow(Instruction)
        val ic       = instrRow.createCell(0)
        ic.setCellValue      (
            "Remplir les en-têtes SB (ligne 3, colonnes B-I) et les valeurs de perte de charge en Pa ci-dessous. " +
                "/ Fill SB headers (row 3, columns B-I) and pressure loss values in Pa below."
        )
        ic.setCellStyle      (styles.example                                      )
        sheet.addMergedRegion(new CellRangeAddress(Instruction, Instruction, 0, 8))

        // MB data rows (10..25 kg)
        for mb <- MbStart to MbEnd do
            val rowIdx = FirstDataRow + (mb - MbStart)
            val row    = sheet.createRow(rowIdx)
            val mbCell = row.createCell(0)
            mbCell.setCellValue(mb.toDouble      )
            mbCell.setCellStyle(styles.lockedGray)

            for col <- SbFirstCol to SbLastCol do
                val dataCell = row.createCell(col)
                dataCell.setCellStyle(styles.centeredBorder)

        sheet.createFreezePane(1, FirstDataRow)

    // Emissions sheet is now built by EmissionsSheetHelper
