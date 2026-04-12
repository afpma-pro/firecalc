/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.FireCalcYAML

/**
 * EN 15544 strict-mode API object.
 * Provides access to strict-specific traits via `v0_2024_10_strict.TraitName`.
 */
object v0_2024_10_strict extends v0_2024_10_core with v0_2024_10_13384_strict_members with v0_2024_10_strict_members:

    object StoveProjectDescr:

        def makeFor_EN15544_Strict(fc: FireCalcYAML): StoveProjectDescr_15544_Strict_Alg =
            val loader = new FireCalcYAML_Loader(fc)
            loader.stoveProjectDescr_EN15544_Strict
