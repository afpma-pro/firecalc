/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import java.nio.file.Path

import afpma.firecalc.xlsx_catalog.PoiHelpers
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object CasingsTemplateWriter:

    private val CasingExample: Seq[Option[Any]] = Seq(
        Some("Boisseau terre cuite 20x20"), None, None, Some("Rectangle"), Some(0.20), Some(0.20),
        Some(0.115), Some("Rth"), Some(0.12),
        None, None, None,
        None, None, None,
        None,
    )

    def generate(outputPath: Path): Unit =
        val wb = new XSSFWorkbook()
        val styles = Styles.create(wb)
        val sheet = wb.createSheet("Boisseaux - Casings")

        PipesTemplateWriter.buildPipeCasingSheet(sheet, styles, CasingExample)
        PipesTemplateWriter.buildRoughnessRefSheet(wb.createSheet("Référence matériaux"), styles)

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close()
