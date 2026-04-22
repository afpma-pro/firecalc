/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Marker trait for idealized pressure-difference pipe elements (fan, pump, or
 * user-imposed Δp) across all descriptor hierarchies.
 *
 * Extends `IsZeroLengthPipeElement` — pressure-diff elements inject a Δp at a
 * point but have no physical length.
 */
trait IsPressureDiff extends IsZeroLengthPipeElement

extension (psr: PipeSectionResult[?])
    def isPressureDiff: Boolean = psr.descr match
        case _: IsPressureDiff => true
        case _                 => false
