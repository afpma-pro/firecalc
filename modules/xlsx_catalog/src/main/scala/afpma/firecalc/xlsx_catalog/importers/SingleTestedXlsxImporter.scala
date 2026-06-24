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

object SingleTestedXlsxImporter:

    def read(path: Path): Firebox.SingleTested =
        val wb = openWorkbook(path)
        try
            val mainSheet      = wb.getSheet("Foyer testé - Tested Firebox")
            val emissionsSheet = wb.getSheet("Émissions - Emissions")

            require(mainSheet != null, "Sheet 'Foyer testé - Tested Firebox' not found")
            require(emissionsSheet != null, "Sheet 'Émissions - Emissions' not found"  )

            val image = readFirstPicture(wb, wb.getSheetIndex(mainSheet))
            readMainSheet(mainSheet, emissionsSheet, image)
        finally wb.close()

    private def readMainSheet(
        main     : org.apache.poi.ss.usermodel.Sheet,
        emissions: org.apache.poi.ss.usermodel.Sheet,
        image    : Option[String]
    ): Firebox.SingleTested =
        import SingleTestedRows.*

        def str(row: Int): Option[String] = readFormValue(main, row, ValueCol, readString)
        def dbl(row: Int): Option[Double] = readFormValue(main, row, ValueCol, readDouble)
        def reqStr(row: Int, name: String): String =
            str(row).getOrElse(throw IllegalArgumentException(s"Required field '$name' is empty"))
        def reqDbl(row: Int, name: String): Double =
            dbl(row).getOrElse(throw IllegalArgumentException(s"Required field '$name' is empty"))

        val typeOfAppliance = str(TypeOfApplianceRow).getOrElse("WoodLogs") match
            case "WoodLogs" => TypeOfAppliance.WoodLogs
            case "Pellets"  => TypeOfAppliance.Pellets
            case other      =>
                throw IllegalArgumentException(s"Unknown type of appliance: $other (expected: WoodLogs, Pellets)")

        val testStandard = str(TestStandardRow) match
            case Some("EN_15250") => Firebox.TestStandard.EN_15250
            case Some("EN_13229") => Firebox.TestStandard.EN_13229
            case Some("National") =>
                val name = reqStr(NationalStdName, "national standard name")
                Firebox.TestStandard.National(name)
            case Some(other)      =>
                throw IllegalArgumentException(
                    s"Unknown test standard: $other (expected: EN_15250, EN_13229, National)"
                )
            case None             => throw IllegalArgumentException("Required field 'test standard' is empty")

        val glassRatioBelow = str(GlassRatioBelow).getOrElse("No") match
            case "Yes" => true
            case "No"  => false
            case other => throw IllegalArgumentException(s"Unknown glass ratio value: $other (expected: Yes, No)")

        val heatMode: HeatOutputReduced.NotDefined_Or_Tested = str(HeatMode).getOrElse("NotDefined") match
            case "NotDefined"   => HeatOutputReduced.NotDefined
            case "FromTypeTest" =>
                val power = reqDbl(HeatPower, "reduced power value")
                HeatOutputReduced.FromTypeTest(power.withUnit[Kilo * Watt])
            case other          =>
                throw IllegalArgumentException(s"Unknown heat output mode: $other (expected: NotDefined, FromTypeTest)")

        Firebox.SingleTested                             (
            reference                              = reqStr(Reference, "reference"),
            type_of_appliance                      = typeOfAppliance,
            test_standard                          = testStandard,
            firebox_depth                          = reqDbl(Depth, "firebox depth").withUnit[Meter],
            firebox_width                          = reqDbl(Width, "firebox width").withUnit[Meter],
            firebox_height                         = reqDbl(Height, "firebox height").withUnit[Meter],
            ash_pit_height                         = reqDbl(AshPitHeight, "ash pit height").withUnit[Meter],
            is_glass_surface_ratio_below_one_fifth = glassRatioBelow,
            glass_area                             = reqDbl(GlassArea, "glass area").withUnit[(Meter ^ 2)],
            mean_firebox_temperature               = dbl(MeanFireboxTemp).map(_.degreesCelsius),
            t_burnout                              = Some(reqDbl(TBurnout, "burnout temperature").degreesCelsius),
            efficiency_nominal                     = reqDbl(EffNominal, "nominal efficiency").withUnit[Percent],
            efficiency_reduced                     = dbl(EffReduced).map(_.withUnit[Percent]),
            heat_output_reduced                    = heatMode,
            minimum_fuel_mass                      = dbl(MinFuelMass).map(_.withUnit[Kilogram]),
            maximum_fuel_mass                      = reqDbl(MaxFuelMass, "max fuel mass").withUnit[Kilogram],
            air_fuel_ratio_nominal                 = reqDbl(AirFuelNominal, "air-fuel ratio nominal").withUnit[1],
            air_fuel_ratio_lowest                  = dbl(AirFuelLowest).map(_.withUnit[1]),
            co2_dry_nominal                        = reqDbl(Co2Nominal, "CO2 dry nominal").withUnit[Percent],
            co2_dry_lowest                         = dbl(Co2Lowest).map(_.withUnit[Percent]),
            pellets_load_burn_duration             = dbl(PelletsBurnDur).map(_.withUnit[Minute]),
            emissions_values                       = FireboxXlsxImporter.readEmissions(emissions),
            image                                  = image
        )
