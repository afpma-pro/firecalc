/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import algebra.instances.all.given

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.FlowAreaConservation
import afpma.firecalc.engine.alg.{IncrementalBuilderAlg, SplitMerge90Validator, SplitGeometryValidator}
import afpma.firecalc.engine.alg.en13384.IncrementalBuilderAlg_13384
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.impl.common.instances.ChannelsDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.DirectionChangeDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.*
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.FlowResistanceDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_Thermal_13384_Instance.ThermalPropsState_13384
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_Thermal_13384_Instance.given
import afpma.firecalc.engine.impl.common.instances.SectionDSL_13384_Instances.given
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PositionTracker
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.ShapeNotMaterialized.Operation
import afpma.firecalc.engine.typeclasses.*

import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import scala.annotation.targetName
import scala.reflect.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.domain.SetsInnerShape
import com.softwaremill.quicklens.*

object models:

    final type Id_ThermalIncrDescr_13384  = (Int, ThermalPipeDescr_13384 )
    final type Id_FlowOnlyIncrDescr_13384 = (Int, FlowOnlyPipeDescr_13384)

    type ThermalPipeIncrDescr  = PipeIncrDescrG[Id_ThermalIncrDescr_13384]
    type FlowOnlyPipeIncrDescr = PipeIncrDescrG[Id_FlowOnlyIncrDescr_13384]

