/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.importers

import java.nio.file.Path

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.xlsx_catalog.PoiHelpers.*
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.AnglePresetCols

object AnglePresetXlsxImporter:

    def read(path: Path): Seq[AnglePresetCatalogEntry] =
        val wb = openWorkbook(path)
        try
            val sheet = wb.getSheetAt(0)
            val pictures = readPicturesByRow(wb, 0, AnglePresetCols.Image)
            val rows = (3 to sheet.getLastRowNum).flatMap: rowIdx =>
                Option(sheet.getRow(rowIdx)).flatMap(parseRow(_, pictures.get(rowIdx)))
            rows
        finally wb.close()

    private def parseRow(row: org.apache.poi.ss.usermodel.Row, image: Option[String]): Option[AnglePresetCatalogEntry] =
        for
            reference <- readString(row, AnglePresetCols.Reference)
            angle     <- readDouble(row, AnglePresetCols.Angle)
            zeta      <- readDouble(row, AnglePresetCols.Zeta)
        yield
            AnglePresetCatalogEntry(
                reference = reference,
                angle     = angle.withUnit[Degree],
                zeta      = zeta.withUnit[1],
                image     = image,
            )
