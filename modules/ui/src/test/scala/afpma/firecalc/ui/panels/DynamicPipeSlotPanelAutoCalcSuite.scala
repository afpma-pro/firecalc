/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Covers the unified "auto-calc button visible iff this slot is the first
 * HEAD_REGION slot (index 0)" rule — regression guard for the bug where a
 * Connector-headed chain hid the auto-calc button.
 *
 * The two pure predicates on `DynamicPipeSlotPanel`
 * (`isFirstHeadSlotAndIsFlue` / `isFirstHeadSlotAndIsConnector`) are the
 * type-routing side of a single underlying rule: the flow-only panel owns
 * the Flue variant, the thermal panel owns the Connector variant; only one
 * of them may be `true` for any given slot.
 */
class DynamicPipeSlotPanelAutoCalcSuite extends AnyFreeSpec with Matchers:

    // Minimal empty descriptors — the predicate only looks at slot types.
    private val emptyFlue: FlueSlot      = FlueSlot(Seq.empty)
    private val emptyConn: ConnectorSlot = ConnectorSlot(Seq.empty)
    private val emptyChim: ChimneySlot   = ChimneySlot(Seq.empty)

    "[Flue, Connector, Chimney] — button on Flue (index 0) only" - {
        val slots = Seq(emptyFlue, emptyConn, emptyChim)

        "isFirstHeadSlotAndIsFlue is true at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 0) shouldBe true
        }
        "isFirstHeadSlotAndIsConnector is false at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 0) shouldBe false
        }
        "no other slot gets the auto-calc button" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 1) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 1) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 2) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 2) shouldBe false
        }
    }

    "[Connector, Flue, Connector, Chimney] — button on Connector (index 0) only (the regression)" - {
        val slots = Seq(emptyConn, emptyFlue, emptyConn, emptyChim)

        "isFirstHeadSlotAndIsConnector is true at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 0) shouldBe true
        }
        "isFirstHeadSlotAndIsFlue is false at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 0) shouldBe false
        }
        "Flue at index 1 does NOT get the button" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 1) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 1) shouldBe false
        }
        "no other slot gets the auto-calc button" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 2) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 2) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 3) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 3) shouldBe false
        }
    }

    "[Flue, Connector, Flue, Connector, Chimney] — button on Flue (index 0) only; mid-chain Connector does not get it" - {
        val slots = Seq(emptyFlue, emptyConn, emptyFlue, emptyConn, emptyChim)

        "isFirstHeadSlotAndIsFlue is true at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 0) shouldBe true
        }
        "isFirstHeadSlotAndIsConnector is false at index 0" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 0) shouldBe false
        }
        "mid-chain Connector at index 1 does NOT get the button" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 1) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 1) shouldBe false
        }
        "mid-chain Connector at index 3 does NOT get the button" in {
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, 3) shouldBe false
            DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, 3) shouldBe false
        }
    }

end DynamicPipeSlotPanelAutoCalcSuite
