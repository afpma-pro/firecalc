/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.ExteriorAir
import afpma.firecalc.engine.ops.ExteriorAirOps

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

private[ops] trait ExteriorAirOpsUsingEN13384(using
    en13384: EN13384_1_A1_2019_Formulas_Alg
) extends ExteriorAirOps:

    extension (ext_air: ExteriorAir)
        def p_L: Pressure = en13384.p_L_calc(ext_air.T_L, ext_air.z)
        def ρ_L: Density = en13384.ρ_L_calc(p_L, ext_air.T_L)

end ExteriorAirOpsUsingEN13384