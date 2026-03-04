/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.impl.en15544.common.FireboxFormulas_Strict
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.typedefs.*

import coulomb.*

/** EN 15544 formula instance for [[Door15aFirebox_Catalog]] fireboxes.
 *
 * Placeholder that delegates entirely to the default standard formulas.
 * Ready for customisation when Door 15a-specific sizing formulas are needed
 * (e.g. different O_BR or H_BR calculation per supplier datasheet).
 */
given door15aCatalogFormulas: FireboxFormulas[Door15aFirebox_Catalog] =
    new FireboxFormulas_Strict[Door15aFirebox_Catalog]:

        extension (firebox: Door15aFirebox_Catalog)

            override def A_BR_min_calc: m_B => A_BR =
                mb => (100 * mb.value).squareCentimeters

            override def A_BR_max_calc: (m_B, U_BR) => A_BR =
                (mb, _) => (130 * mb.value).squareCentimeters
