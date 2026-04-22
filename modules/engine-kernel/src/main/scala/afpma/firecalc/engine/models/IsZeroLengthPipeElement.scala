/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Parent marker for all zero-length pipe elements across descriptor hierarchies
 * (EN 15544 flow-only, EN 13384 flow-only, EN 13384 thermal).
 *
 * Subtraits: `IsDirectionChange`, `IsSectionGeometryChange`, `IsSingularFlowResistance`,
 * `IsPressureDiff`. Tagging the four concrete element families with the per-kind marker
 * (which itself extends this parent) allows consumers to dispatch at either granularity:
 *
 *   - Specific kind:    `case _: IsDirectionChange => ...`
 *   - Any zero-length:  `case _: IsZeroLengthPipeElement => ...`
 *
 * Replaces the old ad-hoc union pattern
 * `case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => ...`
 * with a nameable abstraction at zero runtime cost.
 */
trait IsZeroLengthPipeElement

extension (psr: PipeSectionResult[?])
    def isZeroLengthPipeElement: Boolean = psr.descr match
        case _: IsZeroLengthPipeElement => true
        case _                          => false
