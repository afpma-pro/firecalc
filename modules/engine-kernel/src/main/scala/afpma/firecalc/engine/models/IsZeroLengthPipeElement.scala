/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.domain.IsZeroLengthPipeElement

/**
 * Engine-side predicate on `PipeSectionResult`.
 *
 * The `IsZeroLengthPipeElement` trait itself lives in `domain` so it can be
 * mixed into both engine concrete classes and DTO concrete classes. This file
 * keeps only the engine-specific dispatcher on `PipeSectionResult` (which is
 * an engine-kernel type and cannot move).
 */
extension (psr: PipeSectionResult[?])
    def isZeroLengthPipeElement: Boolean = psr.descr match
        case _: IsZeroLengthPipeElement => true
        case _                          => false
