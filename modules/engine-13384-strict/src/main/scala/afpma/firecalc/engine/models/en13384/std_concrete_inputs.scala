/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.typedefs.*

case class Inputs_13384_WithFlowOnlyAirIntake(
    pipes               : Pipes_13384_WithFlowOnlyAirIntake,
    nationalAcceptedData: NationalAcceptedData,
    fuelType            : FuelType,
    localConditions     : LocalConditions,
    flueGasCondition    : FlueGasCondition
) extends Inputs_13384_Alg
    with HasPipeModules_13384_WithFlowOnlyAirIntake:
    override type Pipes_13384 = Pipes_13384_WithFlowOnlyAirIntake

case class Inputs_13384_WithThermalAirIntake(
    pipes               : Pipes_13384_WithThermalAirIntake,
    nationalAcceptedData: NationalAcceptedData,
    fuelType            : FuelType,
    localConditions     : LocalConditions,
    flueGasCondition    : FlueGasCondition
) extends Inputs_13384_Alg
    with HasPipeModules_13384_WithThermalAirIntake:
    override type Pipes_13384 = Pipes_13384_WithThermalAirIntake
