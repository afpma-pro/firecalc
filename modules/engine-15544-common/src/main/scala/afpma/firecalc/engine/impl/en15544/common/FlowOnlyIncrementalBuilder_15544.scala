/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.SetsInnerShape
import afpma.firecalc.domain.FireboxCoordinateSystem.FireboxOrigin
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.alg.en15544.IncrementalBuilderAlg_15544
import afpma.firecalc.engine.FlowAreaConservation
import afpma.firecalc.engine.impl.common.FramedBuilderSupport
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.impl.common.instances.ChannelsDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.DirectionChangeDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.ElementFactory_15544_Instances.*
import afpma.firecalc.engine.impl.common.instances.ElementFactory_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.FlowResistanceDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_15544_Instance.*
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_15544_Instance.given
import afpma.firecalc.engine.impl.common.instances.SectionDSL_15544_Instances.given
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.geometry.{PipeFrame, PositionTracker}
import afpma.firecalc.units.Vec3
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.validation.AirIntakeValidation
import afpma.firecalc.engine.standard.ShapeNotMaterialized.Operation
import afpma.firecalc.engine.alg.{SplitMerge90Validator, SplitGeometryValidator}
import afpma.firecalc.engine.typeclasses.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.targetName
import scala.reflect.*

import com.softwaremill.quicklens.*

