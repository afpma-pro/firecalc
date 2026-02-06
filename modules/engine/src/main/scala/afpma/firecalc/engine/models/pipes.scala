/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Alg
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_MCE
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Strict

sealed trait Pipes_13384_Alg extends HasPipeModules_13384_Alg:
    val airIntake: AirIntakePipe_Module.PipeCanBe
    val connector: ConnectorPipe
    val chimney  : ChimneyPipe

trait Pipes_13384_WithFlowOnlyAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithFlowOnlyAirIntake

trait Pipes_13384_WithThermalAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithThermalAirIntake

sealed trait Pipes_15544_Alg extends Pipes_13384_Alg with HasPipeModules_15544Only_Alg:
    val combustionAir: CombustionAirPipe_Module.FullDescr
    val firebox      : FireboxPipe_Module.FullDescr
    val flue         : FluePipe_Module.FullDescr

// object Pipes_15544_Alg:
//     given conv: Conversion[Pipes_15544_Alg, Pipes_13384_Alg] =
//         case s: Pipes_15544_Strict => s: Pipes_13384_For_EN15544_Strict
//         case mce: Pipes_15544_MCE  => mce: Pipes_13384_For_EN15544_MCE

case class Pipes_15544_Strict(
    val airIntake    : FlowOnlyAirIntakePipe_13384,
    val combustionAir: CombustionAirPipe_Module_15544.FullDescr,
    val firebox      : FireboxPipe_Module_15544.FullDescr,
    val flue         : FluePipe_Module_15544.FullDescr,
    val connector    : ConnectorPipe,
    val chimney      : ChimneyPipe
) extends Pipes_15544_Alg
    with HasPipeModules_15544Only_Strict
    with Pipes_13384_WithFlowOnlyAirIntake

case class Pipes_15544_MCE(
    val airIntake    : ThermalAirIntakePipe_13384,
    val combustionAir: CombustionAirPipe_Module_13384.FullDescr,
    val firebox      : FireboxPipe_Module_13384.FullDescr,
    val flue         : FluePipe_Module_13384.FullDescr,
    val connector    : ConnectorPipe,
    val chimney      : ChimneyPipe
) extends Pipes_15544_Alg
    with HasPipeModules_15544Only_MCE
    with Pipes_13384_WithThermalAirIntake
