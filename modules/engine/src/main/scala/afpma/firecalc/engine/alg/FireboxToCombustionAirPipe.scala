/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

trait FireboxToCombustionAirPipe[FB <: Firebox_15544]:

    type CombustionAirPipe_FullDescr
    
    extension (firebox: FB)
        def toCombustionAirPipe_FullDescr: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_FullDescr]