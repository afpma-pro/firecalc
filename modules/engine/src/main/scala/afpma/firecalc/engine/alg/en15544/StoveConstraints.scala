/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.typedefs.*

/** Computed stove-level values made available to stove constraint generators.
 *
 * Unlike [[ConstraintContext]] which is firebox-scoped, this context captures
 * data that belongs to the stove as a whole, independently of the firebox type.
 */
case class StoveConstraintContext(
    t_n: t_n
)

/** Typeclass providing EN 15544 constraint sequences for stove-level terms.
 *
 * Stove-level constraints are independent of the firebox variant and are
 * therefore not part of [[FireboxConstraints]].
 */
trait StoveConstraints:

    def t_n_constraints(
        ctx: StoveConstraintContext
    ): Seq[Option[TermConstraint[t_n]]]

end StoveConstraints

object StoveConstraints:

    def summon(using ev: StoveConstraints): StoveConstraints = ev

end StoveConstraints
