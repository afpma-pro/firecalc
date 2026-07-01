/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

/**
 * Air distribution box constants.
 *
 * The air distribution box is a 20cm-high chamber below the firebox
 * (z = -0.20 to z = 0.00). Width and depth are borrowed from the firebox.
 * This is a UI-only concept with no DTO representation.
 *
 * These constants are promoted from the UI layer so the engine loader
 * can construct the same box without UI dependencies.
 */
object AirDistributionBox:
    /** Bottom of the air distribution box: 20cm below firebox base. */
    val Z_BOTTOM: Double = -0.20

    /** Fixed height of the air distribution box: 20cm. */
    val Z_HEIGHT: Double = 0.20

    // Width and depth are always borrowed from the firebox.
