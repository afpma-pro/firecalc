/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.StrictWithoutConnectorFixture_15544
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test for the Strict N-pipe `ConnectorSlot(Seq.empty)` no-op semantics.
 *
 * Instantiates `StrictWithoutConnectorFixture_15544` (a 3-slot Strict topology
 * where the connector slot carries empty descriptors) and proves that:
 *   1. The 3-slot vector is correctly shaped (FlueSlot, ConnectorSlot-empty, ChimneySlot).
 *   2. `en15544_Alg` validates successfully.
 *   3. `postFireboxPipeResults` produces 3 results with correct pipe-type tags.
 *   4. The empty connector propagates upstream state (no-op) without breaking
 *      downstream chimney computation.
 *
 * See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md` for the golden-fixture
 * policy — this suite is NOT a golden validation.
 */
class StrictWithoutConnectorFixtureSuite extends AnyFreeSpec with Matchers:

    "StrictWithoutConnectorFixture_15544" - {

        "overrides postFireboxPipeSlots with a 3-slot vector (FlueSlot, ConnectorSlot-empty, ChimneySlot)" in {
            val slots = StrictWithoutConnectorFixture_15544.postFireboxPipeSlots
            import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot.*
            slots.size shouldBe 3
            slots(0) shouldBe a[FlueSlot]
            slots(1) shouldBe a[ConnectorSlot]
            slots(1).asInstanceOf[ConnectorSlot].descr shouldBe empty
            slots(2) shouldBe a[ChimneySlot]
        }

        "en15544_Alg is valid" in {
            val algV = StrictWithoutConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(_)     => succeed
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "produces a 3-tuple postFireboxPipeResults (flue, connector, chimney)" in {
            val algV = StrictWithoutConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            vec.size shouldBe 3
                            vec.map(_._1) shouldBe Vector(FluePipeT, ConnectorPipeT, ChimneyPipeT)
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "connector result propagates upstream state (no-op semantics)" in {
            val algV = StrictWithoutConnectorFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            // Connector result should exist and be tagged ConnectorPipeT
                            val (connType, connResult) = vec(1)
                            connType shouldBe ConnectorPipeT
                            // Chimney should also succeed (it receives propagated state from connector)
                            val (chimType, _         ) = vec(2)
                            chimType shouldBe ChimneyPipeT
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }
    }

end StrictWithoutConnectorFixtureSuite
