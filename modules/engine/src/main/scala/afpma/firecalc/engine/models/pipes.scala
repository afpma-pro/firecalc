/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

sealed trait Pipes_EN13384_Alg:
    val airIntake: AirIntakePipe
    val connector: ConnectorPipe
    val chimney: ChimneyPipe

case class Pipes_EN13384(
    val airIntake: AirIntakePipe,
    val connector: ConnectorPipe,
    val chimney: ChimneyPipe
) extends Pipes_EN13384_Alg

sealed trait Pipes_EN15544_Alg extends Pipes_EN13384_Alg:
    type CombustionAirPipe_Module_T <: CombustionAirPipe_Module_Generic[DraftCondition]
    type FireboxPipe_Module_T            <: FireboxPipe_Module_Generic[DraftCondition]
    type FluePipe_Module_T                         <: FluePipe_Module_Generic[DraftCondition]
    
    val CombustionAirPipe_Module: CombustionAirPipe_Module_T
    val FireboxPipe_Module: FireboxPipe_Module_T
    val FluePipe_Module: FluePipe_Module_T

    val combustionAir: CombustionAirPipe_Module.FullDescr
    val firebox: FireboxPipe_Module.FullDescr
    val flue: FluePipe_Module.FullDescr

object Pipes_EN15544_Alg:
    given Conversion[Pipes_EN15544_Alg, Pipes_EN13384] = p =>
        Pipes_EN13384(p.airIntake, p.connector, p.chimney)

case class Pipes_EN15544_Strict(
    val airIntake: AirIntakePipe,
    val combustionAir: CombustionAirPipe_Module_EN15544.FullDescr,
    val firebox: FireboxPipe_Module_EN15544.FullDescr,
    val flue: FluePipe_Module_EN15544.FullDescr,
    val connector: ConnectorPipe,
    val chimney: ChimneyPipe
) extends Pipes_EN15544_Alg:

    type CombustionAirPipe_Module_T = CombustionAirPipe_Module_EN15544.type
    type FireboxPipe_Module_T            = FireboxPipe_Module_EN15544.type
    type FluePipe_Module_T                         = FluePipe_Module_EN15544.type

    val CombustionAirPipe_Module = CombustionAirPipe_Module_EN15544
    val FireboxPipe_Module            = FireboxPipe_Module_EN15544
    val FluePipe_Module                         = FluePipe_Module_EN15544

case class Pipes_EN15544_MCE(
    val airIntake: AirIntakePipe,
    val combustionAir: CombustionAirPipe_Module_EN13384.FullDescr,
    val firebox: FireboxPipe_Module_EN13384.FullDescr,
    val flue: FluePipe_Module_EN13384.FullDescr,
    val connector: ConnectorPipe,
    val chimney: ChimneyPipe
) extends Pipes_EN15544_Alg:
    type CombustionAirPipe_Module_T = CombustionAirPipe_Module_EN13384.type
    type FireboxPipe_Module_T            = FireboxPipe_Module_EN13384.type
    type FluePipe_Module_T                         = FluePipe_Module_EN13384.type

    val CombustionAirPipe_Module = CombustionAirPipe_Module_EN13384
    val FireboxPipe_Module            = FireboxPipe_Module_EN13384
    val FluePipe_Module                         = FluePipe_Module_EN13384