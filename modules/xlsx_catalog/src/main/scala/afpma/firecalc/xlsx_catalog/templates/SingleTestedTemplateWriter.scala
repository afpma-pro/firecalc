/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

import java.nio.file.Path

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.Firebox_V3.TestStandard

import afpma.firecalc.xlsx_catalog.PoiHelpers
import afpma.firecalc.xlsx_catalog.templates.CatalogConstants.*
import afpma.firecalc.xlsx_catalog.templates.TemplateHelpers.*
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object SingleTestedTemplateWriter:

    /** Generate an empty template with example values. */
    def generate(outputPath: Path): Unit =
        writeWorkbook(outputPath, None)

    /** Export a SingleTested firebox to a filled xlsx file. */
    def write(firebox: Firebox.SingleTested, outputPath: Path): Unit =
        writeWorkbook(outputPath, Some(firebox))

    private def writeWorkbook(outputPath: Path, data: Option[Firebox.SingleTested]): Unit =
        val wb = new XSSFWorkbook()
        val styles = Styles.create(wb)

        val mainSheet = wb.createSheet("Foyer testé - Tested Firebox")
        buildMainSheet(mainSheet, styles, data)
        EmissionsSheetHelper.buildEmissionsSheet(
            wb.createSheet("Émissions - Emissions"),
            styles,
            data.map(_.emissions_values)
        )

        // Embed image if present (export flow)
        data.flatMap(_.image).foreach: dataUri =>
            PoiHelpers.embedDataUriImage(wb, mainSheet, dataUri, SingleTestedRows.PelletsBurnDur + 3, 0)

        PoiHelpers.saveWorkbook(wb, outputPath)
        wb.close()

    // ---- Sheet 1: Single-tested firebox main data (vertical form) ----

    private def buildMainSheet(sheet: Sheet, styles: Styles.StyleBundle, data: Option[Firebox.SingleTested]): Unit =
        import SingleTestedRows.*
        setColumnWidths(sheet, Seq(22, 22, 12, 20))

        // Value helpers: use real data if exporting, example values if generating template
        def v[A](real: Firebox.SingleTested => A, example: A): Some[A] =
            Some(data.map(real).getOrElse(example))
        def vOpt[A](real: Firebox.SingleTested => Option[A]): Option[A] =
            data.flatMap(real)

        // Title
        val titleRow = sheet.createRow(Title)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("FICHE FOYER TESTÉ / TESTED FIREBOX DATA SHEET")
        titleCell.setCellStyle(styles.title)
        sheet.addMergedRegion(new CellRangeAddress(Title, Title, 0, 3))

        // ── Identification ──
        writeSection(sheet, styles, SectionId, "Identification", 4)
        writeFormField(sheet, styles, Reference, "Référence", "Reference", "texte", v(_.reference, "MON-FOYER-001"))

        val toaStr: Firebox.SingleTested => String = fb =>
            fb.type_of_appliance match
                case TypeOfAppliance.WoodLogs => "WoodLogs"
                case TypeOfAppliance.Pellets  => "Pellets"
        writeFormField(sheet, styles, TypeOfApplianceRow, "Type d'appareil", "Type of appliance", "—", v(toaStr, "WoodLogs"))
        addDropdown(sheet, TypeOfApplianceRow, TypeOfApplianceRow, ValueCol, TypeOfApplianceValues)
        addComment(sheet, TypeOfApplianceRow, 0, "WoodLogs = Bûches de bois\nPellets = Granulés")

        val tsStr: Firebox.SingleTested => String = fb =>
            fb.test_standard match
                case TestStandard.EN_15250     => "EN_15250"
                case TestStandard.EN_13229     => "EN_13229"
                case TestStandard.National(_)  => "National"
        writeFormField(sheet, styles, TestStandardRow, "Norme d'essai", "Test standard", "—", v(tsStr, "EN_15250"))
        addDropdown(sheet, TestStandardRow, TestStandardRow, ValueCol, TestStandardValues)
        addComment(sheet, TestStandardRow, 0,
            "EN_15250 ou EN_13229 ou National.\nSi National, remplir le nom ci-dessous.\n\n" +
            "EN_15250 or EN_13229 or National.\nIf National, fill in the name below.")

        val natName: Option[String] = data.flatMap(_.test_standard match
            case TestStandard.National(n) => Some(n)
            case _                        => None
        )
        writeFormField(sheet, styles, NationalStdName, "Nom norme nationale", "National standard name", "texte", natName, optional = true)
        addComment(sheet, NationalStdName, 0,
            "Uniquement si norme = National.\nEx : ÖNORM B 8303\n\nOnly if standard = National.\nE.g.: ÖNORM B 8303")

        // ── Dimensions ──
        writeSection(sheet, styles, SectionDim, "Dimensions du foyer / Firebox Dimensions", 4)
        writeFormField(sheet, styles, Depth, "Profondeur du foyer", "Firebox depth", "m", v(_.firebox_depth.value, 0.39))
        writeFormField(sheet, styles, Width, "Largeur du foyer", "Firebox width", "m", v(_.firebox_width.value, 0.29))
        writeFormField(sheet, styles, Height, "Hauteur du foyer", "Firebox height", "m", v(_.firebox_height.value, 1.00))
        writeFormField(sheet, styles, AshPitHeight, "Hauteur du cendrier", "Ash pit height", "m", v(_.ash_pit_height.value, 0.05))

        // ── Glass ──
        writeSection(sheet, styles, SectionGlass, "Vitrage / Glazing", 4)
        val glassStr: Firebox.SingleTested => String = fb => if fb.is_glass_surface_ratio_below_one_fifth then "Yes" else "No"
        writeFormField(sheet, styles, GlassRatioBelow, "Ratio surface vitrée < 1/5", "Glass surface ratio < 1/5", "—", v(glassStr, "Yes"))
        addDropdown(sheet, GlassRatioBelow, GlassRatioBelow, ValueCol, BooleanYesNo)
        writeFormField(sheet, styles, GlassArea, "Surface vitrée", "Glass area", "m²", v(_.glass_area.value, 0.05))

        // ── Temperatures ──
        writeSection(sheet, styles, SectionTemp, "Températures / Temperatures", 4)
        writeFormField(sheet, styles, MeanFireboxTemp, "Température moyenne du foyer", "Mean firebox temperature", "°C",
            vOpt(_.mean_firebox_temperature.map(_.value)), optional = true)
        writeFormField(sheet, styles, TBurnout, "Température de fin de combustion", "Burnout temperature", "°C", v(_.t_burnout.value, 700.0))

        // ── Efficiency ──
        writeSection(sheet, styles, SectionEfficiency, "Rendement / Efficiency", 4)
        writeFormField(sheet, styles, EffNominal, "Rendement nominal", "Nominal efficiency", "%", v(_.efficiency_nominal.value, 75.0))
        writeFormField(sheet, styles, EffReduced, "Rendement réduit", "Reduced efficiency", "%",
            vOpt(_.efficiency_reduced.map(_.value)), optional = true)

        // ── Heat output reduced ──
        writeSection(sheet, styles, SectionHeat, "Puissance réduite / Reduced Heat Output", 4)
        val heatStr: Firebox.SingleTested => String = fb =>
            fb.heat_output_reduced match
                case HeatOutputReduced.NotDefined   => "NotDefined"
                case _: HeatOutputReduced.FromTypeTest => "FromTypeTest"
        writeFormField(sheet, styles, HeatMode, "Mode puissance réduite", "Heat output reduced mode", "—", v(heatStr, "NotDefined"))
        addDropdown(sheet, HeatMode, HeatMode, ValueCol, HeatOutputModesTestedOnly)
        addComment(sheet, HeatMode, 0,
            "NotDefined = pas défini\nFromTypeTest = d'après essai de type (remplir valeur ci-dessous)\n\n" +
            "NotDefined = not defined\nFromTypeTest = from type test (fill in power value below)")
        val heatPowerVal: Option[Double] = data.flatMap(_.heat_output_reduced match
            case HeatOutputReduced.FromTypeTest(p) => Some(p.value)
            case _                                 => None
        )
        writeFormField(sheet, styles, HeatPower, "Puissance réduite", "Reduced power value", "kW", heatPowerVal, optional = true)
        addComment(sheet, HeatPower, 0, "Uniquement si mode = FromTypeTest.\nOnly if mode = FromTypeTest.")

        // ── Fuel ──
        writeSection(sheet, styles, SectionFuel, "Combustible / Fuel", 4)
        writeFormField(sheet, styles, MinFuelMass, "Masse combustible min", "Min. fuel mass", "kg",
            vOpt(_.minimum_fuel_mass.map(_.value)), optional = true)
        writeFormField(sheet, styles, MaxFuelMass, "Masse combustible max", "Max. fuel mass", "kg", v(_.maximum_fuel_mass.value, 10.0))

        // ── Air-fuel ratios ──
        writeSection(sheet, styles, SectionAirFuel, "Ratio air/combustible / Air-Fuel Ratio", 4)
        writeFormField(sheet, styles, AirFuelNominal, "Ratio air/combustible nominal", "Air-fuel ratio nominal", "—",
            v(_.air_fuel_ratio_nominal.value, 4.0))
        writeFormField(sheet, styles, AirFuelLowest, "Ratio air/combustible minimal", "Air-fuel ratio lowest", "—",
            vOpt(_.air_fuel_ratio_lowest.map(_.value)), optional = true)

        // ── CO2 ──
        writeSection(sheet, styles, SectionCo2, "CO2", 4)
        writeFormField(sheet, styles, Co2Nominal, "CO2 sec nominal", "Nominal dry CO2", "%", v(_.co2_dry_nominal.value, 12.0))
        writeFormField(sheet, styles, Co2Lowest, "CO2 sec minimal", "Lowest dry CO2", "%",
            vOpt(_.co2_dry_lowest.map(_.value)), optional = true)

        // ── Pellets ──
        writeSection(sheet, styles, SectionPellets, "Granulés / Pellets", 4)
        writeFormField(sheet, styles, PelletsBurnDur, "Durée de combustion de la charge", "Pellets load burn duration", "min",
            vOpt(_.pellets_load_burn_duration.map(_.value)), optional = true)
        addComment(sheet, PelletsBurnDur, 0,
            "Uniquement pour les appareils à granulés.\n\nOnly for pellet appliances.")

        // ── Image ──
        writeSection(sheet, styles, PelletsBurnDur + 2, "Image / Photo (optionnel / optional)", 4)
        addComment(sheet, PelletsBurnDur + 2, 0,
            "Optionnel : insérer une photo du foyer dans cette zone.\n" +
            "Utiliser Insertion > Image. L'image sera importée automatiquement.\n\n" +
            "Optional: insert a photo of the firebox in this area.\n" +
            "Use Insert > Picture. The image will be imported automatically.")
