/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */
package afpma.firecalc.engine.impl.en15544.common

import afpma.firecalc.units.Vec3

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v7.{FlowOnlyPipeDescr_15544_V4, ThermalPipeDescr_13384_V4}
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn as FlowSplit
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn as ThermalSplit

import afpma.firecalc.engine.models.geometry.{PipeFrame, PostFireboxPipeSlot}

import afpma.firecalc.domain.{AzimuthDirection, InclinationDirection}

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
                dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
                InclinationDirection.toDegrees                       (dir.inclination)
            )
        )

    /**
     * Extract the horizontal direction Vec3 for a split from the first slot's
     * descriptor. Used to compute `branchOneStart` offset from `splitPosition`.
     *
     * Throws [[RuntimeException]] if no split direction can be found — this
     * indicates a programming error (caller should have verified `nFlows > 1`).
     */
    def splitBranchDirection(firstSlot: PostFireboxPipeSlot): Vec3 =
        val azDeg: Double = firstSlot match
            case PostFireboxPipeSlot.FlueSlot(descr)        =>
                val split: FlowSplit = descr
                    .collectFirst { case s: FlowSplit =>
                        s
                    }
                    .getOrElse(
                        throw RuntimeException(
                            "splitBranchDirection: FlueSlot has no SplitSingleFlowIntoTwoFlowsWith90DegTurn"
                        )
                    )
                split.absDir
                    .flatMap(_.azimuth.map(AzimuthDirection.toDegrees))
                    .getOrElse(
                        throw RuntimeException(
                            "splitBranchDirection: split has no azimuth direction"
                        )
                    )
            case PostFireboxPipeSlot.ThermalFlueSlot(descr) =>
                val split: ThermalSplit = descr
                    .collectFirst { case s: ThermalSplit =>
                        s
                    }
                    .getOrElse(
                        throw RuntimeException(
                            "splitBranchDirection: ThermalFlueSlot has no SplitSingleFlowIntoTwoFlowsWith90DegTurn"
                        )
                    )
                split.absDir
                    .flatMap(_.azimuth.map(AzimuthDirection.toDegrees))
                    .getOrElse(
                        throw RuntimeException(
                            "splitBranchDirection: split has no azimuth direction"
                        )
                    )
            case _                                          =>
                throw RuntimeException(
                    s"splitBranchDirection: unexpected slot type ${firstSlot.getClass.getSimpleName}"
                )
        Vec3.fromAzimuthElevation(azDeg, 0.0)

end PostFireboxFrameHelpers
