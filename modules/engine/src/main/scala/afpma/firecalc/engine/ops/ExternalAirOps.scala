/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.ExteriorAir
import afpma.firecalc.engine.ops.en13384.ExteriorAirOpsUsingEN13384

import afpma.firecalc.units.coulombutils.*

trait ExteriorAirOps:
    extension (ext_air: ExteriorAir)
        def p_L: Pressure
        def ρ_L: Density
        def pressure: Pressure = p_L
        def density: Density = ρ_L
end ExteriorAirOps

object ExteriorAirOps:

    def mkUsingEN13384(en13384: EN13384_1_A1_2019_Formulas_Alg): ExteriorAirOps = 
        new ExteriorAirOpsUsingEN13384(using en13384) {}