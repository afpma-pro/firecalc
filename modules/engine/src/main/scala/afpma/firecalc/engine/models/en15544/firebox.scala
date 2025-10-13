/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import cats.data.ValidatedNel

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait FireboxModule_EN15544_Strict:

    type FB <: Firebox_15544

    extension (firebox: FB)
        def toCombustionAirPipe_EN15544: ValidatedNel[String, CombustionAirPipe_Module_EN15544.FullDescr]
        def toFireboxPipe_EN15544: ValidatedNel[String, FireboxPipe_Module_EN15544.FullDescr]

object FireboxModule_EN15544_Strict:
    type Aux[FB0 <: Firebox_15544] = FireboxModule_EN15544_Strict {
        type FB = FB0
    }

trait FireboxModule_EN15544_MCE:

    type FB <: Firebox_15544

    extension (firebox: FB)
        def toCombustionAirPipe_EN13384: ValidatedNel[String, CombustionAirPipe_Module_EN13384.FullDescr]
        def toFireboxPipe_EN13384: ValidatedNel[String, FireboxPipe_Module_EN13384.FullDescr]

object FireboxModule_EN15544_MCE:
    type Aux[FB0 <: Firebox_15544] = FireboxModule_EN15544_MCE {
        type FB = FB0
    }