/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.common.{PipeShape, toVec3}
import afpma.firecalc.engine.models.geometry.{PipeFrame, PipePositionComputer, PostFireboxPipeSlot, SlotIntrospector}
import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.units.Vec3

final case class PipeBuildSeed(
    frame                    : Option[PipeFrame],
    nFlows                   : NbOfFlows,
    startPoint               : Option[Vec3] = None,
    slot0FireboxSplitPosition: Option[Vec3] = None
)

object PipeBuildSeed:
    val default: PipeBuildSeed = PipeBuildSeed(None, NbOfFlows(1))

    def fromFrame(frame: Option[PipeFrame]): PipeBuildSeed =
        PipeBuildSeed(frame, NbOfFlows(1))

    /**
     * Build a PipeBuildSeed with position tracking for a post-firebox chain.
     *
     * Encapsulates the 3-step dance duplicated across Strict/MCE applications
     * and the YAML loader:
     *   1. Create default seed + resolve split frame
     *   2. Compute split position and branch start (if nFlows > 1)
     *   3. Apply positions into seed
     *
     * @param slots         Post-firebox pipe slots (engine model)
     * @param initialFrame  Optional initial direction from user config
     * @param fbWidth       Firebox width in meters
     * @param fbDepth       Firebox depth in meters
     * @param fbHeight      Firebox height in meters
     * @param fbBottomZ     Firebox bottom Z (usually 0.0)
     * @param fallbackShape Inner shape fallback when none found in first slot
     * @return PipeBuildSeed with positions populated (or None if single-flow / no split)
     */
    def withPositions(
        slots        : Seq[PostFireboxPipeSlot],
        initialFrame : Option[PipeFrame],
        fbWidth      : Double,
        fbDepth      : Double,
        fbHeight     : Double,
        fbBottomZ    : Double,
        fallbackShape: PipeShape = PipeShape.InnerShapeFallbackCompute
    ): PipeBuildSeed =
        if slots.isEmpty then return PipeBuildSeed(initialFrame, NbOfFlows(1), None, None)

        val defaultSeed = PipeBuildSeed(initialFrame, NbOfFlows(1), None, None)
        val initialSeed = FireboxSplitFrame.resolveInitialSeed(slots.head, defaultSeed)

        if initialSeed.nFlows.unwrap > 1 then
            val firstShape = SlotIntrospector
                .firstInnerShapeIn(slots)
                .getOrElse(fallbackShape)
            val innerH     = PipePositionComputer.innerHeight(firstShape)

            SlotIntrospector
                .findFirstSplitDir(slots)
                .map { absDir =>
                    val splitPos                   = PipePositionComputer.computeSplitPosition(
                        fbBottomZ,
                        fbHeight,
                        innerH,
                        absDir
                    )
                    val branchStart                = PipePositionComputer
                        .computeBranchStartAfterSplit(
                            absDir,
                            fbWidth,
                            fbDepth,
                            fbBottomZ,
                            fbHeight,
                            firstShape
                        )
                        .toVec3
                    initialSeed.copy(
                        startPoint                = Some(branchStart),
                        slot0FireboxSplitPosition = Some(splitPos)
                    )
                }
                .getOrElse(initialSeed)
        else initialSeed
final case class BuiltPipe[A](
    value   : A,
    nextSeed: PipeBuildSeed
)
