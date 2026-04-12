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
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.FlowResCols

object FlowResXlsxImporter:

    def read(path: Path): Seq[FlowResistanceCatalogEntry] =
        val wb = openWorkbook(path)
        try
            val sheet    = wb.getSheetAt(0)
            val pictures = readPicturesByRow(wb, 0, FlowResCols.Image)
            val rows     = (3 to sheet.getLastRowNum).flatMap: rowIdx =>
                Option(sheet.getRow(rowIdx)).flatMap(parseRow(_, pictures.get(rowIdx)))
            rows
        finally wb.close()

    private def parseRow(
        row  : org.apache.poi.ss.usermodel.Row,
        image: Option[String]
    ): Option[FlowResistanceCatalogEntry] =
        for
            name <- readString(row, FlowResCols.Name)
            zeta <- readDouble(row, FlowResCols.Zeta)
        yield
            val crossSection = readString(row, FlowResCols.SectionType) match
                case Some(t) if t.startsWith("Surface") =>
                    readDouble(row, FlowResCols.Area)
                        .map(a => SomeLeft(a.withUnit[(Centimeter ^ 2)]))
                        .getOrElse(NoneOfEither)
                case Some(t) if t.startsWith("Forme")   =>
                    val shapeName = readString(row, FlowResCols.Shape)
                    val dim1      = readDouble(row, FlowResCols.Dim1)
                    val dim2      = readDouble(row, FlowResCols.Dim2)
                    shapeName
                        .flatMap(PipesXlsxImporter.parseShape(_, dim1, dim2))
                        .map(SomeRight(_))
                        .getOrElse(NoneOfEither)
                case _                                  => NoneOfEither

            FlowResistanceCatalogEntry         (
                name          = name,
                zeta          = zeta.withUnit[1],
                cross_section = crossSection,
                image         = image
            )
