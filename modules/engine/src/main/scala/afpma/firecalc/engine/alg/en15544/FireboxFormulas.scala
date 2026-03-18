/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.Area

import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.AreaCalcMethod
import afpma.firecalc.engine.models.en15544.typedefs.*

/** Typeclass providing formula implementations for EN 15544 firebox sizing.
 *
 * Each method returns a function computing the relevant quantity.
 * The `firebox` parameter is passed explicitly to allow firebox-specific
 * formula overrides (though the default instance ignores it).
 */
trait FireboxFormulas[-F <: Firebox_15544]:

    extension (firebox: F)
        /** Section "4.3.1.1" – Firebox surface area O_BR */
        def O_BR_calc: m_B => O_BR

        /** See EN 15544 – Section 4.3.1.2 – Firebox area calculation method.
         *
         * Implementations typically return [[AreaCalcMethod.AutoIfCubic]] unless
         * a manual area override is specified.
         */
        def area_calc_method: AreaCalcMethod

        /** See EN 15544 – Section 4.3.1.2 – Total enclosing firebox surface area. */
        def area_O_BR: Area

        /** See EN 15544 – Section 4.3.1.2 – Derived boolean: glass area ≤ 1/5 of
         *  total enclosing firebox surface area.
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
