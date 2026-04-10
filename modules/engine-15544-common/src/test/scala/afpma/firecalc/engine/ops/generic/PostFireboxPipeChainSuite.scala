/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import cats.data.Validated.{Invalid, Valid}

import afpma.firecalc.engine.models.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PostFireboxPipeChainSuite extends AnyFreeSpec with Matchers:

    private def flue(label: String = "Flue"     ) = PipeSlot.noop(FluePipeT, label)
    private def conn(label: String = "Connector") = PipeSlot.noop(ConnectorPipeT, label)
    private def chim(label: String = "Chimney"  ) = PipeSlot.noop(ChimneyPipeT, label)

    "PostFireboxPipeChain.validated" - {

        "accepts flue + connector + chimney" in {
            PostFireboxPipeChain.validated(Vector(flue(), conn(), chim())) shouldBe a[Valid[?]]
        }

        "accepts chimney only (EN13384 standalone)" in {
            PostFireboxPipeChain.validated(Vector(chim())) shouldBe a[Valid[?]]
        }

        "accepts connector + chimney (no flue)" in {
            PostFireboxPipeChain.validated(Vector(conn(), chim())) shouldBe a[Valid[?]]
        }

        "accepts multiple flue pipes + connector + chimney" in {
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "rejects empty vector" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector.empty): @unchecked
            errs.toList should contain(TopologyError.MissingChimney)
        }

        "rejects when last slot is not chimney" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector(flue(), conn())): @unchecked
            errs.toList should contain(TopologyError.MissingChimney)
        }

        "rejects chimney not in last position" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector(chim(), flue())): @unchecked
            errs.toList should contain(TopologyError.ChimneyNotLast)
            errs.toList should contain(TopologyError.MissingChimney)
        }

        "rejects multiple connectors after last flue" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue(), conn("C1"), conn("C2"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MultipleConnectorsAfterFlue)
        }

        // ── V6 multi-slot topology validation ───────────────────────────

        "accepts flue + chimney (no connector, 2-slot minimal)" in {
            PostFireboxPipeChain.validated(Vector(flue(), chim())) shouldBe a[Valid[?]]
        }

        "accepts 4-slot multi-flue: F1 + F2 + connector + chimney" in {
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts connector interleaved in flue region before last flue" in {
            // Grammar: FLUE_PIPE_REGION := (FluePipeT | ConnectorPipeT)* FluePipeT
            // ConnectorPipeT before the last FluePipeT is part of the flue region
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C-in-flue"), flue("F2"), conn("C-after"), chim())
            ) shouldBe a[Valid[?]]
        }

        "rejects multiple chimneys" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue(), chim("CH1"), chim("CH2"))
            ): @unchecked
            errs.toList should contain(TopologyError.ChimneyNotLast)
        }

        "accepts flue after connector when flue is last in region" in {
            // Vector(F1, C, F-last, chimney): F-last becomes the last flue,
            // C is absorbed into the flue region, afterFlueBeforeChimney is empty → valid
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C"), flue("F-last"), chim())
            ) shouldBe a[Valid[?]]
        }
    }

    "region accessors" - {

        "fluePipeRegion returns all slots up to last FluePipeT" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), conn(), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "F2")
        }

        "fluePipeRegion is empty when no flue pipes" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(conn(), chim())
            ): @unchecked
            chain.fluePipeRegion shouldBe empty
        }

        "connectorSlot returns connector after flue region" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), conn("C"), chim())
            ): @unchecked
            chain.connectorSlot.map(_.label) shouldBe Some("C")
        }

        "connectorSlot is None when no connector" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), chim())
            ): @unchecked
            chain.connectorSlot shouldBe None
        }

        "chimneySlot is always the last slot" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), conn(), chim("CH"))
            ): @unchecked
            chain.chimneySlot.label shouldBe "CH"
        }

        // ── V6 multi-slot region accessor tests ─────────────────────────

        "fluePipeRegion includes all flues in 4-slot multi-flue topology" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "F2", "F3")
        }

        "connectorSlot is None for flue + chimney (no connector)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F"), chim())
            ): @unchecked
            chain.connectorSlot shouldBe None
        }

        "fluePipeRegion absorbs connector interleaved before last flue" in {
            // Vector(F1, C-mid, F2, C-after, chimney)
            // lastFlueIdx = 2 (F2), so fluePipeRegion = [F1, C-mid, F2]
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C-mid"), flue("F2"), conn("C-after"), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "C-mid", "F2")
            chain.connectorSlot.map(_.label) shouldBe Some("C-after")
        }

        "chimneySlot for minimal 2-slot topology (flue + chimney)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), chim("CH"))
            ): @unchecked
            chain.chimneySlot.label shouldBe "CH"
            chain.fluePipeRegion.size shouldBe 1
            chain.connectorSlot shouldBe None
        }
    }
