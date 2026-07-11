/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4
import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.engine.models.FireboxSplitFrame

/** Slot introspection: extract inner shapes and split directions from post-firebox pipe slots. */
object SlotIntrospector:

    private val DefaultSplitDir = AbsoluteDirection(None, InclinationDirection.Up)

    private def extractInnerShapeFlowOnly(descr: Seq[FlowOnlyPipeDescr_15544_V4]): Option[PipeShape] =
        import SetFlowOnlyPipeProp_15544_V4.SetInnerShape as FlowOnlySetInnerShape
        import AddFlowOnlyPipeElement_15544_V4.{
            SplitSingleFlowIntoTwoFlowsWith90DegTurn,
            MergeTwoFlowsIntoSingleWith90DegTurn
        }
        descr.collectFirst {
            case FlowOnlySetInnerShape(shp)                             => shp
            case SplitSingleFlowIntoTwoFlowsWith90DegTurn(_, _, shp, _) => shp
            case MergeTwoFlowsIntoSingleWith90DegTurn(_, _, shp, _)     => shp
        }

    private def extractInnerShapeThermal(descr: Seq[ThermalPipeDescr_13384_V4]): Option[PipeShape] =
        import SetThermalPipeProp_13384_V4.SetInnerShape as ThermalSetInnerShape
        import AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn
        import AddThermalPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn
        descr.collectFirst {
            case ThermalSetInnerShape(shp)                              => shp
            case SplitSingleFlowIntoTwoFlowsWith90DegTurn(_, _, shp, _) => shp
            case MergeTwoFlowsIntoSingleWith90DegTurn(_, _, shp, _)     => shp
        }

    private def extractSplitDirFlowOnly(descr: Seq[FlowOnlyPipeDescr_15544_V4]): Option[AbsoluteDirection] =
        descr.collectFirst { case e: AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
            e.absDir.getOrElse(DefaultSplitDir)
        }

    private def extractSplitDirThermal(descr: Seq[ThermalPipeDescr_13384_V4]): Option[AbsoluteDirection] =
        descr.collectFirst { case e: AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
            e.absDir.getOrElse(DefaultSplitDir)
        }

    private def extractFirstInnerShape(slot: PostFireboxPipeSlot): Option[PipeShape] =
        import PostFireboxPipeSlot.*
        slot match
            case FlueSlot(descr)        => extractInnerShapeFlowOnly(descr)
            case ThermalFlueSlot(descr) => extractInnerShapeThermal(descr)
            case ConnectorSlot(descr)   => extractInnerShapeThermal(descr)
            case ChimneySlot(descr)     => extractInnerShapeThermal(descr)
            case NoFlueSlot             => None

    private def extractAnySplitDir(slot: PostFireboxPipeSlot): Option[AbsoluteDirection] =
        import PostFireboxPipeSlot.*
        slot match
            case FlueSlot(descr)        => extractSplitDirFlowOnly(descr)
            case ThermalFlueSlot(descr) => extractSplitDirThermal(descr)
            case ConnectorSlot(descr)   => extractSplitDirThermal(descr)
            case ChimneySlot(descr)     => extractSplitDirThermal(descr)
            case NoFlueSlot             => None

    /**
     * Extract the first inner shape from the first slot in a post-firebox chain.
     *
     * Returns None for NoFlueSlot or when no SetInnerShape is present.
     */
    def firstInnerShapeIn(slots: Seq[PostFireboxPipeSlot]): Option[PipeShape] =
        slots.headOption.flatMap(s => extractFirstInnerShape(s))

    /**
     * Find the direction of any split element within the leading slot of a post-firebox chain.
     *
     * Scans the entire descriptor sequence (not just the leading element) and
     * returns the AbsoluteDirection of the first split found, defaulting to
     * `AbsoluteDirection(None, InclinationDirection.Up)` when the split's own
     * absDir is absent. Returns None for NoFlueSlot or when no split found.
     */
    def findAnySplitDirInLeadingSlot(slots: Seq[PostFireboxPipeSlot]): Option[AbsoluteDirection] =
        slots.headOption.flatMap(s => extractAnySplitDir(s))

    /**
     * Check if the leading element of the first slot is a split.
     *
     * Delegates to `FireboxSplitFrame.isLeadingSplit` — the canonical
     * implementation. Property elements are skipped.
     */
    def leadingElementIsSplit(slots: Seq[PostFireboxPipeSlot]): Boolean =
        slots.headOption.exists(slot => FireboxSplitFrame.isLeadingSplit(slot))

    /** Describes what kind of split (if any) is in the first slot. */
    enum SplitQueryResult:
        /** NoFlueSlot or empty slot sequence — no descriptors to inspect. */
        case Empty

        /** Leading element is a split; `absDir` is its direction. */
        case LeadingSplit(absDir: AbsoluteDirection)

        /**
         * Leading element is NOT a split. `anySplitDir` may contain a split
         * found later in the descriptor sequence (for position computation).
         */
        case NoLeadingSplit(anySplitDir: Option[AbsoluteDirection])

    /**
     * Single-traversal query: is the leading element a split, and what is the
     * split direction if any split exists in the slot?
     *
     * Replaces the common pattern of calling `leadingElementIsSplit` followed by
     * `findAnySplitDirInLeadingSlot`, which traverses the descriptor sequence twice.
     */
    def querySplit(slots: Seq[PostFireboxPipeSlot]): SplitQueryResult =
        slots.headOption match
            case None                                 => SplitQueryResult.Empty
            case Some(PostFireboxPipeSlot.NoFlueSlot) => SplitQueryResult.Empty
            case Some(slot)                           =>
                if FireboxSplitFrame.isLeadingSplit(slot) then
                    extractAnySplitDir(slot) match
                        case Some(absDir) => SplitQueryResult.LeadingSplit(absDir)
                        case None         => SplitQueryResult.NoLeadingSplit(None)
                else SplitQueryResult.NoLeadingSplit(extractAnySplitDir(slot))
end SlotIntrospector
