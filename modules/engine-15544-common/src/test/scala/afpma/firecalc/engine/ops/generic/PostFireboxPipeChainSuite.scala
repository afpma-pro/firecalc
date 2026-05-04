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

        "rejects chimney-only under EN15544 grammar (missing terminal connector)" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector(chim())): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
        }

        "accepts connector + chimney (empty head region is legal everywhere)" in {
            // Empty HEAD_REGION is legal across all pipelines (EN 15544 Strict, EN 15544
            // MCE, EN 13384 Strict). The minimal legal chain is terminal connector + chimney.
            PostFireboxPipeChain.validated(Vector(conn(), chim())) shouldBe a[Valid[?]]
        }

        "accepts multiple consecutive flue pipes + terminal connector + chimney" in {
            // Under the grammar (post-alternation-drop), consecutive same-type pipes
            // in HEAD_REGION are legal. Head = [F1, F2, F3] ends with Flue → valid.
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts alternating multi-head: F C F C F + terminal connector + chimney" in {
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C1"), flue("F2"), conn("C2"), flue("F3"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts consecutive connectors in head: C C F + terminal connector + chimney" in {
            // Consecutive Connectors in HEAD_REGION are legal under the new grammar;
            // head = [C1, C2, F] ends with Flue → valid.
            PostFireboxPipeChain.validated(
                Vector(conn("C1"), conn("C2"), flue("F"), conn("Cterm"), chim())
            ) shouldBe a[Valid[?]]
        }

        // ── Connector-first head topologies ───────────────────────────────

        "accepts Connector-first head [C, F] + terminal connector + chimney" in {
            // [ConnectorPipe, FluePipe] head — HEAD_REGION may start with Connector.
            PostFireboxPipeChain.validated(
                Vector(conn("Chead"), flue("F"), conn("Cterm"), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts alternating Connector-first head [C, F, C, F] + terminal connector + chimney" in {
            // [ConnectorPipe, FluePipe, ConnectorPipe, FluePipe] head — legal.
            PostFireboxPipeChain.validated(
                Vector(
                    conn("Chead1"),
                    flue("F1"    ),
                    conn("Chead2"),
                    flue("F2"    ),
                    conn("Cterm" ),
                    chim(        )
                )
            ) shouldBe a[Valid[?]]
        }

        "rejects head [C] alone — HeadRegionEndsWithConnector" in {
            // Slot vector [conn, conn(term), chim]: terminal connector is present,
            // head = [conn], which ends with Connector → HeadRegionEndsWithConnector.
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(conn("Chead"), conn("Cterm"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.HeadRegionEndsWithConnector)
        }

        "rejects head [F, C] — HeadRegionEndsWithConnector" in {
            // Slot vector [flue, conn, conn(term), chim]: terminal connector present,
            // head = [flue, conn], head.last = Connector → HeadRegionEndsWithConnector.
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F"), conn("Chead"), conn("Cterm"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.HeadRegionEndsWithConnector)
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

        // ── V6 multi-slot topology validation ───────────────────────────

        "rejects flue + chimney when connector-position slot is missing" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(Vector(flue(), chim())): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
        }

        "accepts 4-slot multi-flue: F1 + F2 + connector + chimney" in {
            // Consecutive flues in HEAD_REGION are now legal.
            PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), conn(), chim())
            ) shouldBe a[Valid[?]]
        }

        "accepts connector interleaved in flue region before last flue" in {
            // HEAD_REGION: (FluePipeT | ConnectorPipeT)* FluePipeT
            // ConnectorPipeT before the last FluePipeT is part of the head region.
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
            // Vector(F1, C, F-last, chimney): terminal connector slot is absent.
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C"), flue("F-last"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
        }

        "accumulates multiple errors in single invalid topology" in {
            // chimney in wrong position + missing chimney at end
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(chim("CH-wrong"), flue(), conn())
            ): @unchecked
            errs.toList should contain(TopologyError.ChimneyNotLast)
            errs.toList should contain(TopologyError.MissingChimney)
        }

        "accepts connector-only + chimney (empty head region)" in {
            PostFireboxPipeChain.validated(
                Vector(conn(), chim())
            ) shouldBe a[Valid[?]]
        }
    }

    "region accessors" - {

        "fluePipeRegion returns all slots up to last FluePipeT (alternating head)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C-mid"), flue("F2"), conn(), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "C-mid", "F2")
        }

        "fluePipeRegion is empty when no flue pipes (empty head region)" in {
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

        "connectorSlot is None for chimney-only (connector missing — rejected)" in {
            // Neither EN13384 nor EN15544 callers actually emit a single-chimney chain;
            // the EN13384 builder always emits [connSlot, chimSlot] (connSlot may be a
            // no-op). Under the grammar, [chim] lacks the mandatory terminal connector
            // slot and must fail validation.
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
        }

        "chimneySlot is always the last slot" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue(), conn(), chim("CH"))
            ): @unchecked
            chain.chimneySlot.label shouldBe "CH"
        }

        // ── V6 multi-slot region accessor tests ─────────────────────────

        "fluePipeRegion includes all flues in 7-slot alternating topology" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C1"), flue("F2"), conn("C2"), flue("F3"), conn(), chim())
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "C1", "F2", "C2", "F3")
        }

        "rejects multi-flue + chimney when terminal connector missing" in {
            // Vector(F1, F2, F3, chim): consecutive flues are legal under the new grammar,
            // but the terminal connector slot between head and chimney is absent.
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), flue("F2"), flue("F3"), chim())
            ): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
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

        "fluePipeRegion includes all 3 flues in alternating 7-slot topology" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C1"), flue("F2"), conn("C2"), flue("F3"), conn("C"), chim("CH"))
            ): @unchecked
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "C1", "F2", "C2", "F3")
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }

        "all accessors for connector-only + chimney (empty head region)" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(conn("C"), chim("CH"))
            ): @unchecked
            chain.fluePipeRegion shouldBe empty
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }

        "rejects chimney-only (minimal topology) — missing terminal connector" in {
            val Invalid(errs) = PostFireboxPipeChain.validated(
                Vector(chim("CH"))
            ): @unchecked
            errs.toList should contain(TopologyError.MissingTerminalConnector)
        }

        // ── Phase 0.3: 5-slot chain invariants ──────────────────────────

        "alternating 7-slot chain F1+C1+F2+C2+F3+C+CH: slot count, region, connector, chimney" in {
            val Valid(chain) = PostFireboxPipeChain.validated(
                Vector(flue("F1"), conn("C1"), flue("F2"), conn("C2"), flue("F3"), conn("C"), chim("CH"))
            ): @unchecked
            chain.slots.length shouldBe 7
            chain.fluePipeRegion.length shouldBe 5 // lastFluePipeIdx == 4 (F3)
            chain.fluePipeRegion.map(_.label) shouldBe Vector("F1", "C1", "F2", "C2", "F3")
            chain.connectorSlot.map(_.label) shouldBe Some("C")
            chain.chimneySlot.label shouldBe "CH"
        }
    }
