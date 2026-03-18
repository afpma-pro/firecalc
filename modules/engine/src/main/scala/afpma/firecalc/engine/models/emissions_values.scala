/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.syntax.either.catsSyntaxEitherId
import cats.syntax.option.catsSyntaxOptionId

import coulomb.ops.standard.all.given

export afpma.firecalc.dto.v4.TestReport

case class EfficienciesValues(
    n_nominal: VNelMcalcErr[Percentage],
    n_lowest : VNelMcalcErr[Option[Percentage]],
    ns       : VNelMcalcErr[Percentage]
)

case class EmissionsAndEfficiencyValues(
    firebox_name                      : String,
    accredited_or_notified_body       : String,
    test_reports                      : List[TestReport],
    min_efficiency_firebox_nominal    : Option[Percentage],
    min_efficiency_full_stove_nominal : VNelMcalcErr[Option[Percentage]],
    min_efficiency_firebox_reduced    : Option[Percentage],
    min_efficiency_full_stove_reduced : VNelMcalcErr[Option[Percentage]],
    min_seasonal_efficiency_full_stove: VNelMcalcErr[Option[Percentage]],
    emissions_values                  : EmissionValues
)

case class TestEmissionValue(
    polluant_name: PolluantName,
    valueO       : Option[EmissionValueU],
    test_method  : String,
    o2ref        : Percentage
)

object TestEmissionValue:
    def defineAt13pO2(
        polluant_name: PolluantName,
        valueO       : Option[EmissionValueU],
        test_method  : String
    ) = TestEmissionValue(
        polluant_name,
        valueO,
        test_method,
        o2ref = 13.percent
    )

case class EmissionValues(
    co  : TestEmissionValue,
    dust: TestEmissionValue,
    ogc : TestEmissionValue,
    nox : TestEmissionValue
) {
    val sum_of_dust_and_ogc: Either[String, TestEmissionValue] =
        for
            o2ref <-
                if (dust.o2ref == ogc.o2ref) then dust.o2ref.asRight
                else s"could not sum value unless they are defined at same O2 percentage : ${dust} ${ogc}".asLeft
            sum   <-
                (dust.valueO, ogc.valueO) match
                    case (Some(dust), Some(ogc)) => ((dust + ogc): EmissionValueU).asRight
                    case (None, _              ) => "dust value missing".asLeft
                    case (Some(_), None        ) => "ogc value missing".asLeft
                    case _ => "dust and ogc values missing".asLeft
        yield TestEmissionValue(PolluantName.`Dust+OGC`, sum.some, test_method = "", o2ref)

}
