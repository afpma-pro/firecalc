/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

// Generic Firebox_15544

given FireboxToFireboxPipe_15544_Strict[Firebox_15544] = GenericFireboxToFireboxPipe_15544_Strict.makeFor[Firebox_15544]

// SingleTested

given FireboxToFireboxPipe_15544_Strict[SingleTested] =
    GenericFireboxToFireboxPipe_15544_Strict.makeFor[SingleTested]

trait GenericFireboxToFireboxPipe_15544_Strict[FB <: Firebox_15544]
    extends FireboxToFireboxPipe_15544_Strict[FB] {

    /** Default direction for firebox pipe. Override to change the initial direction. */
    protected def fireboxInitialDirection: (AzimuthDirection, InclinationDirection) =
        (AzimuthDirection.Rear, InclinationDirection.Up)

    extension (firebox: FB)
        override def toFireboxPipe_FullDescr =
            import FireboxPipe_Module_15544.*
            val (width, depth) = firebox.dimensions.base match
                case Dimensions.Base.Squared(w, d) => (w, d)
            val (az, incl) = fireboxInitialDirection
            FireboxPipe_Module_15544.incremental
                .define(
                    setInitialDirection(az, incl),
                    innerShape(rectangle(width, depth)),
                    roughness         (2.mm), // TOFIX: 3mm or 2mm ???
                    addSectionVertical(
                        "ascension dans foyer",
                        // TOFIX: found in CalculPdM-v0.2.30
                        // - we consider the whole vertical length ? but different injection height...
                        firebox.dimensions.height
                    )
                )
                .toFullDescr()
                .extractPipe
}

object GenericFireboxToFireboxPipe_15544_Strict:
    def makeFor[FB <: Firebox_15544]: GenericFireboxToFireboxPipe_15544_Strict[FB] =
        new GenericFireboxToFireboxPipe_15544_Strict[FB] {}