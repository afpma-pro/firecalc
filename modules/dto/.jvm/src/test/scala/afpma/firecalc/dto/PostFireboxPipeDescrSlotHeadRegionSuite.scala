/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import org.scalacheck.Gen
import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/**
 * Property-based tests for [[PostFireboxPipeDescrSlot.headRegionIndices]].
 *
 * The helper is a pure structural scan that does not assume grammar validity,
 * so we test it against arbitrary slot sequences built from the four enum
 * constructors. Only the *shape* of the sequence matters — descriptor payloads
 * are always empty for test speed.
 */
class PostFireboxPipeDescrSlotHeadRegionSuite extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(minSuccessful = PosInt(200))

    // ── Generators ──────────────────────────────────────────────────

    // Descriptor payload is irrelevant for head-region membership — use empty Seq everywhere.
    private val genFlue     : Gen[PostFireboxPipeDescrSlot] = Gen.const(FlueSlot(Seq.empty)       )
    private val genThermal  : Gen[PostFireboxPipeDescrSlot] = Gen.const(ThermalFlueSlot(Seq.empty))
    private val genConnector: Gen[PostFireboxPipeDescrSlot] = Gen.const(ConnectorSlot(Seq.empty)  )
    private val genChimney  : Gen[PostFireboxPipeDescrSlot] = Gen.const(ChimneySlot(Seq.empty)    )
    private val genNoFlue   : Gen[PostFireboxPipeDescrSlot] = Gen.const(NoFlueSlot)

    private val genAnySlot: Gen[PostFireboxPipeDescrSlot] =
        Gen.oneOf(genFlue, genThermal, genConnector, genChimney, genNoFlue)

    private val genSlotSeq: Gen[Seq[PostFireboxPipeDescrSlot]] =
        Gen.choose(0, 8).flatMap(n => Gen.listOfN(n, genAnySlot))

    private def isFlueLike(s: PostFireboxPipeDescrSlot): Boolean = s match
        case _: FlueSlot | _: ThermalFlueSlot => true
        case _                                => false

    // ── Properties ──────────────────────────────────────────────────

    "PostFireboxPipeDescrSlot.headRegionIndices" - {

        "result is always a contiguous prefix 0..k-1 (or empty)" in
            forAll(genSlotSeq) { slots =>
                val idxs = headRegionIndices(slots)
                if idxs.nonEmpty then idxs.shouldBe((0 to idxs.last).toVector)
            }

        "result is empty iff there is no flue-like slot anywhere" in
            forAll(genSlotSeq) { slots =>
                val idxs    = headRegionIndices(slots)
                val hasFlue = slots.exists(isFlueLike)
                idxs.isEmpty.shouldBe(!hasFlue)
            }

        "when non-empty, the last index points to a flue-like slot" in
            forAll(genSlotSeq) { slots =>
                val idxs = headRegionIndices(slots)
                whenever(idxs.nonEmpty) {
                    isFlueLike(slots(idxs.last)).shouldBe(true)
                }
            }

        "when non-empty, the last index is the *last* flue-like slot in the input" in
            forAll(genSlotSeq) { slots =>
                val idxs = headRegionIndices(slots)
                whenever(idxs.nonEmpty) {
                    val lastFlueIdx = slots.zipWithIndex.collect { case (s, i) if isFlueLike(s) => i }.max
                    idxs.last.shouldBe(lastFlueIdx)
                }
            }

        "no slot past the result is flue-like" in
            forAll(genSlotSeq) { slots =>
                val idxs   = headRegionIndices(slots)
                val cutoff = idxs.lastOption.fold(-1)(identity)
                slots.zipWithIndex.drop(cutoff + 1).exists((s, _) => isFlueLike(s)).shouldBe(false)
            }

        "result length never exceeds slot count" in
            forAll(genSlotSeq) { slots =>
                (headRegionIndices(slots).size <= slots.size).shouldBe(true)
            }
    }

    // ── Hand-picked smoke cases ──────────────────────────────────────

    "PostFireboxPipeDescrSlot.headRegionIndices — hand-picked cases" - {

        "empty input → empty" in
            headRegionIndices(Seq.empty).shouldBe(Vector.empty)

        "single chimney → empty (no flue)" in
            headRegionIndices(Seq(ChimneySlot(Seq.empty))).shouldBe(Vector.empty)

        "[Flue, Connector, Chimney] → [0]" in
            headRegionIndices(
                Seq(
                    FlueSlot     (Seq.empty),
                    ConnectorSlot(Seq.empty),
                    ChimneySlot  (Seq.empty)
                )
            ).shouldBe       (Vector(0))

        "[Connector, Flue, Connector, Chimney] → [0, 1] (connector-first head)" in
            headRegionIndices(
                Seq(
                    ConnectorSlot(Seq.empty),
                    FlueSlot     (Seq.empty),
                    ConnectorSlot(Seq.empty),
                    ChimneySlot  (Seq.empty)
                )
            ).shouldBe       (Vector(0, 1))

        "[Flue, Connector, Flue, Connector, Chimney] → [0, 1, 2]" in
            headRegionIndices(
                Seq(
                    FlueSlot     (Seq.empty),
                    ConnectorSlot(Seq.empty),
                    FlueSlot     (Seq.empty),
                    ConnectorSlot(Seq.empty),
                    ChimneySlot  (Seq.empty)
                )
            ).shouldBe       (Vector(0, 1, 2))

        "[ThermalFlue, Connector, Chimney] → [0] (thermal flue counts as flue-like)" in
            headRegionIndices(
                Seq(
                    ThermalFlueSlot(Seq.empty),
                    ConnectorSlot  (Seq.empty),
                    ChimneySlot    (Seq.empty)
                )
            ).shouldBe       (Vector(0))
    }
