/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.models.Pipes_13384_WithThermalAirIntake_PreFireboxOnly
import afpma.firecalc.engine.models.Pipes_15544_MCE
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.en13384.Inputs_13384_WithThermalAirIntake_PreFireboxOnly
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_MCE
import afpma.firecalc.engine.models.en15544.Inputs_15544_MCE

trait HasTypeMembers_15544_MCE
    extends HasTypeMembers_15544_Alg
    with HasPipeModules_13384_WithThermalAirIntake
    with HasPipeModules_15544Only_MCE:

    self =>

    override type Pipes_15544  = Pipes_15544_MCE
    override type Inputs_15544 = Inputs_15544_MCE

    // Post Phase C: EN 15544 MCE's 13384 adapter uses the pre-firebox-only variants so
    // `Pipes_15544_MCE` (which extends `Pipes_13384_WithThermalAirIntake_PreFireboxOnly`
    // and has no legacy connector/chimney fields) satisfies the widened
    // `HasTypeMembers_13384_Alg.Pipes_13384` upper bound.
    override final type Pipes_13384  = Pipes_13384_WithThermalAirIntake_PreFireboxOnly
    override final type Inputs_13384 = Inputs_13384_WithThermalAirIntake_PreFireboxOnly
