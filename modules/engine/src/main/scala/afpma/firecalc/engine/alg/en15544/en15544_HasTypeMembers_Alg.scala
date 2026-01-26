/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.alg.en13384.HasTypeMembers_13384_Alg
import afpma.firecalc.engine.models.CombustionAirPipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.FireboxPipe_Module_Generic
import afpma.firecalc.engine.models.FluePipe_Module_Generic
import afpma.firecalc.engine.models.Pipes_15544_Alg
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_Alg
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Alg


trait HasTypeMembers_15544_Alg extends HasTypeMembers_13384_Alg with HasPipeModules_15544Only_Alg:
    self =>

    type Pipes_15544 <: Pipes_15544_Alg {
        type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
        type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
        type FluePipe_Module_T          = self.FluePipe_Module_T
    }
    
    type Inputs_15544 <: Inputs_15544_Alg {
        type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
        type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
        type FluePipe_Module_T          = self.FluePipe_Module_T
        type Pipes_15544                = self.Pipes_15544
    }
    