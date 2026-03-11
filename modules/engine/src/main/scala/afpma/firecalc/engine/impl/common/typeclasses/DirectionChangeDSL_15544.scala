/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses
import afpma.firecalc.units.coulombutils.*

/**
 * Typeclass for EN15544-specific direction changes.
 * Simpler model with only 2 types.
 */
trait DirectionChangeDSL_15544[Descr]:
    def addSharpAngle_0_to_180deg(
        name: String,
        angle: Angle,
        roll: Option[Angle] = None
    ): Descr

    def addCircularArc60(name: String, roll: Option[Angle] = None): Descr

    // Convenience methods
    def addSharpAngle_30deg(
        name: String,
        roll: Option[Angle] = None
    ): Descr =
        addSharpAngle_0_to_180deg(name, 30.degrees, roll)

    def addSharpAngle_45deg(
        name: String,
        roll: Option[Angle] = None
    ): Descr =
        addSharpAngle_0_to_180deg(name, 45.degrees, roll)

    def addSharpAngle_60deg(
        name: String,
        roll: Option[Angle] = None
    ): Descr =
        addSharpAngle_0_to_180deg(name, 60.degrees, roll)

    def addSharpAngle_90deg(
        name: String,
        roll: Option[Angle] = None
    ): Descr =
        addSharpAngle_0_to_180deg(name, 90.degrees, roll)

object DirectionChangeDSL_15544:
    def apply[D](using
        ev: DirectionChangeDSL_15544[D]
    ): DirectionChangeDSL_15544[D] = ev
