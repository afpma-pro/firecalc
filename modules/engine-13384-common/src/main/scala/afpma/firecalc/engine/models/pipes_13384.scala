/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg

trait Pipes_13384_Alg extends HasPipeModules_13384_Alg:
    type ConnectorPipe
    type ChimneyPipe
    val airIntake: AirIntakePipe_Module.PipeCanBe
    val connector: ConnectorPipe
    val chimney  : ChimneyPipe
