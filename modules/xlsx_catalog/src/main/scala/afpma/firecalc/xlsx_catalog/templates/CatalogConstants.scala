/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.xlsx_catalog.templates

/** Shared constants for template generation and import parsing. */
object CatalogConstants:

    val Materials: Seq[String] = Seq(
        "WeldedSteel", "Glass", "Plastic", "Aluminium", "ClayFlueLiners",
        "Bricks", "SolderedMetal", "Concrete", "Fibrociment", "Masonry", "CorrugatedMetal",
    )

    val MaterialDefaultRoughness: Seq[(String, Double)] = Seq(
        "WeldedSteel"    -> 0.001,
        "Glass"          -> 0.001,
        "Plastic"        -> 0.001,
        "Aluminium"      -> 0.001,
        "ClayFlueLiners" -> 0.0015,
        "Bricks"         -> 0.005,
        "SolderedMetal"  -> 0.002,
        "Concrete"       -> 0.003,
        "Fibrociment"    -> 0.003,
        "Masonry"        -> 0.005,
        "CorrugatedMetal" -> 0.005,
    )

    val Shapes: Seq[String] = Seq("Circle", "Square", "Rectangle")

    val ThermalTypes: Seq[String] = Seq("Rth", "lambda")

    val CrossSectionTypes: Seq[String] = Seq("Aucune / None", "Surface / Area", "Forme / Shape")

    val HeatOutputModes: Seq[String] = Seq("NotDefined", "HalfOfNominal", "FromTypeTest")

    // SingleTested uses NotDefined_Or_Tested (no HalfOfNominal)
    val HeatOutputModesTestedOnly: Seq[String] = Seq("NotDefined", "FromTypeTest")

    val TypeOfApplianceValues: Seq[String] = Seq("WoodLogs", "Pellets")

    val TestStandardValues: Seq[String] = Seq("EN_15250", "EN_13229", "National")

    val BooleanYesNo: Seq[String] = Seq("Yes", "No")

    // Column indices for pipe/casing tabular sheets (0-based)
    object PipeCols:
        val BatchName   = 0
        val Material    = 1
        val Roughness   = 2
        val InnerShape  = 3
        val Dim1        = 4
        val Dim2        = 5
        val Layer1Thick = 6
        val Layer1Type  = 7
        val Layer1Value = 8
        val Layer2Thick = 9
        val Layer2Type  = 10
        val Layer2Value = 11
        val Layer3Thick = 12
        val Layer3Type  = 13
        val Layer3Value = 14

    // Column indices for flow resistance tabular sheet (0-based)
    object FlowResCols:
        val Name        = 0
        val Zeta        = 1
        val SectionType = 2
        val Area        = 3
        val Shape       = 4
        val Dim1        = 5
        val Dim2        = 6

    // Row indices for firebox form sheet (0-based)
    object FireboxRows:
        val Title       = 0
        val SectionId   = 2
        val Reference   = 3
        val SectionDim  = 5
        val Depth       = 6
        val Width       = 7
        val Height      = 8
        val SectionComb = 10
        val NominalLoad = 11
        val Sb          = 12
        val SbMin       = 13
        val SbMax       = 14
        val MbMin       = 15
        val MbMax       = 16
        val SectionAir  = 18
        val AirShape1   = 19
        val AirDim1_1   = 20
        val AirDim2_1   = 21
        val AirShape2   = 22
        val AirDim1_2   = 23
        val AirDim2_2   = 24
        val AirShape3   = 25
        val AirDim1_3   = 26
        val AirDim2_3   = 27
        val SectionCo2  = 29
        val Co2Nominal  = 30
        val Co2Lowest   = 31
        val GlassArea   = 32
        val LowestOpen  = 33
        val SectionHeat = 35
        val HeatMode    = 36
        val HeatPower   = 37
        val ValueCol    = 3 // Column D for values

    // Row indices for pressure loss sheet (0-based)
    object PressureRows:
        val Title       = 0
        val Subtitle    = 1
        val HeaderRow   = 2
        val Instruction = 3
        val FirstDataRow = 4  // mB = 10
        val LastDataRow  = 19 // mB = 25
        val MbStart     = 10
        val MbEnd       = 25
        val SbFirstCol  = 1  // Column B
        val SbLastCol   = 8  // Column I

    // Row indices for emissions sheet (0-based)
    object EmissionsRows:
        val Title       = 0
        val SectionId   = 2
        val FireboxName = 3
        val AccrBody    = 4
        val SectionRep  = 6
        val FirstReport = 7  // Report 1 name at row 7, date at row 8, etc.
        val MaxReports  = 6
        val SectionEmis = 20 // after 6 reports * 2 rows + gap
        val EmisHeader  = 21
        val CoRow       = 22
        val DustRow     = 23
        val OgcRow      = 24
        val NoxRow      = 25
        val ValueCol    = 3

    // Row indices for single-tested firebox form sheet (0-based)
    object SingleTestedRows:
        val Title              = 0
        val SectionId          = 2
        val Reference          = 3
        val TypeOfApplianceRow = 4
        val TestStandardRow    = 5
        val NationalStdName    = 6
        val SectionDim         = 8
        val Depth              = 9
        val Width              = 10
        val Height             = 11
        val AshPitHeight       = 12
        val SectionGlass       = 14
        val GlassRatioBelow    = 15
        val GlassArea          = 16
        val SectionTemp        = 18
        val MeanFireboxTemp    = 19
        val TBurnout           = 20
        val SectionEfficiency  = 22
        val EffNominal         = 23
        val EffReduced         = 24
        val SectionHeat        = 26
        val HeatMode           = 27
        val HeatPower          = 28
        val SectionFuel        = 30
        val MinFuelMass        = 31
        val MaxFuelMass        = 32
        val SectionAirFuel     = 34
        val AirFuelNominal     = 35
        val AirFuelLowest      = 36
        val SectionCo2         = 38
        val Co2Nominal         = 39
        val Co2Lowest          = 40
        val SectionPellets     = 42
        val PelletsBurnDur     = 43
        val ValueCol           = 3 // Column D for values
