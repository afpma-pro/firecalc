/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg
import afpma.firecalc.engine.models.en15544.HasPipeModules_15544Only_Alg

/**
 * EN 15544 pipes algebra — pre-firebox pipes only.
 *
 * Post Phase C remediation, this trait no longer extends `Pipes_13384_Alg`
 * and no longer owns `flue` / `connector` / `chimney` fields. Post-firebox
 * results are sourced from the N-pipe tagged vector
 * (`postFireboxPipeSlots` / `postFireboxPipeResults`) instead.
 *
 * It still extends `HasPipeModules_13384_Alg` so the pre-firebox air-intake
 * pipe can be modelled with the EN 13384 module types.
 */
trait Pipes_15544_Alg extends HasPipeModules_13384_Alg with HasPipeModules_15544Only_Alg:
    val airIntake    : AirIntakePipe_Module.PipeCanBe
    val combustionAir: CombustionAirPipe_Module.PipeCanBe
    val firebox      : FireboxPipe_Module.PipeCanBe
