/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import coulomb.ops.standard.all.given

case class EfficienciesValues(
    n_nominal: Percentage,
    n_lowest: Option[Percentage],
    ns: Percentage,
)

case class EmissionsAndEfficiencyValues(
    firebox_name: String,
    accredited_or_notified_body: String,
    test_reports: List[TestReport],
    min_efficiency_firebox_nominal: Option[Percentage],
    min_efficiency_full_stove_nominal: Option[Percentage],
    min_efficiency_firebox_reduced: Option[Percentage],
    min_efficiency_full_stove_reduced: Option[Percentage],
    min_seasonal_efficiency_full_stove: Option[Percentage],
    emissions_values: EmissionValues,
)

case class TestReport(name: String, date: String)

case class TestEmissionValue(
    polluant_name: PolluantName,
    valueO: Option[EmissionValueU],
    test_method: String,
    o2ref: Percentage = 13.percent,
)

case class EmissionValues(
    co_at_13pO2: TestEmissionValue,
    dust_at_13pO2: TestEmissionValue,
    ogc_at_13pO2: TestEmissionValue,
    nox_at_13pO2: TestEmissionValue,
) {
    val sum_of_dust_and_ogc_at_13pO2 = 
        val sum_at_13pO2: Option[EmissionValueU] = (dust_at_13pO2.valueO, ogc_at_13pO2.valueO) match
            case (Some(dust), Some(ogc)) => Some(dust + ogc)
            case _ => None
        TestEmissionValue(PolluantName.`Dust+OGC`, sum_at_13pO2, test_method = "")
}