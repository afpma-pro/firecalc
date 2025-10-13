/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

/**
  * Exterior Air
  *
  * @param T_L exterior air temperature, in K
  * @param z height above sea level, in m
  */
final case class ExteriorAir(
    T_L: TempD[Celsius],
    z: QtyD[Meter],
) {
    val temperature = T_L
    val height_above_sea_level = z
}