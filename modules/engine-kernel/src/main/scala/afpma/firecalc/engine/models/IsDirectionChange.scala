/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.domain.IsDirectionChange

/**
 * Engine-side predicate on `PipeSectionResult`.
 *
 * The `IsDirectionChange` trait itself lives in `domain` (with
 * `IsZeroLengthPipeElement` as its parent) so it can be mixed into both engine
 * concrete classes and DTO concrete classes. This file keeps only the
 * engine-specific dispatcher on `PipeSectionResult`.
 */
extension (psr: PipeSectionResult[?])
    def isDirectionChange: Boolean = psr.descr match
        case _: IsDirectionChange => true
        case _ => false
