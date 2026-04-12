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

object PipesTemplateWriter:

    private val Headers: Seq[(String, String, String)] = Seq(
        ("Nom du lot", "Batch name", "texte"                                      ),
        ("Matériau", "Material", "—"                                              ),
        ("Rugosité (surcharge)", "Roughness (override)", "m"                      ),
        ("Forme intérieure", "Inner shape", "—"                                   ),
        ("Dimension 1", "Dimension 1", "m"                                        ),
        ("Dimension 2", "Dimension 2", "m"                                        ),
        ("Couche 1 : Épaisseur", "Layer 1: Thickness", "m"                        ),
        ("Couche 1 : Rth ou lambda ?", "Layer 1: Rth or lambda?", "—"             ),
        ("Couche 1 : Valeur therm.", "Layer 1: Thermal value", "m².K/W ou W/(m.K)"),
        ("Couche 2 : Épaisseur", "Layer 2: Thickness", "m"                        ),
        ("Couche 2 : Rth ou lambda ?", "Layer 2: Rth or lambda?", "—"             ),
        ("Couche 2 : Valeur therm.", "Layer 2: Thermal value", "m².K/W ou W/(m.K)"),
        ("Couche 3 : Épaisseur", "Layer 3: Thickness", "m"                        ),
        ("Couche 3 : Rth ou lambda ?", "Layer 3: Rth or lambda?", "—"             ),
        ("Couche 3 : Valeur therm.", "Layer 3: Thermal value", "m².K/W ou W/(m.K)"),
        ("Image / Photo", "Image / Photo", "—"                                    )
    )

    private val PipeExample: Seq[Option[Any]] = Seq(
        Some("POUJOULAT 200mm DPI"),
        Some("WeldedSteel"        ),
        Some(0.001                ),
        Some("Circle"             ),
        Some(0.200                ),
        None,
        Some(0.026                ),
        Some("Rth"                ),
        Some(0.44                 ),
        None,
        None,
        None,
        None,
        None,
        None,
        None
    )

    def generate(outputPath: Path): Unit =
        val wb     = new XSSFWorkbook()
        val styles = Styles.create(wb)
        val sheet  = wb.createSheet("Conduits - Pipes")

        buildPipeCasingSheet  (sheet, styles, PipeExample                   )
        buildRoughnessRefSheet(wb.createSheet("Référence matériaux"), styles)

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close               (              )

    private[templates] def buildPipeCasingSheet(
        sheet  : org.apache.poi.ss.usermodel.Sheet,
        styles : Styles.StyleBundle,
        example: Seq[Option[Any]]
    ): Unit =
        writeTableHeaders(sheet, styles, 0, 1, 2, Headers)
        writeExampleRow  (sheet, styles, 3, example      )

        setColumnWidths(sheet, Seq(30, 18, 16, 18, 14, 14, 16, 20, 22, 16, 20, 22, 16, 20, 22, 18))

        sheet.createFreezePane(0, 3)

        // Dropdowns (rows 3..99, 0-based)
        addDropdown(sheet, 3, 99, PipeCols.Material, Materials     )
        addDropdown(sheet, 3, 99, PipeCols.InnerShape, Shapes      )
        addDropdown(sheet, 3, 99, PipeCols.Layer1Type, ThermalTypes)
        addDropdown(sheet, 3, 99, PipeCols.Layer2Type, ThermalTypes)
        addDropdown(sheet, 3, 99, PipeCols.Layer3Type, ThermalTypes)

        // Comments
        addComment(
            sheet,
            2,
            PipeCols.Roughness,
            "Laisser vide pour utiliser la rugosité par défaut du matériau.\n" +
                "Leave blank to use material default roughness."
        )
        addComment(
            sheet,
            2,
            PipeCols.InnerShape,
            "Cercle : remplir Dim.1 uniquement (diamètre).\n" +
                "Carré : Dim.1 uniquement (côté).\n" +
                "Rectangle : Dim.1 = largeur a, Dim.2 = hauteur b.\n\n" +
                "Circle: fill Dim.1 only (diameter).\n" +
                "Square: Dim.1 only (side).\n" +
                "Rectangle: Dim.1 = width a, Dim.2 = height b."
        )
        addComment(
            sheet,
            2,
            PipeCols.Layer1Type,
            "Rth = résistance thermique (m².K/W)\n" +
                "lambda = conductivité thermique (W/(m.K))\n\n" +
                "Rth = thermal resistance (m².K/W)\n" +
                "lambda = thermal conductivity (W/(m.K))"
        )
        addComment(
            sheet,
            2,
            PipeCols.Image,
            "Optionnel : insérer une image du produit dans cette colonne.\n" +
                "L'image doit être insérée (Insertion > Image) et positionnée sur la ligne correspondante.\n\n" +
                "Optional: insert a product image in this column.\n" +
                "The image must be inserted (Insert > Picture) and positioned on the corresponding row."
        )

    private[templates] def buildRoughnessRefSheet(
        sheet : org.apache.poi.ss.usermodel.Sheet,
        styles: Styles.StyleBundle
    ): Unit =
        sheet.setColumnWidth(0, 22 * 256)
        sheet.setColumnWidth(1, 22 * 256)

        val titleRow = sheet.createRow(0)
        val tc       = titleRow.createCell(0)
        tc.setCellValue      ("Rugosité par défaut des matériaux / Material default roughness reference")
        tc.setCellStyle      (styles.section                                                            )
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 1)                   )

        val headerRow = sheet.createRow(1)
        val h1        = headerRow.createCell(0); h1.setCellValue("Matériau / Material"         ); h1.setCellStyle(styles.frHeader)
        val h2        = headerRow.createCell(1); h2.setCellValue("Rugosité (m) / Roughness (m)");
        h2.setCellStyle(styles.frHeader)

        for ((mat, rough), idx) <- MaterialDefaultRoughness.zipWithIndex do
            val r  = sheet.createRow(2 + idx)
            val c1 = r.createCell(0); c1.setCellValue(mat  ); c1.setCellStyle(styles.thinBorder)
            val c2 = r.createCell(1); c2.setCellValue(rough); c2.setCellStyle(styles.thinBorder)