trait ThermalIncrementalBuilder_13384
    extends IncrementalBuilderAlg
    with IncrementalBuilderAlg_13384[ThermalPropsState_13384]:

    import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.*

    import AddThermalPipeElement_13384.*
    import SetThermalPipeProp_13384.*
    import ThermalChannelTopologyOp_13384.*
    override protected type SplitDTO  = SplitSingleFlowIntoTwoFlowsWith90DegTurn
    override protected type MergeDTO  = MergeTwoFlowsIntoSingleWith90DegTurn
    override protected type StateOpsT = ThermalPropsStateOps[PropsState]

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] =
        afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasInnerShapeAtPos
    override given hasLength         : HasLength[PipeElDescr]          =
        afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength
    override given hasVerticalElev   : HasVerticalElev[PipeElDescr]    =
        afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasVerticalElev
    // export afpma.firecalc.engine.models.en13384.pipedescr.showPipeElDescr

    export afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.StraightSection

    override type PipeElDescr = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    override type IncrDescr  = ThermalPipeDescr_13384
    override type SetProp    = SetThermalPipeProp_13384
    override type AddElement = AddThermalPipeElement_13384

    override type PreElementOp      = ThermalPreElementOp_13384
    override type ChannelTopologyOp = ThermalChannelTopologyOp_13384

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
    def withInitialDirection(dir: PipeInitialDirection): this.type =
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

    override type PT <: PipeType_EN13384

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    // ========== PropsState via Typeclass ==========

    protected val stateOps = summon[ThermalPropsStateOps[PropsState]]

    override protected def mkSplitMerge90Descr(
        nFlows         : NbOfFlows,
        angleN2        : Option[QtyD[Degree]],
        shape          : PipeShape
    ): PipeElDescr =
        SplitMerge90(
            nFlows         = nFlows,
            angleN2        = angleN2,
            effectiveShape = shape
        )

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean   = stateOps.isValid(propsState)
        def nf              : NbOfFlows = stateOps.getNFlows(propsState)

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[QtyD[Meter]] =
            val nextAddSectionsOps =
                convStep.nextStepOps
                    .map(_._2)
                    .flatMap(op => typeTestAddElement.unapply(op))
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
                ThermalPropsState_13384    (
                    ductType     = Some(DuctType.NonConcentricDuctsHighThermalResistance),
                    initialFrame = Some(frame),
                    currentFrame = Some(frame)
                )
            case None      =>
                ThermalPropsState_13384(
                    // by default, use non concentric air ducts (see EN 13384 7.8.1)
                    ductType = Some(DuctType.NonConcentricDuctsHighThermalResistance)
                )

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
        finalState: PropsState,
        seed      : PipeBuildSeed
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
            else
                val posResult       = PositionTracker.computeThermal13384(
                    incrDescrs.map(_._2).toSeq,
                    PipeInitialDirection.default,
                    finalState.initialFrame,
                    seed.startPoint.getOrElse(Vec3(0, 0, 0)),
                    splitPosition = seed.slot0FireboxSplitPosition
                )
                val mergeValidation = SplitMerge90Validator.validateAllMergePositions(posResult.splitMergePositions, pt)
                val splits          = posResult.splitMergePositions.filter(_.isSplit)
                val splitValidation = NonEmptyList.fromList(splits.toList) match
                    case Some(ne) => SplitGeometryValidator.validateSplitPositions(ne, posResult, pt)
                    case None     => ().validNel
                val positionErrors: ValidatedResult[Unit] =
                    if posResult.errors.isEmpty then ().validNel
                    else posResult.errors.map(e => SymmetryPlaneAbsDirVertical(pt, e).invalidNel).sequence.map(_ => ())
                (splitValidation |+| mergeValidation |+| positionErrors).as(())

    override protected def mkFullElementsDescr(
        prevs   : PipeFullDescr,
        convStep: ConversionStep
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
        given NbOfFlows = summon[PropsState].nf
        val (idIncr, addElementOp) = id_addElementOp
        val st = summon[PropsState]

        val el = addElementOp match
            case op @ (_: AddSectionSlopped | _: AddSectionSloppedForceManualElevationGain | _: AddSectionHorizontal |
                _: AddSectionVertical) =>
                given ThermalStraightSectionCtx_13384 =
                    ThermalStraightSectionCtx_13384(
                        stateOps.getInnerShape(st),
                        stateOps.getOuterShape(st),
                        stateOps.getRoughness (st),
                        stateOps.getLayers    (st),
                        stateOps.getAirSpace  (st),
                        stateOps.getPipeLoc   (st),
                        stateOps.getDuctType  (st),
                        pt,
                        currentFrame = st.currentFrame
                    )
                thermalStraightSection13384.make(op)

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
                mkSplitMerge90(st, op.absDir, 2.flows, op.newInnerShape)

            case op: MergeTwoFlowsIntoSingleWith90DegTurn =>
                mkSplitMerge90(st, op.absDir, 1.flow, op.newInnerShape)

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
                        thermalDirectionChange13384.make(op)
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
                        thermalSectionGeometryChange13384.make(op)
                    }

            case op: AddFlowResistance =>
                given FlowResistanceCtx_13384 =
                    FlowResistanceCtx_13384(stateOps.getInnerShape(st), pt)
                thermalFlowResistance13384.make(op)

            case op: AddPressureDiff =>
                given FlowResistanceCtx_13384 =
                    FlowResistanceCtx_13384(stateOps.getInnerShape(st), pt)
                thermalPressureDiff13384.make(op)

        val elIdx          = PipeIdx(prevs.elems.size)
        val prevInnerGeomO = prevs.lastInnerGeom
        // Dev-only escape hatch: if the op batch preceding this add-element set the
        // inner shape via SetInnerShapePreventSectionGeometryChangeAuto, skip the
        // automatic SectionGeometryChange insertion for this element.
        val preventAuto    = convStep.allPreElementOpsUntilNextAddElement.exists {
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
        val nextAdd = convStep.findNextAddElement
        nextAdd.map(_._2) match
            case None                                               =>
                propsState.validNel
            case Some(
                    _: AddSectionSlopped | _: AddSectionSloppedForceManualElevationGain | _: AddSectionHorizontal |
                    _: AddSectionVertical
                ) =>
                stateOps.materialize(propsState).validNel
            case Some(addDC: AddDirectionChange)                    =>
                // Update direction tracking if absDir is defined and we have a current frame
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
            case Some(_ @AddFlowResistance(_, _, _))                =>
                propsState.validNel
            case Some(_ @AddPressureDiff(_, _))                     => propsState.validNel
            case Some(op: AddSectionChange)                         =>
                stateOps.setInnerShape(propsState, op.to_shape).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState] =
        // Use the AddElement's idIncr for elementIndex in errors — it is the physical
        // element that the pre-element ops target, so the error points to the element
        // the user sees (and can fix) rather than the preceding SetInnerShape row.
        val (nextElemIdIncr, nextElemName) =
            convStep.findNextAddElement.map(idOp => (idOp._1, idOp._2.name)).getOrElse((-1, "?"))

        def updateVNelState(
            vState  : ValidatedNel[IncrementalValidation_Error, PropsState],
            idIncr  : Int,
            elemName: String
        )(
            atom: SetSingleProp
        ): ValidatedNel[IncrementalValidation_Error, PropsState] =
            def applyInnerShapeSet(
                vState: ValidatedNel[IncrementalValidation_Error, PropsState],
                g     : PipeShape
            ): ValidatedNel[IncrementalValidation_Error, PropsState] =
                vState.andThen { st =>
                    stateOps.validateMaterialized(st, Operation.SetInnerShape, pt, nextElemIdIncr, elemName).andThen {
                        _ =>
                            FlowAreaConservation
                                .validateSetInnerShape(st, g, pt, nextElemIdIncr, elemName)(using stateOps)
                                .toValidatedNel
                                .map(s => stateOps.setInnerShape(s, g))
                    }
                }
            atom match
                case SetInnerShape(g)                                 =>
                    applyInnerShapeSet(vState, g)
                case SetInnerShapePreventSectionGeometryChangeAuto(g) =>
                    // Same state update as plain SetInnerShape (area-conservation
                    // validation still runs); only the later auto-insertion is
                    // suppressed (handled at the mkFullElementsDescr call site).
                    applyInnerShapeSet(vState, g)
                case SetOuterShape(g)                                 =>
                    vState.map(_.modify(_.outer_shape).setTo(g.some))
                case SetThickness(t)                                  =>
                    vState andThen: v =>
                        stateOps.getInnerShape(v) match
                            case None     => ThicknessRequiresInnerGeometry(pt).invalidNel
                            case Some(ig) =>
                                v.modify(_.outer_shape).setTo(ig.expandGeomWithThickness(t).some).validNel
                case SetRoughness(r)                                  =>
                    vState.map(_.modify(_.roughness).setTo(r.some))
                case SetMaterial(lm)                                  =>
                    vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                case SetLayer(e, lambda)                              =>
                    val vGeom = vState.andThen(_.getValidated(stateOps.getInnerShape, LayerRequiresSectionGeometry(pt)))
                    vGeom.andThen: geom =>
                        vState.map(
                            _.modify(_.layers)
                                .setTo(List(AppendLayerDescr.FromLambdaUsingThickness(e, lambda)).some)
                                .modify(_.outer_shape)
                                .setTo(geom.expandGeomWithThickness(e).some)
                        )
                case SetLayers(ldescrs)                               =>
                    val vGeom = vState.andThen(_.getValidated(stateOps.getInnerShape, LayersRequireInnerShape(pt)))

                    vGeom andThen: geom =>
                        vState.map(
                            _.modify(_.layers)
                                .setTo(ldescrs.some)
                                .modify(_.outer_shape)
                                .setTo(ldescrs.compute_outer_shape(geom).some)
                        )
                case SetAirSpaceAfterLayers(asp)                      =>
                    vState.map(_.modify(_.airSpace_afterLayers).setTo(asp.some))
                case SetPipeLocation(loc)                             =>
                    vState.map(_.modify(_.pipeLoc).setTo(loc.some))
                case SetDuctType(duct)                                =>
                    vState.map(_.modify(_.ductType).setTo(duct.some))

        convStep.allPreElementOpsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (idIncr, op)) =>
                op match
                    case prop: SetSingleProp =>
                        updateVNelState(vState, idIncr, nextElemName)(prop)
                    case ThermalChannelTopologyOp_13384.SetNumberOfFlows(nf) =>
                        vState.andThen { st =>
                            handleSetNumberOfFlows(st, nf, convStep, nextElemIdIncr, nextElemName)
                        }
                    case SetPropertiesInBatch(batch_name, props, _) =>
                        props.foldLeft(vState)((vs, prop) => updateVNelState(vs, idIncr, nextElemName)(prop))
                    case lf  : LinedFlue     =>
                        expandLinedFlue(vState, lf, idIncr, nextElemName, updateVNelState)
            }

    /**
     * Expand a [[LinedFlue]] into equivalent atomic operations.
     *
     * A lined flue is a 3-part pipe: liner (tubage) + air space + casing (boisseau).
     * This method applies the liner's non-layer props (material, inner shape, roughness),
     * then builds a combined [[SetLayers]] from the liner layers, air space layer, and casing layers.
     */
    private def expandLinedFlue(
        vState         : ValidatedNel[IncrementalValidation_Error, PropsState],
        lf             : LinedFlue,
        idIncr         : Int,
        elemName       : String,
        updateVNelState: (
            ValidatedNel[IncrementalValidation_Error, PropsState],
            Int,
            String
        ) => SetSingleProp => ValidatedNel[
            IncrementalValidation_Error,
            PropsState
        ]
    ): ValidatedNel[IncrementalValidation_Error, PropsState] =
        val LinedFlue(_, liner, airSpace, casing) = lf

        // Apply liner's non-layer props (material, inner shape, roughness, etc.)
        val nonLayerLinerProps = liner.props.filterNot(_.isInstanceOf[SetLayers]).filterNot(_.isInstanceOf[SetLayer])
        val afterLinerProps    =
            nonLayerLinerProps.foldLeft(vState)((vs, prop) => updateVNelState(vs, idIncr, elemName)(prop))

        // Extract layers from liner and casing
        val linerLayers  = liner.props.extractLayers
        val casingLayers = casing.props.extractLayers

        // Check that casing is large enough to contain the expanded liner + air space
        val casingInnerShape = casing.props.extractInnerShape
        val linerInnerShape  = liner.props.extractInnerShape
        (linerInnerShape, casingInnerShape) match
            case (Some(lis), Some(cis)) =>
                val expandedLinerDh    : Length = linerLayers.compute_outer_shape(lis).dh
                // Account for air space width (applies on both sides of liner)
                val airSpaceWidthMeters: Length = airSpace match
                    case AirSpaceDetailed_V2.WithAirSpace_V2(width, _, _) => width * 2.0
                    case _                                                => 0.0.meters
                val totalRequiredDh = expandedLinerDh + airSpaceWidthMeters
                if totalRequiredDh > cis.dh then
                    return CasingTooSmallForLiner(s"${totalRequiredDh.show}", cis.dh.show, pt).invalidNel
            case _ => // cannot check — proceed

        // Build air space layer: use casing's inner shape if available, otherwise width-based
        val airSpaceLayer: List[AppendLayerDescr] = airSpace match
            case AirSpaceDetailed_V2.WithoutAirSpace_V2                    => Nil
            case AirSpaceDetailed_V2.WithAirSpace_V2(width, dir, openings) =>
                casingInnerShape match
                    case Some(shape) =>
                        List(AppendLayerDescr.AirSpaceUsingOuterShape(shape, dir, openings))
                    case None        =>
                        List(AppendLayerDescr.AirSpaceUsingThickness(width, dir, openings))

        // Combine all layers and apply
        val combinedLayers = linerLayers ++ airSpaceLayer ++ casingLayers
        if combinedLayers.nonEmpty then updateVNelState(afterLinerProps, idIncr, elemName)(SetLayers(combinedLayers))
        else afterLinerProps

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
    def outer_shape(shape: PipeShape)                                           =
        SetOuterShape(shape)
    @deprecated def thickness(t: QtyD[Meter])                                   =
        SetThickness(t)
    def roughness(r: Roughness)                                                 =
        SetRoughness(r)

    def material(lm: Material_13384) =
        SetMaterial(lm)

    def layer(e: Length, tr: SquareMeterKelvinPerWatt) =
        layers(AppendLayerDescr.FromThermalResistanceUsingThickness(e, tr))

    def layer(e: Length, λ: ThermalConductivity) =
        SetLayer(e, λ)
    def layers(ls: AppendLayerDescr*)            =
        SetLayers(ls.toList)

    def airSpace_afterLayers(asp: AirSpaceDetailed_V2) =
        SetAirSpaceAfterLayers(asp)

    export PipeLocation.*
    def pipeLocation(ploc: PipeLocation) =
        SetPipeLocation(ploc)

    def ductType(duct: DuctType) =
        SetDuctType(duct)

    // Delegate to ChannelsDSL typeclass
    def channelsSplit(n: Int) =
        summon[ChannelsDSL[ThermalPipeDescr_13384]].channelsSplit(n)
    def channelsJoin()        =
        summon[ChannelsDSL[ThermalPipeDescr_13384]].channelsJoin()

    // builder methods for obj modifiers - Delegate to DirectionChangeDSL_13384 typeclass

    given directionDSL: DirectionChangeDSL_13384[ThermalPipeDescr_13384] =
        summon[DirectionChangeDSL_13384[ThermalPipeDescr_13384]]

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
    given sectionDSL: SectionDSL[ThermalPipeDescr_13384] =
        summon[SectionDSL[ThermalPipeDescr_13384]]

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
    given flowResistanceDSL: FlowResistanceDSL[ThermalPipeDescr_13384] =
        summon[FlowResistanceDSL[ThermalPipeDescr_13384]]

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

object ThermalIncrementalBuilder_13384:

    def makeFor[PType <: PipeType_EN13384](using
        ptype: PType,
        tt1  : TypeTest[ThermalPipeDescr_13384, SetThermalPipeProp_13384],
        tt2  : TypeTest[ThermalPipeDescr_13384, AddThermalPipeElement_13384],
        tt3  : TypeTest[ThermalPipeDescr_13384, ThermalPreElementOp_13384]
    ): ThermalIncrementalBuilder_13384 {
        // type PipeElDescr    = PipeElDescr0
        type PT = PType
    } =
        new ThermalIncrementalBuilder_13384:
            override given typeTestSetProp     : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement  : TypeTest[IncrDescr, AddElement]   = tt2
            override given typeTestPreElementOp: TypeTest[IncrDescr, PreElementOp] = tt3
            type PT = PType
            given pt: PT = ptype

trait ThermalIncrementalPipeDefModule_13384[PType <: PipeType_EN13384] extends IncrementalPipeDefModule_Common[PType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PipeElDescr = PipeElDescr0
        type PT          = PType
    }

    type Params = DraftCondition

    override given hasLength: HasLength[PipeElDescr0] =
        afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength
