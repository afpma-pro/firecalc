/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_MCE

case class Pipes_15544_MCE(
    val airIntake    : ThermalAirIntakePipe_13384,
    val combustionAir: CombustionAirPipe_13384,
    val firebox      : FireboxPipe_13384,
    val flue         : FluePipe_13384,
    val connector    : ConnectorPipe,
    val chimney      : ChimneyPipe
) extends Pipes_15544_Alg
    with HasPipeModules_15544Only_MCE
    with Pipes_13384_WithThermalAirIntake
