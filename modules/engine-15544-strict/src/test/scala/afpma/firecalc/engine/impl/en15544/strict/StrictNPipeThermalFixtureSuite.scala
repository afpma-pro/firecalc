/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.StrictNPipeThermalFixture_15544
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Step 6 — Smoke test for the Strict N-pipe mixed `FlueSlot` + `ThermalFlueSlot` chain path.
 *
 * Instantiates `StrictNPipeThermalFixture_15544` (a 4-slot Strict topology with a
 * `FlueSlot` followed by a `ThermalFlueSlot` in the flue region) and probes the
 * chain path end-to-end, verifying that Stage 1 folds correctly over both slot types.
 *
 * ── Design ──────────────────────────────────────────────────────────────────
 *
 * `PipeChain_15544_Strict.toSlots` collapses the entire `fluePipeDescr` Seq into
 * a single `FlueSlot`. To exercise the mixed-slot Stage 1 fold,
 * `StrictNPipeThermalFixture_15544` overrides `postFireboxPipeSlots` directly with a
 * hand-crafted 4-slot vector:
 *   [FlueSlot, ThermalFlueSlot, ConnectorSlot, ChimneySlot]
 *
 * This exercises the new `ThermalFlueSlot` arm in Stage 1 that was unlocked by
 * Step 6 of the N-pipe topology remediation.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy — this suite is NOT a golden validation.
 */
class StrictNPipeThermalFixtureSuite extends AnyFreeSpec with Matchers:

    "StrictNPipeThermalFixture_15544" - {

        "overrides postFireboxPipeSlots with a 4-slot vector (FlueSlot, ThermalFlueSlot, ConnectorSlot, ChimneySlot)" in {
            val slots = StrictNPipeThermalFixture_15544.postFireboxPipeSlots
            import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
            slots.size shouldBe 4
            slots(0) shouldBe a[FlueSlot]
            slots(1) shouldBe a[ThermalFlueSlot]
            slots(2) shouldBe a[ConnectorSlot]
            slots(3) shouldBe a[ChimneySlot]
        }

        "drives the Strict Stage 1 path over FlueSlot + ThermalFlueSlot, producing 2 flue PipeResults" in {
            val algV = StrictNPipeThermalFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val stage1V = app.atDraftMin_LoadNominal.conceptualFlueRegionPipeResults
                    stage1V match
                        case Validated.Valid(results) =>
                            // FlueSlot + ThermalFlueSlot in the flue region → 2 PipeResults
                            results.size shouldBe 2
                        case Validated.Invalid(nel)   =>
                            fail(s"Stage 1 failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "produces a 4-tuple postFireboxPipeResults (flue, flue, connector, chimney)" in {
            val algV = StrictNPipeThermalFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
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

end StrictNPipeThermalFixtureSuite
