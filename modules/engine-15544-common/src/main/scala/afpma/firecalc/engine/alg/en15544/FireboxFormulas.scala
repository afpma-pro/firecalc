/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.Area

import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.AreaCalcMethod
import afpma.firecalc.engine.models.en15544.typedefs.*

/**
 * Typeclass providing formula implementations for EN 15544 firebox sizing.
 *
 * Each method returns a function computing the relevant quantity.
 * The `firebox` parameter is passed explicitly to allow firebox-specific
 * formula overrides (though the default instance ignores it).
 */
trait FireboxFormulas[-F]:

    extension (firebox: F)
        /** Section "4.3.1.2", "Firebox surface" – formula (3): O_BR = 900·m_B (printed p. 11) */
        def O_BR_calc: m_B => O_BR

        /**
         * Our own input switch: derive the surface from the cubic dimensions,
         * or take a manual override.  No EN 15544 clause prescribes it — the
         * identity of the surface itself belongs to [[area_O_BR]].
         */
        def area_calc_method: AreaCalcMethod

        /**
         * The BUILT interior chamber surface: EN 15544:2023 §3.7 « surface de
         * la chambre de combustion » (printed p. 8), measured per the §4.3.1.2
         * note « comme si l'ouverture n'existait pas » (printed p. 11).  Not
         * the §4.3.1.2 design target ([[O_BR_calc]]).
         */
        def area_O_BR: Area

        /**
         * The glass-area cap: the glass surface of a stove door must not
         * exceed one fifth of the BUILT interior surface of the combustion
         * chamber — EN 15544:2023 §3.7 « surface de la chambre de
         * combustion », the sum of the interior surfaces (printed p. 8),
         * measured per the §4.3.1.2 note « comme si l'ouverture
         * n'existait pas » (printed p. 11); this code's [[area_O_BR]].
         * Authority for the cap itself: EN 15544:2023 clause 1, printed
         * p. 7 — « Le présent document ne s'applique pas dans les cas
         * suivants : […] chambres de combustion avec des plaques de verre
         * supérieures à 1/5 de la surface de la chambre de combustion ».
         * That is a scope exclusion, not a dimensioning rule: a chamber at
         * or under 1/5 stays inside the standard.  The denominator is never
         * the §4.3.1.2 Formule (3) design target 900·m_B (printed p. 11,
         * [[O_BR_calc]]): it shares only the letter O_BR with the surface
         * the cap measures against.  Reinforced by MB10-2022 printed p. 8
         * and MB17 printed p. 5, both worded against the interior surface
         * of the combustion chamber.  Open question, not a rule: whether
         * certified Ecolabeled stoves are still held to an O_BR/6 limit —
         * 1/6 appears in no current source (neither EN 15544:2023 nor the
         * 2022 Merkblatts), and this code enforces 1/5 for every kind.
         * This scaladoc is the single prose owner of that authority chain;
         * other sites cite it, they do not restate it.
         */
        def firebox_glass_surface_ratio_below_one_fifth: Boolean

        /** Section "4.3.1.3" – Minimum firebox base area A_BR_min */
        def A_BR_min_calc: m_B => A_BR

        /** Section "4.3.1.3" – Maximum firebox base area A_BR_max */
        def A_BR_max_calc: (m_B, U_BR) => A_BR

        /** Section "4.3.1.4" – Minimum firebox height H_BR_min */
        def H_BR_min_calc: m_B => H_BR

        /** Section "4.3.1.4" – Calculated firebox height H_BR */
        def H_BR_calc: (m_B, A_BR, U_BR) => H_BR

end FireboxFormulas

object FireboxFormulas:

    def apply[F <: Firebox_15544](using ev: FireboxFormulas[F]): FireboxFormulas[F] = ev

end FireboxFormulas
