/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*

/**
 * Typeclass for building section-related pipe descriptors.
 * 
 * @tparam Descr The incremental descriptor ADT type
 */
trait SectionDSL[Descr]:
    def addSectionSlopped(
        name: String,
        length: Length,
        elevation_gain: Length
    ): Descr
    
    def addSectionHorizontal(
        name: String,
        horizontal_length: Length
    ): Descr
    
    def addSectionVertical(name: String, elevation_gain: Length): Descr

object SectionDSL:
    def apply[D](using ev: SectionDSL[D]): SectionDSL[D] = ev
