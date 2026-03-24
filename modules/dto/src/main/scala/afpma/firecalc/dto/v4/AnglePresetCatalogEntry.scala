/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*

/** A catalog entry for an angle preset (specific angle with user-provided zeta).
  * Mirrors the structure of AddAngleAdjustable so selection can populate name, angle and zeta.
  */
case class AnglePresetCatalogEntry(
    reference: String,
    angle    : Angle,
    zeta     : QtyD[1],
    image    : Option[String] = None
)
