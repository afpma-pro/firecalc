/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.base

import org.scalacheck.Gen
import afpma.firecalc.units.coulombutils.*

trait PrimitiveGenerators:

    // Length generators - based on realistic masonry heater dimensions

    def genLengthCm(min: Double, max: Double): Gen[Length] =
        Gen.choose(min, max).map(_.cm)

    def genLengthMm(min: Double, max: Double): Gen[Length] =
        Gen.choose(min, max).map(_.mm)

    def genLengthM(min: Double, max: Double): Gen[Length] =
        Gen.choose(min, max).map(_.m)

    // General length categories
    def genSmallLength: Gen[Length] = genLengthMm(1.0, 100.0)

    def genMediumLength: Gen[Length] = genLengthCm(10.0, 100.0)

    def genLargeLength: Gen[Length] = genLengthM(0.5, 7.0)

    // Firebox dimensions from examples: 33.2cm, 54cm
    def genFireboxDimension: Gen[Length] = genLengthCm(30.0, 80.0)

    // Pipe diameters from examples: 130mm, 250mm
    def genPipeDiameter: Gen[Length] = genLengthCm(10.0, 30.0)

    // Layer thickness from examples: 2mm, 2.5cm
    def genLayerThickness: Gen[Length] = genLengthCm(0.1, 5.0)

    // Angle generators
    def genAngle: Gen[Angle] = Gen.choose(0.0, 180.0).map(_.degrees)

    def genSmallAngle: Gen[Angle] = Gen.choose(0.0, 90.0).map(_.degrees)

    def genRightAngle: Gen[Angle] = Gen.const(90.0.degrees)

    def genSixtyDegree: Gen[Angle] = Gen.const(60.0.degrees)

    // Roughness - based on EN13384 Table B.4
    def genRoughness: Gen[Roughness] = Gen.choose(1.0, 5.0).map(_.mm)

    // Dimensionless quantities
    def genDimensionless: Gen[Dimensionless] =
        Gen.choose(0.1, 5.0).map(_.unitless)

    def genZeta: Gen[Dimensionless] =
        Gen.choose(0.1, 2.0).map(_.unitless)

    def genPressureLossCoeff: Gen[Dimensionless] =
        Gen.choose(0.1, 1.0).map(_.unitless)

    // Percentage
    def genPercent: Gen[QtyD[Percent]] =
        Gen.choose(0.0, 100.0).map(_.percent)

    def genEfficiency: Gen[QtyD[Percent]] =
        Gen.choose(70.0, 92.0).map(_.percent)

    // Altitude
    def genAltitude: Gen[QtyD[Meter]] =
        Gen.choose(0.0, 1500.0).map(_.meters)

    // Mass - from examples: 10kg, 26kg
    def genMaxLoad: Gen[QtyD[Kilogram]] =
        Gen.choose(5.0, 35.0).map(_.kg)

    // Time - from examples: 8h, 12h, 24h cycles
    def genHeatingCycle: Gen[QtyD[Hour]] =
        Gen.oneOf(8.0, 12.0, 24.0).map(_.hours)

    // Thermal resistance values
    def genThermalResistance: Gen[SquareMeterKelvinPerWatt] =
        Gen.choose(0.001, 0.5).map(SquareMeterKelvinPerWatt(_))

    // Thermal conductivity values
    def genThermalConductivity: Gen[WattsPerMeterKelvin] =
        Gen.choose(0.1, 2.0).map(WattsPerMeterKelvin(_))

    // String generators for element names
    def genElementName: Gen[String] =
        for
            prefix <- Gen.oneOf(
                "section",
                "segment",
                "partie",
                "element",
                "channel"
            )
            num    <- Gen.choose(1, 20)
        yield s"$prefix-$num"

    // Section names from examples: sortie foyer, colonne ascendante, buse, etc.
    def genSectionName: Gen[String] =
        Gen.oneOf(
            "sortie foyer",
            "colonne ascendante",
            "buse",
            "etage",
            "sortie de toit",
            "descente",
            "vers colonne",
            "colonne",
            "intérieur",
            "combles",
            "extérieur",
            "element terminal",
            "allez banc",
            "demi tour banc",
            "retour banc",
            "virage 90 deg",
            "horizontal",
            "vertical",
            "connector",
            "chimney",
            "flue"
        )

end PrimitiveGenerators
