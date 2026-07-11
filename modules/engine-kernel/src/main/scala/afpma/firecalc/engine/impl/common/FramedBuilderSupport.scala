/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.engine.impl.common

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*
import coulomb.syntax.*

trait FramedBuilderSupport:
    def withInitialDirection(dir: PipeInitialDirection): this.type

    /**
     * Wire the initial frame from a framed sequence into the builder.
     *
     * Note: only the initial *direction* is wired here. Position tracking
     * is the responsibility of the `PositionTracker` domain, not the builder's
     * `PropsState`.
     *
     * @param seq the framed sequence containing the initial frame
     * @return this builder
     */
    def fromFramedSequence[D, P](seq: FramedPipeSequence[D, P]): this.type =
        this.withInitialDirection(seq.initialDirection)

object FramedBuilderSupport:

    /**
     * Compute angleN2 from 3D frame tracking.
     *
     * angleN2 = angle between the direction BEFORE the previous bend
     * and the direction AFTER the current bend.
     *
     * @param dirBeforePreviousDC direction vector before the previous DirectionChange
     * @param currentFrame current pipe frame (pre-bend)
     * @param absDir absolute direction after the current bend
     * @param bendAngleDeg deflection angle of the current bend in degrees
     * @return angleN2 if all inputs are available, None otherwise
     */
    def computeAngleN2(
        dirBeforePreviousDC: Option[Vec3],
        currentFrame       : Option[PipeFrame],
        absDir             : Option[AbsoluteDirection],
        bendAngleDeg       : Double
    ): Option[QtyD[Degree]] =
        (dirBeforePreviousDC, currentFrame, absDir) match
            case (Some(dirBefore), Some(frame), Some(fd)) =>
                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                val targetVec     = Vec3.fromAzimuthElevation(azDeg, elDeg)
                val postBendFrame = frame.applyBendForFinalDir(bendAngleDeg, targetVec)
                Some(dirBefore.angleTo(postBendFrame.direction).withUnit[Degree])
            case _ => None
