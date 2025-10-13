/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.GasProps
import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import afpma.firecalc.engine.ops.ExteriorAirOps
import afpma.firecalc.engine.ops.GasOps

import afpma.firecalc.units.coulombutils.*

private[ops] trait GasOpsUsingEN13384(using
    en13384: EN13384_1_A1_2019_Formulas_Alg
) extends GasOps:

    val extAirOps = ExteriorAirOps.mkUsingEN13384(en13384)

    extension (gas: GasProps)
        def λ_A: WattsPerMeterKelvin = en13384.λ_A_calc(gas.temperature)
        def η_A: NewtonSecondsPerSquareMeter = en13384.η_A_calc(gas.temperature)
        def P_r: Dimensionless = en13384.P_r_calc(gas.cp, η_A, λ_A)
        def σ_H2O: QtyD[Percent] = en13384.σ_H2O_from_σ_CO2_calc(FuelType.WoodLog30pHumidity, gas.σ_CO2)
        def R: JoulesPerKilogramKelvin =
            en13384.R_calc(
                FuelType.WoodLog30pHumidity,
                gas.σ_H2O,
                gas.σ_CO2,
            )

end GasOpsUsingEN13384
