/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.units.Vec3

object PipeDirection:
    def directionToVec3(dir: PipeInitialDirection): Vec3 =
        Vec3.fromAzimuthElevation(
            dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
            InclinationDirection.toDegrees                       (dir.inclination)
        )
