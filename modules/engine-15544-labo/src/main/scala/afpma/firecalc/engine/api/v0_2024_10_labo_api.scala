/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

/**
 * EN 15544 labo-mode API object.
 * Provides access to labo-specific traits via `v0_2024_10_labo.TraitName`.
 */
object v0_2024_10_labo
    extends v0_2024_10_core
    with v0_2024_10_13384_members
    with v0_2024_10_mce_members
    with v0_2024_10_labo_members
