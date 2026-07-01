/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.StrictInterleavedConnectorFixture_15544
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test for interleaved `ConnectorSlot` support in Strict Stage 1.
 *
 * Instantiates `StrictInterleavedConnectorFixture_15544` (a 5-slot Strict topology with
 * a `ConnectorSlot` interleaved between two `FlueSlot`s in the flue region) and probes
 * the chain path end-to-end, verifying that Stage 1 folds correctly over the
 * interleaved connector using Thermal 13384 computation.
 *
 * ── Design ──────────────────────────────────────────────────────────────────
 *
 * `StrictInterleavedConnectorFixture_15544` overrides `postFireboxPipeSlots` directly
 * with a hand-crafted 5-slot vector:
 *   [FlueSlot, ConnectorSlot, FlueSlot, ConnectorSlot, ChimneySlot]
 *
 * The flue region spans slots 0-2 (up to and including the last FlueSlot at index 2).
 * Slot 1 (ConnectorSlot) is interleaved within the flue region and must be computed
 * via Thermal 13384 rather than rejected.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy — this suite is NOT a golden validation.
 */
class StrictInterleavedConnectorFixtureSuite extends AnyFreeSpec with Matchers:

    "StrictInterleavedConnectorFixture_15544" - {

        "overrides postFireboxPipeSlots with a 5-slot vector" in {
            val slots = StrictInterleavedConnectorFixture_15544.postFireboxPipeSlots
            import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot.*
            slots.size shouldBe 5
            slots(0) shouldBe a[FlueSlot]
            slots(1) shouldBe a[ConnectorSlot]
            slots(2) shouldBe a[FlueSlot]
            slots(3) shouldBe a[ConnectorSlot]
            slots(4) shouldBe a[ChimneySlot]
        }

        "en15544_Alg is valid" in {
            val algV = StrictInterleavedConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(_)     => succeed
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "Stage 1 computes FlueSlot + ConnectorSlot + FlueSlot in flue region (3 results)" in {
            val algV = StrictInterleavedConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val stage1V = app.atDraftMin_LoadNominal.conceptualFlueRegionPipeResults
                    stage1V match
                        case Validated.Valid(results) =>
                            // FlueSlot + ConnectorSlot + FlueSlot in the flue region → 3 PipeResults
                            results.size shouldBe 3
                        case Validated.Invalid(nel)   =>
                            fail(s"Stage 1 failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "produces a 5-tuple postFireboxPipeResults" in {
            val algV = StrictInterleavedConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            vec.size shouldBe 5
                            vec.map(_._1) shouldBe Vector(
                                FluePipeT,
                                ConnectorPipeT,
                                FluePipeT,
                                ConnectorPipeT,
                                ChimneyPipeT
                            )
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }
    }

end StrictInterleavedConnectorFixtureSuite
