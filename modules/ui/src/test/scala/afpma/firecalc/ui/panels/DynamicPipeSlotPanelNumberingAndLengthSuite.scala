/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Unit tests for the pure helpers `DynamicPipeSlotPanel.numberedTitle` and
 * `DynamicPipeSlotPanel.lengthSummary`.
 *
 * Tests cover every row of the PRD "Full display matrix" (head region sizes 0,
 * 1, 2, 3), D5 fail-closed policy (intermediate Invalid), connector-first head
 * region (commit f416e7db topology), terminal connector returning `None`, and
 * chimney returning `None`.
 *
 * No Laminar owners or reactive wiring required — these are pure functions.
 */
class DynamicPipeSlotPanelNumberingAndLengthSuite extends AnyFreeSpec with Matchers:

    // ── Minimal empty descriptors ──────────────────────────────────
    private val emptyFlue  : FlueSlot        = FlueSlot(Seq.empty)
    private val emptyThFlue: ThermalFlueSlot = ThermalFlueSlot(Seq.empty)
    private val emptyConn  : ConnectorSlot   = ConnectorSlot(Seq.empty)
    private val emptyChim  : ChimneySlot     = ChimneySlot(Seq.empty)

    // ── Format stubs ───────────────────────────────────────────────
    // Simple stubs that produce recognisable output without requiring i18n
    // infrastructure. Mirror the format used by the real panels (%.2f m).
    private def fmtLen              (v: Double           ): String = f"$v%.2f m"
    private def fmtChanLength       (x: String           ): String = s"Length: $x"
    private def fmtChanLengthWithMin(x: String, z: String): String = s"Length: $x (min. $z)"
    private def fmtChanLengthWithCum(x: String, y: String): String = s"Length: $x (cum. $y)"
    private def fmtChanLengthWithCumAndMin(x: String, y: String, z: String): String =
        s"Length: $x (cum. $y, min. $z)"

    // ── Convenience wrapper ─────────────────────────────────────────
    private def numberedTitle(slots: Seq[PostFireboxPipeDescrSlot], idx: Int): Option[String] =
        DynamicPipeSlotPanel.numberedTitle(slots, idx, "Channel", "Connector")

    private def lengthSummary(
        slots                       : Seq[PostFireboxPipeDescrSlot],
        idx                         : Int,
        lengths                     : Option[Vector[Double]],
        lZMin                       : Option[Double]
    ): Option[String] =
        DynamicPipeSlotPanel.lengthSummary(
            slots                      = slots,
            slotIndex                  = idx,
            lengths                    = lengths,
            lZMin                      = lZMin,
            fmtLength                  = fmtLen,
            fmtChanLength              = fmtChanLength,
            fmtChanLengthWithMin       = fmtChanLengthWithMin,
            fmtChanLengthWithCum       = fmtChanLengthWithCum,
            fmtChanLengthWithCumAndMin = fmtChanLengthWithCumAndMin
        )

    // ════════════════════════════════════════════════════════════════
    // numberedTitle — head region sizes 0, 1, 2, 3
    // ════════════════════════════════════════════════════════════════

    "numberedTitle — head region size 0 (no flue slots)" - {
        // [ConnectorSlot, ChimneySlot] — no flue so head region is empty
        val slots = Seq(emptyConn, emptyChim)

        "terminal connector at index 0 returns None" in {
            numberedTitle(slots, 0) shouldBe None
        }
        "chimney at index 1 returns None" in {
            numberedTitle(slots, 1) shouldBe None
        }
    }

    "numberedTitle — head region size 1 [Flue, Connector, Chimney]" - {
        // Head region = [S₀=Flue]; terminal connector = index 1; chimney = index 2
        val slots = Seq(emptyFlue, emptyConn, emptyChim)

        "S₀ (Flue) gets title 'Channel #1'" in {
            numberedTitle(slots, 0) shouldBe Some("Channel #1")
        }
        "terminal connector (index 1) returns None" in {
            numberedTitle(slots, 1) shouldBe None
        }
        "chimney (index 2) returns None" in {
            numberedTitle(slots, 2) shouldBe None
        }
    }

    "numberedTitle — head region size 1 [ThermalFlue, Connector, Chimney] (D1: ThermalFlue = Channel)" - {
        val slots = Seq(emptyThFlue, emptyConn, emptyChim)

        "S₀ (ThermalFlue) gets title 'Channel #1' (same label as FlueSlot)" in {
            numberedTitle(slots, 0) shouldBe Some("Channel #1")
        }
        "terminal connector returns None" in {
            numberedTitle(slots, 1) shouldBe None
        }
    }

    "numberedTitle — head region size 2 [Flue, Flue, Connector, Chimney]" - {
        val slots = Seq(emptyFlue, emptyFlue, emptyConn, emptyChim)

        "S₀ gets 'Channel #1'" in {
            numberedTitle(slots, 0) shouldBe Some("Channel #1")
        }
        "S₁ gets 'Channel #2'" in {
            numberedTitle(slots, 1) shouldBe Some("Channel #2")
        }
        "terminal connector (index 2) returns None" in {
            numberedTitle(slots, 2) shouldBe None
        }
        "chimney (index 3) returns None" in {
            numberedTitle(slots, 3) shouldBe None
        }
    }

    "numberedTitle — head region size 3 [Flue, Flue, Flue, Connector, Chimney]" - {
        val slots = Seq(emptyFlue, emptyFlue, emptyFlue, emptyConn, emptyChim)

        "S₀ gets 'Channel #1'" in {
            numberedTitle(slots, 0) shouldBe Some("Channel #1")
        }
        "S₁ gets 'Channel #2'" in {
            numberedTitle(slots, 1) shouldBe Some("Channel #2")
        }
        "S₂ gets 'Channel #3'" in {
            numberedTitle(slots, 2) shouldBe Some("Channel #3")
        }
        "terminal connector (index 3) returns None" in {
            numberedTitle(slots, 3) shouldBe None
        }
        "chimney (index 4) returns None" in {
            numberedTitle(slots, 4) shouldBe None
        }
    }

    "numberedTitle — connector-first head region [Connector, Flue, Connector, Chimney] (f416e7db topology)" - {
        // Head region = [S₀=Connector, S₁=Flue]; terminal connector = index 2; chimney = index 3
        // D2: ConnectorSlot in head region uses 'Connector' label
        val slots = Seq(emptyConn, emptyFlue, emptyConn, emptyChim)

        "S₀ (Connector in head region) gets 'Connector #1'" in {
            numberedTitle(slots, 0) shouldBe Some("Connector #1")
        }
        "S₁ (Flue) gets 'Channel #2'" in {
            numberedTitle(slots, 1) shouldBe Some("Channel #2")
        }
        "terminal connector (index 2) returns None" in {
            numberedTitle(slots, 2) shouldBe None
        }
        "chimney (index 3) returns None" in {
            numberedTitle(slots, 3) shouldBe None
        }
    }

    "numberedTitle — mixed [Flue, Connector, Flue, Connector, Chimney]" - {
        // Head region = [S₀=Flue, S₁=Connector, S₂=Flue]; terminal connector = index 3; chimney = index 4
        val slots = Seq(emptyFlue, emptyConn, emptyFlue, emptyConn, emptyChim)

        "S₀ (Flue) gets 'Channel #1'" in {
            numberedTitle(slots, 0) shouldBe Some("Channel #1")
        }
        "S₁ (Connector in head region) gets 'Connector #2'" in {
            numberedTitle(slots, 1) shouldBe Some("Connector #2")
        }
        "S₂ (Flue) gets 'Channel #3'" in {
            numberedTitle(slots, 2) shouldBe Some("Channel #3")
        }
        "terminal connector (index 3) returns None" in {
            numberedTitle(slots, 3) shouldBe None
        }
        "chimney (index 4) returns None" in {
            numberedTitle(slots, 4) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // lengthSummary — head region size 1
    // ════════════════════════════════════════════════════════════════

    "lengthSummary — head region size 1 [Flue, Connector, Chimney]" - {
        val slots = Seq(emptyFlue, emptyConn, emptyChim)

        "all valid: shows 'Length: X (min. Z)'" in {
            lengthSummary(slots, 0, Some(Vector(2.5)), Some(3.0)) shouldBe
                Some("Length: 2.50 m (min. 3.00 m)")
        }
        "pipe Invalid (lengths=None): returns None (empty summary)" in {
            lengthSummary(slots, 0, None, Some(3.0)) shouldBe None
        }
        "lZMin Invalid (pipe valid): shows 'Length: X' (no min suffix — D6)" in {
            lengthSummary(slots, 0, Some(Vector(2.5)), None) shouldBe
                Some("Length: 2.50 m")
        }
        "terminal connector (index 1) returns None" in {
            lengthSummary(slots, 1, Some(Vector(2.5)), Some(3.0)) shouldBe None
        }
        "chimney (index 2) returns None" in {
            lengthSummary(slots, 2, Some(Vector(2.5)), Some(3.0)) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // lengthSummary — head region size 2
    // ════════════════════════════════════════════════════════════════

    "lengthSummary — head region size 2 [Flue, Flue, Connector, Chimney]" - {
        val slots = Seq(emptyFlue, emptyFlue, emptyConn, emptyChim)
        val lens  = Some(Vector(1.0, 2.0))
        val lzMin = Some(4.0)

        "S₀: all valid — shows 'Length: X' (no cum for headIdx=0)" in {
            lengthSummary(slots, 0, lens, lzMin) shouldBe Some("Length: 1.00 m")
        }
        "S₀: pipe Invalid — empty summary" in {
            lengthSummary(slots, 0, None, lzMin) shouldBe None
        }
        "S₀: lZMin Invalid — still 'Length: X' (min lives on S₁)" in {
            lengthSummary(slots, 0, lens, None) shouldBe Some("Length: 1.00 m")
        }
        "S₁ (last): all valid — shows 'Length: X (cum. Y, min. Z)'" in {
            // cum = 1.0 + 2.0 = 3.0
            lengthSummary(slots, 1, lens, lzMin) shouldBe
                Some("Length: 2.00 m (cum. 3.00 m, min. 4.00 m)")
        }
        "S₁ (last): pipe Invalid — empty summary" in {
            lengthSummary(slots, 1, None, lzMin) shouldBe None
        }
        "S₁ (last): lZMin Invalid — shows 'Length: X (cum. Y)' (D6)" in {
            lengthSummary(slots, 1, lens, None) shouldBe Some("Length: 2.00 m (cum. 3.00 m)")
        }
        "terminal connector (index 2) returns None" in {
            lengthSummary(slots, 2, lens, lzMin) shouldBe None
        }
        "chimney (index 3) returns None" in {
            lengthSummary(slots, 3, lens, lzMin) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // lengthSummary — head region size 3
    // ════════════════════════════════════════════════════════════════

    "lengthSummary — head region size 3 [Flue, Flue, Flue, Connector, Chimney]" - {
        val slots = Seq(emptyFlue, emptyFlue, emptyFlue, emptyConn, emptyChim)
        val lens  = Some(Vector(1.0, 2.0, 3.0))
        val lzMin = Some(10.0)

        "S₀: shows 'Length: X'" in {
            lengthSummary(slots, 0, lens, lzMin) shouldBe Some("Length: 1.00 m")
        }
        "S₁ (intermediate): shows 'Length: X (cum. Y)'" in {
            // cum = 1.0 + 2.0 = 3.0
            lengthSummary(slots, 1, lens, lzMin) shouldBe Some("Length: 2.00 m (cum. 3.00 m)")
        }
        "S₂ (last): shows 'Length: X (cum. Y, min. Z)'" in {
            // cum = 1.0 + 2.0 + 3.0 = 6.0
            lengthSummary(slots, 2, lens, lzMin) shouldBe
                Some("Length: 3.00 m (cum. 6.00 m, min. 10.00 m)")
        }
        "S₀: pipe Invalid — empty" in {
            lengthSummary(slots, 0, None, lzMin) shouldBe None
        }
        "S₁: pipe Invalid — empty" in {
            lengthSummary(slots, 1, None, lzMin) shouldBe None
        }
        "S₂ (last): pipe Invalid — empty" in {
            lengthSummary(slots, 2, None, lzMin) shouldBe None
        }
        "S₂ (last): lZMin Invalid — shows 'Length: X (cum. Y)'" in {
            lengthSummary(slots, 2, lens, None) shouldBe Some("Length: 3.00 m (cum. 6.00 m)")
        }
        "terminal connector (index 3) returns None" in {
            lengthSummary(slots, 3, lens, lzMin) shouldBe None
        }
        "chimney (index 4) returns None" in {
            lengthSummary(slots, 4, lens, lzMin) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // D5 fail-closed: intermediate Invalid makes downstream empty
    // ════════════════════════════════════════════════════════════════

    "D5 fail-closed: if any head-region pipe result is Invalid, lengths=None and all slots get empty summary" - {
        // Simulates S₁ being Invalid in a 3-slot region:
        // Container produces lengths=None (fail-closed), so S₂ also gets None.
        val slots = Seq(emptyFlue, emptyFlue, emptyFlue, emptyConn, emptyChim)

        "S₀ with lengths=None returns None" in {
            lengthSummary(slots, 0, None, Some(10.0)) shouldBe None
        }
        "S₁ with lengths=None returns None" in {
            lengthSummary(slots, 1, None, Some(10.0)) shouldBe None
        }
        "S₂ (last) with lengths=None returns None — not a partial cumulative" in {
            lengthSummary(slots, 2, None, Some(10.0)) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // connector-first head region (f416e7db topology)
    // ════════════════════════════════════════════════════════════════

    "lengthSummary — connector-first head region [Connector, Flue, Connector, Chimney]" - {
        // Head region = [S₀=Connector, S₁=Flue]; terminal connector = index 2
        val slots = Seq(emptyConn, emptyFlue, emptyConn, emptyChim)
        val lens  = Some(Vector(0.5, 1.5))
        val lzMin = Some(5.0)

        "S₀ (Connector, headIdx=0): shows 'Length: X'" in {
            lengthSummary(slots, 0, lens, lzMin) shouldBe Some("Length: 0.50 m")
        }
        "S₁ (Flue, last, headIdx=1): shows 'Length: X (cum. Y, min. Z)'" in {
            // cum = 0.5 + 1.5 = 2.0
            lengthSummary(slots, 1, lens, lzMin) shouldBe
                Some("Length: 1.50 m (cum. 2.00 m, min. 5.00 m)")
        }
        "terminal connector (index 2) returns None" in {
            lengthSummary(slots, 2, lens, lzMin) shouldBe None
        }
        "chimney (index 3) returns None" in {
            lengthSummary(slots, 3, lens, lzMin) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // head region size 0
    // ════════════════════════════════════════════════════════════════

    "lengthSummary — head region size 0 (no flue slots)" - {
        // [Connector, Chimney] — no flue so head region is empty
        val slots = Seq(emptyConn, emptyChim)

        "terminal connector (index 0) returns None" in {
            lengthSummary(slots, 0, Some(Vector.empty), Some(3.0)) shouldBe None
        }
        "chimney (index 1) returns None" in {
            lengthSummary(slots, 1, Some(Vector.empty), Some(3.0)) shouldBe None
        }
    }

    // ════════════════════════════════════════════════════════════════
    // D8 — single-slot head region: cum omitted (X ≡ Y), min shown
    // ════════════════════════════════════════════════════════════════

    "D8 — single-slot: 'Length: X (min. Z)' not 'Length: X (cum. X, min. Z)'" in {
        val slots  = Seq(emptyFlue, emptyConn, emptyChim)
        val result = lengthSummary(slots, 0, Some(Vector(2.5)), Some(3.0))
        result shouldBe Some("Length: 2.50 m (min. 3.00 m)")
        // Verify cum is NOT present
        result.foreach(_ should not include "cum.")
    }

end DynamicPipeSlotPanelNumberingAndLengthSuite