trait FlowOnlyIncrementalBuilder_15544
    extends IncrementalBuilderAlg
    with IncrementalBuilderAlg_15544[FlowOnlyPropsState_15544]
    with FramedBuilderSupport:

    import AddFlowOnlyPipeElement_15544.*
    import SetFlowOnlyPipeProp_15544.*

    override protected type SplitDTO  = SplitSingleFlowIntoTwoFlowsWith90DegTurn
    override protected type MergeDTO  = MergeTwoFlowsIntoSingleWith90DegTurn
    override protected type StateOpsT = PropsStateOps[PropsState]

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] =
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.hasInnerShapeAtPos
    override given hasLength         : HasLength[PipeElDescr]          =
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.hasLength
    override given hasVerticalElev   : HasVerticalElev[PipeElDescr]    =
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.hasVerticalElev
    given showPipeElDescr            : Show[PipeElDescr]               =
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.showPipeElDescr

    export afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.StraightSection
    export afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange

    override type PipeElDescr = afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.PipeElDescr

    override type IncrDescr  = FlowOnlyPipeDescr_15544
    override type SetProp    = SetFlowOnlyPipeProp_15544
    override type AddElement = AddFlowOnlyPipeElement_15544

    override type PreElementOp      = FlowOnlyPreElementOp_15544
    override type ChannelTopologyOp = FlowOnlyChannelTopologyOp_15544

    /**
     * Wrapper-level initial direction (V7).
     * When defined, seeds the initial/current frame in mkInitPropsState.
     * Replaces descriptor-level SetInitialDirection after V6→V7 migration.
     */
    protected var wrapperInitialDirection: Option[PipeInitialDirection] = None

    /**
     * Set wrapper-level initial direction.
     * @param dir the initial direction
     * @return this builder (for chaining)
     */
    override def withInitialDirection(dir: PipeInitialDirection): this.type =
        wrapperInitialDirection = Some(dir)
        this

    extension (addElement: AddElement) override def name: String = addElement.name

    override protected def isForbiddenAddElementAtStart(
        addElement: AddElement
    ): Boolean =
        addElement match
            case _: AddDirectionChange                       => true
            case _: SplitSingleFlowIntoTwoFlowsWith90DegTurn => false
            case _: MergeTwoFlowsIntoSingleWith90DegTurn     => true
            case _ => false

    override protected def isForbiddenAddElementAtEnd(
        addElement: AddElement
    ): Boolean =
        addElement match
            case _: AddDirectionChange                       => true
            case _: SplitSingleFlowIntoTwoFlowsWith90DegTurn => true
            case _: MergeTwoFlowsIntoSingleWith90DegTurn     => true
            case _ => false

    override protected def isTrailingAllowed(preOp: PreElementOp): Boolean =
        false

    override type PT <: PipeType_EN15544

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    // ========== PropsState via Typeclass ==========

    override protected type PropsState = FlowOnlyPropsState_15544
    protected val stateOps = summon[PropsStateOps[PropsState]]

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean   = stateOps.isValid(propsState)
        def nf              : NbOfFlows = stateOps.getNFlows(propsState)

    extension (piDescr: PipeIncrDescr)
        def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

        /** Inner geometry in effect after folding the first n descriptors — used by UI prefill. */
        def innerShapeAtPrefix(n: Int): Option[PipeShape] =
            val prefixResult = piDescr.propsStateAtPrefix(n)(using SlotContext.unslotted)
            prefixResult.toOption.flatMap(stateOps.getInnerShape)

        /** Number of flows in effect after folding the first n descriptors. */
        def nFlowsAtPrefix(n: Int): Option[NbOfFlows] =
            val prefixResult = piDescr.propsStateAtPrefix(n)(using SlotContext.unslotted)
            prefixResult.toOption.map(stateOps.getNFlows)

    override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState =
        val initDir = wrapperInitialDirection
        initDir match
            case Some(dir) =>
                val dirVec = Vec3.fromAzimuthElevation(
                    dir.azimuth.map(AzimuthDirection.toDegrees).getOrElse(0.0            ),
                    InclinationDirection.toDegrees                       (dir.inclination)
                )
                val frame  = PipeFrame.initial(dirVec)
                FlowOnlyPropsState_15544(
                    initialFrame = Some(frame),
                    currentFrame = Some(frame)
                )
            case None      =>
                FlowOnlyPropsState_15544()

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    override protected def currentFrameFromPropsState(s: PropsState): Option[PipeFrame] =
        s.currentFrame

    override protected def currentNFlowsFromPropsState(s: PropsState): NbOfFlows =
        stateOps.getNFlows(s)

    override protected def applyExternalFrame(s: PropsState, frame: PipeFrame): PropsState =
        // External frame from seed always takes precedence over builder's wrapperInitialDirection.
        // This ensures test isolation and correct frame chaining in PipeChainGeneric.
        s.copy(initialFrame = Some(frame), currentFrame = Some(frame))

    override protected def applyExternalNFlows(s: PropsState, nFlows: NbOfFlows): PropsState =
        s.copy(nFlows = nFlows)

    override protected def postBuildValidation(
        incrDescrs: Vector[Id_IncrDescr],
        finalState: PropsState,
        seed      : PipeBuildSeed
    )(using sc: SlotContext): ValidatedResult[Unit] =
        val airIntakeValidation = AirIntakeValidation.validateAirIntakeConstraints(pt, incrDescrs)

        val hasGeometry        = incrDescrs.exists:
            case (_, _: AddElement) => true
            case _ => false
        val geometryValidation =
            if hasGeometry && finalState.initialFrame.isEmpty then GeometryWithoutInitialDirection(pt).invalidNel
            else ().validNel

        val hasFinalDir        = incrDescrs.exists:
            case (_, dc: AddDirectionChange                     ) => dc.absDir.isDefined
            case (_, _: SplitSingleFlowIntoTwoFlowsWith90DegTurn) => true
            case (_, _: MergeTwoFlowsIntoSingleWith90DegTurn    ) => true
            case _ => false
        val finalDirValidation =
            if hasFinalDir && finalState.initialFrame.isEmpty then FinalDirWithoutInitialDirection(pt).invalidNel
            else ().validNel

        // If geometry validation fails, skip position-dependent validations
        if geometryValidation.isEmpty then (airIntakeValidation |+| geometryValidation |+| finalDirValidation).as(())
        else
            val posResult       = PositionTracker.computeFlowOnly15544(
                incrDescrs.map(_._2).toSeq,
                PipeInitialDirection.default,
                finalState.initialFrame,
                seed.positionContext
                    .flatMap(_.startPoint)
                    .getOrElse(FireboxOrigin),
                splitPosition = seed.positionContext.flatMap(_.slot0FireboxSplitPosition)
            )
            val mergeValidation = SplitMerge90Validator.validateAllMergePositions(posResult.splitMergePositions, pt)
            val splits          = posResult.splitMergePositions.filter(_.isSplit)
            val splitValidation = NonEmptyList.fromList(splits.toList) match
                case Some(ne) => SplitGeometryValidator.validateSplitPositions(ne, posResult, pt)
                case None     => ().validNel
            val positionErrors  =
                if posResult.errors.isEmpty then ().validNel
                else posResult.errors.map(e => SymmetryPlaneAzimuthMissing(pt, e).invalidNel).sequence.map(_ => ())
            (airIntakeValidation |+| geometryValidation |+| finalDirValidation |+| splitValidation |+| mergeValidation |+| positionErrors)
                .as(())

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[Length] =
            convStep.nextStepOps.headOption.flatMap:
                case (_, op) =>
                    op match
                        case _ @AddSectionSlopped(_, l)                            => l.some
                        case _ @AddSectionSloppedForceManualElevationGain(_, l, _) => l.some
                        case _ @AddSectionHorizontal(_, l)                         => l.some
                        case _ @AddSectionVertical(_, l)                           => l.some
                        case _: (AddSectionShapeChange | AddDirectionChange | AddFlowResistance | AddPressureDiff |
                                SplitSingleFlowIntoTwoFlowsWith90DegTurn | MergeTwoFlowsIntoSingleWith90DegTurn |
                                SetInnerShape | SetInnerShapePreventSectionGeometryChangeAuto | SetRoughness |
                                SetMaterial | FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows) =>
                            None

    override protected def mkFullElementsDescr(
        prevs   : PipeFullDescr,
        convStep: ConversionStep
    )(using
        sc: SlotContext
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
        given NbOfFlows = summon[PropsState].nf
        val (idIncr, addElementOp) = id_addElementOp
        val st    = summon[PropsState]
        val elIdx = PipeIdx(prevs.elems.size)
        extension (vnelEl: ValidatedNel[IncrementalValidation_Error, PipeElDescr])
            def asNonEmptyList = vnelEl.map(el => NonEmptyList.one((elIdx, None, el)))
        val prevInnerGeomO     = prevs.lastInnerGeom

        val vels: ValidatedNel[IncrementalValidation_Error, NonEmptyList[(PipeIdx, Option[String], PipeElDescr)]] =
            // Dev-only escape hatch: if the op batch preceding this add-element set the
            // inner shape via SetInnerShapePreventSectionGeometryChangeAuto, skip the
            // automatic SectionGeometryChange insertion for this element.
            val preventAuto = convStep.allPreElementOpsUntilNextAddElement.exists {
                case (_, _: SetInnerShapePreventSectionGeometryChangeAuto) => true
                case _ => false
            }
            addElementOp match
                case op @ (_: AddSectionSlopped | _: AddSectionSloppedForceManualElevationGain |
                    _: AddSectionHorizontal | _: AddSectionVertical) =>
                    given FlowOnlyStraightSectionCtx_15544 =
                        FlowOnlyStraightSectionCtx_15544(
                            stateOps.getInnerShape(st),
                            stateOps.getRoughness (st),
                            pt,
                            currentFrame = st.currentFrame
                        )
                    flowOnlyStraightSection15544.make(op).andThen { s =>
                        if preventAuto then
                            // Dev-only escape hatch: skip the automatic SectionGeometryChange
                            // element. Area-conservation validation already ran in
                            // updateStateBeforeConversionStep; only the transition element
                            // is suppressed here.
                            NonEmptyList.one((elIdx, None, s)).validNel[IncrementalValidation_Error]
                        else
                            prevInnerGeomO match
                                case None => NonEmptyList.one((elIdx, None, s)).validNel
                                case Some(prevInnerGeom) if (prevInnerGeom == s.geometry) =>
                                    NonEmptyList.one((elIdx, None, s)).validNel
                                case Some(prevInnerGeom) =>
                                    val sectGeomCh = SectionGeometryChange(from = prevInnerGeom, to = s.geometry)
                                    NonEmptyList(
                                        (elIdx, Some("section geometry change"), sectGeomCh),
                                        (elIdx.incr(1), None, s                            ) :: Nil
                                    ).validNel[IncrementalValidation_Error]
                    }

                // Split/Merge: no SectionGeometryChange auto-insertion needed.
                // The element's `effectiveShape` (from DTO `newInnerShape`) already
                // captures the post-transition shape. `hasInnerShapeAtPos` returns
                // `effectiveShape` for `SplitMerge90` via its `DirectionChange` parent,
                // so downstream shape resolution and any subsequent SectionGeometryChange
                // comparison work correctly. Intentional for both EN 15544 and EN 13384.
                case op: SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
                    val angleN2 = FramedBuilderSupport.computeAngleN2(
                        st.dirBeforePreviousDC,
                        st.currentFrame,
                        op.absDir,
                        90.0
                    )
                    val dc      = SplitMerge90(
                        nFlows         = 2.flows,
                        angleN2        = angleN2,
                        effectiveShape = op.newInnerShape
                    )
                    NonEmptyList.one((elIdx, None, dc)).validNel

                case op: MergeTwoFlowsIntoSingleWith90DegTurn =>
                    val angleN2 = FramedBuilderSupport.computeAngleN2(
                        st.dirBeforePreviousDC,
                        st.currentFrame,
                        op.absDir,
                        90.0
                    )
                    val dc      = SplitMerge90(
                        nFlows         = 1.flow,
                        angleN2        = angleN2,
                        effectiveShape = op.newInnerShape
                    )
                    NonEmptyList.one((elIdx, None, dc)).validNel

                case op: AddDirectionChange =>
                    stateOps
                        .validateMaterialized(
                            st,
                            Operation.AddDirectionChange,
                            pt,
                            idIncr.unwrap,
                            addElementOp.name
                        )
                        .andThen { _ =>
                            given DirectionChangeCtx_15544 =
                                DirectionChangeCtx_15544(
                                    stateOps.getInnerShape(st),
                                    pt,
                                    dirBeforePreviousDC = st.dirBeforePreviousDC,
                                    currentFrame        = st.currentFrame
                                )
                            directionChange15544.make(op).map(dc => NonEmptyList.one((elIdx, None, dc)))
                        }

                case op: AddSectionShapeChange =>
                    stateOps
                        .validateMaterialized(
                            st,
                            Operation.AddSectionShapeChange,
                            pt,
                            idIncr.unwrap,
                            addElementOp.name
                        )
                        .andThen { _ =>
                            given SectionGeometryChangeCtx_15544 =
                                SectionGeometryChangeCtx_15544(
                                    stateOps.getInnerShape(st),
                                    convStep.allPreElementOpsUntilNextAddElement.exists {
                                        case (_, _: SetsInnerShape) => true
                                        case _ => false
                                    },
                                    pt
                                )
                            sectionGeometryChange15544.make(op).asNonEmptyList
                        }

                case op: AddFlowResistance =>
                    given FlowResistanceCtx_15544 =
                        FlowResistanceCtx_15544(stateOps.getInnerShape(st), pt)
                    flowResistance15544.make(op).asNonEmptyList

                case op: AddPressureDiff =>
                    given FlowResistanceCtx_15544 =
                        FlowResistanceCtx_15544(stateOps.getInnerShape(st), pt)
                    pressureDiff15544.make(op).asNonEmptyList

        vels.map(_.map: (idx, newNameO, el) =>
            (idIncr, el.named(idx, pt, newNameO.getOrElse(addElementOp.name))))

    override protected def updateStateAfterConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState] =
        convStep.findNextAddElement.map(_._2) match
            case None                                               => propsState.validNel
            case Some(
                    _: AddSectionSlopped | _: AddSectionSloppedForceManualElevationGain | _: AddSectionHorizontal |
                    _: AddSectionVertical
                ) =>
                stateOps.materialize(propsState).validNel
            case Some(addDC: AddDirectionChange)                    =>
                addDC.absDir match
                    case Some(fd) =>
                        propsState.currentFrame match
                            case Some(frame) =>
                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                val deflDeg   = addDC.angle.toUnit[Degree].value
                                val newFrame  = frame.applyBendForFinalDir(deflDeg, targetVec)
                                propsState
                                    .copy(
                                        dirBeforePreviousDC = Some(frame.direction),
                                        currentFrame        = Some(newFrame)
                                    )
                                    .validNel
                            case None        => propsState.validNel
                    case None     => propsState.validNel
            case Some(op: SplitSingleFlowIntoTwoFlowsWith90DegTurn) =>
                handleSplitStateUpdate(propsState, op, op.absDir, op.newInnerShape, op.name, convStep)
            case Some(op: MergeTwoFlowsIntoSingleWith90DegTurn)     =>
                handleMergeStateUpdate(propsState, op, op.absDir, op.newInnerShape)
            case Some(_ @AddFlowResistance(_, _, _))                => propsState.validNel
            case Some(_ @AddPressureDiff(_, _))                     => propsState.validNel
            case Some(obj: AddSectionShapeChange)                   =>
                stateOps.setInnerShape(propsState, obj.to_shape).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    )(using sc: SlotContext): ValidatedResult[PropsState] =
        // Use the AddElement's idIncr for elementIndex in errors — it is the physical
        // element that the pre-element ops target, so the error points to the element
        // the user sees (and can fix) rather than the preceding SetInnerShape row.
        val (nextElemIdIncr, nextElemName) =
            convStep.findNextAddElement.map(idOp => (idOp._1, idOp._2.name)).getOrElse((-1, "?"))
        convStep.allPreElementOpsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (idIncr, op)) =>
                def applyInnerShapeSet(
                    vState: ValidatedNel[IncrementalValidation_Error, PropsState],
                    g     : PipeShape
                ): ValidatedNel[IncrementalValidation_Error, PropsState] =
                    vState.andThen { st =>
                        stateOps
                            .validateMaterialized(
                                st,
                                Operation.SetInnerShape,
                                pt,
                                nextElemIdIncr,
                                nextElemName
                            )
                            .andThen { _ =>
                                FlowAreaConservation
                                    .validateSetInnerShape(st, g, pt, nextElemIdIncr, nextElemName)(using stateOps)
                                    .toValidatedNel
                                    .map(s => stateOps.setInnerShape(s, g))
                            }
                    }
                op match
                    case SetInnerShape(g)                                     =>
                        applyInnerShapeSet(vState, g)
                    case SetInnerShapePreventSectionGeometryChangeAuto(g)     =>
                        // Same state update as plain SetInnerShape (area-conservation
                        // validation still runs); only the later auto-insertion is
                        // suppressed (handled at the mkFullElementsDescr call site).
                        applyInnerShapeSet(vState, g)
                    case SetRoughness(r)                                      =>
                        vState.map(_.modify(_.roughness).setTo(r.some))
                    case SetMaterial(lm)                                      =>
                        vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                    case FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(nf) =>
                        vState.andThen { st =>
                            val updatedSt = FlowAreaConservation.computeSetNFlows(st, nf, pt)(using stateOps)
                            stateOps
                                .validateMaterialized(
                                    st,
                                    Operation.SetNumberOfFlows,
                                    pt,
                                    nextElemIdIncr,
                                    nextElemName
                                )
                                .map(_ => updatedSt)
                        }
            }

    override protected def mkSplitMerge90Descr(
        nFlows : NbOfFlows,
        angleN2: Option[QtyD[Degree]],
        shape  : PipeShape
    ): PipeElDescr =
        SplitMerge90(nFlows = nFlows, angleN2 = angleN2, effectiveShape = shape)

    // Minimal ElementFactory object required by trait - delegates to typeclass instances
    object ElementFactory extends ElementFactoryModule

    // builder methods for Atomic modifiers

    /**
     * Sets the inner pipe shape. When `preventAutoSectionGeometryChange` is `true`,
     * emits the dev-only `SetInnerShapePreventSectionGeometryChangeAuto` DTO, which
     * suppresses the automatic SectionGeometryChange element insertion on the next
     * length-bearing element. This is a DSL-only escape hatch: any project carrying
     * the resulting DTO is rejected by the payments backend (`IsBackendForbidden`).
     * When `false`, collapses to the plain `SetInnerShape` so normal projects never
     * carry the dev-only variant on the wire.
     */
    def innerShape(shape: PipeShape, preventAutoSectionGeometryChange: Boolean) =
        if preventAutoSectionGeometryChange then SetInnerShapePreventSectionGeometryChangeAuto(shape)
        else SetInnerShape                                                                    (shape)
    def innerShape(shape: PipeShape)                                            =
        SetInnerShape(shape)
    def roughness(r: Roughness)                                                 =
        SetRoughness(r)

    def material(material: Material_15544) =
        SetMaterial(material)

    // Delegate to ChannelsDSL typeclass
    def channelsSplit(n: Int) =
        summon[ChannelsDSL[FlowOnlyPipeDescr_15544]].channelsSplit(n)

    def channelsJoin() =
        summon[ChannelsDSL[FlowOnlyPipeDescr_15544]].channelsJoin()

    // builder methods for obj modifiers - Delegate to DirectionChangeDSL_15544 typeclass

    given directionDSL: DirectionChangeDSL_15544[FlowOnlyPipeDescr_15544] =
        summon[DirectionChangeDSL_15544[FlowOnlyPipeDescr_15544]]

    def addSharpAngle_0_to_180deg(name: String, angle: Angle, absDir: AbsoluteDirection) =
        directionDSL.addSharpAngle_0_to_180deg(name, angle, absDir)
    def addSharpAngle_30deg(name: String, absDir: AbsoluteDirection)                     =
        directionDSL.addSharpAngle_30deg(name, absDir)
    def addSharpAngle_45deg(name: String, absDir: AbsoluteDirection)                     =
        directionDSL.addSharpAngle_45deg(name, absDir)
    def addSharpAngle_60deg(name: String, absDir: AbsoluteDirection)                     =
        directionDSL.addSharpAngle_60deg(name, absDir)
    def addSharpAngle_90deg(name: String, absDir: AbsoluteDirection)                     =
        directionDSL.addSharpAngle_90deg(name, absDir)

    def addCircularArc60(name: String, absDir: AbsoluteDirection) =
        directionDSL.addCircularArc60(name, absDir)

    // Split/Merge operations
    def addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
        name         : String,
        absDir       : AbsoluteDirection,
        newInnerShape: PipeShape
    ) = SplitSingleFlowIntoTwoFlowsWith90DegTurn(name, Some(absDir), newInnerShape)

    def addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
        name                : String,
        absDir              : AbsoluteDirection,
        newInnerShape       : PipeShape,
        symmetryPlaneAzimuth: AzimuthDirection
    ) = SplitSingleFlowIntoTwoFlowsWith90DegTurn(name, Some(absDir), newInnerShape, Some(symmetryPlaneAzimuth))

    def addMergeTwoFlowsIntoSingleWith90DegTurn(
        name         : String,
        absDir       : AbsoluteDirection,
        newInnerShape: PipeShape
    ) = MergeTwoFlowsIntoSingleWith90DegTurn(name, Some(absDir), newInnerShape)

    def addMergeTwoFlowsIntoSingleWith90DegTurn(
        name                : String,
        absDir              : AbsoluteDirection,
        newInnerShape       : PipeShape,
        symmetryPlaneAzimuth: AzimuthDirection
    ) = MergeTwoFlowsIntoSingleWith90DegTurn(name, Some(absDir), newInnerShape, Some(symmetryPlaneAzimuth))

    def addSectionShapeChange(
        name    : String,
        to_shape: PipeShape
    ) = AddSectionShapeChange(name, to_shape)

    // Delegate to SectionDSL typeclass
    given sectionDSL: SectionDSL[FlowOnlyPipeDescr_15544] =
        summon[SectionDSL[FlowOnlyPipeDescr_15544]]

    def addSectionSlopped(
        name  : String,
        length: Length
    ) = sectionDSL.addSectionSlopped(name, length)

    def addSectionSloppedForceManualElevationGain(
        name          : String,
        length        : Length,
        elevation_gain: Length
    ) = sectionDSL.addSectionSloppedForceManualElevationGain(name, length, elevation_gain)

    @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
    def addSectionHorizontal(
        name             : String,
        horizontal_length: Length
    ) = sectionDSL.addSectionSlopped(name, horizontal_length)

    @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
    def addSectionVertical(
        name          : String,
        elevation_gain: Length
    ) = sectionDSL.addSectionSlopped(
        name,
        if (elevation_gain) >= 0.meters then elevation_gain else elevation_gain * -1.0
    )

    // Delegate to FlowResistanceDSL typeclass
    given flowResistanceDSL: FlowResistanceDSL[FlowOnlyPipeDescr_15544] =
        summon[FlowResistanceDSL[FlowOnlyPipeDescr_15544]]

    def addPressureDiff(name: String, pressure_difference: Pressure) =
        flowResistanceDSL.addPressureDiff(name, pressure_difference)

    def addFlowResistance(name: String, zeta: ζ) =
        flowResistanceDSL.addFlowResistance(name, zeta)

    @targetName("addFlowResistance_crossSection")
    def addFlowResistance(name: String, zeta: ζ, cross_section: AreaInCm2) =
        flowResistanceDSL.addFlowResistance_crossSection(name, zeta, cross_section)

    @targetName("addFlowResistance_dh")
    def addFlowResistance(name: String, zeta: ζ, hydraulic_diameter: Length) =
        flowResistanceDSL.addFlowResistance_dh(name, zeta, hydraulic_diameter)

