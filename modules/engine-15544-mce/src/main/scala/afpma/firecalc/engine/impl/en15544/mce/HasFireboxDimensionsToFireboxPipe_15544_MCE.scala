/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.impl.en15544.mce.FireboxToFireboxPipe_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait HasFireboxDimensionsToFireboxPipe_15544_MCE[FB <: Firebox_15544] extends FireboxToFireboxPipe_15544_MCE[FB]:

    /** Default direction for firebox pipe. Override to change the initial direction. */
    protected def fireboxInitialDirection: (AzimuthDirection, InclinationDirection) =
        (AzimuthDirection.Rear, InclinationDirection.Up)

    extension (firebox: FB)
        override def toFireboxPipe_FullDescr =
            import FireboxPipe_Module_13384.*
            val (width, depth) = firebox.dimensions.base match
                case Dimensions.Base.Squared(w, d) => (w, d)
            val (az, incl) = fireboxInitialDirection
            FireboxPipe_Module_13384.incremental
                .withInitialDirection(PipeInitialDirection(az, incl))
                .define(
                    pipeLocation      (PipeLocation.HeatedArea   ), // added for EN13384
                    innerShape(rectangle(width, depth)),
                    roughness         (2.mm                      ), // TOFIX: 3mm or 2mm ???
                    layer             (e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                    addSectionVertical(
                        "ascension dans foyer",
                        // TOFIX: found in CalculPdM-v0.2.30
                        // - we consider the whole vertical length ? but different injection height...
                        firebox.dimensions.height
                    )
                )
                .toFullDescr()
                .extractPipe

given FireboxToFireboxPipe_15544_MCE[Firebox_15544] = HasFireboxDimensionsToFireboxPipe_15544_MCE

object HasFireboxDimensionsToFireboxPipe_15544_MCE extends HasFireboxDimensionsToFireboxPipe_15544_MCE[Firebox_15544]
