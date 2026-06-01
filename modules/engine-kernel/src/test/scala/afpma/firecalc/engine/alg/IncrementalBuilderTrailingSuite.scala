/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.reflect.TypeTest

/**
 * Tests the isTrailingAllowed hook in IncrementalBuilderAlg.
 *
 * Uses custom minimal types and a minimal concrete implementation
 * of IncrementalBuilderAlg to test the hook in isolation.
 */
class IncrementalBuilderTrailingSuite extends AnyFlatSpec with Matchers:

    // ---- Custom minimal types ----

    sealed trait TestIncrDescr
    sealed trait TestSetProp    extends TestIncrDescr
    sealed trait TestAddElement extends TestIncrDescr:
        def name: String

    case class TestSetRect(w: Length, h: Length)                  extends TestSetProp
    case class TestSetInitialPos(x: Length, y: Length, z: Length) extends TestSetProp
    case class TestSetFinalPos(x: Length, y: Length, z: Length)   extends TestSetProp

    case class TestStraight(name: String, length: Length) extends TestAddElement
    case class TestBend90(name: String)                   extends TestAddElement

    sealed trait TestPipeElDescr
    case class TestElStraight(length: Length) extends TestPipeElDescr
    case class TestElBend90()                 extends TestPipeElDescr

    given Show[TestPipeElDescr] = Show.show:
        case TestElStraight(l) => s"TestElStraight($l)"
        case TestElBend90()    => "TestElBend90"

    given HasLength[TestPipeElDescr] = new HasLength[TestPipeElDescr]:
        extension (a: TestPipeElDescr)
            def length: Length = a match
                case TestElStraight(l) => l
                case TestElBend90()    => 0.meters

    given HasVerticalElev[TestPipeElDescr] = new HasVerticalElev[TestPipeElDescr]:
        extension (a: TestPipeElDescr) def verticalElev: Length = 0.meters

    given HasInnerShapeAtPos[TestPipeElDescr] = new HasInnerShapeAtPos[TestPipeElDescr]:
        extension         (a         : TestPipeElDescr  )
            def innerShape(oPrevShape: Option[PipeShape]): Option[PositionOp[PipeShape]] = None

    case class TestState(geometry: Option[PipeShape])

    // ---- Minimal concrete builder ----

    class TestBuilder(
        override val pt: FluePipeT
    ) extends IncrementalBuilderAlg:

        override type IncrDescr            = TestIncrDescr
        override type SetProp              = TestSetProp
        override type AddElement           = TestAddElement
        override type PT                   = FluePipeT
        override type PipeElDescr          = TestPipeElDescr
        override protected type PropsState = TestState

        override given typeTestSetProp: TypeTest[TestIncrDescr, TestSetProp] =
            new TypeTest[TestIncrDescr, TestSetProp]:
                def unapply(u: TestIncrDescr): Option[u.type & TestSetProp] =
                    if u.isInstanceOf[TestSetProp] then Some(u.asInstanceOf[u.type & TestSetProp]) else None

        override given typeTestAddElement: TypeTest[TestIncrDescr, TestAddElement] =
            new TypeTest[TestIncrDescr, TestAddElement]:
                def unapply(u: TestIncrDescr): Option[u.type & TestAddElement] =
                    if u.isInstanceOf[TestAddElement] then Some(u.asInstanceOf[u.type & TestAddElement]) else None

        extension (ae: AddElement) override def name: String = ae.name

        override protected def isForbiddenAddElementAtStart(ae: AddElement): Boolean = false
        override protected def isForbiddenAddElementAtEnd  (ae: AddElement): Boolean = false

        override protected def isTrailingAllowed(setProp: SetProp): Boolean =
            setProp match
                case _: TestSetFinalPos | _: TestSetInitialPos => true
                case _                                         => false

        extension (piDescr: PipeIncrDescr) def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

        extension (propsState: PropsState) override def isValid: Boolean = true

        override def define(iDescrs: IncrDescr*): PipeIncrDescr =
            val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
            PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

        override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState =
            TestState(None)

        override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
            PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

        override protected def updateStateBeforeConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            convStep.allSetPropsUntilNextAddElement
                .foldLeft(propsState.validNel[IncrementalValidation_Error]) { case (vState, (_, setPropOp)) =>
                    setPropOp match
                        case TestSetRect(w, h)                         =>
                            vState.map(_.copy(geometry = Some(PipeShape.Rectangle(w, h))))
                        case _: TestSetInitialPos | _: TestSetFinalPos =>
                            vState
                }

        override protected def updateStateAfterConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            propsState.validNel[IncrementalValidation_Error]

        override protected def mkFullElementsDescr(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            val (idIncr, addOp) = id_addElementOp
            val elIdx       = PipeIdx(prevs.elems.size)
            given NbOfFlows = NbOfFlows(1)
            addOp match
                case TestStraight(n, l) =>
                    val el = TestElStraight(l)
                    NonEmptyList.one((idIncr, el.named(elIdx, pt, n))).validNel
                case TestBend90(n)      =>
                    val el = TestElBend90()
                    NonEmptyList.one((idIncr, el.named(elIdx, pt, n))).validNel

        object ElementFactory extends ElementFactoryModule

    // ---- Tests ----

    given pt: FluePipeT = FluePipeT
    val builder = TestBuilder(pt)

    "IncrementalBuilderAlg" should "allow trailing SetFinalPos" in {
        val descr  = builder.define(
            TestSetRect    (10.cm, 10.cm                ),
            TestStraight   ("s", 1.meters               ),
            TestSetFinalPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "allow trailing SetInitialPos" in {
        val descr  = builder.define(
            TestSetRect      (10.cm, 10.cm                ),
            TestStraight     ("s", 1.meters               ),
            TestSetInitialPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "reject trailing non-allowed SetProp (SetRect)" in {
        val descr  = builder.define(
            TestSetRect (10.cm, 10.cm ),
            TestStraight("s", 1.meters),
            TestSetRect (20.cm, 20.cm )
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` false
        result.toEither.left.toOption.get.head `shouldBe` a[AddElementMissingAfterSetProp[?]]
    }

    it should "allow only trailing ops with no AddElement" in {
        val descr  = builder.define(
            TestSetFinalPos(0.meters, 0.meters, 1.meters)
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` true
        result.toOption.get._2.elems.size `shouldBe` 0
    }

    it should "allow multiple trailing ops at end" in {
        val descr  = builder.define(
            TestSetRect      (10.cm, 10.cm                ),
            TestStraight     ("s", 1.meters               ),
            TestSetFinalPos  (0.meters, 0.meters, 1.meters),
            TestSetInitialPos(1.meters, 2.meters, 3.meters)
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` true
        result.toOption.get._2.elems.size `shouldBe` 1
    }

    it should "reject non-trailing after trailing and reference correct element" in {
        val descr  = builder.define(
            TestSetRect    (10.cm, 10.cm                ),
            TestStraight   ("s", 1.meters               ),
            TestSetFinalPos(0.meters, 0.meters, 1.meters),
            TestSetRect    (20.cm, 20.cm                )
        )
        val result = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors = result.toEither.left.toOption.get
        errors.head match
            case e: AddElementMissingAfterSetProp[_] =>
                e.lastElRef.get should include("'#3'")
            case other =>
                fail(s"Expected AddElementMissingAfterSetProp but got ${other.getClass.getName}")
    }
