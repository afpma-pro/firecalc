/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.FluePipe_Module_15544

trait HasPipeModules_15544Only_Strict extends HasPipeModules_15544Only_Alg:
    override final type CombustionAirPipe_Module_T = CombustionAirPipe_Module_15544.type
    override final type FireboxPipe_Module_T       = FireboxPipe_Module_15544.type
    override final type FluePipe_Module_T          = FluePipe_Module_15544.type

    override final val CombustionAirPipe_Module = CombustionAirPipe_Module_15544
    override final val FireboxPipe_Module       = FireboxPipe_Module_15544
    override final val FluePipe_Module          = FluePipe_Module_15544
