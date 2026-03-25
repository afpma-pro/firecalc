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
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*

object FireboxXlsxImporter:

    def read(path: Path): Firebox.Door15aFirebox_Catalog =
        val wb = openWorkbook(path)
        try
            val mainSheet = wb.getSheet("Foyer - Firebox")
            val pressureSheet = wb.getSheet("Pertes de charge")
            val emissionsSheet = wb.getSheet("Émissions - Emissions")

            require(mainSheet != null, "Sheet 'Foyer - Firebox' not found")
            require(pressureSheet != null, "Sheet 'Pertes de charge' not found")
            require(emissionsSheet != null, "Sheet 'Émissions - Emissions' not found")

            val image = readFirstPicture(wb, wb.getSheetIndex(mainSheet))
            readMainSheet(mainSheet, pressureSheet, emissionsSheet, image)
        finally wb.close()

    private def readMainSheet(
        main: org.apache.poi.ss.usermodel.Sheet,
        pressure: org.apache.poi.ss.usermodel.Sheet,
        emissions: org.apache.poi.ss.usermodel.Sheet,
        image: Option[String],
    ): Firebox.Door15aFirebox_Catalog =
        import FireboxRows.*

        def str(row: Int): Option[String] = readFormValue(main, row, ValueCol, readString)
        def dbl(row: Int): Option[Double] = readFormValue(main, row, ValueCol, readDouble)
        def reqStr(row: Int, name: String): String =
            str(row).getOrElse(throw IllegalArgumentException(s"Required field '$name' is empty"))
        def reqDbl(row: Int, name: String): Double =
            dbl(row).getOrElse(throw IllegalArgumentException(s"Required field '$name' is empty"))

        val airShapes: List[PipeShape] = List(
            (AirShape1, AirDim1_1, AirDim2_1),
            (AirShape2, AirDim1_2, AirDim2_2),
            (AirShape3, AirDim1_3, AirDim2_3),
        ).flatMap { (shapeRow, dim1Row, dim2Row) =>
            str(shapeRow).flatMap { shapeName =>
                PipesXlsxImporter.parseShape(shapeName, dbl(dim1Row), dbl(dim2Row))
            }
        }
        require(airShapes.nonEmpty, "At least one expected air intake pipe shape must be defined")

        val heatMode = str(HeatMode).getOrElse("NotDefined") match
            case "NotDefined"     => HeatOutputReduced.NotDefined
            case "HalfOfNominal"  => HeatOutputReduced.HalfOfNominal.makeWithoutValue
            case "FromTypeTest"   =>
                val power = reqDbl(HeatPower, "reduced power value")
                HeatOutputReduced.FromTypeTest(power.withUnit[Kilo * Watt])
            case other => throw IllegalArgumentException(s"Unknown heat output mode: $other")

        Firebox.Door15aFirebox_Catalog(
            reference                  = reqStr(Reference, "reference"),
            firebox_depth              = reqDbl(Depth, "firebox depth").withUnit[Meter],
            firebox_width              = reqDbl(Width, "firebox width").withUnit[Meter],
            firebox_height             = reqDbl(Height, "firebox height").withUnit[Meter],
            load_size_nominal          = dbl(NominalLoad).map(_.withUnit[Kilogram]),
            sb                         = reqDbl(Sb, "SB slot width").withUnit[Centimeter],
            sb_min                     = dbl(SbMin).map(_.withUnit[Centimeter]),
            sb_max                     = dbl(SbMax).map(_.withUnit[Centimeter]),
            mb_min                     = dbl(MbMin).map(_.withUnit[Kilogram]),
            mb_max                     = dbl(MbMax).map(_.withUnit[Kilogram]),
            pressure_loss_table_raw    = readPressureLossTable(pressure),
            expectedAirIntakePipeShapes = airShapes,
            actualAirIntakePipeShape   = airShapes.head,
            co2_dry_nominal            = reqDbl(Co2Nominal, "CO2 dry nominal").withUnit[Percent],
            co2_dry_lowest             = dbl(Co2Lowest).map(_.withUnit[Percent]),
            emissions_values           = readEmissions(emissions),
            glass_area                 = reqDbl(GlassArea, "glass area").withUnit[(Meter ^ 2)],
            height_of_lowest_opening   = reqDbl(LowestOpen, "height of lowest opening").withUnit[Meter],
            heat_output_reduced        = heatMode,
            image                      = image,
        )

    private def readPressureLossTable(sheet: org.apache.poi.ss.usermodel.Sheet): String =
        import PressureRows.*
        val headerRow = sheet.getRow(HeaderRow)
        if headerRow == null then
            throw IllegalArgumentException("Pressure loss table: header row (row 3) is missing")

        // Read SB column headers
        val sbValues = (SbFirstCol to SbLastCol).flatMap: col =>
            readDouble(headerRow, col).orElse(readString(headerRow, col).flatMap(_.toDoubleOption))
        if sbValues.isEmpty then
            throw IllegalArgumentException(
                "Pressure loss table: no valid SB values found in header row (row 3, columns B-I). " +
                    "Replace template placeholders ('sB 1', 'sB 2', ...) with numeric SB values in cm (e.g., 1.6, 2.4, 3.2, 4.0).",
            )

        val sb = new StringBuilder()
        sb.append("mb_in_kg/sb_in_cm")
        sbValues.foreach(v => sb.append(s"\t$v"))
        sb.append("\n")

        for mb <- MbStart to MbEnd do
            val rowIdx = FirstDataRow + (mb - MbStart)
            val row = sheet.getRow(rowIdx)
            sb.append(mb.toString)
            for col <- SbFirstCol to (SbFirstCol + sbValues.size - 1) do
                val value =
                    if row != null then
                        readDouble(row, col).map(v => if v == v.floor then v.toInt.toString else v.toString).getOrElse("")
                    else ""
                sb.append(s"\t$value")
            sb.append("\n")

        sb.toString.trim

    private[importers] def readEmissions(sheet: org.apache.poi.ss.usermodel.Sheet): EmissionsAndEfficiencyValues_DTO =
        import EmissionsRows.*

        def str(row: Int, col: Int): Option[String] = readFormValue(sheet, row, col, readString)
        def dbl(row: Int, col: Int): Option[Double] = readFormValue(sheet, row, col, readDouble)

        val fireboxName = str(FireboxName, ValueCol).getOrElse("")
        val accrBody = str(AccrBody, ValueCol).getOrElse("")

        // Read test reports (up to 6)
        val reports = (0 until MaxReports).flatMap: i =>
            val nameRow = FirstReport + i * 2
            val dateRow = nameRow + 1
            for name <- str(nameRow, ValueCol)
            yield TestReport(name, str(dateRow, ValueCol).getOrElse(""))
        .toList

        def readEmissionRow(rowIdx: Int, pollutant: PolluantName): TestEmissionValue_DTO =
            TestEmissionValue_DTO(
                polluant_name = pollutant,
                valueO = dbl(rowIdx, 1).map(_.withUnit[Milli * Gram / (Meter ^ 3)]),
                test_method = str(rowIdx, 2).getOrElse(""),
                o2ref = dbl(rowIdx, 3).map(_.withUnit[Percent]).getOrElse(13.0.withUnit[Percent]),
            )

        EmissionsAndEfficiencyValues_DTO(
            firebox_name = fireboxName,
            accredited_or_notified_body = accrBody,
            test_reports = reports,
            emissions_values = EmissionValues_DTO(
                co   = readEmissionRow(CoRow, PolluantName.CO),
                dust = readEmissionRow(DustRow, PolluantName.Dust),
                ogc  = readEmissionRow(OgcRow, PolluantName.OGC),
                nox  = readEmissionRow(NoxRow, PolluantName.NOx),
            ),
        )
