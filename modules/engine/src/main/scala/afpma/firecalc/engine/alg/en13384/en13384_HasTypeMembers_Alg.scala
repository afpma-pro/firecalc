/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import afpma.firecalc.engine.models.Pipes_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg


trait HasTypeMembers_13384_Alg extends HasPipeModules_13384_Alg:
    self =>

    type Pipes_13384 <: Pipes_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
    }

    type Inputs_13384 <: Inputs_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
        type Pipes_13384            = self.Pipes_13384
    }