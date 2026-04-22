/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Marker trait for singular flow-resistance pipe elements (fittings, constrictions
 * with known ζ coefficient) across all descriptor hierarchies.
 *
 * Extends `IsZeroLengthPipeElement` — singular resistances carry a ζ coefficient
 * but no physical length.
 */
trait IsSingularFlowResistance extends IsZeroLengthPipeElement

extension (psr: PipeSectionResult[?])
    def isSingularFlowResistance: Boolean = psr.descr match
        case _: IsSingularFlowResistance => true
        case _                           => false
