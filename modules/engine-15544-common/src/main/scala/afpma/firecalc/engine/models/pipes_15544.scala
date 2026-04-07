/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Alg
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_MCE
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Strict

sealed trait Pipes_15544_Alg extends Pipes_13384_Alg with HasPipeModules_15544Only_Alg:
    val combustionAir: CombustionAirPipe_Module.PipeCanBe
    val firebox      : FireboxPipe_Module.PipeCanBe
    val flue         : FluePipe_Module.PipeCanBe

// object Pipes_15544_Alg:
//     given conv: Conversion[Pipes_15544_Alg, Pipes_13384_Alg] =
//         case s: Pipes_15544_Strict => s: Pipes_13384_For_EN15544_Strict
//         case mce: Pipes_15544_MCE  => mce: Pipes_13384_For_EN15544_MCE

case class Pipes_15544_Strict(
    val airIntake    : FlowOnlyAirIntakePipe_13384,
    val combustionAir: CombustionAirPipe_15544,
    val firebox      : FireboxPipe_15544,
    val flue         : FluePipe_15544,
    val connector    : ConnectorPipe,
    val chimney      : ChimneyPipe
) extends Pipes_15544_Alg
    with HasPipeModules_15544Only_Strict
    with Pipes_13384_WithFlowOnlyAirIntake

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
