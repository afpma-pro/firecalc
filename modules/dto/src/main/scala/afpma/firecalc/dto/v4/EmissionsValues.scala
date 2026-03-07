/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import magnolia1.Transl

@Transl(I(_.pollutant_names._self))
enum PolluantName:
    case CO, Dust, OGC, NOx, `Dust+OGC`

object PolluantName:
    @Transl(I(_.pollutant_names.CO))
    type CO = PolluantName.CO.type
    @Transl(I(_.pollutant_names.Dust))
    type Dust = PolluantName.Dust.type
    @Transl(I(_.pollutant_names.OGC))
    type OGC = PolluantName.OGC.type
    @Transl(I(_.pollutant_names.NOx))
    type NOx = PolluantName.NOx.type
    @Transl(I(_.pollutant_names.Dust_OGC))
    type `Dust+OGC` = PolluantName.`Dust+OGC`.type

    given ShowUsingLocale[PolluantName] = showUsingLocale:
        case CO         => I18N.pollutant_names.CO
        case Dust       => I18N.pollutant_names.Dust
        case OGC        => I18N.pollutant_names.OGC
        case NOx        => I18N.pollutant_names.NOx
        case `Dust+OGC` => I18N.pollutant_names.Dust_OGC

@Transl(I(_.test_report._self))
case class TestReport(
    @Transl(I(_.test_report.name))
    name: String,
    @Transl(I(_.test_report.date))
    date: String
)

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
