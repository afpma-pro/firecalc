/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.units.Vec3

/**
 * Spatial position of a single physical pipe segment.
 *
 * @param elementIndex  Index in the incremental descriptor sequence (for correlation with UI elements)
 * @param startPoint    Absolute XYZ start of this segment (in meters)
 * @param endPoint      Absolute XYZ end of this segment (in meters)
 * @param direction     Unit vector of flow direction during this segment
 * @param length        3D length of this segment in meters
 * @param innerShape    Current inner cross-section shape at this segment (if known)
 * @param frame         PipeFrame at the START of this segment
 */
case class PipeSegmentPosition(
    elementIndex: Int,
    startPoint  : Vec3,
    endPoint    : Vec3,
    direction   : Vec3,
    length      : Double,
    innerShape  : Option[PipeShape],
    frame       : PipeFrame
)

/**
 * Position of a split or merge element in the pipe geometry.
 *
 * @param elementIndex  Index in the incremental descriptor sequence
 * @param position      Absolute XYZ position where the split/merge occurs (in meters)
 * @param frame         PipeFrame at this point (direction is the flow direction)
 * @param isSplit       true for split, false for merge
 */
case class SplitMergePosition(
    elementIndex: Int,
    position    : Vec3,
    frame       : PipeFrame,
    frameAfter  : PipeFrame,
    isSplit     : Boolean
)

/**
 * Complete position tracking result for one pipe.
 *
 * @param segments    Positions for each physical straight section
 * @param finalPoint  XYZ end of the last segment (equals startPoint if no segments)
 * @param finalFrame  PipeFrame after the last element (None if no frame was ever set)
 * @param splitMergePositions  Positions of split and merge elements
 */
case class PipePositionResult(
    segments           : Seq[PipeSegmentPosition],
    finalPoint         : Vec3,
    finalFrame         : Option[PipeFrame],
    splitMergePositions: Seq[SplitMergePosition] = Seq.empty
):
    /** Translate all spatial positions by the given offset vector. */
    def translate(offset: Vec3): PipePositionResult =
        PipePositionResult(
            segments.map           (s => s.copy(startPoint = s.startPoint + offset, endPoint = s.endPoint + offset)),
            finalPoint + offset,
            finalFrame,
            splitMergePositions.map(s => s.copy(position = s.position + offset)                                    )
        )
