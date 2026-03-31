/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.AddElementMissingAfterSetProp
import afpma.firecalc.engine.standard.ForbiddenAddElementAtEnd
import afpma.firecalc.engine.standard.ForbiddenAddElementAtStart
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import scala.annotation.tailrec
import scala.reflect.*

trait IncrementalBuilderAlg extends PipeDescrAlg:

    /**
     * data type for the incremental description of some pipe modification
     * type of modification needs type `SetProp` or `AddElement`
     */
    type IncrDescr

    opaque type IdIncr = Int
    object IdIncr:
        def apply (i : Int   )           : IdIncr = i
        extension (ii: IdIncr) def unwrap: Int    = ii

    opaque type IdEl = PipeIdx
    object IdEl:
        def apply (i: PipeIdx)           : IdEl    = i
        extension (x: IdEl   ) def unwrap: PipeIdx = x

    /** Keeps track of the original index of the IncrDescr in the input sequence (for traceability later on) */
    type Id_IncrDescr = (IdIncr, IncrDescr)

    final type PipeIncrDescr = PipeIncrDescrG[Id_IncrDescr]

    opaque type IdsMapping = Map[IdIncr, IdEl]
    object IdsMapping:
        val empty: IdsMapping = Map()
        extension (m: IdsMapping)
            def getAll: Seq[(IdIncr, IdEl)] = m.map(kv => (kv._1, kv._2)).toSeq
            def get      (i: IdIncr): Option[IdEl] = m.get(i)
            def getUnsafe(i: Int   ): Option[IdEl] = m.get(i)

            /**
             * Reverse map: PipeIdx (section_id) → descriptor index.
             * Inside the opaque scope: IdIncr = Int, IdEl = PipeIdx.
             */
            def reverseToIntMap: Map[Int, Int] =
                m.map { case (descrIdx, pipeIdx) => (pipeIdx.unwrap, descrIdx) }

    type SetProp <: IncrDescr
    type AddElement <: IncrDescr

    extension (addElement: AddElement) def name: String

    given typeTestSetProp   : TypeTest[IncrDescr, SetProp]    = scala.compiletime.deferred
    given typeTestAddElement: TypeTest[IncrDescr, AddElement] = scala.compiletime.deferred

    /** restrict pipe types that can be defined using this builder */
    type PT <: PipeType
    def pt: PT

    /**
     * Whether the given AddElement is forbidden at the
     * start of the incremental description sequence.
     */
    protected def isForbiddenAddElementAtStart(
        addElement: AddElement
    ): Boolean

    /**
     * Whether the given AddElement is forbidden at the
     * end of the incremental description sequence.
     */
    protected def isForbiddenAddElementAtEnd(
        addElement: AddElement
    ): Boolean

    type ValidatedResult[A]    = ValidatedNel[IncrementalValidation_Error, A]
    type CtxValidatedResult[A] = PropsState ?=> ValidatedNel[IncrementalValidation_Error, A]

    private def validateBoundaryElements(
        incrDescrs: Vector[Id_IncrDescr]
    ): ValidatedResult[Unit] =
        val firstAddElement: Option[(IdIncr, AddElement)] =
            incrDescrs.collectFirst:
                case (id, ae: AddElement) => (id, ae)
        val lastAddElement : Option[(IdIncr, AddElement)] =
            incrDescrs.reverse.collectFirst:
                case (id, ae: AddElement) => (id, ae)

        val startCheck: ValidatedResult[Unit] =
            firstAddElement match
                case Some((_, ae)) if isForbiddenAddElementAtStart(ae) =>
                    ForbiddenAddElementAtStart(pt, ae.name).invalidNel
                case _                                                 => ().validNel

        val endCheck: ValidatedResult[Unit] =
            lastAddElement match
                case Some((_, ae)) if isForbiddenAddElementAtEnd(ae) =>
                    ForbiddenAddElementAtEnd(pt, ae.name).invalidNel
                case _                                               => ().validNel

        (startCheck, endCheck).mapN((_, _) => ())

    extension (piDescr: PipeIncrDescr)
        def listIncrDescr(): Vector[Id_IncrDescr]
        def toFullDescr(): ValidatedNel[IncrementalValidation_Error, (IdsMapping, PipeFullDescr)] =
            buildFrom(piDescr, externalInitialFrame = None).map((ids, fd, _) => (ids, fd))

        /** Like toFullDescr(), but also returns the final PipeFrame (if direction tracking was active). */
        def toFullDescrWithFinalFrame()
            : ValidatedNel[IncrementalValidation_Error, (IdsMapping, PipeFullDescr, Option[PipeFrame])] =
            buildFrom(piDescr, externalInitialFrame = None)

        /**
         * Like toFullDescr(), but seeds the initial direction from an external frame
         * (e.g. the final frame of the preceding pipe in the sequence).
         * Only takes effect when the pipe itself does not already define an initial direction.
         */
        def toFullDescrWithExternalInitialFrame(
            externalInitialFrame: Option[PipeFrame]
        ): ValidatedNel[IncrementalValidation_Error, (IdsMapping, PipeFullDescr, Option[PipeFrame])] =
            buildFrom(piDescr, externalInitialFrame)

    private def buildFrom(
        piDescr             : PipeIncrDescr,
        externalInitialFrame: Option[PipeFrame]
    ): ValidatedNel[IncrementalValidation_Error, (IdsMapping, PipeFullDescr, Option[PipeFrame])] =
        val iPropsState0   = mkInitPropsState(piDescr)
        val iPropsState    = externalInitialFrame.fold(iPropsState0)(applyExternalFrame(iPropsState0, _))
        val iPipeFullDescr = mkInitPipeFullDescr(piDescr)
        val iIdsMapping    = IdsMapping.empty
        val iListIncrDescr = piDescr.listIncrDescr()
        validateBoundaryElements(iListIncrDescr) *>
            buildIncrDescr(
                iPipeFullDescr,
                iIdsMapping,
                iPropsState,
                opsDone = Vector.empty,
                opsLeft = iListIncrDescr
            ).andThen: (ids, fd, finalState) =>
                postBuildValidation(iListIncrDescr, finalState) *>
                    (ids, fd, currentFrameFromPropsState(finalState)).validNel

    def define(iDescrs: IncrDescr*): PipeIncrDescr

    // PRIVATE or other ALGEBRA

    // should be implemented as a case class where members have Option[?] type
    protected type PropsState

    protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState

    /**
     * Extract the current PipeFrame from PropsState, if direction tracking is active.
     * Default: returns None (no direction tracking).
     * Concrete builders that support direction tracking override this.
     */
    protected def currentFrameFromPropsState(s: PropsState): Option[PipeFrame] = None

    /**
     * Hook for post-build validation. Called after all incremental descriptions have been
     * processed. Override in concrete builders to add pipe-specific validations.
     * Default: no validation (always valid).
     */
    protected def postBuildValidation(
        incrDescrs: Vector[Id_IncrDescr],
        finalState: PropsState
    ): ValidatedResult[Unit] = ().validNel

    /**
     * Apply an external initial frame to a freshly-created PropsState, but ONLY if
     * that state does not already have a direction defined (i.e. SetInitialDirection was
     * not used in the pipe's own descriptor).
     * Default: no-op (returns the state unchanged).
     * Concrete builders that support direction tracking override this.
     */
    protected def applyExternalFrame(s: PropsState, frame: PipeFrame): PropsState = s

    extension (propsState: PropsState) {

        /**
         *  val t = Tuple.fromProductTyped(this)
         *  t.toList.forall(_.isDefined)
         */
        def isValid: Boolean

        def getValidated[A](
            get  : PropsState => Option[A],
            error: IncrementalValidation_Error
        ): ValidatedResult[A] =
            Validated
                .fromOption(get(propsState), ifNone = error)
                .toValidatedNel

        def checkNotSet[A](
            get  : PropsState => Option[A],
            error: IncrementalValidation_Error
        ): ValidatedResult[Unit] =
            Validated.fromEither:
                get(propsState) match
                    case None    => Right(())
                    case Some(_) => Left(NonEmptyList.one(error))

    }

    protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr

    protected case class ConversionStep(
        allRemainingOps: Vector[Id_IncrDescr]
    ) {
        def allSetPropsUntilNextAddElement: Vector[(Int, SetProp)] =
            allRemainingOps
                .takeWhile:
                    case (_, _: SetProp) => true
                    case (_, _         ) => false
                .map(_.asInstanceOf[(Int, SetProp)])

        def findNextAddElement: Option[(Int, AddElement)] =
            allRemainingOps
                .find:
                    case (_, _: AddElement) => true
                    case (_, _            ) => false
                .map(_.asInstanceOf[(Int, AddElement)])

        def nextOp: Option[Id_IncrDescr] = allRemainingOps.headOption

        def nextOpIfAddElement: Option[(Int, AddElement)] = nextOp.flatMap:
            case (id, o: AddElement) => (id, o).some
            case (_, _             ) => None

        def isLastStep: Boolean =
            findNextAddElement.isEmpty &&
                allSetPropsUntilNextAddElement.isEmpty

        def currentStepOps: Vector[Id_IncrDescr] =
            findNextAddElement match
                case Some(nextAddElement) => allSetPropsUntilNextAddElement appended nextAddElement
                case None                 => allSetPropsUntilNextAddElement

        def nextStepOps: Vector[Id_IncrDescr] =
            allRemainingOps.drop(currentStepOps.size)
    }

    protected def mkConversionStep(
        allRemainingOps: Vector[Id_IncrDescr]
    ): ConversionStep =
        ConversionStep(allRemainingOps)

    protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState]

    protected def updateStateAfterConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState]

    protected def updateIdsMappingAndPipeFullDescr(
        inPipe      : PipeFullDescr,
        inIdsMapping: IdsMapping,
        propsState  : PropsState,
        convStep    : ConversionStep
    ): ValidatedResult[(IdsMapping, PipeFullDescr)] =
        val nextGeomOp = convStep.findNextAddElement
        nextGeomOp match
            case None      =>
                val lastIncrDescr = convStep.allRemainingOps.lastOption
                val lastElRef     = lastIncrDescr.map(x => s"'#${x._1}'")
                AddElementMissingAfterSetProp(pt, lastElRef).invalidNel
            case Some(gop) =>
                mkFullElementsDescr(inPipe, convStep)(gop)(using propsState).map: nel =>
                    nel.foldLeft((inIdsMapping, inPipe)):
                        case ((outIdsMapping, outPipe), (idIncr, nextFullElem)) =>
                            (
                                outIdsMapping.updated(idIncr, nextFullElem.idx),
                                outPipe.appendElem   (nextFullElem            )
                            )

    protected def mkFullElementsDescr(
        prevs          : PipeFullDescr,
        convStep       : ConversionStep
    )                                (
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]]

    // protected def appendFullElementToPipe(pipe: PipeFullDescr)(nel: NamedPipeElDescr): PipeFullDescr

    @tailrec
    protected final def buildIncrDescr(
        pFullDescr: PipeFullDescr,
        idsMapping: IdsMapping,
        propsState: PropsState,
        opsDone   : Vector[Id_IncrDescr],
        opsLeft   : Vector[Id_IncrDescr]
    ): ValidatedResult[(IdsMapping, PipeFullDescr, PropsState)] =
        val convStep = mkConversionStep(opsLeft)
        if (convStep.isLastStep)
            // we hit the end of the conversion steps, nothing left to do
            // return the last computed full descr along with final state
            (idsMapping, pFullDescr, propsState).validNel
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
                                        nextOpsLeft
                                    )
                                case Invalid(e)            => Invalid(e)
                        case Invalid(e)                               => Invalid(e)
                case Invalid(e)           => Invalid(e)

    val ElementFactory: ElementFactoryModule
    export ElementFactory.{*, given}

    protected trait ElementFactoryModule:
        case class Ctx(propsState: PropsState)
        def ctx     (using ev: Ctx): Ctx = ev
        def ctxState(using ev: Ctx) = ev.propsState
        given mkCtx: (ps: PropsState) => Ctx = Ctx(ps)

        type MakeFor[
            In <: IncrDescr,
            El <: PipeElDescr
        ] = Ctx ?=> In => ValidatedNel[IncrementalValidation_Error, El]
