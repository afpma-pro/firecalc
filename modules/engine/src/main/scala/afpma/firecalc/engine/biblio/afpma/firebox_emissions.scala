/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.biblio.afpma

import afpma.firecalc.engine.models.*

object firebox_emissions:

    val AFPMA_PRSE: EmissionsAndEfficiencyValues = EmissionsAndEfficiencyValues(
        firebox_name = "AFPMA PRSE",
        accredited_or_notified_body = "AFPMA - Association Française du Poêle Maçonné Artisanal",
        test_reports = Nil,
        min_efficiency_firebox_nominal         = None,
        min_efficiency_firebox_reduced          = None,
        min_efficiency_full_stove_nominal           = None,
        min_efficiency_full_stove_reduced            = None,
        min_seasonal_efficiency_full_stove  = None,
        emissions_values = EmissionValues(
            co_at_13pO2   = TestEmissionValue(PolluantName.CO  , None, test_method = ""),
            dust_at_13pO2 = TestEmissionValue(PolluantName.Dust, None, test_method = ""),
            ogc_at_13pO2  = TestEmissionValue(PolluantName.OGC , None, test_method = ""),
            nox_at_13pO2  = TestEmissionValue(PolluantName.NOx , None, test_method = ""),
        )
    )
