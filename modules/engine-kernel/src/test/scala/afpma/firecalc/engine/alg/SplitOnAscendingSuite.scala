/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.engine.models.*

import cats.data.NonEmptyList

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import afpma.firecalc.units.Vec3

class SplitOnAscendingSuite extends AnyFlatSpec with Matchers with IncrementalBuilderTestFixture:

    class TestBuilder(override val pt: FluePipeT) extends TestBuilderBase(pt):
        override protected def updateStateForSetProp(
            vState : ValidatedResult[PropsState],
            setProp: SetProp
        ): ValidatedResult[PropsState] = vState

        override protected def mkFullElementForTest(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            sys.error("not used")

    def builder = TestBuilder(pt)

    "splitOnAscending" should "return true for ascending split" in {
        builder.splitOnAscending(NbOfFlows(1), NbOfFlows(2), Some(Vec3.Up)) shouldBe true
    }

    it should "return false for horizontal split (Rear)" in {
        builder.splitOnAscending(NbOfFlows(1), NbOfFlows(2), Some(Vec3.Rear)) shouldBe false
    }

    it should "return false for horizontal split (Right)" in {
        builder.splitOnAscending(NbOfFlows(1), NbOfFlows(2), Some(Vec3.Right)) shouldBe false
    }

    it should "return false for descending split" in {
        builder.splitOnAscending(NbOfFlows(1), NbOfFlows(2), Some(Vec3.Down)) shouldBe false
    }

    it should "return false for merge on ascending pipe" in {
        builder.splitOnAscending(NbOfFlows(2), NbOfFlows(1), Some(Vec3.Up)) shouldBe false
    }

    it should "return false when no direction is set" in {
        builder.splitOnAscending(NbOfFlows(1), NbOfFlows(2), None) shouldBe false
    }

    it should "return false for same flow count" in {
        builder.splitOnAscending(NbOfFlows(2), NbOfFlows(2), Some(Vec3.Up)) shouldBe false
    }
