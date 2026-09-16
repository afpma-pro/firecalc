/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.impl.en15544.common.AirIntakeShapeChecks
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE
import afpma.firecalc.engine.standard.*

/**
 * EN 15544 constraints for [[AFPMA_PRSE]] fireboxes.
 *
 * This design inherits the standard EN 15544 sizing constraints from
 * [[FireboxConstraints_Strict]]. The only PRSE-specific constraint is the
 * air-intake topology check: when an "Amenée d'air" pipe is drawn, its
 * end shape must match the declared `actual_air_intake_pipe_shape`
 * (tolerance-aware, same variant).
 */
given afpmaPrseConstraints: FireboxConstraints[AFPMA_PRSE] =
    new FireboxConstraints_Strict[AFPMA_PRSE]:

        override def kind: FireboxKind = FireboxKind.AFPMA_PRSE

        override def firebox_custom_constraints(
            firebox: AFPMA_PRSE,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            ctx.airIntakePipeShape
                .flatMap(computed =>
                    AirIntakeShapeChecks.topologyMismatch(firebox.actual_air_intake_pipe_shape, computed)
                )
                .toList

end afpmaPrseConstraints
