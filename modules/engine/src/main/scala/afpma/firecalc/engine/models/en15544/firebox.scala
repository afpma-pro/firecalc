/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

trait FireboxModule_15544_Strict:

    type FB <: Firebox_15544

    extension (firebox: FB)
        def toCombustionAirPipe_15544
            : ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_15544.FullDescr]
        def toFireboxPipe_15544: ValidatedNel[IncrementalValidation_Error, FireboxPipe_Module_15544.FullDescr]

object FireboxModule_15544_Strict:
    type Aux[FB0 <: Firebox_15544] = FireboxModule_15544_Strict {
        type FB = FB0
    }

trait FireboxModule_15544_MCE:

    type FB <: Firebox_15544

    extension (firebox: FB)
        def toCombustionAirPipe_13384
            : ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_13384.FullDescr]
        def toFireboxPipe_13384: ValidatedNel[IncrementalValidation_Error, FireboxPipe_Module_13384.FullDescr]

object FireboxModule_15544_MCE:
    type Aux[FB0 <: Firebox_15544] = FireboxModule_15544_MCE {
        type FB = FB0
    }
