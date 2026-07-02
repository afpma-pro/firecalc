/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.SetsInnerShape
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.engine.FlowAreaConservation
import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.FramedBuilderSupport
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.impl.common.instances.ChannelsDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.DirectionChangeDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.*
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.FlowResistanceDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_13384_Instance.FlowOnlyPropsState_13384
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_13384_Instance.given
import afpma.firecalc.engine.impl.common.instances.SectionDSL_13384_Instances.given
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.FinalDirWithoutInitialDirection
import afpma.firecalc.engine.standard.GeometryWithoutInitialDirection
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.ShapeNotMaterialized.Operation
import afpma.firecalc.engine.typeclasses.*
import cats.data.*
import cats.syntax.all.*
import coulomb.policy.standard.given
import scala.annotation.targetName
import scala.reflect.*
import com.softwaremill.quicklens.*
import afpma.firecalc.units.Vec3

trait FlowOnlyIncrementalBuilder_13384 extends IncrementalBuilderAlg with FramedBuilderSupport:

    import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.*
    import AddFlowOnlyPipeElement_13384.*
    import SetFlowOnlyPipeProp_13384.*

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] =
        afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.hasInnerShapeAtPos
    override given hasLength         : HasLength[PipeElDescr]          =
        afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.hasLength
    override given hasVerticalElev   : HasVerticalElev[PipeElDescr]    =
        afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.hasVerticalElev

    // export afpma.firecalc.engine.models.en13384.pipedescr.showPipeElDescr
    export afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.StraightSection

    override type PipeElDescr = afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.PipeElDescr

    override type IncrDescr  = FlowOnlyPipeDescr_13384
    override type SetProp    = SetFlowOnlyPipeProp_13384
    override type AddElement = AddFlowOnlyPipeElement_13384

    override type PreElementOp      = FlowOnlyPreElementOp_13384
    override type ChannelTopologyOp = FlowOnlyChannelTopologyOp_13384

    /**
     * Wrapper-level initial direction (V7).
     * When defined, seeds the initial/cur frame in mkInitPropsState.
     * Replaces descriptor-level SetInitialDirection after V6→V7 migration.
     */
    protected var wrapperInitialDirection: Option[PipeInitialDirection] = None

    /**
     * Set wrapper-level initial direction.
     * @param dir the initial direction
     * @return this builder (for chaining)
     */
    def withInitialDirection(dir: PipeInitialDirection): this.type =
        wrapperInitialDirection = Some(dir)
        this

    extension (addElement: AddElement) override def name: String = addElement.name

    override protected def isForbiddenAddElementAtStart(
        addElement: AddElement
    ): Boolean =
        addElement match
            case _: AddDirectionChange                       => true
            case _: SplitSingleFlowIntoTwoFlowsWith90DegTurn => true
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

    override type PT <: PipeType_EN13384

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    // ========== PropsState via Typeclass ==========

    override protected type PropsState = FlowOnlyPropsState_13384
    private val stateOps = summon[PropsStateOps[PropsState]]

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean   = stateOps.isValid(propsState)
        def nf              : NbOfFlows = stateOps.getNFlows(propsState)

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[QtyD[Meter]] =
            val nextAddSectionsOps =
                convStep.allRemainingOps
                    .map(_._2)
                    .find:
                        case _: (AddSectionSlopped | AddSectionSloppedForceManualElevationGain) => true
                        case _: (AddSectionHorizontal | AddSectionVertical)                     => true
                        case _: (AddSectionChange | AddDirectionChange | AddFlowResistance | AddPressureDiff |
                                SplitSingleFlowIntoTwoFlowsWith90DegTurn | MergeTwoFlowsIntoSingleWith90DegTurn) =>
                            false
                        case _: PreElementOp                                                    => false
                    .map(_.asInstanceOf[AddElement])
            nextAddSectionsOps.headOption.flatMap:
                case _ @AddSectionSlopped(_, l)                            => l.some
                case _ @AddSectionSloppedForceManualElevationGain(_, l, _) => l.some
                case _ @AddSectionHorizontal(_, l)                         => l.some
                case _ @AddSectionVertical(_, l)                           => l.some
                case _: (AddSectionChange | AddDirectionChange | AddFlowResistance | AddPressureDiff |
                        SplitSingleFlowIntoTwoFlowsWith90DegTurn | MergeTwoFlowsIntoSingleWith90DegTurn) =>
                    None

    extension (piDescr: PipeIncrDescr) override def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

    /** Inner geometry in effect after folding the first n descriptors — used by UI prefill. */
    extension (piDescr: PipeIncrDescr)
        def innerShapeAtPrefix(n: Int): Option[PipeShape] =
            val prefixResult = piDescr.propsStateAtPrefix(n)
            prefixResult.toOption.flatMap(stateOps.getInnerShape)

        /** Number of flows in effect after folding the first n descriptors. */
        def nFlowsAtPrefix(n: Int): Option[NbOfFlows] =
            val prefixResult = piDescr.propsStateAtPrefix(n)
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
                FlowOnlyPropsState_13384(
                    initialFrame = Some(frame),
                    currentFrame = Some(frame)
                )
            case None      =>
                FlowOnlyPropsState_13384()

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    override protected def currentFrameFromPropsState(s: PropsState): Option[PipeFrame] =
        s.currentFrame

    override protected def currentNFlowsFromPropsState(s: PropsState): NbOfFlows =
        stateOps.getNFlows(s)

    override protected def applyExternalFrame(s: PropsState, frame: PipeFrame): PropsState =
        // External frame from seed always takes precedence — V7 enforces initial direction at wrapper level, not descriptor level.
        s.copy(initialFrame = Some(frame), currentFrame = Some(frame))

    override protected def applyExternalNFlows(s: PropsState, nFlows: NbOfFlows): PropsState =
        s.copy(nFlows = nFlows)

    override protected def postBuildValidation(
        incrDescrs: Vector[Id_IncrDescr],
        finalState: PropsState
    ): ValidatedResult[Unit] =
        val hasGeometry = incrDescrs.exists:
            case (_, _: AddElement) => true
            case _ => false
        if hasGeometry && finalState.initialFrame.isEmpty then GeometryWithoutInitialDirection(pt).invalidNel
        else
            val hasFinalDir = incrDescrs.exists:
                case (_, dc: AddDirectionChange                     ) => dc.absDir.isDefined
                case (_, _: SplitSingleFlowIntoTwoFlowsWith90DegTurn) => true
                case (_, _: MergeTwoFlowsIntoSingleWith90DegTurn    ) => true
                case _ => false
            if hasFinalDir && finalState.initialFrame.isEmpty then FinalDirWithoutInitialDirection(pt).invalidNel
            else ().validNel

    override protected def mkFullElementsDescr(
        prevs   : PipeFullDescr,
        convStep: ConversionStep
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
        given NbOfFlows = summon[PropsState].nf
        val (idIncr, addElementOp) = id_addElementOp
        val st             = summon[PropsState]
        val elIdx          = PipeIdx(prevs.elems.size)
        val prevInnerGeomO = prevs.lastInnerGeom

        val el = addElementOp match
            case op @ (_: AddSectionSlopped | _: AddSectionSloppedForceManualElevationGain | _: AddSectionHorizontal |
                _: AddSectionVertical) =>
                given FlowOnlyStraightSectionCtx_13384 =
                    FlowOnlyStraightSectionCtx_13384(
                        stateOps.getInnerShape(st),
                        stateOps.getRoughness (st),
                        pt,
                        currentFrame = st.currentFrame
                    )
                flowOnlyStraightSection13384.make(op)

            // Split/Merge: no SectionGeometryChange auto-insertion needed.
            // The element's `effectiveShape` (from DTO `newInnerShape`) already
            // captures the post-transition shape. `hasInnerShapeAtPos` returns
            // `effectiveShape` for `SplitMerge90` via its `DirectionChange` parent,
            // so downstream shape resolution and any subsequent SectionGeometryChange
            // comparison work correctly. Intentional for both EN 15544 and EN 13384.
            //
            // Note: the result passes through `AutoInsertionHelper_13384` below,
            // but at this point `stateOps.getInnerShape(st)` still holds the shape
            // BEFORE the split/merge (state update runs in `updateStateAfterConversionStep`),
            // so `prevInnerGeomO == currentShapeO` → no SectionGeometryChange inserted.
            case op: SplitSingleFlowIntoTwoFlowsWith90DegTurn =>
                val dc = SplitMerge90(
                    nFlows         = 2.flows,
                    zeta           = CoefficientOfFlowResistance.splitMerge90Zeta,
                    angleN2        = None,
                    effectiveShape = op.newInnerShape
                )
                dc.validNel

            case op: MergeTwoFlowsIntoSingleWith90DegTurn =>
                val dc = SplitMerge90(
                    nFlows         = 1.flow,
                    zeta           = CoefficientOfFlowResistance.splitMerge90Zeta,
                    angleN2        = None,
                    effectiveShape = op.newInnerShape
                )
                dc.validNel

            case op: AddDirectionChange =>
                stateOps
                    .validateMaterialized(st, Operation.AddDirectionChange, pt, idIncr.unwrap, addElementOp.name)
                    .andThen { _ =>
                        given DirectionChangeCtx_13384 = DirectionChangeCtx_13384(
                            stateOps.getInnerShape(st),
                            convStep.nextSectionLengthOpt,
                            pt,
                            dirBeforePreviousDC = st.dirBeforePreviousDC,
                            currentFrame        = st.currentFrame
                        )
                        flowOnlyDirectionChange13384.make(op)
                    }

            case op: AddSectionChange =>
                stateOps
                    .validateMaterialized(st, Operation.AddSectionChange, pt, idIncr.unwrap, addElementOp.name)
                    .andThen { _ =>
                        given SectionGeometryChangeCtx_13384 =
                            SectionGeometryChangeCtx_13384(
                                stateOps.getInnerShape(st),
                                convStep.allPreElementOpsUntilNextAddElement.exists {
                                    case (_, _: SetsInnerShape) => true
                                    case _ => false
                                },
                                pt
                            )
                        flowOnlySectionGeometryChange13384.make(op)
                    }

            case op: AddFlowResistance =>
                given FlowResistanceCtx_13384 =
                    FlowResistanceCtx_13384(stateOps.getInnerShape(st), pt)
                flowOnlyFlowResistance13384.make(op)

            case op: AddPressureDiff =>
                given FlowResistanceCtx_13384 =
                    FlowResistanceCtx_13384(stateOps.getInnerShape(st), pt)
                flowOnlyPressureDiff13384.make(op)

        // Dev-only escape hatch: if the op batch preceding this add-element set the
        // inner shape via SetInnerShapePreventSectionGeometryChangeAuto, skip the
        // automatic SectionGeometryChange insertion for this element.
        val preventAuto = convStep.allPreElementOpsUntilNextAddElement.exists {
            case (_, _: SetInnerShapePreventSectionGeometryChangeAuto) => true
            case _ => false
        }
        el.andThen { s =>
            AutoInsertionHelper_13384.maybeInsertSectionGeometryChange(
                this,
                s,
                preventAuto,
                prevInnerGeomO,
                stateOps.getInnerShape(st),
                idIncr,
                elIdx,
                pt,
                addElementOp.name,
                SectionGeometryChange.make
            )
        }

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
                val frameUpdate = op.absDir match
                    case Some(fd) =>
                        propsState.currentFrame match
                            case Some(frame) =>
                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                val newFrame  = frame.applyBendForFinalDir(90.0, targetVec)
                                propsState.copy(
                                    dirBeforePreviousDC = Some(frame.direction),
                                    currentFrame        = Some(newFrame)
                                )
                            case None        => propsState
                    case None     => propsState
                stateOps
                    .setNFlows(stateOps.setInnerShape(frameUpdate, op.newInnerShape), 2.flows)
                    .validNel
            case Some(op: MergeTwoFlowsIntoSingleWith90DegTurn)     =>
                val frameUpdate = op.absDir match
                    case Some(fd) =>
                        propsState.currentFrame match
                            case Some(frame) =>
                                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                                val newFrame  = frame.applyBendForFinalDir(90.0, targetVec)
                                propsState.copy(
                                    dirBeforePreviousDC = Some(frame.direction),
                                    currentFrame        = Some(newFrame)
                                )
                            case None        => propsState
                    case None     => propsState
                stateOps
                    .setNFlows(stateOps.setInnerShape(frameUpdate, op.newInnerShape), 1.flow)
                    .validNel
            case Some(_ @AddFlowResistance(_, _, _))                => propsState.validNel
            case Some(_ @AddPressureDiff(_, _))                     => propsState.validNel
            case Some(op: AddSectionChange)                         =>
                stateOps.setInnerShape(propsState, op.to_shape).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState] =
        val nextElemName = convStep.findNextAddElement.map(_._2.name).getOrElse("?")
        convStep.allPreElementOpsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (idIncr, op)) =>
                def applyInnerShapeSet(
                    vState: ValidatedNel[IncrementalValidation_Error, PropsState],
                    g     : PipeShape
                ): ValidatedNel[IncrementalValidation_Error, PropsState] =
                    vState.andThen { st =>
                        stateOps.validateMaterialized(st, Operation.SetInnerShape, pt, idIncr, nextElemName).andThen {
                            _ =>
                                FlowAreaConservation
                                    .validateSetInnerShape(st, g, pt, idIncr, nextElemName)(using stateOps)
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
                    case FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(nf) =>
                        vState.andThen { st =>
                            stateOps
                                .validateMaterialized(st, Operation.SetNumberOfFlows, pt, idIncr, nextElemName)
                                .andThen { _ =>
                                    validateSplitNotOnAscending(st, nf, IdIncr(idIncr)).andThen(_ =>
                                        FlowAreaConservation.computeSetNFlows(st, nf, pt)(using stateOps).validNel
                                    )
                                }
                        }
            }

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
    def material(lm: Material_13384)                                            =
        SetMaterial(lm)

    // Delegate to ChannelsDSL typeclass
    def channelsSplit(n: Int) =
        summon[ChannelsDSL[FlowOnlyPipeDescr_13384]].channelsSplit(n)
    def channelsJoin()        =
        summon[ChannelsDSL[FlowOnlyPipeDescr_13384]].channelsJoin()

    // builder methods for obj modifiers - Delegate to DirectionChangeDSL_13384 typeclass

    given directionDSL: DirectionChangeDSL_13384[FlowOnlyPipeDescr_13384] =
        summon[DirectionChangeDSL_13384[FlowOnlyPipeDescr_13384]]

    def addAngleVifDe0A90(name: String, angle: QtyD[Degree], absDir: AbsoluteDirection) =
        directionDSL.addAngleVifDe0A90(name, angle, absDir)
    def addSharpAngle_30deg(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_30deg(name, absDir)
    def addSharpAngle_45deg(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_45deg(name, absDir)
    def addSharpAngle_60deg(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_60deg(name, absDir)
    def addSharpAngle_90deg(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_90deg(name, absDir)

    // __INTERPRETATION__
    def addAngleVifDe0A90_unsafe(name: String, angle: QtyD[Degree], absDir: AbsoluteDirection) =
        directionDSL.addAngleVifDe0A90_unsafe(name, angle, absDir)
    def addSharpAngle_30deg_unsafe(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_30deg_unsafe(name, absDir)
    def addSharpAngle_45deg_unsafe(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_45deg_unsafe(name, absDir)
    def addSharpAngle_60deg_unsafe(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_60deg_unsafe(name, absDir)
    def addSharpAngle_90deg_unsafe(name: String, absDir: AbsoluteDirection)                    =
        directionDSL.addSharpAngle_90deg_unsafe(name, absDir)

    def addCoudeCourbe90(name: String, R: QtyD[Meter], absDir: AbsoluteDirection) =
        directionDSL.addCoudeCourbe90(name, R, absDir)
    def addCoudeCourbe60(name: String, R: QtyD[Meter], absDir: AbsoluteDirection) =
        directionDSL.addCoudeCourbe60(name, R, absDir)

    // __INTERPRETATION__
    def addCoudeCourbe90_unsafe(name: String, R: QtyD[Meter], absDir: AbsoluteDirection) =
        directionDSL.addCoudeCourbe90_unsafe(name, R, absDir)
    def addCoudeCourbe60_unsafe(name: String, R: QtyD[Meter], absDir: AbsoluteDirection) =
        directionDSL.addCoudeCourbe60_unsafe(name, R, absDir)

    def addCoudeASegment90Avec2A45(name: String, R: QtyD[Meter], absDir: AbsoluteDirection)   =
        directionDSL.addCoudeASegment90Avec2A45(name, R, absDir)
    def addCoudeASegment90Avec3A30(name: String, R: QtyD[Meter], absDir: AbsoluteDirection)   =
        directionDSL.addCoudeASegment90Avec3A30(name, R, absDir)
    def addCoudeASegment90Avec4A22p5(name: String, R: QtyD[Meter], absDir: AbsoluteDirection) =
        directionDSL.addCoudeASegment90Avec4A22p5(name, R, absDir)

    def addAngleSpecifique(name: String, angle: Angle, zeta: Double, absDir: AbsoluteDirection) =
        directionDSL.addAngleSpecifique(name, angle, zeta, absDir)

    // def addSectionChange(
    //     name: String,
    //     to: PipeShape
    // ) = PipeUserModifier.Obj.AddSectionChange(name, to)

    def addSectionDecrease(name: String, toDiameter: QtyD[Meter]) = AddSectionDecrease(name, toDiameter)
    def addSectionIncrease(name: String, toDiameter: QtyD[Meter]) = AddSectionIncrease(name, toDiameter)

    // def addSectionDecreaseProgressive(name: String, toDiameter: QtyD[Meter], ɣ: QtyD[Degree]) =
    //     AddSectionDecreaseProgressive(name, toDiameter, ɣ)

    // Delegate to SectionDSL typeclass
    given sectionDSL: SectionDSL[FlowOnlyPipeDescr_13384] =
        summon[SectionDSL[FlowOnlyPipeDescr_13384]]

    def addSectionSlopped(
        name  : String,
        length: QtyD[Meter]
    ) = sectionDSL.addSectionSlopped(name, length)

    def addSectionSloppedForceManualElevationGain(
        name          : String,
        length        : QtyD[Meter],
        elevation_gain: QtyD[Meter]
    ) = sectionDSL.addSectionSloppedForceManualElevationGain(name, length, elevation_gain)

    @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
    def addSectionHorizontal(
        name             : String,
        horizontal_length: QtyD[Meter]
    ) = sectionDSL.addSectionHorizontal(name, horizontal_length)

    @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
    def addSectionVertical(
        name          : String,
        elevation_gain: QtyD[Meter]
    ) = sectionDSL.addSectionVertical(name, elevation_gain)

    // Delegate to FlowResistanceDSL typeclass
    given flowResistanceDSL: FlowResistanceDSL[FlowOnlyPipeDescr_13384] =
        summon[FlowResistanceDSL[FlowOnlyPipeDescr_13384]]

    def addPressureDiff(name: String, pressure_difference: Pressure) =
        flowResistanceDSL.addPressureDiff(name, pressure_difference)

    def addFlowResistance(name: String, zeta: QtyD[1]) =
        flowResistanceDSL.addFlowResistance(name, zeta)

    @targetName("addFlowResistance_crossSection")
    def addFlowResistance_crossSection(name: String, zeta: QtyD[1], cross_section: AreaInCm2) =
        flowResistanceDSL.addFlowResistance_crossSection(name, zeta, cross_section)

    @targetName("addFlowResistance_dh")
    def addFlowResistance(name: String, zeta: QtyD[1], hydraulic_diameter: Length) =
        flowResistanceDSL.addFlowResistance_dh(name, zeta, hydraulic_diameter)

    /** add rain cap with H / Dh = 1.0 (meaning ζ = 1.0) according to Table B.8 */
    def addRainCapEN13384_withHeightEqualsDiameter(name: String) =
        flowResistanceDSL.addRainCapEN13384_withHeightEqualsDiameter(name)

    /** add rain cap with H / Dh = 0.5 (meaning ζ = 1.5) according to Table B.8 */
    def addRainCapEN13384_withHeightEquals2Diameter(name: String) =
        flowResistanceDSL.addRainCapEN13384_withHeightEquals2Diameter(name)

object FlowOnlyIncrementalBuilder_13384:

    type Aux[PType <: PipeType_EN13384] = FlowOnlyIncrementalBuilder_13384 {
        type PT = PType
    }

    def makeFor[PType <: PipeType_EN13384](using
        ptype: PType,
        tt1  : TypeTest[FlowOnlyPipeDescr_13384, SetFlowOnlyPipeProp_13384],
        tt2  : TypeTest[FlowOnlyPipeDescr_13384, AddFlowOnlyPipeElement_13384],
        tt3  : TypeTest[FlowOnlyPipeDescr_13384, FlowOnlyPreElementOp_13384]
    ): FlowOnlyIncrementalBuilder_13384.Aux[PType] =
        new FlowOnlyIncrementalBuilder_13384:
            override given typeTestSetProp     : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement  : TypeTest[IncrDescr, AddElement]   = tt2
            override given typeTestPreElementOp: TypeTest[IncrDescr, PreElementOp] = tt3
            type PT = PType
            given pt: PT = ptype

trait FlowOnlyIncrementalPipeDefModule_13384[PType <: PipeType_EN13384] extends IncrementalPipeDefModule_Common[PType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.PipeElDescr

    type _IncrementalBuilder = FlowOnlyIncrementalBuilder_13384 {
        type PipeElDescr = PipeElDescr0
        type PT          = PType
    }

    type Params = DraftCondition

    override given hasLength: HasLength[PipeElDescr0] =
        afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.hasLength
