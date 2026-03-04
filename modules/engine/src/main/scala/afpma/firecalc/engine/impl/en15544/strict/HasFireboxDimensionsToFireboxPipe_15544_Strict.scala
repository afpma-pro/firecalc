/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait HasFireboxDimensionsToFireboxPipe_15544_Strict[FB <: Firebox_15544]
    extends FireboxToFireboxPipe_15544_Strict[FB] {

    extension (firebox: FB)
        override def toFireboxPipe_FullDescr =
            import FireboxPipe_Module_15544.*
            val (width, depth) = firebox.dimensions.base match
                case Dimensions.Base.Squared(w, d) => (w, d)
            FireboxPipe_Module_15544.incremental
                .define(
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

object HasFireboxDimensionsToFireboxPipe_15544_Strict 
    extends HasFireboxDimensionsToFireboxPipe_15544_Strict[Firebox_15544]