object FlowOnlyIncrementalBuilder_15544:

    def makeFor[PType <: PipeType_EN15544](using
        ptype: PType,
        tt1  : TypeTest[FlowOnlyPipeDescr_15544, SetFlowOnlyPipeProp_15544],
        tt2  : TypeTest[FlowOnlyPipeDescr_15544, AddFlowOnlyPipeElement_15544],
        tt3  : TypeTest[FlowOnlyPipeDescr_15544, FlowOnlyPreElementOp_15544]
    ): FlowOnlyIncrementalBuilder_15544 {
        type PT = PType
    } =
        new FlowOnlyIncrementalBuilder_15544:
            override given typeTestSetProp     : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement  : TypeTest[IncrDescr, AddElement]   = tt2
            override given typeTestPreElementOp: TypeTest[IncrDescr, PreElementOp] = tt3
            type PT = PType
            given pt: PT = ptype

// usage: ```object FluePipe extends IncrementalPipeDef[FluePipeT, FluePipe]```
trait FlowOnlyIncrementalPipeDefModule_15544[PipeType <: PipeType_EN15544]
    extends IncrementalPipeDefModule_Common[PipeType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.PipeElDescr

    type _IncrementalBuilder = FlowOnlyIncrementalBuilder_15544 {
        type PipeElDescr = PipeElDescr0
        type PT          = PipeType
    }

    type Params = DraftCondition

    override given hasLength: HasLength[PipeElDescr] =
        afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.hasLength
