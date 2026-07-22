/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import cats.data.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import afpma.firecalc.engine.Slot0ContextFixture

class IncrementalBuilderTrailingSuite
    extends AnyFlatSpec
    with Matchers
    with IncrementalBuilderTestFixture
    with Slot0ContextFixture:

    case class TestSetInitialPos(x: Length, y: Length, z: Length) extends TestSetProp
    case class TestSetFinalPos(x: Length, y: Length, z: Length)   extends TestSetProp

    case class TestBend90(name: String) extends TestAddElement

    class TestBuilder(
        override val pt: FluePipeT
    ) extends TestBuilderBase(pt):

        override protected def isTrailingAllowed(setProp: SetProp): Boolean =
            setProp match
                case _: TestSetFinalPos | _: TestSetInitialPos => true
                case _                                         => false

        override protected def updateStateForSetProp(
            vState : ValidatedResult[PropsState],
            setProp: SetProp
        ): ValidatedResult[PropsState] =
            setProp match
                case _: TestSetInitialPos | _: TestSetFinalPos => vState

        override protected def mkFullElementForTest(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            id_addElementOp match
                case (idIncr, TestBend90(n)) => mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestElBend90"))
                case (_, other             ) => sys.error(s"Unexpected trailing test add-element: $other")

    val builder = TestBuilder(pt)

    "IncrementalBuilderAlg" should "allow trailing SetFinalPos" in {
        val descr  = builder.define(
            TestSetRect    (10.cm, 10.cm                ),
            TestStraight   ("s", 1.meters               ),
            TestSetFinalPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` true
    }

    it should "allow trailing SetInitialPos" in {
        val descr  = builder.define(
            TestSetRect      (10.cm, 10.cm                ),
            TestStraight     ("s", 1.meters               ),
            TestSetInitialPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` true
    }

    it should "reject trailing non-allowed SetProp (SetRect)" in {
        val descr  = builder.define(
            TestSetRect (10.cm, 10.cm ),
            TestStraight("s", 1.meters),
            TestSetRect (12.cm, 12.cm )
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` false
        result.toEither.left.toOption.get.head `shouldBe` a[AddElementMissingAfterSetProp[?]]
    }

    it should "reject trailing non-allowed SetProp when multiple trailing props include one non-allowed" in {
        val descr  = builder.define(
            TestSetRect      (10.cm, 10.cm                ),
            TestStraight     ("s", 1.meters               ),
            TestSetFinalPos  (0.meters, 0.meters, 1.meters),
            TestSetRect      (12.cm, 12.cm                ),
            TestSetInitialPos(0.meters, 0.meters, 0.meters)
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` false
        result.toEither.left.toOption.get.head `shouldBe` a[AddElementMissingAfterSetProp[?]]
    }

    it should "allow only trailing ops with no AddElement" in {
        val descr  = builder.define(
            TestSetFinalPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` true
        result.toOption.get._2.elems.size `shouldBe` 0
    }

    it should "allow allowed trailing props after the last add element" in {
        val descr  = builder.define(
            TestSetRect      (10.cm, 10.cm                ),
            TestStraight     ("s", 1.meters               ),
            TestSetFinalPos  (0.meters, 0.meters, 1.meters),
            TestSetInitialPos(0.meters, 0.meters, 0.meters)
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` true
    }

    it should "reject non-trailing after trailing and reference correct element" in {
        val descr  = builder.define(
            TestSetRect    (10.cm, 10.cm                ),
            TestStraight   ("s", 1.meters               ),
            TestSetFinalPos(0.meters, 0.meters, 1.meters),
            TestSetRect    (20.cm, 20.cm                )
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` false
        val errors = result.toEither.left.toOption.get
        errors.head match
            case e: AddElementMissingAfterSetProp[?] =>
                e.lastElRef.get should include("'#3'")
            case other =>
                fail(s"Expected AddElementMissingAfterSetProp but got ${other.getClass.getName}")
    }

    it should "still allow regular SetProps before an AddElement" in {
        val descr  = builder.define(
            TestSetRect (10.cm, 10.cm ),
            TestStraight("s", 1.meters),
            TestSetRect (12.cm, 12.cm ),
            TestBend90  ("b"          )
        )
        val result = descr.toFullDescr
        result.isValid `shouldBe` true
    }
