/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.impl.en15544.mce.FireboxToCombustionAirPipe_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

import cats.syntax.validated.catsSyntaxValidatedId

trait GenericFirebox15544ToCombustionAirPipe_15544_MCE[FB <: Firebox_15544]
    extends FireboxToCombustionAirPipe_15544_MCE[FB] {

    extension (firebox: FB)
        override def toCombustionAirPipe_FullDescr = 
            CombustionAirPipe_Module_13384.without.validNel
}

object GenericFirebox15544ToCombustionAirPipe_15544_MCE 
    extends GenericFirebox15544ToCombustionAirPipe_15544_MCE[Firebox_15544]