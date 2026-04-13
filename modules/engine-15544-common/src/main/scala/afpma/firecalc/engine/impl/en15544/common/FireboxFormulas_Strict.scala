/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.AreaCalcMethod
import afpma.firecalc.engine.models.en15544.typedefs.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

/**
 * Default EN 15544 formula implementations.
 *
 * The `firebox` parameter is intentionally ignored by most formulas: the
 * standard formulas depend only on the fuel mass and base dimensions.
 */
given fireboxFormulas_Strict: FireboxFormulas[Firebox_15544] =
    FireboxFormulas_Strict.make[Firebox_15544]

object FireboxFormulas_Strict:
    def make[F <: Firebox_15544] = new FireboxFormulas_Strict[F] {}

/**
 * Base class for formula instances that override only specific methods.
 *
 * Delegates all methods to [[defaultFireboxFormulas]] by upcasting the
 * concrete firebox to [[Firebox_15544]].
 */
trait FireboxFormulas_Strict[F <: Firebox_15544] extends FireboxFormulas[F]:

    extension (firebox: F)

        // ── Section "4.3.1.1" – Firebox surface area O_BR ────────────────────

        override def O_BR_calc: m_B => O_BR =
            mb => (900 * mb.value).squareCentimeters

        // ── Section "4.3.1.2" – Total enclosing firebox surface area ──────────

        override def area_calc_method: AreaCalcMethod =
            AreaCalcMethod.AutoIfCubic

        override def area_O_BR: Area =
            firebox.area_calc_method match
                case AreaCalcMethod.AutoIfCubic =>
                    val base_or_ceiling = firebox.dimensions.base match
                        case Firebox_15544.Dimensions.Base.Squared(w, d) => w * d
                    val left_or_right   = firebox.dimensions.base match
                        case Firebox_15544.Dimensions.Base.Squared(w, d) =>
                            firebox.dimensions.height * d
                    val front_or_back   = firebox.dimensions.base match
                        case Firebox_15544.Dimensions.Base.Squared(w, d) =>
                            firebox.dimensions.height * w
                    2 * (base_or_ceiling + left_or_right + front_or_back)
                case AreaCalcMethod.Manual(v)   => v

        override def firebox_glass_surface_ratio_below_one_fifth: Boolean =
            (firebox.glass_area / firebox.area_O_BR) <= (1.0 / 5.0)

        // ── Section "4.3.1.3" – Firebox base ─────────────────────────────────

        override def A_BR_min_calc: m_B => A_BR =
            mb => (100 * mb.value).squareCentimeters

        override def A_BR_max_calc: (m_B, U_BR) => A_BR =
            (mb, ubr) =>
                (
                    ((900 * mb.value) - (25 + mb.value) * ubr.toUnit[Centimeter].value)
                        / 2.0
                ).squareCentimeters

        // ── Section "4.3.1.4" – Firebox height ───────────────────────────────

        override def H_BR_min_calc: m_B => H_BR =
            mb => (25.0 + mb.value).cm

        override def H_BR_calc: (m_B, A_BR, U_BR) => H_BR =
            (mb, abr, ubr) =>
                (
                    (900.0 * mb.toUnit[Kilogram].value
                        - 2.0 * abr.toUnit[(Centimeter ^ 2)].value)
                        / ubr.toUnit[Centimeter].value
                ).cm

end FireboxFormulas_Strict
