/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.typedefs.*

trait Inputs_13384_Alg extends HasPipeModules_13384_Alg:
    self =>

    /**
     * Pipes type. Upper bound widened from `Pipes_13384_Alg` to
     * `HasPipeModules_13384_Alg` (mirrors the widening in
     * `HasTypeMembers_13384_Alg`) so that EN 15544's `Pipes_15544_*` case
     * classes — which no longer extend `Pipes_13384_Alg` — satisfy the bound.
     */
    type Pipes_13384 <: HasPipeModules_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
    }

    val pipes               : Pipes_13384
    val nationalAcceptedData: NationalAcceptedData
    val fuelType            : FuelType
    val localConditions     : LocalConditions
    val flueGasCondition    : FlueGasCondition
