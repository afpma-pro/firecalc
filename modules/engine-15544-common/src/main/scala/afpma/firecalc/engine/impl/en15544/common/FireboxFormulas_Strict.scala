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

        // ── Glass-area cap ≤ 1/5 — EN 15544:2023 clause 1 + MB10/MB17 ─────
        //
        // The 1/5 cap IS in EN 15544:2023 — clause 1 (Domaine d'application)
        // excludes combustion chambers with glass plates above 1/5 of the
        // chamber surface; the verbatim text is quoted in the banner above
        // `glassArea_constraints` in `FireboxConstraints_Strict`.  The KOV
        // technical papers carry the same 1/5 affirmatively (MB10-2022 §3
        // "Détails d'exécution", English p. 8 / French p. 10; MB17 §3,
        // English p. 5 / French p. 6).
        //
        // The denominator below is the BUILT interior surface (`area_O_BR`
        // above): §3.7 defines "surface de la chambre de combustion" as
        // "somme des surfaces intérieures de la chambre de combustion", so
        // the cap is measured against all walls + ceiling + base + glazing
        // as built — not against the §4.3.1.2 REQUIRED surface (formula (3),
        // 900·m_B) that `ctx.O_BR` carries on the constraint path.
        //
        // 1/6 appears nowhere in the 2023 text; the UZ37 workbook's O_BR/6
        // predates this edition, and whether it still governs a certified
        // UZ37 stove is an open question to KOV (tracker ticket 20 item 2);
        // until answered, 1/5 stands for every kind.

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
