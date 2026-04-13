/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.*
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.toFunctorOps
import cats.syntax.all.toFlatMapOps

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en15544.FireboxSizing_15544_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok

import en15544_typedefs.*

class FireboxSizing_15544_Common(
    val firebox: Firebox_15544,
    val m_B    : m_B
) extends FireboxSizing_15544_Alg
    with FireboxOps:
    import en15544_typedefs.*

    // Section "4.3.1.2", "Firebox surface"
    override def O_BR: O_BR = firebox.O_BR_calc(m_B)

    override def U_BR: U_BR =
        firebox.dimensions.base.perimeter

    // Section "4.3.1.3", "Firebox base"

    val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double = firebox.FLOOR_DEPTH_TO_WIDTH_MIN_RATIO
    val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double = firebox.FLOOR_DEPTH_TO_WIDTH_MAX_RATIO

    override def A_BR_min: Option[A_BR] =
        firebox.ifNotSingleTested(orElse = None)(
            firebox.A_BR_min_calc(m_B).some
        )

    override def A_BR_max: Option[A_BR] =
        firebox.ifNotSingleTested(orElse = None)(
            U_BR.map(ubr => firebox.A_BR_max_calc(m_B, ubr).some)
        )

    override def A_BR: A_BR =
        firebox.dimensions.base.area

    // Section "4.3.1.4", "Firebox height"
    override def H_BR_min: Option[H_BR] =
        firebox.ifNotSingleTested(orElse = None)(
            firebox.H_BR_min_calc(m_B).some
        )

    override def H_BR: H_BR =
        for
            abr <- A_BR
            ubr <- U_BR
        yield firebox.H_BR_calc(m_B, abr, ubr)

end FireboxSizing_15544_Common
