/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en15544.std.*

case class Inputs_15544_Strict(
    localConditions            : LocalConditions,
    en13384NationalAcceptedData: NationalAcceptedData,
    stoveParams                : StoveParams,
    design                     : Design,
    pipes                      : Pipes_15544_Strict
    // wood: Wood,
) extends std.Inputs_15544_Alg
    with HasPipeModules_15544Only_Strict:
    override type Pipes_15544 = Pipes_15544_Strict
