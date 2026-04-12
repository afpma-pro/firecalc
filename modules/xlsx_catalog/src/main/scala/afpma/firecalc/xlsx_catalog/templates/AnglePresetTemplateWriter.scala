/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import java.nio.file.Path

import afpma.firecalc.xlsx_catalog.PoiHelpers
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*
import afpma.firecalc.xlsx_catalog.templates.TemplateHelpers.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object AnglePresetTemplateWriter:

    private val Headers: Seq[(String, String, String)] = Seq(
        ("Référence",         "Reference",          "texte"),
        ("Angle",             "Angle",              "°"),
        ("Zeta (coefficient)", "Zeta (coefficient)", "sans unité"),
        ("Image / Photo",     "Image / Photo",      "—"),
    )

    private val Example: Seq[Option[Any]] = Seq(
        Some("Coude 90° R=1D / 90° Bend R=1D"),
        Some(90.0), Some(1.13), None,
    )

    def generate(outputPath: Path): Unit =
        val wb = new XSSFWorkbook()
        val styles = Styles.create(wb)
        val sheet = wb.createSheet("Angles - Angle Presets")

        writeTableHeaders(sheet, styles, 0, 1, 2, Headers)
        writeExampleRow(sheet, styles, 3, Example)

        setColumnWidths(sheet, Seq(38, 14, 20, 18))

        sheet.createFreezePane(0, 3)

        addComment(sheet, 2, AnglePresetCols.Image,
            "Optionnel : insérer une image du produit dans cette colonne.\n" +
            "L'image doit être insérée (Insertion > Image) et positionnée sur la ligne correspondante.\n\n" +
            "Optional: insert a product image in this column.\n" +
            "The image must be inserted (Insert > Picture) and positioned on the corresponding row.")

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close()
