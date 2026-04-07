/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.alg.FireboxToCombustionAirPipe
import afpma.firecalc.engine.alg.FireboxToFireboxPipe
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait FireboxToFireboxPipe_15544_MCE[FB <: Firebox_15544] extends FireboxToFireboxPipe[FB]:
    type FireboxPipe_FullDescr = FireboxPipe_Module_13384.FullDescr

trait FireboxToCombustionAirPipe_15544_MCE[FB <: Firebox_15544] extends FireboxToCombustionAirPipe[FB]:
    type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_13384.PipeCanBe

trait FireboxToInternalPipes_15544_MCE[FB <: Firebox_15544]
    extends FireboxToFireboxPipe_15544_MCE[FB]
    with FireboxToCombustionAirPipe_15544_MCE[FB]
