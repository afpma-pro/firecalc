/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.engine.models.Pipes_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.Pipes_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_WithThermalAirIntake
import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg


trait HasTypeMembers_13384_WithThermalAirIntake 
    extends HasTypeMembers_13384_Alg 
    with HasPipeModules_13384_WithThermalAirIntake:

    self =>

    override type Pipes_13384  = Pipes_13384_WithThermalAirIntake
    override type Inputs_13384 = Inputs_13384_WithThermalAirIntake