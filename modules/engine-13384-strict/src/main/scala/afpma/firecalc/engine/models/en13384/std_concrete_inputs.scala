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

/** Pre-firebox-only flavour used by EN 15544's `en13384_inputs` — the `pipes`
  * member is narrower (`Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly`) so
  * it carries only `airIntake`, not legacy `connector`/`chimney`.
  */
case class Inputs_13384_WithFlowOnlyAirIntake_PreFireboxOnly(
    pipes               : Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly,
    nationalAcceptedData: NationalAcceptedData,
    fuelType            : FuelType,
    localConditions     : LocalConditions,
    flueGasCondition    : FlueGasCondition
) extends Inputs_13384_Alg
    with HasPipeModules_13384_WithFlowOnlyAirIntake:
    override type Pipes_13384 = Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly

/** Pre-firebox-only flavour used by EN 15544 MCE's `en13384_inputs`. */
case class Inputs_13384_WithThermalAirIntake_PreFireboxOnly(
    pipes               : Pipes_13384_WithThermalAirIntake_PreFireboxOnly,
    nationalAcceptedData: NationalAcceptedData,
    fuelType            : FuelType,
    localConditions     : LocalConditions,
    flueGasCondition    : FlueGasCondition
) extends Inputs_13384_Alg
    with HasPipeModules_13384_WithThermalAirIntake:
    override type Pipes_13384 = Pipes_13384_WithThermalAirIntake_PreFireboxOnly
