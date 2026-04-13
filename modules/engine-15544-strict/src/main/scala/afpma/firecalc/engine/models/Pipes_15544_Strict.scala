/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Strict

case class Pipes_15544_Strict(
    val airIntake    : FlowOnlyAirIntakePipe_13384,
    val combustionAir: CombustionAirPipe_15544,
    val firebox      : FireboxPipe_15544
) extends Pipes_15544_Alg
    with HasPipeModules_15544Only_Strict
    with Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly
