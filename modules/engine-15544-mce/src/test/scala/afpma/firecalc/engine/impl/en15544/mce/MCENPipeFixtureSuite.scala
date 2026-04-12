/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.MCENPipeFixture_15544
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Smoke test for the MCE N-pipe multi-`ThermalFlueSlot` chain path.
  *
  * Instantiates `MCENPipeFixture_15544` (a 4-slot N-pipe MCE topology with two
  * distinct `ThermalFlueSlot` entries) and probes the chain path end-to-end,
  * verifying that Stage 1 folds correctly over multiple thermal flue slots.
  *
  * ── Design ──────────────────────────────────────────────────────────────────
  *
  * `PipeChain_15544_MCE.toSlots` collapses the entire `fluePipeDescr` Seq into a
  * single `ThermalFlueSlot`. To exercise the multi-slot Stage 1 fold,
  * `MCENPipeFixture_15544` overrides `postFireboxPipeSlots` directly with a
  * hand-crafted 4-slot vector:
  *   [ThermalFlueSlot#1, ThermalFlueSlot#2, ConnectorSlot, ChimneySlot]
  *
  * This is the MCE equivalent of `NPipeTopologyFixture_15544`'s 5-slot override
  * for Strict.
  *
  * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
  * policy — this suite is NOT a golden validation.
  */
class MCENPipeFixtureSuite extends AnyFreeSpec with Matchers:

    "MCENPipeFixture_15544" - {

        "overrides postFireboxPipeSlots with a 4-slot N-pipe vector containing 2 ThermalFlueSlots" in {
            val slots = MCENPipeFixture_15544.postFireboxPipeSlots
            import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
            slots.size should be >= 4
            // First two slots must be ThermalFlueSlots (multi-flue segment)
            val thermalFlueSlots = slots.collect { case s: ThermalFlueSlot => s }
            thermalFlueSlots.size should be >= 2
            // Slot ordering: ThermalFlueSlot, ThermalFlueSlot, ConnectorSlot, ChimneySlot
            slots(0) shouldBe a[ThermalFlueSlot]
            slots(1) shouldBe a[ThermalFlueSlot]
            slots(2) shouldBe a[ConnectorSlot]
            slots(3) shouldBe a[ChimneySlot]
        }

        "drives the MCE chain-aware Stage 1 path over multiple ThermalFlueSlots" in {
            val algV = MCENPipeFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app) =>
                    val stage1V = app.atDraftMin_LoadNominal.conceptualFlueRegionPipeResults
                    stage1V match
                        case Validated.Valid(results) =>
                            // Two ThermalFlueSlots in the flue region → 2 PipeResults
                            results.size shouldBe 2
                        case Validated.Invalid(nel)   =>
                            fail(s"Stage 1 failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "produces a 4-tuple postFireboxPipeResults (flue, flue, connector, chimney)" in {
            val algV = MCENPipeFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app) =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            vec.size shouldBe 4
                            vec.map(_._1) shouldBe Vector(FluePipeT, FluePipeT, ConnectorPipeT, ChimneyPipeT)
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }
    }

end MCENPipeFixtureSuite
