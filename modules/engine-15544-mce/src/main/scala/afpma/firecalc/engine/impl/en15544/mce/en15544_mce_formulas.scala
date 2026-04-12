/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.impl.en15544.common.*

open class EN15544_MCE_Formulas(
    override val net_calorific_value_of_wet_wood: HeatCapacity,
    override val net_calorific_value_of_dry_wood: HeatCapacity
) extends EN15544_V_2023_Common_Formulas

object EN15544_MCE_Formulas:
    def make(
        net_calorific_value_of_wet_wood: HeatCapacity,
        net_calorific_value_of_dry_wood: HeatCapacity
    ) = new EN15544_MCE_Formulas(
        net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
        net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
    )
