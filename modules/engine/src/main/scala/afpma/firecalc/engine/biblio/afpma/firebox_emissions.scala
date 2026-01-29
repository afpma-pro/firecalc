/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.biblio.afpma

import afpma.firecalc.engine.models.*
import cats.syntax.validated.*

object firebox_emissions:

    val AFPMA_PRSE: EmissionsAndEfficiencyValues = EmissionsAndEfficiencyValues(
        firebox_name = "AFPMA PRSE",
        accredited_or_notified_body = "AFPMA - Association Française du Poêle Maçonné Artisanal",
        test_reports = Nil,
        min_efficiency_firebox_nominal      = None,
        min_efficiency_firebox_reduced      = None,
        min_efficiency_full_stove_nominal   = None.validNel,
        min_efficiency_full_stove_reduced   = None.validNel,
        min_seasonal_efficiency_full_stove  = None.validNel,
        emissions_values = EmissionValues(
            co   = TestEmissionValue.defineAt13pO2(PolluantName.CO  , None, test_method = ""),
            dust = TestEmissionValue.defineAt13pO2(PolluantName.Dust, None, test_method = ""),
            ogc  = TestEmissionValue.defineAt13pO2(PolluantName.OGC , None, test_method = ""),
            nox  = TestEmissionValue.defineAt13pO2(PolluantName.NOx , None, test_method = ""),
        )
    )
