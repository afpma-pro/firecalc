/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

import afpma.firecalc.filaire.FilaireTypes.LineColor

/**
 * Configurable defaults for the symbolic end-cap disc rendered at the end of a
 * chimney pipe whose last element is a singular flow resistance (DTO:
 * `AddFlowResistance` tagged with `IsSingularFlowResistance`).
 *
 * The end-cap is purely visual and carries no engine semantics. Tweak these
 * constants to adjust appearance.
 */
object ChimneyEndCapDefaults:

    /** End-cap diameter expressed as a multiple of the chimney's last (hydraulic) diameter. */
    final val DiameterMultiplier: Double = 2.0

    /** End-cap thickness along the chimney axis, in centimeters. */
    final val ThicknessCm: Double = 2.0

    /** Gap between the chimney's end and the end-cap's near face, in centimeters. */
    final val GapCm: Double = 10.0

    /** End-cap color. Medium grey, neutral against the colored pipes. */
    final val Color: LineColor = LineColor("#888888")

end ChimneyEndCapDefaults
