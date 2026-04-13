/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.ExteriorAir

trait ExteriorAirOps:
    extension (ext_air: ExteriorAir)
        def p_L     : Pressure
        def ρ_L     : Density
        def pressure: Pressure = p_L
        def density : Density  = ρ_L
end ExteriorAirOps

object ExteriorAirOps
