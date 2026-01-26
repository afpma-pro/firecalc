/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.engine.models.Pipes_13384_Alg
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.CombustionAirPipe_Module_Generic
import afpma.firecalc.engine.models.FireboxPipe_Module_Generic
import afpma.firecalc.engine.models.FluePipe_Module_Generic
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.FluePipe_Module_15544

trait HasPipeModules_15544Only_Strict extends HasPipeModules_15544Only_Alg:
    final override type CombustionAirPipe_Module_T     = CombustionAirPipe_Module_15544.type
    final override type FireboxPipe_Module_T           = FireboxPipe_Module_15544.type
    final override type FluePipe_Module_T              = FluePipe_Module_15544.type

    final override val CombustionAirPipe_Module        = CombustionAirPipe_Module_15544
    final override val FireboxPipe_Module              = FireboxPipe_Module_15544
    final override val FluePipe_Module                 = FluePipe_Module_15544
