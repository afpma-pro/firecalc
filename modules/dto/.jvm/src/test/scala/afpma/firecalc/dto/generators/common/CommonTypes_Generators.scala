/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.common

import afpma.firecalc.dto.common.Country
import afpma.firecalc.dto.common.DisplayUnits
import afpma.firecalc.dto.common.LocalConditions
import afpma.firecalc.dto.common.ProjectDescr
import afpma.firecalc.dto.common.StandardOrComputationMethod
import afpma.firecalc.dto.generators.base.PrimitiveGenerators

import io.taig.babel.Language
import io.taig.babel.Locale
import org.scalacheck.Gen

trait CommonTypes_Generators extends PrimitiveGenerators:

    def genLocale: Gen[Locale] =
        Gen.const(Locale(Language("fr")))

    def genDisplayUnits: Gen[DisplayUnits] =
        Gen.oneOf(DisplayUnits.SI, DisplayUnits.Imperial)

    def genStandardOrComputationMethod: Gen[StandardOrComputationMethod] =
        Gen.const(StandardOrComputationMethod.EN_15544_2023)

    def genCountry: Gen[Country] =
        Gen.oneOf(Country.France, Country.Belgique)

    def genProjectDescr: Gen[ProjectDescr] =
        for
            reference <- Gen.alphaNumStr.map(_.take(20))
            country   <- genCountry
        yield ProjectDescr(
            reference = reference,
            date      = "2025-01-15",
            country   = country
        )

    def genChimneyTermination: Gen[LocalConditions.ChimneyTermination] =
        Gen.const(LocalConditions.ChimneyTermination.Classic)

    def genLocalConditions: Gen[LocalConditions] =
        for
            altitude            <- genAltitude
            coastal_region      <- Gen.oneOf(true, false)
            chimney_termination <- genChimneyTermination
        yield LocalConditions           (
            altitude            = altitude,
            coastal_region      = coastal_region,
            chimney_termination = chimney_termination
        )

end CommonTypes_Generators
