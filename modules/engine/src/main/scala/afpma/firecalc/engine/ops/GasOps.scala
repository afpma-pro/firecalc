/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.GasProps
import afpma.firecalc.engine.ops.en13384.GasOpsUsingEN13384

import afpma.firecalc.units.coulombutils.*

trait GasOps:
    extension (gas: GasProps)
        def λ_A: WattsPerMeterKelvin
        def η_A: NewtonSecondsPerSquareMeter
        def P_r: Dimensionless
        def σ_H2O: QtyD[Percent]
        def R: JoulesPerKilogramKelvin
end GasOps

object GasOps:

    def mkUsingEN13384(en13384: EN13384_1_A1_2019_Formulas_Alg): GasOps = 
        new GasOpsUsingEN13384(using en13384) {}