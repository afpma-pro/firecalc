/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.firebox.{AFPMA_PRSE, Ecolabeled, TraditionalFirebox}

object FireboxConstraintsResolver:

    /**
     * Resolve the concrete [[FireboxConstraints]] instance for a firebox whose
     * static type has been erased to [[Firebox_15544]].
     *
     * Exhaustive over the sealed hierarchy — the compiler warns if a new
     * subtype is added without a branch here. Each branch summons the
     * subtype-specific given (imported locally), so the returned instance
     * carries every override (mass bounds, removed sizing constraints, custom
     * constraints, ...).
     *
     * The single `asInstanceOf` is sound: in each branch the runtime value IS
     * the matched concrete type, and the summoned instance accepts at least
     * that type. `firebox.Self` is abstract (lower-bounded by `this.type`), so
     * a static proof is impossible — the exhaustive match is the proof.
     *
     * TODO(registry): replace this hand-written match with the instance
     * registry described in `FireboxConstraints.scala`'s header. Deleting this
     * object is the goal once each firebox owns its module + registration.
     */
    def resolve(firebox: Firebox_15544): FireboxConstraints[firebox.Self] =
        import afpma.firecalc.engine.impl.en15544.instances.given
        val inst: FireboxConstraints[? <: Firebox_15544] = firebox match
            case _: Ecolabeled                             => summon[FireboxConstraints[Ecolabeled]]
            case _: TraditionalFirebox                     => summon[FireboxConstraints[TraditionalFirebox]]
            case _: AFPMA_PRSE                             => summon[FireboxConstraints[AFPMA_PRSE]]
            case _: Firebox_15544.SingleTested             => summon[FireboxConstraints[Firebox_15544.SingleTested]]
            case _: Firebox_15544.Door15aFirebox_Catalog   =>
                summon[FireboxConstraints[Firebox_15544.Door15aFirebox_Catalog]]
            case _: Firebox_15544.Traditional.CustomForLab =>
                summon[FireboxConstraints[Firebox_15544.Traditional.CustomForLab]]
            case _: Firebox_15544.Traditional              => summon[FireboxConstraints[Firebox_15544]]
            case _: Firebox_15544.CertifiedDesign          => summon[FireboxConstraints[Firebox_15544]]
        inst.asInstanceOf[FireboxConstraints[firebox.Self]]
