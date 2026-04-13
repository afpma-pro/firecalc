/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.models.en15544.typedefs.*

/** Firebox sizing algebra (Section 4.3 of EN 15544:2023) */
trait FireboxSizing_15544_Alg:

    // Section "4.3.1.2", "Firebox surface"
    def U_BR: U_BR

    def O_BR: O_BR

    // Section "4.3.1.3", "Firebox base"

    val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double
    val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double

    def A_BR_min: Option[A_BR]
    def A_BR_max: Option[A_BR]
    def A_BR    : A_BR

    // Section "4.3.1.4", "Firebox height"
    def H_BR_min: Option[H_BR]
    def H_BR    : H_BR
