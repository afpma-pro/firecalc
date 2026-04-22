/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Marker trait for direction-change (bend / angle) pipe elements across all
 * descriptor hierarchies (EN 15544 flow-only, EN 13384 flow-only, EN 13384 thermal).
 *
 * Tag concrete `DirectionChange` sealed abstract parents with this trait so
 * consumers can detect the kind structurally without importing per-standard
 * concrete types and without resorting to length-based heuristics
 * (which over-match other zero-length elements like SingularFlowResistance
 * or PressureDiff).
 */
trait IsDirectionChange extends IsZeroLengthPipeElement

extension (psr: PipeSectionResult[?])
    def isDirectionChange: Boolean = psr.descr match
        case _: IsDirectionChange => true
        case _                    => false
