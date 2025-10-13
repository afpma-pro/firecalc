/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en16510
import algebra.instances.all.given

import afpma.firecalc.engine.alg.en16510.EN16510_1_2022_Formulas_Alg
import afpma.firecalc.engine.models.Document
import afpma.firecalc.engine.models.en16510.*

import afpma.firecalc.units.coulombutils.*
import coulomb.ops.standard.all.given

object EN16510_1_2022_Formulas extends EN16510_1_2022_Formulas_Alg:

    override val doc: Document = Document(
        name = "16510-1:2022",
        date = "2022-12",
        version = "2022-12",
        revision = "Version 1",
        status = "Approved",
        author = "AFNOR D35R"
    )

    // A.6.2.1.5

    /** rendement énergétique saisonnier pour le chauffage des locaux */
    override def η_s(
        η: Percentage, 
        f2: CorrectionFactor_F2,
        f3: CorrectionFactors_F3,
        f4: CorrectionFactor_F4,
    ): Percentage = 
        η - 10.percent + f2.p + f3.value - f4.p
