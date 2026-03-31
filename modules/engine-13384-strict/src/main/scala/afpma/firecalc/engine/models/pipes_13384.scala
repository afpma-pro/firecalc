/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake

trait Pipes_13384_Alg extends HasPipeModules_13384_Alg:
    val airIntake: AirIntakePipe_Module.PipeCanBe
    val connector: ConnectorPipe
    val chimney  : ChimneyPipe

trait Pipes_13384_WithFlowOnlyAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithFlowOnlyAirIntake

trait Pipes_13384_WithThermalAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithThermalAirIntake
