/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.syntax.all.*

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.conversions.mg_per_Nm3

case class LocalRegulations(
    regulation_ref: String,
    country: Country,
    type_of_appliance: LocalRegulations.TypeOfAppliance,
    min_efficiency: Option[Percentage],
    min_seasonal_efficiency: Option[Percentage],
    max_co_at_13pO2: Option[TestEmissionValue],
    max_dust_at_13pO2: Option[TestEmissionValue],
    max_ogc_at_13pO2: Option[TestEmissionValue],
    max_nox_at_13pO2: Option[TestEmissionValue],
    max_sum_of_dust_and_ogc_at_13pO2: Option[TestEmissionValue],
) {
    lazy val all_polluants: List[TestEmissionValue] = List(
        max_co_at_13pO2,
        max_dust_at_13pO2,
        max_ogc_at_13pO2,
        max_nox_at_13pO2,
        max_sum_of_dust_and_ogc_at_13pO2,
    ).flatten

    def find_max_for(polluant_name: PolluantName, o2ref: Percentage): Option[TestEmissionValue] = 
        all_polluants.filter(_.o2ref == o2ref).find(_.polluant_name == polluant_name)
}

object LocalRegulations:

    enum TypeOfAppliance:
        case Pellets, WoodLogs

    given ShowUsingLocale[TypeOfAppliance] = showUsingLocale:
        case TypeOfAppliance.Pellets  => I18N.type_of_appliance.pellets
        case TypeOfAppliance.WoodLogs => I18N.type_of_appliance.woodlogs
    
    lazy val all = fr.all

    def findBy(c: Country, t: TypeOfAppliance): LocalRegulations = 
        val res = all
            .filter(_.country == c)
            .filter(_.type_of_appliance == t)
        res match
            case Nil       => throw new IllegalStateException(s"Local regulations not found for country '$c' and appliance type '$t'")
            case h :: Nil  => h
            case xs => 
                val regulations_detail = xs.map(x => s"${x.country} | ${x.type_of_appliance} | ${x.regulation_ref} | ${x}").mkString("-  ", "\n  -", "\n")
                throw new IllegalStateException(s"Found ${xs.size} local regulations for country '$c' and appliance type '$t' (expecting only one):\n${regulations_detail}")
  
    object fr:
        lazy val all = List(wood_logs, pellets)

        lazy val wood_logs = LocalRegulations(
            regulation_ref                      = "Label Flamme Verte",
            country                             = Country.France,
            type_of_appliance                   = TypeOfAppliance.WoodLogs,
            min_efficiency                      = None,
            min_seasonal_efficiency             = 65.percent.some,
            max_co_at_13pO2                     = TestEmissionValue(PolluantName.CO          , 1500  .mg_per_Nm3.some, test_method = "").some,
            max_dust_at_13pO2                   = TestEmissionValue(PolluantName.Dust        , 40    .mg_per_Nm3.some, test_method = "").some,
            max_ogc_at_13pO2                    = TestEmissionValue(PolluantName.OGC         , 120   .mg_per_Nm3.some, test_method = "").some,
            max_nox_at_13pO2                    = TestEmissionValue(PolluantName.NOx         , 200   .mg_per_Nm3.some, test_method = "").some,
            max_sum_of_dust_and_ogc_at_13pO2    = TestEmissionValue(PolluantName.`Dust+OGC`  , 150   .mg_per_Nm3.some, test_method = "").some,
        )
        lazy val pellets = LocalRegulations(
            regulation_ref                      = "Label Flamme Verte",
            country                             = Country.France,
            type_of_appliance                   = TypeOfAppliance.Pellets,
            min_efficiency                      = None,
            min_seasonal_efficiency             = 79.percent.some,
            max_co_at_13pO2                     = TestEmissionValue(PolluantName.CO          , 300  .mg_per_Nm3.some, test_method = "").some,
            max_dust_at_13pO2                   = TestEmissionValue(PolluantName.Dust        , 20   .mg_per_Nm3.some, test_method = "").some,
            max_ogc_at_13pO2                    = TestEmissionValue(PolluantName.OGC         , 60   .mg_per_Nm3.some, test_method = "").some,
            max_nox_at_13pO2                    = TestEmissionValue(PolluantName.NOx         , 200  .mg_per_Nm3.some, test_method = "").some,
            max_sum_of_dust_and_ogc_at_13pO2    = TestEmissionValue(PolluantName.`Dust+OGC`  , 70   .mg_per_Nm3.some, test_method = "").some,
        )