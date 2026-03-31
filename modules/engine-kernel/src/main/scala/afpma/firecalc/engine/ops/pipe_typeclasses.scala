/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.en13384.typedefs.*

import coulomb.*

trait HasOutsideSurfaceInLocation[P]:
    extension (p: P)
        def outsideSurfaceIn[PLoc <: PipeLocation](
            inLoc: PLoc
        ): QtyD[(Meter ^ 2)]

trait HasUnheatedHeightInsideAndOutside[A]:
    extension (a: A) def unheatedHeightInsideAndOutside: UnheatedHeightInsideAndOutside
