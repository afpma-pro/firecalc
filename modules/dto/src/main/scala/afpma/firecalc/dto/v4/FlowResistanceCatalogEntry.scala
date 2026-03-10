/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.dto.all.*

/** A catalog entry for a flow resistance element (wire mesh screen, damper, grate, etc.).
  * Mirrors the structure of AddFlowResistance so selection can populate all three fields.
  */
case class FlowResistanceCatalogEntry(
    name         : String,
    zeta         : QtyD[1],
    cross_section: OptionOfEither[AreaInCm2, PipeShape] = NoneOfEither
)
