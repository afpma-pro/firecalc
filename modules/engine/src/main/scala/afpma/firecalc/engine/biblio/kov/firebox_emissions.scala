/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.biblio.kov

import cats.syntax.all.*

import afpma.firecalc.engine.models.*

import afpma.firecalc.units.coulombutils.*

object firebox_emissions:

    val Standing_Standard_Burning_Firebox: EmissionsAndEfficiencyValues = EmissionsAndEfficiencyValues(
        firebox_name = "standing standard burning chamber",
        accredited_or_notified_body = "Test Laboratory for Combustion Systems - Technical University of Vienna",
        test_reports = List(
            TestReport(name = "6398", date = "???"),
            TestReport(name = "6388/1", date = "27.01.1994"),
        ),
        min_efficiency_firebox_nominal         = None,
        min_efficiency_firebox_reduced          = None,
        min_efficiency_full_stove_nominal           = None,
        min_efficiency_full_stove_reduced            = None,
        min_seasonal_efficiency_full_stove          = None,
        emissions_values = EmissionValues(
            co_at_13pO2   = TestEmissionValue(PolluantName.CO  , 1137  .mg_per_Nm3.some, test_method = ""),
            dust_at_13pO2 = TestEmissionValue(PolluantName.Dust, 26    .mg_per_Nm3.some, test_method = ""),
            ogc_at_13pO2  = TestEmissionValue(PolluantName.OGC , 87    .mg_per_Nm3.some, test_method = ""),
            nox_at_13pO2  = TestEmissionValue(PolluantName.NOx , 113   .mg_per_Nm3.some, test_method = ""),
        )
    )

    val Lying_Standard_Burning_Firebox: EmissionsAndEfficiencyValues  = EmissionsAndEfficiencyValues(
        firebox_name = "lying standard burning chamber",
        accredited_or_notified_body = "",
        test_reports = List(
            TestReport(name = "VTWS-9714-P", date = ""),
        ),
        min_efficiency_firebox_nominal         = None,
        min_efficiency_firebox_reduced          = None,
        min_efficiency_full_stove_nominal           = None,
        min_efficiency_full_stove_reduced            = None,
        min_seasonal_efficiency_full_stove          = None,
        emissions_values = EmissionValues(
            co_at_13pO2   = TestEmissionValue(PolluantName.CO  , 1304  .mg_per_Nm3.some, test_method = ""),
            dust_at_13pO2 = TestEmissionValue(PolluantName.Dust, 33    .mg_per_Nm3.some, test_method = ""),
            ogc_at_13pO2  = TestEmissionValue(PolluantName.OGC , 69    .mg_per_Nm3.some, test_method = ""),
            nox_at_13pO2  = TestEmissionValue(PolluantName.NOx , 137   .mg_per_Nm3.some, test_method = ""),
        )
    )

    val EcoPlus_Combustion_Firebox: EmissionsAndEfficiencyValues  = EmissionsAndEfficiencyValues(
        firebox_name = "eco+ firebox",
        accredited_or_notified_body = "Test Laboratory for Combustion Systems - Technical University of Vienna",
        test_reports = List(
            TestReport(name = "PL-10174-P", date = "11.1.2016"),
        ),
        min_efficiency_firebox_nominal         = None,
        min_efficiency_firebox_reduced          = None,
        min_efficiency_full_stove_nominal           = None,
        min_efficiency_full_stove_reduced            = None,
        min_seasonal_efficiency_full_stove          = None,
        emissions_values = EmissionValues(
            co_at_13pO2   = TestEmissionValue(PolluantName.CO  , 557  .mg_per_Nm3.some, test_method = "Austrian Standard ÖNORM B 8303"),
            dust_at_13pO2 = TestEmissionValue(PolluantName.Dust, 30   .mg_per_Nm3.some, test_method = "Austrian Standard ÖNORM M 5861-1 and ÖNORM M 5861-2"),
            ogc_at_13pO2  = TestEmissionValue(PolluantName.OGC , 39   .mg_per_Nm3.some, test_method = "Austrian Standard ÖNORM B 8303"),
            nox_at_13pO2  = TestEmissionValue(PolluantName.NOx , 133  .mg_per_Nm3.some, test_method = "Austrian Standard ÖNORM B 8303"),
        )
    )

    val `15A_Combustion_Firebox`: EmissionsAndEfficiencyValues  = EmissionsAndEfficiencyValues(
        firebox_name = "15a firebox",
        accredited_or_notified_body = "Test Laboratory for Combustion Systems - Technical University of Vienna",
        test_reports = List(
            TestReport(name = "PL-19075-1-P", date = "9.6.2020"),
            TestReport(name = "PL-19075-2-P", date = "9.6.2020"),
            TestReport(name = "PL-19075-3-P", date = "27.10.2020"),
            TestReport(name = "PL-19075-4-P", date = "19.1.2021"),
            TestReport(name = "PL-19075-5-P", date = "19.1.2021"),
            TestReport(name = "PL-19075-6-P", date = "19.1.2021"),
        ),
        min_efficiency_firebox_nominal         = None,
        min_efficiency_firebox_reduced          = None,
        min_efficiency_full_stove_nominal           = None,
        min_efficiency_full_stove_reduced            = None,
        min_seasonal_efficiency_full_stove          = None,
        emissions_values = EmissionValues(
            co_at_13pO2   = TestEmissionValue(PolluantName.CO  , 1154 .mg_per_Nm3.some, test_method = ""),
            dust_at_13pO2 = TestEmissionValue(PolluantName.Dust, 25   .mg_per_Nm3.some, test_method = ""),
            ogc_at_13pO2  = TestEmissionValue(PolluantName.OGC , 40   .mg_per_Nm3.some, test_method = ""),
            nox_at_13pO2  = TestEmissionValue(PolluantName.NOx , 119  .mg_per_Nm3.some, test_method = ""),
        )
    )
