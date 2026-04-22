/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

/**
 * Marker trait for zero-length cross-section-change pipe elements across all
 * descriptor hierarchies (EN 15544 flow-only, EN 13384 flow-only, EN 13384 thermal).
 *
 * Used by validators and UI code to detect this kind of element structurally,
 * without importing each concrete variant. Any future engine that introduces its
 * own SectionGeometryChange variant should extend this trait to remain correctly
 * handled by existing consumers (velocity validation, graph rendering, etc.).
 */
trait IsSectionGeometryChange

extension (psr: PipeSectionResult[?])
    def isSectionGeometryChange: Boolean = psr.descr match
        case _: IsSectionGeometryChange => true
        case _                          => false
