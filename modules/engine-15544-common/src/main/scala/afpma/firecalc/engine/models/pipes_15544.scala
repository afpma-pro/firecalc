/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Alg

trait Pipes_15544_Alg extends Pipes_13384_Alg with HasPipeModules_15544Only_Alg:
    val combustionAir: CombustionAirPipe_Module.PipeCanBe
    val firebox      : FireboxPipe_Module.PipeCanBe
    val flue         : FluePipe_Module.PipeCanBe
