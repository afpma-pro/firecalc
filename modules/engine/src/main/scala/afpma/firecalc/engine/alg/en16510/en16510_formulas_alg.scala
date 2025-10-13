/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en16510

import afpma.firecalc.engine.alg.Standard
import afpma.firecalc.engine.models.en16510.*

import afpma.firecalc.units.coulombutils.*

trait EN16510_1_2022_Formulas_Alg extends Standard:

    // A.6.2.1.5

    /** rendement énergétique saisonnier pour le chauffage des locaux */
    def η_s(
        η: Percentage, 
        f2: CorrectionFactor_F2,
        f3: CorrectionFactors_F3,
        f4: CorrectionFactor_F4,
    ): Percentage