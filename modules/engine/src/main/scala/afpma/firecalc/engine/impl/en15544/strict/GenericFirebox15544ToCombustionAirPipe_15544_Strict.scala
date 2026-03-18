/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.impl.en15544.strict.FireboxToCombustionAirPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

import cats.syntax.validated.catsSyntaxValidatedId

// Generic Firebox_15544

given FireboxToCombustionAirPipe_15544_Strict[Firebox_15544] = 
    GenericFirebox15544ToCombustionAirPipe_15544_Strict.makeFor[Firebox_15544]

// SingleTested

given FireboxToCombustionAirPipe_15544_Strict[SingleTested] = 
    GenericFirebox15544ToCombustionAirPipe_15544_Strict.makeFor[SingleTested]

trait GenericFirebox15544ToCombustionAirPipe_15544_Strict[FB <: Firebox_15544]
    extends FireboxToCombustionAirPipe_15544_Strict[FB] {

    extension (firebox: FB)
        override def toCombustionAirPipe_FullDescr = 
            CombustionAirPipe_Module_15544.without.validNel
}

object GenericFirebox15544ToCombustionAirPipe_15544_Strict:
    def makeFor[FB <: Firebox_15544]: GenericFirebox15544ToCombustionAirPipe_15544_Strict[FB] =
        new GenericFirebox15544ToCombustionAirPipe_15544_Strict[FB] {}