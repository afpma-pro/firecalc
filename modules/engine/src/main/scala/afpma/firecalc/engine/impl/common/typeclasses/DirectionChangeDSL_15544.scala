/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*

/**
 * Typeclass for EN15544-specific direction changes.
 * Simpler model with only 2 types.
 */
trait DirectionChangeDSL_15544[Descr]:
    def addSharpAngle_0_to_180deg(
        name: String,
        angle: Angle,
        angleN2: Option[Angle] = None
    ): Descr
    
    def addCircularArc60(name: String): Descr

    // Convenience methods
    def addSharpAngle_30deg(
        name: String,
        angleN2: Option[Angle] = None
    ): Descr = 
        addSharpAngle_0_to_180deg(name, 30.degrees, angleN2)
    
    def addSharpAngle_45deg(
        name: String,
        angleN2: Option[Angle] = None
    ): Descr = 
        addSharpAngle_0_to_180deg(name, 45.degrees, angleN2)
    
    def addSharpAngle_60deg(
        name: String,
        angleN2: Option[Angle] = None
    ): Descr = 
        addSharpAngle_0_to_180deg(name, 60.degrees, angleN2)
    
    def addSharpAngle_90deg(
        name: String,
        angleN2: Option[Angle] = None
    ): Descr = 
        addSharpAngle_0_to_180deg(name, 90.degrees, angleN2)

object DirectionChangeDSL_15544:
    def apply[D](using 
        ev: DirectionChangeDSL_15544[D]
    ): DirectionChangeDSL_15544[D] = ev
