/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.units

import cats.{Show, Monoid}
import cats.data.*

import algebra.instances.all.given
import coulomb.policy.standard.given

import afpma.firecalc.i18n.I

import coulomb.*
import coulomb.syntax.*
import coulomb.units.accepted.{*, given}
import coulomb.units.si.{*, given}
import coulomb.units.si.prefixes.{*, given}
import coulomb.units.time.{*, given}
import coulomb.units.temperature.{*, given}
import coulomb.units.mksa.{*, given}
import coulomb.units.temperature.{*, given}
import coulomb.units.us.{*, given}
import coulomb.conversion.*
import coulomb.define.*

import magnolia1.Transl

object coulombutils:
    export conversions.*
    export monoids.given
    export shows.defaults.given
    export ShowUnit.given

    // export coulomb.{`*`, `/`, `^`, showUnit, Quantity}
    // export coulomb.syntax.withUnit
    // export coulomb.Quantity.pow

    // export coulomb.policy.standard.given
    // export coulomb.ops.standard.all.{*, given}

    // export coulomb.ops.algebra.all.{*, given}

    export coulomb.units.si.{*, given}
    export coulomb.units.si.prefixes.{*, given}

    // export coulomb.rational.Rational.given_Conversion_Double_Rational

    export coulomb.units.accepted.{
        Degree,
        ctx_unit_Degree,
        Percent,
        ctx_unit_Percent,
        Centimeter,
        ctx_unit_Centimeter,
        Millimeter,
        ctx_unit_Millimeter,
        Gram,
        ctx_unit_Gram
    }

    export coulomb.units.mks.{
        Watt,
        ctx_unit_Watt,
        Joule,
        ctx_unit_Joule,
        Radian,
        ctx_unit_Radian,
    }

    export coulomb.units.mksa.{
        Pascal,
        ctx_unit_Pascal,
    }

    export coulomb.units.us.{
        Inch,
        ctx_unit_Inch,
        Foot,
        ctx_unit_Foot,
        Pound,
        ctx_unit_Pound,
        BTU,
        ctx_unit_BTU,
    }

    // final type BTU_Hour
    // given ctx_unit_BTU_Hour: DerivedUnit[
    //     BTU_Hour,
    //     BTU * Hour,
    //     "BTU.h",
    //     "BTU.h"
    // ] = DerivedUnit()

    // given UnitConversion[Double, Kilo * Watt, BTU * Hour] = (kw: Double) =>
    //     (kw.kWh.toUnit[BTU] / 1.hours).value

    export coulomb.units.time.{
        Hour, 
        ctx_unit_Hour, 
        Minute, 
        ctx_unit_Minute
    }

    export coulomb.units.temperature.{
        Celsius, 
        ctx_unit_Celsius, 
        Fahrenheit,
        ctx_unit_Fahrenheit,
        withTemperature
    }

    type QtyD[U] = Quantity[Double, U]
    type TempD[U] = Temperature[Double, U]

    given showOptQtyD: [U] => Show[QtyD[U]] => Show[Option[QtyD[U]]] =
        cats.Show.catsShowForOption[QtyD[U]]
    given showOptTempD: [U] => Show[TempD[U]] => Show[Option[TempD[U]]] =
        cats.Show.catsShowForOption[TempD[U]]

    given numericTempD: [U] => (n: Fractional[Double]) => Fractional[TempD[U]] = 
        new Fractional[TempD[U]] {

          override def toLong(x: TempD[U]): Long = n.toLong(x.value)

          override def toInt(x: TempD[U]): Int = n.toInt(x.value)

          override def plus(x: TempD[U], y: TempD[U]): TempD[U] =
            n.plus(x.value, y.value).withTemperature[U]

          override def toFloat(x: TempD[U]): Float = n.toFloat(x.value)

          override def compare(x: TempD[U], y: TempD[U]): Int = n.compare(x.value, y.value)

          override def parseString(str: String): Option[TempD[U]] = n.parseString(str).map(_.withTemperature[U])

          override def minus(x: TempD[U], y: TempD[U]): TempD[U] = 
            n.minus(x.value, y.value).withTemperature[U]

          override def fromInt(x: Int): TempD[U] = x.toDouble.withTemperature[U]

          override def negate(x: TempD[U]): TempD[U] = 
            n.negate(x.value).withTemperature[U]

          override def toDouble(x: TempD[U]): Double = x.value

          override def times(x: TempD[U], y: TempD[U]): TempD[U] = 
            n.times(x.value, y.value).withTemperature[U]

          override def div(x: TempD[U], y: TempD[U]): TempD[U] = 
            n.div(x.value, y.value).withTemperature[U]
        }
    
    given numericQtyD: [U] => (n: Fractional[Double]) => Fractional[QtyD[U]] = 
        new Fractional[QtyD[U]] {

            override def toLong(x: QtyD[U]): Long = n.toLong(x.value)

            override def toInt(x: QtyD[U]): Int = n.toInt(x.value)

            override def plus(x: QtyD[U], y: QtyD[U]): QtyD[U] =
                n.plus(x.value, y.value).withUnit[U]

            override def toFloat(x: QtyD[U]): Float = n.toFloat(x.value)

            override def compare(x: QtyD[U], y: QtyD[U]): Int = n.compare(x.value, y.value)

            override def parseString(str: String): Option[QtyD[U]] = n.parseString(str).map(_.withUnit[U])

            override def minus(x: QtyD[U], y: QtyD[U]): QtyD[U] = 
                n.minus(x.value, y.value).withUnit[U]

            override def fromInt(x: Int): QtyD[U] = x.toDouble.withUnit[U]

            override def negate(x: QtyD[U]): QtyD[U] = 
                n.negate(x.value).withUnit[U]

            override def toDouble(x: QtyD[U]): Double = x.value

            override def times(x: QtyD[U], y: QtyD[U]): QtyD[U] = 
                n.times(x.value, y.value).withUnit[U]

            override def div(x: QtyD[U], y: QtyD[U]): QtyD[U] = 
                n.div(x.value, y.value).withUnit[U]
        }

    // TempD - sorted alhabetically
    @Transl(I(_.terms.temperature_celsius))
    type TCelsius        = TempD[Celsius]
    @Transl(I(_.terms.temperature_kelvin))
    type TKelvin         = TempD[Kelvin]
    
    // QtyD - sorted alhabetically
    type Acceleration    = QtyD[Meter / (Second ^ 2)]
    type Angle           = QtyD[Degree]
    type Area            = QtyD[(Meter ^ 2)]
    type AreaInCm2       = QtyD[(Centimeter ^ 2)]
    type Density         = QtyD[Kilogram / (Meter ^ 3)] // COULOMB_MIGRATION
    type Dimensionless   = QtyD[1]
    type Duration        = QtyD[Minute]
    type Energy          = QtyD[Kilo * Watt * Hour]
    type FlowVelocity    = QtyD[Meter / Second] // COULOMB_MIGRATION: to remove
    type HeatCapacity    = QtyD[Kilo * Watt * Hour / Kilogram]
    type Length          = QtyD[Meter]
    type Mass            = QtyD[Kilogram]
    type MassFlow        = QtyD[Kilogram / Second]
    type MolarMass       = QtyD[Gram / Mole]
    type Percentage      = QtyD[Percent]
    type Power           = QtyD[Kilo * Watt]
    type Pressure        = QtyD[Pascal] // COULOMB_MIGRATION
    type Time            = QtyD[Hour] | QtyD[Minute]
    type Velocity        = QtyD[Meter / Second] // COULOMB_MIGRATION: to remove
    type Volume          = QtyD[(Meter ^ 3)]
    type VolumeFlow      = QtyD[(Meter ^ 3) / Second]

    opaque type EmissionValueU <: QtyD[Milli * Gram / (Meter ^ 3)] = QtyD[Milli * Gram / (Meter ^ 3)]
    object EmissionValueU:
        given Conversion[QtyD[Milli * Gram / (Meter ^ 3)], EmissionValueU] = identity
        extension (x: EmissionValueU) def unwrap: QtyD[Milli * Gram / (Meter ^ 3)] = x
    
    opaque type Roughness <: QtyD[Meter] = QtyD[Meter]
    object Roughness:
        given Conversion[QtyD[Meter], Roughness] = identity
        extension (r: Roughness) def unwrap: QtyD[Meter] = r

    // %.2f
    type JoulesPerKilogramKelvin = QtyD[Joule / (Kilogram * Kelvin)]
    object JoulesPerKilogramKelvin:
        def apply(d: Double): JoulesPerKilogramKelvin =
            d.withUnit[Joule / (Kilogram * Kelvin)]

    type WattsPerSquareMeterKelvin = QtyD[Watt / ((Meter ^ 2) * Kelvin)]
    object WattsPerSquareMeterKelvin:
        def apply(d: Double): WattsPerSquareMeterKelvin =
            d.withUnit[Watt / ((Meter ^ 2) * Kelvin)]

    type SquareMeterKelvinPerWatt = QtyD[(Meter ^ 2) * Kelvin / Watt]
    object SquareMeterKelvinPerWatt:
        def apply(d: Double): SquareMeterKelvinPerWatt =
            d.withUnit[(Meter ^ 2) * Kelvin / Watt]

    type WattsPerMeterKelvin = QtyD[Watt / (Meter * Kelvin)]
    object WattsPerMeterKelvin:
        def apply(d: Double): WattsPerMeterKelvin =
            d.withUnit[Watt / (Meter * Kelvin)]

    type NewtonSecondsPerSquareMeter = QtyD[Newton * Second / (Meter ^ 2)]
    object NewtonSecondsPerSquareMeter:
        def apply(d: Double): NewtonSecondsPerSquareMeter =
            d.withUnit[Newton * Second / (Meter ^ 2)]

    object conversions:

        extension (x: Area)
            def to_cm2: QtyD[(Centimeter ^ 2)] = x.toUnit[(Centimeter ^ 2)]
        extension (x: Dimensionless)
            def toPercent = (x.value * 100.0).withUnit[Percent]

        extension (x: Length)
            def to_m: QtyD[Meter] = x.toUnit[Meter]
            def to_cm: QtyD[Centimeter] = x.toUnit[Centimeter]
            def to_mm: QtyD[Millimeter] = x.toUnit[Millimeter]

        extension (x: Percentage)
            def asRatio: QtyD[1] = x.toUnit[1]
        extension (x: VolumeFlow)
            def to_m3_per_h: QtyD[(Meter ^ 3) / Hour] = x.toUnit[(Meter ^ 3) / Hour]
        extension (x: TKelvin)
            def to_degC: TCelsius = x.toUnit[Celsius]
        extension (c: TCelsius)
            def to_degK: TKelvin = c.toUnit[Kelvin]


        extension (x: Int)
            def cm                = x.toDouble.withUnit[Centimeter].toUnit[Meter]
            def cm2               = x.squareCentimeters
            def degrees           = x.toDouble.withUnit[Degree]
            def degreesCelsius    = x.toDouble.withTemperature[Celsius]
            def degreesKelvin     = x.toDouble.withTemperature[Kelvin]
            def ea                = x.toDouble.withUnit[1]
            def g_per_mol         = x.toDouble.withUnit[Gram / Mole]
            def g_per_s           = x.toDouble.withUnit[Gram / Second]
            def minutes           = x.toDouble.withUnit[Minute]
            def hours             = x.toDouble.withUnit[Hour]
            def kg                = x.toDouble.withUnit[Kilogram]
            def kg_per_m3         = x.toDouble.withUnit[Kilogram / (Meter ^ 3)]
            def kilowatts         = x.toDouble.withUnit[Kilo * Watt]
            def kJ_per_kg         = x.toDouble.withUnit[Kilo * Joule / Kilogram]
            def kW                = x.toDouble.withUnit[Kilo * Watt]
            def kWh               = x.toDouble.withUnit[Kilo * Watt * Hour]
            def kWh_per_kg        = x.toDouble.withUnit[Kilo * Watt * Hour / Kilogram]
            def m_per_s           = x.toDouble.withUnit[Meter / Second]
            def m                 = x.meters
            def m2                = x.squareMeters
            def m2_K_per_W        = SquareMeterKelvinPerWatt(x)
            def meters            = x.toDouble.withUnit[Meter]
            def metersPerSecond   = x.toDouble.withUnit[Meter / Second]
            def mg_per_Nm3: EmissionValueU = x.toDouble.withUnit[Milli * Gram / (Meter ^ 3)]
            def mm                = x.toDouble.withUnit[Millimeter].toUnit[Meter]
            def mol               = x.toDouble.withUnit[Mole]
            def pascals           = x.toDouble.withUnit[Pascal]
            def percent           = x.toDouble.withUnit[Percent]
            def seconds           = x.toDouble.withUnit[Second]
            def squareCentimeters = x.toDouble.withUnit[(Centimeter ^ 2)].toUnit[(Meter ^ 2)]
            def squareMeters      = x.toDouble.withUnit[(Meter ^ 2)]
            def unitless          = x.ea
            def watts             = x.toDouble.withUnit[Watt]
            def W_per_mK          = x.toDouble.withUnit[Watt / (Meter * Kelvin)]

        extension (x: Double)
            def cm                = x.withUnit[Centimeter].toUnit[Meter]
            def cm2               = x.squareCentimeters
            def degrees           = x.withUnit[Degree]
            def degreesCelsius    = x.withTemperature[Celsius]
            def degreesKelvin     = x.withTemperature[Kelvin]
            def ea                = x.withUnit[1]
            def g_per_mol         = x.withUnit[Gram / Mole]
            def g_per_s           = x.withUnit[Gram / Second]
            def minutes           = x.withUnit[Minute]
            def hours             = x.withUnit[Hour]
            def kg                = x.withUnit[Kilogram]
            def kg_per_m3         = x.withUnit[Kilogram / (Meter ^ 3)]
            def kilowatts         = x.withUnit[Kilo * Watt]
            def kJ_per_kg         = x.withUnit[Kilo * Joule / Kilogram]
            def kW                = x.withUnit[Kilo * Watt]
            def kWh               = x.withUnit[Kilo * Watt * Hour]
            def kWh_per_kg        = x.withUnit[Kilo * Watt * Hour / Kilogram]
            def m_per_s           = x.withUnit[Meter / Second]
            def m                 = x.meters
            def m2                = x.squareMeters
            def m2_K_per_W        = SquareMeterKelvinPerWatt(x)
            def meters            = x.withUnit[Meter]
            def metersPerSecond   = x.withUnit[Meter / Second]
            def mm                = x.withUnit[Millimeter].toUnit[Meter]
            def mg_per_Nm3: EmissionValueU = x.withUnit[Milli * Gram / (Meter ^ 3)]
            def mol               = x.withUnit[Mole]
            def pascals           = x.withUnit[Pascal]
            def percent           = x.withUnit[Percent]
            def seconds           = x.withUnit[Second]
            def squareCentimeters = x.withUnit[(Centimeter ^ 2)].toUnit[(Meter ^ 2)]
            def squareMeters      = x.withUnit[(Meter ^ 2)]
            def unitless          = x.ea
            def watts             = x.withUnit[Watt]
            def W_per_mK          = x.withUnit[Watt / (Meter * Kelvin)]

    end conversions

    object monoids:
        given monoidSumPressure: Monoid[Pressure] =
            Monoid.instance(0.0.pascals, _ + _)
        def monoidSumVNelPressure[E]: Monoid[ValidatedNel[E, Pressure]] = 
            afpma.firecalc.units.others.vnel_monoid.mkMonoidSumForVNelQtyD[E, Pascal](0.0.pascals)
    end monoids

    trait ShowUnit[U]:
        def showUnit: String
    
    object ShowUnit:
        def apply[U](using ev: ShowUnit[U]): ShowUnit[U] = ev
        
        def fromString[U](s: String): ShowUnit[U] = 
            new ShowUnit[U]:
                def showUnit: String = s

        inline def fromCoulombUnit[U] = ShowUnit.fromString[U](coulomb.showUnit[U])

        // ordered alphabetically

        // imperial
        given ShowUnit[Fahrenheit]                              = fromCoulombUnit[Fahrenheit]
        given ShowUnit[Foot]                                    = fromCoulombUnit[Foot]
        given ShowUnit[Inch]                                    = fromCoulombUnit[Inch]
        given ShowUnit[Pound]                                   = fromCoulombUnit[Pound]
        given showUnit_BTU: ShowUnit[BTU]                       = fromCoulombUnit[BTU]
        given showUnit_BTU_per_Hour: ShowUnit[BTU / Hour]       = fromCoulombUnit[BTU / Hour]
        given showUnit_SquareInch: ShowUnit[(Inch^2)]           = fromString("in²")
        given showUnit_Foot_per_Second: ShowUnit[Foot / Second] = fromString("ft/s")
        given showUnit_Inch_per_Second: ShowUnit[Inch / Second] = fromString("in/s")
        

        given ShowUnit[1]               = fromString[1]("")
        given ShowUnit[Celsius]         = fromCoulombUnit[Celsius]
        given ShowUnit[Centimeter]      = fromCoulombUnit[Centimeter]
        given ShowUnit[(Centimeter ^ 2)]= fromString("cm²")
        given ShowUnit[Degree]          = fromCoulombUnit[Degree]
        given ShowUnit[Hour]            = fromCoulombUnit[Hour]
        given ShowUnit[Kelvin]          = fromCoulombUnit[Kelvin]
        given ShowUnit[Kilogram]        = fromCoulombUnit[Kilogram]
        given ShowUnit[Kilo * Watt]     = fromString("kW")
        given ShowUnit[Meter]           = fromCoulombUnit[Meter]
        given ShowUnit[Millimeter]      = fromCoulombUnit[Millimeter]
        given ShowUnit[Mole]            = fromCoulombUnit[Mole]
        given ShowUnit[Pascal]          = fromCoulombUnit[Pascal]
        given ShowUnit[Percent]         = fromCoulombUnit[Percent]
        given ShowUnit[String]          = fromString[String]("")

        // ordered alphabetically
        given showUnit_CubicMeters_per_Second: ShowUnit[(Meter ^ 3) / Second] = 
            fromString("m³/s")
        given showUnit_CubicMeters_per_Hour: ShowUnit[(Meter ^ 3) / Hour] = 
            fromString("m³/h")
        given showUnit_Density: ShowUnit[Kilogram / (Meter ^ 3)] = 
            fromString("kg/m³")
        given showUnit_Gram_per_Second: ShowUnit[Gram / Second] = 
            fromCoulombUnit[Gram / Second]
        given showUnit_Joule_per_Kilogram: ShowUnit[Joule / Kilogram] = 
            fromCoulombUnit[Joule / Kilogram]
        given showUnit_Joule_per_KilogramKelvin: ShowUnit[Joule / (Kilogram * Kelvin)] = 
            fromCoulombUnit[Joule / (Kilogram * Kelvin)]
        given showUnit_Kilogram_per_Hour: ShowUnit[Kilogram / Hour] = 
            fromCoulombUnit[Kilogram / Hour]
        given showUnit_Kilogram_per_Second: ShowUnit[Kilogram / Second] = 
            fromCoulombUnit[Kilogram / Second]
        given showUnit_Meter_per_Second: ShowUnit[Meter / Second] = 
            fromCoulombUnit[Meter / Second]
        given showUnit_Meter_per_SecondSquared: ShowUnit[Meter / (Second ^ 2)] = 
            fromString("m/s²")
        given showUnit_MicroNewtonSecond_per_SquareMeter: ShowUnit[Micro * Newton * Second / (Meter ^ 2)] = 
            fromString("µN.s/m²")
        val showUnit_Milligram_per_NormalCubicMeter: ShowUnit[Milli * Gram / (Meter ^ 3)]= 
            fromString("mg/Nm³")
        given showUnit_NewtonSecond_per_SquareMeter: ShowUnit[Newton * Second / (Meter ^ 2)] = 
            fromString("N.s/m²")
        given showUnit_SquareMeters: ShowUnit[(Meter ^ 2)] = 
            fromString("m²")
        given showUnit_SquareMeterKelvin_per_Watt: ShowUnit[(Meter ^ 2) * Kelvin / Watt] = 
            fromString("m²K/W")
        given showUnit_Watt_per_MeterKelvin: ShowUnit[Watt / (Meter * Kelvin)] = 
            fromCoulombUnit[Watt / (Meter * Kelvin)]
        given showUnit_Watt_per_SquareMeterKelvin: ShowUnit[Watt / ((Meter ^ 2) * Kelvin)] = 
            fromString("W/(m²K)")

    extension [A: Show](a: A)
        def showP: String = Show[A].show(a)
    extension [U](q: QtyD[U])(using Show[QtyD[U]])
        def showP_IfNonZero: String = if (q.value == 0.0) "-" else Show[QtyD[U]].show(q)


    object shows:

        def mkShowForQtyD[U: ShowUnit](
            prettyFmt: String = "%.2f"
        ): Show[QtyD[U]] =
            Show.show: qu =>
                val prettyVU = prettyFmt.format(qu.value)
                s"${prettyVU} ${ShowUnit[U].showUnit}"

        def mkShowPrettyForQtyD[U, PrettyU: ShowUnit](
            prettyFmt: String = "%.2f"
        )(using conv: UnitConversion[Double, U, PrettyU]): Show[QtyD[U]] =
            Show.show: qu =>
                val qpu = qu.toUnit[PrettyU]
                val prettyVU = prettyFmt.format(qpu.value)
                s"${prettyVU} ${ShowUnit[PrettyU].showUnit}"

        def mkShowForTempD[U: ShowUnit](
            prettyFmt: String = "%.1f"
        ): Show[TempD[U]] =
            Show.show: tu =>
                val prettyVU = prettyFmt.format(tu.value)
                s"${prettyVU} ${ShowUnit[U].showUnit}"

        def mkShowPrettyForTempD[U, PrettyU: ShowUnit](
            prettyFmt: String = "%.1f"
        )(using conv: DeltaUnitConversion[Double, Kelvin, U, PrettyU]): Show[TempD[U]] =
            Show.show: qu =>
                val qpu = qu.toUnit[PrettyU]
                val prettyVU = prettyFmt.format(qpu.value)
                s"${prettyVU} ${ShowUnit[PrettyU].showUnit}"

        object defaults:
            
            // TempD
            given show_Celsius: Show[TempD[Celsius]]        = mkShowPrettyForTempD[Celsius, Celsius]("%.1f")
            
            // TempD Imperial
            given show_Kelvin: Show[TempD[Kelvin]]          = mkShowPrettyForTempD[Kelvin, Kelvin]("%.1f")
            val show_Fahrenheit_0: Show[TempD[Fahrenheit]]  = mkShowPrettyForTempD[Fahrenheit, Fahrenheit]("%.0f")
            given show_Fahrenheit: Show[TempD[Fahrenheit]]  = mkShowPrettyForTempD[Fahrenheit, Fahrenheit]("%.1f")

            // QtyD for imperial
            given show_Inch: Show[QtyD[Inch]] = 
                mkShowPrettyForQtyD[Inch, Inch]("%.1f")
            given show_Foot: Show[QtyD[Foot]] = 
                mkShowPrettyForQtyD[Foot, Foot]("%.1f")
            val show_Foot_0: Show[QtyD[Foot]] = 
                mkShowPrettyForQtyD[Foot, Foot]("%.0f")
            given show_Pound: Show[QtyD[Pound]] = 
                mkShowPrettyForQtyD[Pound, Pound]("%.1f")
            val show_Pound_0: Show[QtyD[Pound]] = 
                mkShowPrettyForQtyD[Pound, Pound]("%.0f")
            given show_BTU: Show[QtyD[BTU]] = 
                mkShowPrettyForQtyD[BTU, BTU]("%.1f")
            given show_BTU_per_Hour: Show[QtyD[BTU / Hour]] = 
                mkShowPrettyForQtyD[BTU / Hour, BTU / Hour]("%.0f")
            given show_Foot_per_Second: Show[QtyD[Foot / Second]] = 
                mkShowPrettyForQtyD[Foot / Second, Foot / Second]("%.1f")
            given show_Inch_per_Second: Show[QtyD[Inch / Second]] = 
                mkShowPrettyForQtyD[Inch / Second, Inch / Second]("%.1f")

            // QtyD - ordered alphabetically
            given show_Angle: Show[QtyD[Degree]] = 
                mkShowPrettyForQtyD[Degree, Degree]("%.1f")
            given show_Centimeters: Show[QtyD[Centimeter]] = 
                mkShowPrettyForQtyD[Centimeter, Centimeter]("%.1f")
            given show_Density: Show[QtyD[Kilogram / (Meter ^ 3)]] = 
                mkShowPrettyForQtyD[Kilogram / (Meter ^ 3), Kilogram / (Meter ^ 3)]("%.3f")
            given show_Dimensionless: Show[QtyD[1]] = 
                mkShowPrettyForQtyD[1, 1]("%.3f")
            given show_EmissionValueU: Show[EmissionValueU] = Show.show: ev =>
                show_Milligram_per_Nm3.show(ev)
            given show_Gram_per_Second: Show[QtyD[Gram / Second]] = 
                mkShowPrettyForQtyD[Gram / Second, Gram / Second]("%.1f")
            given show_JoulesPerKilogramKelvin: Show[JoulesPerKilogramKelvin] = 
                mkShowForQtyD[Joule / (Kilogram * Kelvin)]("%.2f")
            given show_Kilograms: Show[QtyD[Kilogram]] = 
                mkShowPrettyForQtyD[Kilogram, Kilogram]("%.3f")
            val show_Kilograms_0: Show[QtyD[Kilogram]] = 
                mkShowPrettyForQtyD[Kilogram, Kilogram]("%.0f")
            val show_Kilograms_1: Show[QtyD[Kilogram]] = 
                mkShowPrettyForQtyD[Kilogram, Kilogram]("%.1f")
            given show_Kilowatt: Show[QtyD[Kilo * Watt]] = 
                mkShowForQtyD[Kilo * Watt]("%.2f")
            given show_MassFlow: Show[MassFlow] = 
                mkShowPrettyForQtyD[Kilogram / Second, Gram / Second]("%.1f")
            given show_Meters: Show[QtyD[Meter]] = 
                mkShowPrettyForQtyD[Meter, Meter]("%.3f")
            val show_Meters_0: Show[QtyD[Meter]] = 
                mkShowForQtyD[Meter]("%.0f")
            given show_Millimeters: Show[QtyD[Millimeter]] = 
                mkShowPrettyForQtyD[Millimeter, Millimeter]("%.1f")
            private val show_Milligram_per_Nm3: Show[QtyD[Milli * Gram / (Meter ^ 3)]] =                 
                given ShowUnit[Milli * Gram / (Meter ^ 3)] = ShowUnit.showUnit_Milligram_per_NormalCubicMeter
                mkShowForQtyD[Milli * Gram / (Meter ^ 3)]("%.0f")
            given show_Moles: Show[QtyD[Mole]] = 
                mkShowForQtyD[Mole]("%.1f")
            private val show_Millimeters_0: Show[QtyD[Millimeter]] =                 
                mkShowPrettyForQtyD[Millimeter, Millimeter]("%.0f")
            given show_NewtonSecondsPerSquareMeter: Show[NewtonSecondsPerSquareMeter] = 
                mkShowPrettyForQtyD[Newton * Second / (Meter ^ 2), Micro * Newton * Second / (Meter ^ 2)]("%.4f")
            given show_Pascals: Show[QtyD[Pascal]] = 
                mkShowPrettyForQtyD[Pascal, Pascal]("%.2f")
            val show_Pascals_1: Show[QtyD[Pascal]] = 
                mkShowPrettyForQtyD[Pascal, Pascal]("%.1f")
            val show_Pascals_0: Show[QtyD[Pascal]] = 
                mkShowPrettyForQtyD[Pascal, Pascal]("%.0f")
            val show_Percent_0: Show[QtyD[Percent]] = 
                mkShowForQtyD("%.0f")
            given show_Percent: Show[QtyD[Percent]] = 
                mkShowForQtyD("%.1f")
            given show_Rougness: Show[Roughness] = Show.show: r =>
                if (r.toUnit[Millimeter].value * 100 % 10 == 0) show_Millimeters_0.show(r)
                else show_Millimeters.show(r)
            given show_SquareCentimeters: Show[QtyD[(Centimeter ^ 2)]] = 
                mkShowPrettyForQtyD[(Centimeter ^ 2), (Centimeter ^ 2)]("%.1f")
            given show_SquareMeters: Show[QtyD[(Meter ^ 2)]] = 
                mkShowPrettyForQtyD[(Meter ^ 2), (Meter ^ 2)]("%.3f")
            given show_SquareMeterKelvinPerWatt: Show[SquareMeterKelvinPerWatt] = 
                mkShowForQtyD[(Meter ^ 2) * Kelvin / Watt]("%.3f")
            val show_Unitless_1: Show[QtyD[1]] = 
                mkShowForQtyD[1]("%.1f")
            given show_Velocity: Show[QtyD[Meter / Second]] = 
                mkShowPrettyForQtyD[Meter / Second, Meter / Second]("%.2f")
            val show_Velocity_1: Show[QtyD[Meter / Second]] = 
                mkShowPrettyForQtyD[Meter / Second, Meter / Second]("%.1f")
            given show_VolumeFlow_m3_per_s: Show[QtyD[(Meter ^ 3) / Second]] = 
                mkShowForQtyD[(Meter ^ 3) / Second]("%.2f")
            given show_VolumeFlow_m3_per_h: Show[QtyD[(Meter ^ 3) / Hour]] = 
                mkShowForQtyD[(Meter ^ 3) / Hour]("%.2f")
            given show_WattsPerMeterKelvin: Show[WattsPerMeterKelvin] = 
                mkShowForQtyD[Watt / (Meter * Kelvin)]("%.3f")
            given show_WattsPerSquareMeterKelvin: Show[WattsPerSquareMeterKelvin] = 
                mkShowForQtyD[Watt / ((Meter ^ 2) * Kelvin)]("%.2f")

        def defaultForQtyD[A] = Show.show[QtyD[A]](qa => qa.show)

        val showMetersInCm = Show.show[QtyD[Meter]](qm => qm.toUnit[Centimeter].show)

    end shows

end coulombutils
