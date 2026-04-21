/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.dev_fixtures.en15544.v20241001.EmptyHeadRegionFixture_15544
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.ChimneyPipeT

import cats.data.Validated

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test for the empty HEAD_REGION code path.
 *
 * Exercises the fallback branches added to:
 *   - `flueRegionPipeResults` — returns `Valid(Vector.empty, None)` when no flue slots
 *   - Stage-2 seeding         — seeds `UpstreamState` from `firebox_PipeResult`
 *   - `conceptualFluePipeResult` — falls back to `firebox_PipeResult`
 *   - `t_F`                   — falls back to `firebox_PipeResult.gas_temp_end`
 *
 * The topology is `[ConnectorSlot, ChimneySlot]` — zero masonry flue tunnels.
 *
 * NOT a golden validation fixture. See `docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md`.
 */
class EmptyHeadRegionSuite extends AnyFreeSpec with Matchers:

    "EmptyHeadRegionFixture_15544 — [Connector, Chimney] topology" - {

        "overall en15544_Alg result is Valid (no UnexpectedDevError)" in {
            val algV = EmptyHeadRegionFixture_15544.en15544_Alg
            algV shouldBe a[Validated.Valid[?]]
        }

        "stage-1 flue region results vector is empty" in {
            val algV = EmptyHeadRegionFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val stage1V = app.atDraftMin_LoadNominal.conceptualFlueRegionPipeResults
                    stage1V match
                        case Validated.Valid(results) =>
                            results shouldBe empty
                        case Validated.Invalid(nel)   =>
                            fail(s"conceptualFlueRegionPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "stage-2 produces exactly 2 PipeResults (connector + chimney)" in {
            val algV = EmptyHeadRegionFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val pfbV = app.atDraftMin_LoadNominal.postFireboxPipeResults
                    pfbV match
                        case Validated.Valid(vec)   =>
                            vec.size shouldBe 2
                            vec.map(_._1) shouldBe Vector(ConnectorPipeT, ChimneyPipeT)
                        case Validated.Invalid(nel) =>
                            fail(s"postFireboxPipeResults failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "t_F equals firebox_PipeResult.gas_temp_end (degenerate empty-flue fallback)" in {
            val algV = EmptyHeadRegionFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val atP     = app.atDraftMin_LoadNominal
                    val tFV     = atP.t_F
                    val fbTempV = atP.firebox_PipeResult.map(_.gas_temp_end)
                    (tFV, fbTempV) match
                        case (Validated.Valid(tF), Validated.Valid(fbTemp)) =>
                            tF.value shouldBe fbTemp.value
                        case _ =>
                            fail(s"t_F or firebox_PipeResult failed: tF=$tFV, fb=$fbTempV")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }

        "combustion efficiency η_calc(t_F) is a finite non-NaN value" in {
            val algV = EmptyHeadRegionFixture_15544.en15544_Alg
            algV match
                case Validated.Valid(app)   =>
                    val ηV = app.atDraftMin_LoadNominal.η
                    ηV match
                        case Validated.Valid(ηVal)  =>
                            val d = ηVal.value
                            d.isNaN shouldBe false
                            d.isInfinite shouldBe false
                        case Validated.Invalid(nel) =>
                            fail(s"η failed: ${nel.toList.mkString("; ")}")
                case Validated.Invalid(nel) =>
                    fail(s"en15544_Alg failed: ${nel.toList.mkString("; ")}")
        }
    }

end EmptyHeadRegionSuite
