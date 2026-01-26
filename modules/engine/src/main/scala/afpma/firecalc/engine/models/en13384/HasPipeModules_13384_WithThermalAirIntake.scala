/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import afpma.firecalc.engine.models.Pipes_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.ThermalAirIntakePipe_Module_13384

trait HasPipeModules_13384_WithThermalAirIntake extends HasPipeModules_13384_Alg:
    override type AirIntakePipe_Module_T     = ThermalAirIntakePipe_Module_13384.type
    final override val AirIntakePipe_Module  = ThermalAirIntakePipe_Module_13384
