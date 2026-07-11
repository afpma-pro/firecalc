/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
import afpma.firecalc.dto.common.PipeShape

import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class FireboxSplitWarningSignalSuite extends AnyFreeSpec with Matchers:

    private val split    = SplitSingleFlowIntoTwoFlowsWith90DegTurn(
        name          = "Test Split",
        absDir        = None,
        newInnerShape = PipeShape.Circle(100.mm)
    )
    private val section  = AddSectionSlopped("Test", 100.mm)
    private val setShape = SetInnerShape(PipeShape.Circle(100.mm))

    private val isSplit   : FlowOnlyPipeDescr_15544 => Boolean = {
        case _: SplitSingleFlowIntoTwoFlowsWith90DegTurn => true
        case _ => false
    }
    private val isProperty: FlowOnlyPipeDescr_15544 => Boolean = {
        case _: SetFlowOnlyPipeProp_15544 => true
        case _ => false
    }

    "fireboxSplitWarning — slotIndex 0 with split first" in {
        PanelStatusHelper.fireboxSplitWarning[FlowOnlyPipeDescr_15544] (
            slotIndex  = 0,
            elems      = Seq(split, section),
            isSplit    = isSplit,
            isProperty = isProperty
        ) shouldBe PanelStatusHelper.PanelWarning.FireboxSplitDirectionOverridden.invalidNel
    }

    "fireboxSplitWarning — slotIndex 0 without split" in {
        PanelStatusHelper.fireboxSplitWarning[FlowOnlyPipeDescr_15544] (
            slotIndex  = 0,
            elems      = Seq(section),
            isSplit    = isSplit,
            isProperty = isProperty
        ) shouldBe ().validNel
    }

    "fireboxSplitWarning — slotIndex > 0 with split" in {
        PanelStatusHelper.fireboxSplitWarning[FlowOnlyPipeDescr_15544] (
            slotIndex  = 1,
            elems      = Seq(split),
            isSplit    = isSplit,
            isProperty = isProperty
        ) shouldBe ().validNel
    }

    "fireboxSplitWarning — empty slot" in {
        PanelStatusHelper.fireboxSplitWarning[FlowOnlyPipeDescr_15544] (
            slotIndex  = 0,
            elems      = Seq.empty[FlowOnlyPipeDescr_15544],
            isSplit    = isSplit,
            isProperty = isProperty
        ) shouldBe ().validNel
    }

    "fireboxSplitWarning — split after SetInnerShape (property skipped)" in {
        PanelStatusHelper.fireboxSplitWarning[FlowOnlyPipeDescr_15544] (
            slotIndex  = 0,
            elems      = Seq(setShape, split),
            isSplit    = isSplit,
            isProperty = isProperty
        ) shouldBe PanelStatusHelper.PanelWarning.FireboxSplitDirectionOverridden.invalidNel
    }
