/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import java.nio.file.Path

import org.apache.poi.xssf.usermodel.XSSFWorkbook

import afpma.firecalc.xlsx_catalog.PoiHelpers
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*
import afpma.firecalc.xlsx_catalog.templates.TemplateHelpers.*

object FlowResTemplateWriter:

    private val Headers: Seq[(String, String, String)] = Seq(
        ("Nom",                "Name",               "texte"),
        ("Zeta (coefficient)", "Zeta (coefficient)",  "sans unité"),
        ("Type de section",    "Cross-section type",  "—"),
        ("Surface de section", "Cross-section area",  "cm²"),
        ("Forme de section",   "Cross-section shape", "—"),
        ("Dimension 1",        "Dimension 1",         "m"),
        ("Dimension 2",        "Dimension 2",         "m"),
        ("Image / Photo",      "Image / Photo",       "—"),
    )

    private val Example: Seq[Option[Any]] = Seq(
        Some("Grillage pare-étincelles / Wire mesh screen"),
        Some(0.61), Some("Aucune / None"), None, None, None, None, None,
    )

    def generate(outputPath: Path): Unit =
        val wb = new XSSFWorkbook()
        val styles = Styles.create(wb)
        val sheet = wb.createSheet("Résistances - Flow Resist.")

        writeTableHeaders(sheet, styles, 0, 1, 2, Headers)
        writeExampleRow(sheet, styles, 3, Example)

        setColumnWidths(sheet, Seq(38, 20, 24, 20, 22, 14, 14, 18))

        sheet.createFreezePane(0, 3)

        addDropdown(sheet, 3, 99, FlowResCols.SectionType, CrossSectionTypes)
        addDropdown(sheet, 3, 99, FlowResCols.Shape, Shapes)

        addComment(sheet, 2, FlowResCols.SectionType,
            "Aucune = pas de section transversale spécifiée\n" +
            "Surface = donner l'aire en cm²\n" +
            "Forme = donner la géométrie (forme + dimensions)\n\n" +
            "None = no cross-section specified\n" +
            "Area = specify area in cm²\n" +
            "Shape = specify geometry (shape + dimensions)")
        addComment(sheet, 2, FlowResCols.Image,
            "Optionnel : insérer une image du produit dans cette colonne.\n" +
            "L'image doit être insérée (Insertion > Image) et positionnée sur la ligne correspondante.\n\n" +
            "Optional: insert a product image in this column.\n" +
            "The image must be inserted (Insert > Picture) and positioned on the corresponding row.")

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close()
