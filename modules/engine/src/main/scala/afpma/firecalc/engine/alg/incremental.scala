/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import scala.annotation.tailrec
import scala.reflect.*

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.*

trait IncrementalBuilderAlg extends PipeDescrAlg:

    /**
      * data type for the incremental description of some pipe modification
      * type of modification needs type `SetProp` or `AddElement`
      */
    type IncrDescr

    opaque type IdIncr = Int
    object IdIncr:
        def apply(i: Int): IdIncr = i
        extension (ii: IdIncr) def unwrap: Int = ii

    opaque type IdEl = PipeIdx
    object IdEl:
        def apply(i: PipeIdx): IdEl = i
        extension (x: IdEl) def unwrap: PipeIdx = x

    /**
      * Keeps track of the original index of the IncrDescr in the input sequence (for traceability later on)
      */
    type Id_IncrDescr = (IdIncr, IncrDescr)

    final type PipeIncrDescr = PipeIncrDescrG[Id_IncrDescr]

    opaque type IdsMapping = Map[IdIncr, IdEl]
    object IdsMapping:
        val empty: IdsMapping = Map()
        extension (m: IdsMapping)
            def getAll: Seq[(IdIncr, IdEl)]  = m.map(kv => (kv._1, kv._2)).toSeq
            def get(i: IdIncr): Option[IdEl] = m.get(i)
            def getUnsafe(i: Int): Option[IdEl]    = m.get(i)
    
    type SetProp <: IncrDescr
    type AddElement <: IncrDescr

    extension (addElement: AddElement) def name: String

    given typeTestSetProp       : TypeTest[IncrDescr, SetProp] = scala.compiletime.deferred
    given typeTestAddElement   : TypeTest[IncrDescr, AddElement] = scala.compiletime.deferred
    
    /**
      * restrict pipe types that can be defined using this builder
      */
    type PT <: PipeType
    def pt: PT

    type ValidatedResult[A] = ValidatedNel[String, A]
    type CtxValidatedResult[A] = PropsState ?=> ValidatedNel[String, A]

    extension (piDescr: PipeIncrDescr)
        def listIncrDescr(): Vector[Id_IncrDescr]
        def toFullDescr(): ValidatedNel[String, (IdsMapping, PipeFullDescr)] =
            val iPropsState     = mkInitPropsState(piDescr)
            val iPipeFullDescr  = mkInitPipeFullDescr(piDescr)
            val iIdsMapping     = IdsMapping.empty
            val iListIncrDescr  = piDescr.listIncrDescr()
            buildIncrDescr(
                iPipeFullDescr, 
                iIdsMapping,
                iPropsState,
                opsDone = Vector.empty,
                opsLeft = iListIncrDescr
            )

    def define(iDescrs: IncrDescr*): PipeIncrDescr

    // PRIVATE or other ALGEBRA

    // should be implemented as a case class where members have Option[?] type
    protected type PropsState

    protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState

    extension (propsState: PropsState)
    {
        /** 
         *  val t = Tuple.fromProductTyped(this)
         *  t.toList.forall(_.isDefined)
        */
        def isValid: Boolean

        def getValidated[A](
            get: PropsState => Option[A],
            msgIfNone: String
        ): ValidatedResult[A] =
            Validated
                .fromOption(get(propsState), ifNone = msgIfNone)
                .toValidatedNel

        def checkNotSet[A](
            get: PropsState => Option[A],
            msgIfSet: String
        ): ValidatedResult[Unit] =
            Validated.fromEither:
                get(propsState) match
                    case None => Right(())
                    case Some(_) => Left(NonEmptyList.one(msgIfSet))

            
    }
    
    protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr

    protected case class ConversionStep(
        allRemainingOps: Vector[Id_IncrDescr]
    ) {
        def allSetPropsUntilNextAddElement = 
            allRemainingOps
                .takeWhile:
                    case (_, _: SetProp)    => true
                    case (_, _)             => false
                .map(_.asInstanceOf[(Int, SetProp)])

        def findNextAddElement: Option[(Int, AddElement)] = 
            allRemainingOps
                .find:
                    case (_, _: AddElement) => true
                    case (_, _)              => false
                .map(_.asInstanceOf[(Int, AddElement)])

        def nextOp: Option[Id_IncrDescr] = allRemainingOps.headOption

        def nextOpIfAddElement: Option[(Int, AddElement)] = nextOp.flatMap: 
            case (id, o: AddElement) => (id, o).some
            case (_, _) => None

        def isLastStep: Boolean = 
            findNextAddElement.isEmpty && 
            allSetPropsUntilNextAddElement.isEmpty

        def currentStepOps: Vector[Id_IncrDescr] =
            findNextAddElement match
                case Some(nextAddElement) => allSetPropsUntilNextAddElement appended nextAddElement
                case None => allSetPropsUntilNextAddElement

        def nextStepOps: Vector[Id_IncrDescr] = 
            allRemainingOps.drop(currentStepOps.size)
    }

    protected def mkConversionStep(
        allRemainingOps: Vector[Id_IncrDescr]
    ): ConversionStep =
        ConversionStep(allRemainingOps)

    protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState]

    protected def updateStateAfterConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState]

    protected def updateIdsMappingAndPipeFullDescr(
        inPipe: PipeFullDescr,
        inIdsMapping: IdsMapping,
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[(IdsMapping, PipeFullDescr)] =
        val nextGeomOp = convStep.findNextAddElement 
        nextGeomOp match
            case None =>
                "invalid operation sequence, expecting some 'add geometry' operation but none found".invalidNel
            case Some(gop) =>
                mkFullElementsDescr(inPipe, convStep)(gop)(using propsState).map: nel => 
                    nel.foldLeft((inIdsMapping, inPipe)):
                        case ((outIdsMapping, outPipe), (idIncr, nextFullElem)) =>
                            (
                                outIdsMapping.updated(idIncr, nextFullElem.idx),
                                outPipe.appendElem(nextFullElem)
                            )

    protected def mkFullElementsDescr(
        prevs: PipeFullDescr,
        convStep: ConversionStep,
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]]

    // protected def appendFullElementToPipe(pipe: PipeFullDescr)(nel: NamedPipeElDescr): PipeFullDescr

    @tailrec
    final protected def buildIncrDescr(
        pFullDescr: PipeFullDescr,
        idsMapping: IdsMapping,
        propsState: PropsState,
        opsDone: Vector[Id_IncrDescr],
        opsLeft: Vector[Id_IncrDescr]
    ): ValidatedResult[(IdsMapping, PipeFullDescr)] = 
        val convStep = mkConversionStep(opsLeft)
        if (convStep.isLastStep)
            // we hit the end of the conversion steps, nothing left to do
            // return the last computed full descr
            (idsMapping, pFullDescr).validNel
        else
            // update state BEFORE updating full descr
            updateStateBeforeConversionStep(propsState, convStep) match
                case Valid(preparedState) =>
                    // update full descr
                    updateIdsMappingAndPipeFullDescr(pFullDescr, idsMapping, preparedState, convStep) match
                        case Valid(nextIdMappings, nextPipeFullDescr) =>
                            // update state AFTER updating full descr
                            updateStateAfterConversionStep(
                                preparedState,
                                convStep
                            ) match
                                case Valid(nextPropsState) =>
                                    val nextOpsDone = opsDone ++ convStep.currentStepOps.toVector
                                    val nextOpsLeft = convStep.nextStepOps
                                    // recursive call to handle remaining incr descr
                                    buildIncrDescr(
                                        nextPipeFullDescr,
                                        nextIdMappings,
                                        nextPropsState,
                                        nextOpsDone,
                                        nextOpsLeft,
                                    )
                                case i @ Invalid(_) => i
                        case i @ Invalid(_) => i
                case i @ Invalid(_) => i

    val ElementFactory: ElementFactoryModule
    export ElementFactory.{*, given}

    protected trait ElementFactoryModule:
        case class Ctx(propsState: PropsState)
        def ctx(using ev: Ctx): Ctx = ev
        def ctxState(using ev: Ctx) = ev.propsState
        given mkCtx: (ps: PropsState) => Ctx = Ctx(ps)

        type MakeFor[
            In <: IncrDescr, 
            El <: PipeElDescr
        ] = Ctx ?=> In => ValidatedNel[String, El]
