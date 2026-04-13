/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.GasProps

trait GasOps:
    extension (gas: GasProps)
        def λ_A  : WattsPerMeterKelvin
        def η_A  : NewtonSecondsPerSquareMeter
        def P_r  : Dimensionless
        def σ_H2O: QtyD[Percent]
        def R    : JoulesPerKilogramKelvin
end GasOps

object GasOps
