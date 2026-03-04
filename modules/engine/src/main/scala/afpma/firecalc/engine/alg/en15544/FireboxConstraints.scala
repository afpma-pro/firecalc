/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.VolumeFlow

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ

import io.taig.babel.Locale

/** Computed values made available to constraint generators.
 *
 * Populated by the sizing algorithm before constraint evaluation.
 */
case class ConstraintContext(
    m_B    : m_B,
    O_BR   : O_BR,
    A_BR_min: Option[A_BR],
    A_BR_max: Option[A_BR],
    A_BR   : A_BR,
    H_BR_min: Option[H_BR],
    H_BR   : H_BR,
    n_min  : n_min
)

/** Typeclass providing EN 15544 constraint sequences for each firebox term.
 *
 * Each method returns a `Seq[Option[TermConstraint[T]]]` that is evaluated
 * and dispatched by the application layer.
 */
trait FireboxConstraints[-F <: Firebox_15544]:

    def m_B_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[m_B]]]

    def m_B_min_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[m_B_min]]]

    def glassArea_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[GlassArea]]]

    def fireboxDimensions_Base_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[Dimensions.Base]]]

    def h_br_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[H_BR]]]

    def lambda_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[λ]]]

    def eta_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[η]]]

    def height_of_lowest_opening_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[height_of_lowest_opening]]]

    /** Glass surface ratio constraint (does not need ctx). */
    def firebox_glass_surface_ratio_constraint(
        firebox: F
    ): Option[TermConstraint[Unit]]

    /** Validate firebox-specific constraints not covered by EN 15544. */
    def firebox_custom_constraints(
        firebox  : F,
        mB       : m_B,
        flow_rate: Option[VolumeFlow]
    )(using Locale): List[FireboxError]

end FireboxConstraints

/** Mixin trait that removes all §4.3.1.x firebox sizing constraints.
 *
 * Mix this in **last** (rightmost position) so that its overrides take
 * precedence over any base implementation, e.g.:
 *
 * {{{
 *   new DefaultFireboxConstraints[MyFirebox] with RemovedFireboxSizingConstraints
 * }}}
 *
 * Sections removed:
 *   - 4.3.1.1 height of lowest opening
 *   - 4.3.1.2 glass area
 *   - 4.3.1.3 firebox base dimensions
 *   - 4.3.1.4 firebox height
 */
trait RemovedFireboxSizingConstraints[-F <: Firebox_15544]
    extends FireboxConstraints[F]:

    override def height_of_lowest_opening_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[height_of_lowest_opening]]] = Seq.empty

    override def glassArea_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[GlassArea]]] = Seq.empty

    override def fireboxDimensions_Base_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[Dimensions.Base]]] = Seq.empty

    override def h_br_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[H_BR]]] = Seq.empty

end RemovedFireboxSizingConstraints

object FireboxConstraints:

    def apply[F <: Firebox_15544](
        using ev: FireboxConstraints[F]
    ): FireboxConstraints[F] = ev

end FireboxConstraints
