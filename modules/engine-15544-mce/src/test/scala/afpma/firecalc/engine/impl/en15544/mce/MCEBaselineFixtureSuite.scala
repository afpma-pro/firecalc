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

/** Smoke test for the MCE `WithPipeChain_15544_MCE` chain path.
  *
  * Instantiates `MCEBaselineFixture_15544` (a baseline 3-pipe topology that
  * mixes in `WithPipeChain_15544_MCE`) and probes the chain path end-to-end.
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
