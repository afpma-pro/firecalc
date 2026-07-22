/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.domain.NbOfFlows
import NbOfFlows.*

import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class UnmergedFlowsWarningSuite extends AnyFreeSpec with Matchers:

    "unmergedFlowsWarning — last slot, 2 flows" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = 2.flows,
            upstreamFailure = false,
            isLastSlot      = true
        ) shouldBe PanelStatusHelper.PanelWarning.UnmergedFlowsAtExit(2).invalidNel
    }

    "unmergedFlowsWarning — last slot, 1 flow" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = 1.flow,
            upstreamFailure = false,
            isLastSlot      = true
        ) shouldBe ().validNel
    }

    "unmergedFlowsWarning — last slot, upstream failure" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = 2.flows,
            upstreamFailure = true,
            isLastSlot      = true
        ) shouldBe ().validNel
    }

    "unmergedFlowsWarning — non-last slot, 2 flows" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = 2.flows,
            upstreamFailure = false,
            isLastSlot      = false
        ) shouldBe ().validNel
    }

    "unmergedFlowsWarning — last slot, 3 flows" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = 3.flows,
            upstreamFailure = false,
            isLastSlot      = true
        ) shouldBe PanelStatusHelper.PanelWarning.UnmergedFlowsAtExit(3).invalidNel
    }

    "unmergedFlowsWarning — last slot, 0 flows (edge)" in {
        PanelStatusHelper.unmergedFlowsWarning    (
            finalNFlows     = NbOfFlows(0),
            upstreamFailure = false,
            isLastSlot      = true
        ) shouldBe ().validNel
    }
