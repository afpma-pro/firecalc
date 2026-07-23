/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.engine.standard.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SlotIndexSuite extends AnyFlatSpec with Matchers:

    "SlotIndex.from" should "return Some for non-negative values" in {
        SlotIndex.from(0) shouldBe defined
        SlotIndex.from(1) shouldBe defined
        SlotIndex.from(42) shouldBe defined
    }

    it should "return None for negative values" in {
        SlotIndex.from(-1) shouldBe empty
        SlotIndex.from(-100) shouldBe empty
        SlotIndex.from(Int.MinValue) shouldBe empty
    }

    it should "preserve the value" in {
        SlotIndex.from(0).map(_.value) shouldBe Some(0)
        SlotIndex.from(7).map(_.value) shouldBe Some(7)
    }

    "SlotIndex.unsafe" should "create a SlotIndex for non-negative values" in {
        SlotIndex.unsafe(0).value shouldBe 0
        SlotIndex.unsafe(5).value shouldBe 5
    }

    "SlotIndex.isFirst" should "be true only for index 0" in {
        SlotIndex.unsafe(0).isFirst shouldBe true
        SlotIndex.unsafe(1).isFirst shouldBe false
        SlotIndex.unsafe(99).isFirst shouldBe false
    }

    "SlotIndex equality" should "be structural" in {
        SlotIndex.unsafe(3) shouldBe SlotIndex.unsafe(3)
        SlotIndex.unsafe(3) should not be SlotIndex.unsafe(4)
    }

end SlotIndexSuite
