/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict
import afpma.firecalc.engine.alg.FireboxToCombustionAirPipe
import afpma.firecalc.engine.alg.FireboxToFireboxPipe
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait FireboxToFireboxPipe_15544_Strict[FB <: Firebox_15544] extends FireboxToFireboxPipe[FB]:
    override type FireboxPipe_FullDescr = FireboxPipe_Module_15544.FullDescr

trait FireboxToCombustionAirPipe_15544_Strict[FB <: Firebox_15544] extends FireboxToCombustionAirPipe[FB]:
    override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.PipeCanBe

trait FireboxToInternalPipes_15544_Strict[FB <: Firebox_15544] extends FireboxToFireboxPipe_15544_Strict[FB] with FireboxToCombustionAirPipe_15544_Strict[FB]
