/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake

trait Pipes_13384_WithFlowOnlyAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithFlowOnlyAirIntake:
    override type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
    override type ChimneyPipe  = ChimneyPipe_Module.PipeCanBe

trait Pipes_13384_WithThermalAirIntake extends Pipes_13384_Alg with HasPipeModules_13384_WithThermalAirIntake:
    override type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
    override type ChimneyPipe  = ChimneyPipe_Module.PipeCanBe

/** Narrower variant used by EN 15544 composition to avoid inheriting `connector`/`chimney` from `Pipes_13384_Alg`.
  * Post-firebox results come from the 15544 N-pipe tagged vector instead.
  */
trait Pipes_13384_WithFlowOnlyAirIntake_PreFireboxOnly extends HasPipeModules_13384_WithFlowOnlyAirIntake

/** Narrower variant used by EN 15544 composition to avoid inheriting `connector`/`chimney` from `Pipes_13384_Alg`.
  * Post-firebox results come from the 15544 N-pipe tagged vector instead.
  */
trait Pipes_13384_WithThermalAirIntake_PreFireboxOnly extends HasPipeModules_13384_WithThermalAirIntake
