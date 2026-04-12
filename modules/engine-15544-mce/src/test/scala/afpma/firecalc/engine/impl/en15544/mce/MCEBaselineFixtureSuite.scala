/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.dev_fixtures.en15544.MCEBaselineFixture_15544
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Step 5.4 — Smoke test for the MCE `WithPipeChain_15544_MCE` chain path.
  *
  * Instantiates `MCEBaselineFixture_15544` (a baseline 3-pipe topology that
  * mixes in `WithPipeChain_15544_MCE`) and probes the chain path end-to-end.
  *
  * ── Step 5.4 finding ────────────────────────────────────────────────────
  *
  * Test #1 (below) asserts the **descriptor-level** override works:
  * `MCEBaselineFixture_15544.postFireboxPipeSlots` returns the 3-slot
  * chain-aware vector built by `PipeChain_15544_MCE.toSlots`.
  *
  * Tests #2 and #3 are `ignore`d because Step 5.1 is incomplete: the
  * override lives on `WithPipeChain_15544_MCE` (a project-descriptor trait),
  * but `EN15544_MCE_Application` reads `en15544_mce.postFireboxPipeSlots`
  * via its own `self =>` self-reference, which inherits the common
  * algebra's default `Seq(FlueSlot(Seq.empty), ConnectorSlot(Seq.empty),
  * ChimneySlot(Seq.empty))` rather than the descriptor's override. The
  * missing wiring is the symmetric equivalent of
  * `EN15544_Strict_Application.make(...)(i, postFireboxPipeSlots)` which
  * threads the slots through a second curried parameter — MCE has no such
  * parameter.
  *
  * Concretely: dropping a `println` probe into the non-empty branch of
  * `flueRegionPipeResults` and running this suite under the fixture
  * **did** fire (chain path IS selected at runtime by the application),
  * but Stage 1 then iterates over the common algebra's default slots —
  * which contain a `FlueSlot` (flow-only) at index 0 — and rejects with
  * `NotYetSupportedInFlueRegion(FlueSlot (flow-only) in MCE flue region
  * — MCE uses ThermalFlueSlot)`. So the descriptor override is unseen by
  * the application until the wiring is fixed.
  *
  * Un-ignore tests #2 and #3 once Step 5.1 is completed (either by adding
  * a `pfbSlots` curried parameter to `EN15544_MCE_Application.make` or by
  * overriding `postFireboxPipeSlots` inside the anonymous instance built
  * in `StoveProjectDescr_15544_MCE_Alg.en15544_Alg`).
  *
  * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
  * policy — this suite is NOT a golden validation.
  */
class MCEBaselineFixtureSuite extends AnyFreeSpec with Matchers:

    "MCEBaselineFixture_15544" - {

        "mixes in WithPipeChain_15544_MCE and produces a 3-slot postFireboxPipeSlots at the descriptor level" in {
            val slots = MCEBaselineFixture_15544.postFireboxPipeSlots
            import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
            slots should not be empty
            slots.size shouldBe 3
            slots(0) shouldBe a[ThermalFlueSlot]
            slots(1) shouldBe a[ConnectorSlot]
            slots(2) shouldBe a[ChimneySlot]
        }

        "drives the MCE chain-aware Stage 1 path (flueRegionPipeResults non-empty branch)" in {
            // Step 5.1b: wiring completed — pfbSlots now forwarded via EN15544_MCE_Application.make second curry.
            val algV = MCEBaselineFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app) =>
                    val stage1V = app.atDraftMin_LoadNominal.conceptualFlueRegionPipeResults
                    stage1V match
                        case Validated.Valid(results) =>
                            results.size shouldBe 1
                        case Validated.Invalid(nel)   =>
                            fail(s"Stage 1 failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "produces a 3-tuple postFireboxPipeResults (flue, connector, chimney)" in {
            // Step 5.1b: wiring completed — pfbSlots now forwarded via EN15544_MCE_Application.make second curry.
            val algV = MCEBaselineFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app) =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            vec.map(_._1) shouldBe Vector(FluePipeT, ConnectorPipeT, ChimneyPipeT)
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }
    }

end MCEBaselineFixtureSuite
