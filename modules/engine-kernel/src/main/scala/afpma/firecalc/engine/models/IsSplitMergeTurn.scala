/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.domain.IsSplitMergeTurn

/**
 * Engine-side predicate on `PipeSectionResult`.
 *
 * The `IsSplitMergeTurn` trait itself lives in `domain` so it can be mixed
 * into both engine concrete classes (`SplitMerge90`) and DTO concrete classes
 * (`SplitSingleFlowIntoTwoFlowsWith90DegTurn`, `MergeTwoFlowsIntoSingleWith90DegTurn`).
 * This file keeps only the engine-specific dispatcher on `PipeSectionResult`.
 */
extension (psr: PipeSectionResult[?])
    def isSplitMergeTurn: Boolean = psr.descr match
        case _: IsSplitMergeTurn => true
        case _ => false
