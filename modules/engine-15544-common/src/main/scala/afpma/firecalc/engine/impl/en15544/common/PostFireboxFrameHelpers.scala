/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.units.Vec3

import afpma.firecalc.dto.common.PipeInitialDirection

import afpma.firecalc.engine.models.geometry.PipeFrame

import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

/**
 * Shared helpers for converting V7 wrapper-level post-firebox pipe metadata
 * into geometry frames. Used by both `PipeChainGeneric` (engine-15544-strict)
 * and application fold seeding (Strict, MCE).
 */
object PostFireboxFrameHelpers:

    /**
     * Convert a V7 wrapper-level initial direction to a PipeFrame.
     * Shared by PipeChainGeneric and application classes for seeding folds.
     */
    def toPipeFrame(dir: PipeInitialDirection): PipeFrame =
        PipeFrame.initial(
            Vec3.fromAzimuthElevation(
                AzimuthDirection.toDegrees    (dir.azimuth    ),
                InclinationDirection.toDegrees(dir.inclination)
            )
        )

end PostFireboxFrameHelpers
