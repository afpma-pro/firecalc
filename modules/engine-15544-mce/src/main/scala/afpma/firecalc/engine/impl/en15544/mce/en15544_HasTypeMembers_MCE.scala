/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.Pipes_15544_MCE
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_MCE
import afpma.firecalc.engine.models.en15544.Inputs_15544_MCE

trait HasTypeMembers_15544_MCE
    extends HasTypeMembers_15544_Alg
    with HasTypeMembers_13384_WithThermalAirIntake
    with HasPipeModules_15544Only_MCE:

    self =>

    override type Pipes_15544  = Pipes_15544_MCE
    override type Inputs_15544 = Inputs_15544_MCE
