/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.UnslottedFixture

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IncompatibleDirectionInPipeTargetingSuite extends AnyFlatSpec with Matchers with UnslottedFixture:

    "IncompatibleDirectionInPipe with a slot" should "target SlotTarget" in {
        val err = IncompatibleDirectionInPipe(FluePipeT, 0)(using SlotContext.forSlot(SlotIndex.unsafe(2)))
        err.target shouldBe ErrorTarget.SlotTarget(SlotIndex.unsafe(2))
    }

    "IncompatibleDirectionInPipe without a slot (air intake)" should "target TypeTarget" in {
        val err = IncompatibleDirectionInPipe(AirIntakePipeT, 3)
        err.target shouldBe ErrorTarget.TypeTarget(AirIntakePipeT)
    }

    "TypeTarget from unslotted error" should "match TypeScope" in {
        val err    = IncompatibleDirectionInPipe(AirIntakePipeT, 0)
        val target = err.target
        target shouldBe a[ErrorTarget.TypeTarget]
        target match
            case ErrorTarget.TypeTarget(pt) => pt shouldBe AirIntakePipeT
            case _                          => fail("Expected TypeTarget")
    }

    "ErrorTarget.SlotTarget" should "carry typed SlotIndex, not raw Int" in {
        val si     = SlotIndex.unsafe(5)
        val target = ErrorTarget.SlotTarget(si)
        target match
            case ErrorTarget.SlotTarget(idx) => idx.value shouldBe 5
            case _                           => fail("Expected SlotTarget")
    }

end IncompatibleDirectionInPipeTargetingSuite
