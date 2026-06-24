/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE
import afpma.firecalc.engine.standard.*

/**
 * EN 15544 constraints for [[AFPMA_PRSE]] fireboxes.
 *
 * This firebox design is not yet validated: all computations raise an error
 * to warn the user.
 */
given afpmaPrseConstraints: FireboxConstraints[AFPMA_PRSE] =
    new FireboxConstraints_Strict[AFPMA_PRSE]:

        override def firebox_custom_constraints(
            firebox: AFPMA_PRSE,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] = Nil

end afpmaPrseConstraints
