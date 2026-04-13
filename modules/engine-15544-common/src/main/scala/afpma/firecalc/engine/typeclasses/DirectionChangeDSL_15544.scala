/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.typeclasses
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.AbsoluteDirection

/**
 * Typeclass for EN15544-specific direction changes.
 * Simpler model with only 2 types.
 */
trait DirectionChangeDSL_15544[Descr]:
    def addSharpAngle_0_to_180deg(
        name  : String,
        angle : Angle,
        absDir: AbsoluteDirection
    ): Descr

    def addCircularArc60(name: String, absDir: AbsoluteDirection): Descr

    // Convenience methods
    def addSharpAngle_30deg(
        name  : String,
        absDir: AbsoluteDirection
    ): Descr =
        addSharpAngle_0_to_180deg(name, 30.degrees, absDir)

    def addSharpAngle_45deg(
        name  : String,
        absDir: AbsoluteDirection
    ): Descr =
        addSharpAngle_0_to_180deg(name, 45.degrees, absDir)

    def addSharpAngle_60deg(
        name  : String,
        absDir: AbsoluteDirection
    ): Descr =
        addSharpAngle_0_to_180deg(name, 60.degrees, absDir)

    def addSharpAngle_90deg(
        name  : String,
        absDir: AbsoluteDirection
    ): Descr =
        addSharpAngle_0_to_180deg(name, 90.degrees, absDir)

object DirectionChangeDSL_15544:
    def apply[D](using
        ev: DirectionChangeDSL_15544[D]
    ): DirectionChangeDSL_15544[D] = ev
