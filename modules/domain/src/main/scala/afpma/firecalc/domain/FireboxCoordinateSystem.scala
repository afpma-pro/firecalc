/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Convention-based coordinate system constants for the firebox.
 *
 * The firebox is centered at (0, 0) in the horizontal plane, with its bottom
 * at Z = 0. All positions relative to the firebox use these constants so that
 * the coordinate system is explicit rather than implicit via literal zeros.
 */
object FireboxCoordinateSystem:
    val FireboxBaseCenterX: Double = 0.0
    val FireboxBaseCenterY: Double = 0.0
    val FireboxBaseCenterZ: Double = 0.0

    /**
     * Threshold for verticality check: sin(elevation) > 0.99 ⟺ within ~8° of vertical.
     * Shared with [[PipePositionComputer.computeSplitPosition]].
     */
    val NearlyVerticalThreshold: Double = 0.99
