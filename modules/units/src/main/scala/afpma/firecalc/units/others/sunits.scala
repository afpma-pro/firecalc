/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.units.others

import coulomb.*
import coulomb.syntax.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.units.all.*

object sunits:
    object SUnits:

        def findByKey(suKey: String): Option[SUnit[?]] = 
            sunit_all.find(_.key == suKey)

        def findByKeyOrThrow[U](suKey: String): SUnit[U] = 
            SUnits.findByKey(suKey)
                .getOrElse(throw new Exception(s"unexpected unit '$suKey'"))
                .asInstanceOf[SUnit[U]]

        given sunit_BTU_per_Hour: SUnit[BTU / Hour] = 
            val su = mkShowUnitFull[BTU / Hour]
            new SUnit[BTU / Hour](_.btu_per_hour)(using su)

        given sunit_Celsius: SUnit[Celsius] = 
            val su = ShowUnit.fromCoulombUnit[Celsius]
            val suf = mkShowUnitFull[Celsius]
            new SUnit[Celsius](_.celsius)(using suf, su)

        given sunit_Centimeter: SUnit[Centimeter] = 
            val su = mkShowUnitFull[Centimeter]
            new SUnit[Centimeter](_.centimeter)(using su)

        given sunit_Degree: SUnit[Degree] = 
            val su = ShowUnit.fromCoulombUnit[Degree]
            val suf = mkShowUnitFull[Degree]
            new SUnit[Degree](_.degree)(using suf, su)

        given sunit_Foot: SUnit[Foot] = 
            val su = ShowUnit.fromCoulombUnit[Foot]
            val suf = mkShowUnitFull[Foot]
            new SUnit[Foot](_.foot)(using suf, su)

        given sunit_Hour: SUnit[Hour] = 
            val su = mkShowUnitFull[Hour]
            new SUnit[Hour](_.hour)(using su)

        given sunit_Inch: SUnit[Inch] = 
            val su = ShowUnit.fromCoulombUnit[Inch]
            val suf = mkShowUnitFull[Inch]
            new SUnit[Inch](_.inch)(using suf, su)

        given sunit_Kelvin: SUnit[Kelvin] = 
            val su = ShowUnit.fromCoulombUnit[Kelvin]
            val suf = mkShowUnitFull[Kelvin]
            new SUnit[Kelvin](_.kelvin)(using suf, su)

        given sunit_Kilogram: SUnit[Kilogram] = 
            val su = mkShowUnitFull[Kilogram]
            new SUnit[Kilogram](_.kilogram)(using su)

        given sunit_Kilowatt: SUnit[Kilo * Watt] = 
            val su = mkShowUnitFull[Kilo * Watt]
            new SUnit[Kilo * Watt](_.kilowatt)(using su)

        given sunit_Meter: SUnit[Meter] = 
            val su = mkShowUnitFull[Meter]
            new SUnit[Meter](_.meter)(using su)

        given sunit_Millimeter: SUnit[Millimeter] = 
            val su = mkShowUnitFull[Millimeter]
            new SUnit[Millimeter](_.millimeter)(using su)

        given sunit_Pascal: SUnit[Pascal] = 
            val su = mkShowUnitFull[Pascal]
            new SUnit[Pascal](_.pascal)(using su)

        given sunit_Percent: SUnit[Percent] = 
            val su = mkShowUnitFull[Percent]
            new SUnit[Percent](_.percent)(using su)

        given sunit_Pound: SUnit[Pound] = 
            val su = ShowUnit.fromCoulombUnit[Pound]
            val suf = mkShowUnitFull[Pound]
            new SUnit[Pound](_.pound)(using suf, su)

        given sunit_SquareCentimeter: SUnit[(Centimeter ^ 2)] = 
            val su = mkShowUnitFull[(Centimeter ^ 2)]
            new SUnit[(Centimeter ^ 2)](_.square_centimeter)(using su)

        given sunit_SquareInch: SUnit[(Inch ^ 2)] = 
            val su = ShowUnit.fromString[(Inch ^ 2)]("in²")
            val suf = mkShowUnitFull[(Inch ^ 2)]
            new SUnit[(Inch ^ 2)](_.square_inch)(using suf, su)

        given sunit_SquareMeter: SUnit[(Meter ^ 2)] = 
            val su = mkShowUnitFull[(Meter ^ 2)]
            new SUnit[(Meter ^ 2)](_.square_meter)(using su)

        given sunit_SquareMeter_Kelvin_per_Watt: SUnit[(Meter ^ 2) * Kelvin / Watt] =
            val su = ShowUnit.fromString[(Meter ^ 2) * Kelvin / Watt]("m².K/W")
            val suf = mkShowUnitFull[(Meter ^ 2) * Kelvin / Watt]
            new SUnit[(Meter ^ 2) * Kelvin / Watt](_.square_meter_kelvin_per_watt)(using suf, su)

        given sunit_Unitless: SUnit[1] = 
            val su = mkShowUnitFull[1]
            new SUnit[1](_.unitless)(using su)

        given sunit_Watt_per_MeterKelvin: SUnit[Watt / (Meter * Kelvin)] =
            val su = ShowUnit.fromString[Watt / (Meter * Kelvin)]("W/m.K")
            val suf = mkShowUnitFull[Watt / (Meter * Kelvin)]
            new SUnit[Watt / (Meter * Kelvin)](_.watt_per_meter_kelvin)(using suf, su)

        given sunit_all: List[SUnit[?]] = List(
            SUnits.sunit_BTU_per_Hour,
            SUnits.sunit_Celsius,
            SUnits.sunit_Centimeter,
            SUnits.sunit_Degree,
            SUnits.sunit_Foot,
            SUnits.sunit_Hour,
            SUnits.sunit_Inch,
            SUnits.sunit_Kelvin,
            SUnits.sunit_Kilogram,
            SUnits.sunit_Kilowatt,
            SUnits.sunit_Meter,
            SUnits.sunit_Millimeter,
            SUnits.sunit_Pascal,
            SUnits.sunit_Percent,
            SUnits.sunit_Pound,
            SUnits.sunit_SquareCentimeter,
            SUnits.sunit_SquareMeter,
            SUnits.sunit_SquareMeter_Kelvin_per_Watt,
            SUnits.sunit_SquareInch,
            SUnits.sunit_Unitless,
            SUnits.sunit_Watt_per_MeterKelvin,
        )

        given sunit_all_map: Map[String, SUnit[?]] = 
            sunit_all.map(su => su.showUnitFull -> su).toMap
