/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Dimensional and coordinate constants for the air distribution box.
 *
 * The air distribution box sits below the firebox (which starts at Z = 0).
 * Its XY center aligns with the firebox center; its top is at Z = 0.
 */
object AirDistributionBox:
    /** Height of the air distribution box in meters (20 cm). */
    val Z_HEIGHT: Double = 0.20

    val CenterX: Double = FireboxCoordinateSystem.FireboxBaseCenterX
    val CenterY: Double = FireboxCoordinateSystem.FireboxBaseCenterY
    val CenterZ: Double = -Z_HEIGHT
