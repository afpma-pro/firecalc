/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran&#231;aise du Po&#235;le Ma&#235;onn&#233; Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.{ErrorTarget, SlotIndex}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Unit tests for the `PanelScope.sees` predicate.
 *
 * Covers every branch of the scope-containment filter:
 * slot-to-slot matching, type-to-type matching, global passthrough,
 * and cross-scope rejection.
 */
class PanelScopeFilterTest extends AnyFreeSpec with Matchers:

    "SlotScope sees SlotTarget for matching slot" in {
        val scope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        scope.sees(ErrorTarget.SlotTarget(SlotIndex.unsafe(2))) shouldBe true
    }

    "SlotScope does NOT see SlotTarget for other slot" in {
        val scope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        scope.sees(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))) shouldBe false
    }

    "SlotScope sees TypeTarget for matching type" in {
        val scope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        scope.sees(ErrorTarget.TypeTarget(FluePipeT)) shouldBe true
    }

    "SlotScope does NOT see TypeTarget for other type" in {
        val scope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(2))
        scope.sees(ErrorTarget.TypeTarget(ConnectorPipeT)) shouldBe false
    }

    "TypeScope sees TypeTarget for matching type" in {
        val scope = PanelScope.TypeScope(FireboxPipeT)
        scope.sees(ErrorTarget.TypeTarget(FireboxPipeT)) shouldBe true
    }
    "TypeScope does NOT see SlotTarget" in {
        val scope = PanelScope.TypeScope(FluePipeT)
        scope.sees(ErrorTarget.SlotTarget(SlotIndex.unsafe(1))) shouldBe false
    }

    "MultiTypeScope does NOT see SlotTarget for FluePipeT+ConnectorPipeT" in {
        val scope = PanelScope.MultiTypeScope(List(FluePipeT, ConnectorPipeT))
        scope.sees(ErrorTarget.SlotTarget(SlotIndex.unsafe(1))) shouldBe false
    }

    "SlotScope sees GlobalTarget" in {
        val scope = PanelScope.SlotScope(FluePipeT, SlotIndex.unsafe(0))
        scope.sees(ErrorTarget.GlobalTarget) shouldBe true
    }

    "TypeScope sees GlobalTarget" in {
        val scope = PanelScope.TypeScope(FireboxPipeT)
        scope.sees(ErrorTarget.GlobalTarget) shouldBe true
    }

    "MultiTypeScope sees TypeTarget for contained type" in {
        val scope = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))
        scope.sees(ErrorTarget.TypeTarget(FireboxPipeT)      ) shouldBe true
        scope.sees(ErrorTarget.TypeTarget(CombustionAirPipeT)) shouldBe true
    }

    "MultiTypeScope does NOT see TypeTarget for uncontained type" in {
        val scope = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))
        scope.sees(ErrorTarget.TypeTarget(FluePipeT)) shouldBe false
    }

    "MultiTypeScope sees GlobalTarget" in {
        val scope = PanelScope.MultiTypeScope(List(FireboxPipeT))
        scope.sees(ErrorTarget.GlobalTarget) shouldBe true
    }

    "MultiTypeScope does NOT see SlotTarget" in {
        val scope = PanelScope.MultiTypeScope(List(FireboxPipeT, CombustionAirPipeT))
        scope.sees(ErrorTarget.SlotTarget(SlotIndex.unsafe(0))) shouldBe false
    }

end PanelScopeFilterTest
