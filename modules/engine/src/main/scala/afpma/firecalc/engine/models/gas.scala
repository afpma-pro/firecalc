/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

/**
  * GasProps 
  *
  * @param t
  * @param cp
  * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
  */
final case class GasProps(
    t: TempD[Celsius],
    cp: JoulesPerKilogramKelvin,
    σ_CO2: QtyD[Percent],
) {
    val temperature = t
    val specificHeatCapacity = cp
}