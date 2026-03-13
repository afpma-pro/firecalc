/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses
import afpma.firecalc.units.coulombutils.*

/**
 * Typeclass for EN13384-specific direction changes.
 * Has many variants due to complex zeta calculations.
 */
trait DirectionChangeDSL_13384[Descr]:
    def addAngleVifDe0A90           (name: String, angle: Angle,  roll: Angle): Descr
    def addAngleVifDe0A90_unsafe    (name: String, angle: Angle,  roll: Angle): Descr
    def addCoudeCourbe90            (name: String, R    : Length, roll: Angle): Descr
    def addCoudeCourbe90_unsafe     (name: String, R    : Length, roll: Angle): Descr
    def addCoudeCourbe60            (name: String, R    : Length, roll: Angle): Descr
    def addCoudeCourbe60_unsafe     (name: String, R    : Length, roll: Angle): Descr
    def addCoudeASegment90Avec2A45  (name: String, R    : Length, roll: Angle): Descr
    def addCoudeASegment90Avec3A30  (name: String, R    : Length, roll: Angle): Descr
    def addCoudeASegment90Avec4A22p5(name: String, R    : Length, roll: Angle): Descr
    def addAngleSpecifique          (
        name : String,
        angle: Angle,
        zeta : Double,
        roll : Angle
    ): Descr

    // Convenience methods with fixed angles
    def addSharpAngle_30deg(name: String, roll: Angle): Descr =
        addAngleVifDe0A90(name, 30.degrees, roll)

    def addSharpAngle_45deg(name: String, roll: Angle): Descr =
        addAngleVifDe0A90(name, 45.degrees, roll)

    def addSharpAngle_60deg(name: String, roll: Angle): Descr =
        addAngleVifDe0A90(name, 60.degrees, roll)

    def addSharpAngle_90deg(name: String, roll: Angle): Descr =
        addAngleVifDe0A90(name, 90.degrees, roll)

    def addSharpAngle_30deg_unsafe(name: String, roll: Angle): Descr =
        addAngleVifDe0A90_unsafe(name, 30.degrees, roll)

    def addSharpAngle_45deg_unsafe(name: String, roll: Angle): Descr =
        addAngleVifDe0A90_unsafe(name, 45.degrees, roll)

    def addSharpAngle_60deg_unsafe(name: String, roll: Angle): Descr =
        addAngleVifDe0A90_unsafe(name, 60.degrees, roll)

    def addSharpAngle_90deg_unsafe(name: String, roll: Angle): Descr =
        addAngleVifDe0A90_unsafe(name, 90.degrees, roll)

object DirectionChangeDSL_13384:
    def apply[D](using
        ev: DirectionChangeDSL_13384[D]
    ): DirectionChangeDSL_13384[D] = ev
