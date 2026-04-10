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

    type Pipes_13384 <: Pipes_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
    }

    val pipes               : Pipes_13384
    val nationalAcceptedData: NationalAcceptedData
    val fuelType            : FuelType
    val localConditions     : LocalConditions
    val flueGasCondition    : FlueGasCondition
