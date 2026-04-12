/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.models.en13384.Inputs_13384_Alg

trait HasTypeMembers_13384_Alg extends HasPipeModules_13384_Alg:
    self =>

    /**
     * Abstract pipes type. Upper bound widened from `Pipes_13384_Alg` to
     * `HasPipeModules_13384_Alg` so that EN 15544's `Pipes_15544_*` case
     * classes (which mix in only the pre-firebox-only trait variants)
     * satisfy this type member.
     *
     * Standalone EN 13384 still fixes this to `Pipes_13384_WithFlowOnlyAirIntake`
     * / `Pipes_13384_WithThermalAirIntake`, which DO extend `Pipes_13384_Alg`
     * and carry `connector`/`chimney` fields — see `postFireboxChainResults`.
     */
    type Pipes_13384 <: HasPipeModules_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
    }

    type Inputs_13384 <: Inputs_13384_Alg {
        type AirIntakePipe_Module_T = self.AirIntakePipe_Module_T
        type Pipes_13384            = self.Pipes_13384
    }
