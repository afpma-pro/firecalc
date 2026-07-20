/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.IsLengthBearingPipeElement
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import scala.reflect.TypeTest

trait IncrementalBuilderTestFixture:

    trait TestIncrDescr
    trait TestSetProp    extends TestIncrDescr
    trait TestAddElement extends TestIncrDescr:
        def name: String

    case class TestSetRect(w: Length, h: Length) extends TestSetProp

    case class TestStraight(name: String, length: Length) extends TestAddElement with IsLengthBearingPipeElement

    trait TestPipeElDescr
    case class TestElStraight(length: Length)  extends TestPipeElDescr
    case class TestElZeroLength(label: String) extends TestPipeElDescr

    given Show[TestPipeElDescr] = Show.show:
        case TestElStraight(l)     => s"TestElStraight($l)"
        case TestElZeroLength(lbl) => lbl

    given HasLength[TestPipeElDescr] = new HasLength[TestPipeElDescr]:
        extension (a: TestPipeElDescr)
            def length: Length = a match
                case TestElStraight(l)   => l
                case TestElZeroLength(_) => 0.meters

    given HasVerticalElev[TestPipeElDescr] = new HasVerticalElev[TestPipeElDescr]:
        extension (a: TestPipeElDescr) def verticalElev: Length = 0.meters

    given HasInnerShapeAtPos[TestPipeElDescr] = new HasInnerShapeAtPos[TestPipeElDescr]:
        extension         (a         : TestPipeElDescr  )
            def innerShape(oPrevShape: Option[PipeShape]): Option[PositionOp[PipeShape]] = None

    case class TestState(
        geometry    : Option[PipeShape] = None,
        roughness   : Option[Length]    = None,
        materialized: Boolean           = true
    )

    abstract class TestBuilderBase(
        override val pt: FluePipeT
    ) extends IncrementalBuilderAlg:

        override type IncrDescr            = TestIncrDescr
        override type SetProp              = TestSetProp
        override type AddElement           = TestAddElement
        override type PT                   = FluePipeT
        override type PipeElDescr          = TestPipeElDescr
        override protected type PropsState = TestState

        override type PreElementOp      = TestSetProp
        override type ChannelTopologyOp = TestSetProp
        override type PipeTrackingOp    = TestSetProp

        override given typeTestSetProp: TypeTest[TestIncrDescr, TestSetProp] =
            new TypeTest[TestIncrDescr, TestSetProp]:
                def unapply(u: TestIncrDescr): Option[u.type & TestSetProp] =
                    if u.isInstanceOf[TestSetProp] then Some(u.asInstanceOf[u.type & TestSetProp]) else None

        override given typeTestAddElement: TypeTest[TestIncrDescr, TestAddElement] =
            new TypeTest[TestIncrDescr, TestAddElement]:
                def unapply(u: TestIncrDescr): Option[u.type & TestAddElement] =
                    if u.isInstanceOf[TestAddElement] then Some(u.asInstanceOf[u.type & TestAddElement]) else None

        override given typeTestPreElementOp: TypeTest[TestIncrDescr, PreElementOp] =
            typeTestSetProp

        extension (ae: AddElement) override def name: String = ae.name

        override protected def isForbiddenAddElementAtStart(ae     : AddElement): Boolean = false
        override protected def isForbiddenAddElementAtEnd  (ae     : AddElement): Boolean = false
        override protected def isTrailingAllowed           (setProp: SetProp   ): Boolean = false

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
        )(using sc: SlotContext): ValidatedResult[PropsState] =
            convStep.allPreElementOpsUntilNextAddElement
                .foldLeft(propsState.validNel[IncrementalValidation_Error]) { case (vState, (_, setPropOp)) =>
                    setPropOp match
                        case TestSetRect(w, h) =>
                            vState.map(_.copy(geometry = Some(PipeShape.Rectangle(w, h))))
                        case other             => updateStateForSetProp(vState, other)
                }

        protected def updateStateForSetProp(
            vState : ValidatedResult[PropsState],
            setProp: SetProp
        ): ValidatedResult[PropsState]

        override protected def updateStateAfterConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            propsState.validNel[IncrementalValidation_Error]

        override protected def mkFullElementsDescr(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(using
            sc: SlotContext
        )(
            id_addElementOp: (IdIncr, AddElement)
        ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            id_addElementOp match
                case (idIncr, TestStraight(n, l)) =>
                    mkNamedElement(prevs, idIncr, n, TestElStraight(l))
                case other =>
                    mkFullElementForTest(prevs, convStep)(other)

        protected def mkFullElementForTest(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )                                 (id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]]

        protected def mkNamedElement(
            prevs : PipeFullDescr,
            idIncr: IdIncr,
            name  : String,
            el    : PipeElDescr
        ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            val elIdx       = PipeIdx(prevs.elems.size)
            given NbOfFlows = NbOfFlows(1)
            NonEmptyList.one((idIncr, el.named(elIdx, pt, name))).validNel

        object ElementFactory extends ElementFactoryModule

    given pt: FluePipeT = FluePipeT
