/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.*

import afpma.firecalc.domain.PolluantName
import afpma.firecalc.domain.TestReport
import magnolia1.Transl

@Transl(I(_.test_emission_value._self))
case class TestEmissionValue_DTO(
    @Transl(I(_.test_emission_value.polluant_name))
    polluant_name: PolluantName,
    @Transl(I(_.test_emission_value.value))
    valueO       : Option[EmissionValueU],
    @Transl(I(_.test_emission_value.test_method))
    test_method  : String,
    @Transl(I(_.test_emission_value.o2ref))
    o2ref        : Percentage
)

/** Per-pollutant emission test values.
  * Note: `PolluantName.Dust+OGC` has no field here — it is a derived value
  * computed at runtime by the engine (see `EmissionValues.sum_of_dust_and_ogc`).
  */
@Transl(I(_.emission_values._self))
case class EmissionValues_DTO(
    @Transl(I(_.emission_values.co))
    co  : TestEmissionValue_DTO,
    @Transl(I(_.emission_values.dust))
    dust: TestEmissionValue_DTO,
    @Transl(I(_.emission_values.ogc))
    ogc : TestEmissionValue_DTO,
    @Transl(I(_.emission_values.nox))
    nox : TestEmissionValue_DTO
)

/** DTO-side representation of emissions and efficiency values.
  * Only contains the static biblio data (test reports, emission measurements).
  * The efficiency fields (VNelMcalcErr) are computed by the engine at runtime
  * and are not stored — the engine transformer initializes them to None.validNel.
  */
@Transl(I(_.emissions_and_efficiency_values._self))
case class EmissionsAndEfficiencyValues_DTO(
    @Transl(I(_.emissions_and_efficiency_values.firebox_name))
    firebox_name               : String,
    @Transl(I(_.emissions_and_efficiency_values.accredited_or_notified_body))
    accredited_or_notified_body: String,
    @Transl(I(_.emissions_and_efficiency_values.test_reports))
    test_reports               : List[TestReport],
    @Transl(I(_.emissions_and_efficiency_values.emissions_values))
    emissions_values           : EmissionValues_DTO
)
