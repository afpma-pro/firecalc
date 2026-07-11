/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.units.Vec3

/**
 * Shared rule for the first slot after the firebox.
 *
 * When the first element of the first post-firebox slot is a
 * `SplitSingleFlowIntoTwoFlowsWith90DegTurn`, the incoming direction must be
 * `Vec3.Up` (the physical firebox outlet direction), regardless of the user's
 * configured initial direction.
 *
 * The split's own `absDir` is then used as the frame seed for the rest of the
 * slot sequence — the builder handles this naturally once the incoming frame
 * direction is set to `Up`.
 *
 * This helper is used by all pipe chain builders that thread `PipeBuildSeed`
 * through post-firebox slots:
 *   - `PipeChainGeneric.build` (Strict UI-facing)
 *   - `StrictAtParams.flueRegionPipeResults` (Strict computation)
 *   - `MCEAtParams.flueRegionPipeResults` (MCE computation)
 */
object FireboxSplitFrame:

    private val UpFrame = PipeFrame.initial(Vec3.Up)

    /**
     * Resolve the initial seed for the first slot after the firebox.
     *
     * If the first slot's first element is a split, returns a seed with
     * `Vec3.Up` as the frame direction. Otherwise returns the default seed
     * unchanged.
     *
     * @param firstSlot  the first slot in the post-firebox chain
     * @param defaultSeed the seed derived from the user's initial direction
     * @return seed with `Vec3.Up` frame if first element is a split, otherwise defaultSeed
     */
    def resolveInitialSeed(
        firstSlot  : PostFireboxPipeSlot,
        defaultSeed: PipeBuildSeed
    ): PipeBuildSeed =
        if isLeadingSplit(firstSlot) then defaultSeed.copy(frame = Some(UpFrame), nFlows = NbOfFlows(2))
        else defaultSeed

    /**
     * Check if this slot uses flow-only (EN 15544) descriptors.
     *
     * @return true for `FlueSlot`, false for thermal slots and `NoFlueSlot`
     */
    def isFlowOnlySlot(slot: PostFireboxPipeSlot): Boolean =
        slot match
            case PostFireboxPipeSlot.FlueSlot(_) => true
            case _                               => false

    /**
     * Check if this slot uses thermal (EN 13384) descriptors.
     *
     * @return true for `ThermalFlueSlot`, `ConnectorSlot`, `ChimneySlot`; false otherwise
     */
    def isThermalSlot(slot: PostFireboxPipeSlot): Boolean =
        slot match
            case PostFireboxPipeSlot.ThermalFlueSlot(_) => true
            case PostFireboxPipeSlot.ConnectorSlot(_)   => true
            case PostFireboxPipeSlot.ChimneySlot(_)     => true
            case _                                      => false

    private def isThermalSplit(d: Seq[ThermalPipeDescr_13384_V4]): Boolean =
        ElementPredicates.startsWith(
            d,
            isProperty = { case _: SetThermalPipeProp_13384_V4 => true; case _ => false },
            predicate  = {
                case _: AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn => true;
                case _ => false
            }
        )

    /**
     * Check if the first non-property element of this slot is a split.
     *
     * Property elements (SetInnerShape, SetRoughness, etc.) are skipped.
     *
     * @return true if the leading element is a SplitSingleFlowIntoTwoFlowsWith90DegTurn
     */
    def isLeadingSplit(slot: PostFireboxPipeSlot): Boolean =
        slot match
            case PostFireboxPipeSlot.FlueSlot(descr)    =>
                ElementPredicates.startsWith(
                    descr,
                    isProperty = { case _: SetFlowOnlyPipeProp_15544_V4 => true; case _ => false },
                    predicate  = {
                        case _: AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn => true;
                        case _ => false
                    }
                )
            case PostFireboxPipeSlot.ThermalFlueSlot(d) => isThermalSplit(d)
            case PostFireboxPipeSlot.ConnectorSlot(d)   => isThermalSplit(d)
            case PostFireboxPipeSlot.ChimneySlot(d)     => isThermalSplit(d)
            case PostFireboxPipeSlot.NoFlueSlot         => false
end FireboxSplitFrame
