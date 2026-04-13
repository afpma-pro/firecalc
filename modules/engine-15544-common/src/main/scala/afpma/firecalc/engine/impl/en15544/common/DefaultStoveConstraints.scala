/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en15544.StoveConstraintContext
import afpma.firecalc.engine.alg.en15544.StoveConstraints
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.typedefs.*

/**
 * Default EN 15544 stove constraint instance.
 *
 * Encodes the standard `t_n` bounds from EN 15544 Section 4.2.1:
 *   - minimum heating cycle: 8 hours
 *   - maximum heating cycle: 24 hours
 */
given defaultStoveConstraints: StoveConstraints with

    override def t_n_constraints(
        ctx: StoveConstraintContext
    ): Seq[Option[TermConstraint[t_n]]] =
        import StoragePeriod.given
        Seq (
            Some(TermConstraint.Min(8.hours) ),
            Some(TermConstraint.Max(24.hours))
        )

end defaultStoveConstraints
