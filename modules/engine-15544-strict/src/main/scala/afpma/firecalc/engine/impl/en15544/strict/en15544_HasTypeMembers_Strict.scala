/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.Pipes_15544_Strict
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544_Strict
import afpma.firecalc.engine.models.en15544.Inputs_15544_Strict

trait HasTypeMembers_15544_Strict
    extends HasTypeMembers_15544_Alg
    with HasTypeMembers_13384_WithFlowOnlyAirIntake
    with HasPipeModules_15544_Strict:

    self =>

    override final type Pipes_15544  = Pipes_15544_Strict
    override final type Inputs_15544 = Inputs_15544_Strict
