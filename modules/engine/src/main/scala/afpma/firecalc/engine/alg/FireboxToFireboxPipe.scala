/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

trait FireboxToFireboxPipe[FB <: Firebox_15544]:
    
    type FireboxPipe_FullDescr

    extension (firebox: FB)
        def toFireboxPipe_FullDescr: ValidatedNel[IncrementalValidation_Error, FireboxPipe_FullDescr]