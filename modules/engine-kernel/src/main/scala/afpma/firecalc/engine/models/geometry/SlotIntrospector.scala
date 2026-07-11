/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4

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
            case FlowOnlySetInnerShape(shape)                         => shape
            case SplitSingleFlowIntoTwoFlowsWith90DegTurn(_, _, s, _) => s
            case MergeTwoFlowsIntoSingleWith90DegTurn(_, _, s, _)     => s
        }

    private def extractInnerShapeThermal(descr: Seq[ThermalPipeDescr_13384_V4]): Option[PipeShape] =
        import SetThermalPipeProp_13384_V4.SetInnerShape as ThermalSetInnerShape
        import AddThermalPipeElement_13384_V4.{
            SplitSingleFlowIntoTwoFlowsWith90DegTurn,
            MergeTwoFlowsIntoSingleWith90DegTurn
        }
        descr.collectFirst {
            case ThermalSetInnerShape(shape)                          => shape
            case SplitSingleFlowIntoTwoFlowsWith90DegTurn(_, _, s, _) => s
            case MergeTwoFlowsIntoSingleWith90DegTurn(_, _, s, _)     => s
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

    private def extractFirstSplitDir(slot: PostFireboxPipeSlot): Option[AbsoluteDirection] =
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
     * Find the first split element in the first slot of a post-firebox chain.
     *
     * Returns the resolved AbsoluteDirection of branch one, defaulting to
     * `AbsoluteDirection(None, InclinationDirection.Up)` when the split's own
     * absDir is absent. Returns None for NoFlueSlot or when no split found.
     */
    def findFirstSplitDir(slots: Seq[PostFireboxPipeSlot]): Option[AbsoluteDirection] =
        slots.headOption.flatMap(s => extractFirstSplitDir(s))
end SlotIntrospector
