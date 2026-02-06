/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544
import afpma.firecalc.engine.models.CombustionAirPipe_Module_Generic
import afpma.firecalc.engine.models.FireboxPipe_Module_Generic
import afpma.firecalc.engine.models.FluePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

trait HasPipeModules_15544Only_Alg:

    type CombustionAirPipe_Module_T <: CombustionAirPipe_Module_Generic[DraftCondition]
    type FireboxPipe_Module_T <: FireboxPipe_Module_Generic[DraftCondition]
    type FluePipe_Module_T <: FluePipe_Module_Generic[DraftCondition]

    val CombustionAirPipe_Module: CombustionAirPipe_Module_T
    val FireboxPipe_Module      : FireboxPipe_Module_T
    val FluePipe_Module         : FluePipe_Module_T
