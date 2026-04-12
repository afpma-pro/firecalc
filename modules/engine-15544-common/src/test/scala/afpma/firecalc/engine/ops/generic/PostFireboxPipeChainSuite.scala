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

        "rejects flue + chimney when connector-position slot is missing" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector(flue(), chim())): @unchecked
            errs.toList should contain(TopologyError.MissingConnectorAfterFlue)
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

        "rejects flue after connector when trailing connector is missing" in {
            // Vector(F1, C, F-last, chimney): F-last is the last flue,
            // C is absorbed into the flue region, afterFlueBeforeChimney is empty → Rule 6 rejects
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C"), flue("F-last"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MissingConnectorAfterFlue)
        }

        "rejects multiple connectors in 4-slot topology (multi-flue context)" in {
            // F1 + F2 + C1 + C2 + chimney: lastFlueIdx = 1 (F2),
            // afterFlueBeforeChimney = [C1, C2] → 2 connectors → rejected
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), conn("C1"), conn("C2"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MultipleConnectorsAfterFlue)
        }

        "accumulates multiple errors in single invalid topology" in {
            // chimney in wrong position + missing chimney at end
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(chim("CH-wrong"), flue(), conn())
            ): @unchecked
            errs.toList should contain(TopologyError.ChimneyNotLast)
            errs.toList should contain(TopologyError.MissingChimney)
        }

        "accepts 5-slot chain: F1 + F2 + F3 + connector + chimney" in {
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts connector-only + chimney (no flue region)" in {
            PostFireboxPipeChain.validated(
                Vector(conn(), chim())
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

        "connectorSlot is None for chimney-only (no flue region)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(chim())
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

        "fluePipeRegion includes all flues in 5-slot multi-flue topology" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn(), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "F2", "F3")
        }

        "rejects multi-flue + chimney when connector is missing" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MissingConnectorAfterFlue)
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

        "chimneySlot for minimal 3-slot topology (flue + connector + chimney)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), conn("C"), chim("CH"))
            ): @unchecked
            chain.chimneySlot.label shouldBe "CH"
            chain.fluePipeRegion.size shouldBe 1
            chain.connectorSlot.map(_.label) shouldBe Some("C")
        }

        "fluePipeRegion includes all 3 flues in 5-slot topology" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn("C"), chim("CH"))
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "F2", "F3")
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }

        "all accessors for connector-only + chimney (no flue region)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(conn("C"), chim("CH"))
            ): @unchecked
            chain.fluePipeRegion shouldBe empty
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }

        "all accessors for chimney-only (minimal topology)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(chim("CH"))
            ): @unchecked
            chain.fluePipeRegion shouldBe empty
            chain.connectorSlot shouldBe None
            chain.chimneySlot.label shouldBe "CH"
        }

        // ── Phase 0.3: 5-slot chain invariants ──────────────────────────

        "5-slot chain F1+F2+F3+C+CH: slot count, region, connector, chimney" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn("C"), chim("CH"))
            ): @unchecked
            chain.slots.length shouldBe 5
            chain.fluePipeRegion.length shouldBe 3           // lastFluePipeIdx == 2
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "F2", "F3")
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }
    }
