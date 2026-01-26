/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.models.Pipes_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.models.Pipes_15544_Strict
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_Strict
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544_Strict
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithFlowOnlyAirIntake


trait HasTypeMembers_15544_Strict extends HasTypeMembers_15544_Alg
    with HasTypeMembers_13384_WithFlowOnlyAirIntake
    with HasPipeModules_15544_Strict:

    self =>

    final override type Pipes_15544  = Pipes_15544_Strict
    final override type Inputs_15544 = Inputs_15544_Strict