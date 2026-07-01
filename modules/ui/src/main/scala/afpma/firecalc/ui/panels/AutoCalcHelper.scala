/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.{AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.models.geometry.FrameReplay
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.units.Vec3

/**
 * Shared helper for pipe position geometry.
 *
 * Pure math has moved to engine-kernel: `PipePositionComputer`.
 *
 * Remaining here:
 *   - ElemExtractors, replayFrame, lastShapeBefore (re-exported from FrameReplay)
 *   - wrapperDirectionToFrame (UI helper)
 */
object AutoCalcHelper:

    // ── ElemExtractors / replayFrame / lastShapeBefore ──────────────────
    // Moved to engine-kernel for headless testability; re-exported here for
    // backward compatibility of all UI call sites.
    export FrameReplay.{ElemExtractors, replayFrame, replayFrameMap, lastShapeBefore}

    /** Convert a V7 wrapper-level initial direction to a PipeFrame. */
    def wrapperDirectionToFrame(dir: PipeInitialDirection): PipeFrame =
        PipeFrame.initial(
            Vec3.fromAzimuthElevation(
                dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
                InclinationDirection.toDegrees                       (dir.inclination)
            )
        )
