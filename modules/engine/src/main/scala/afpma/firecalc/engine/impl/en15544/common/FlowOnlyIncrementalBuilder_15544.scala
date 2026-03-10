/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3
import afpma.firecalc.engine.impl.common.instances.ChannelsDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.DirectionChangeDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.ElementFactory_15544_Instances.*
import afpma.firecalc.engine.impl.common.instances.ElementFactory_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.FlowResistanceDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_15544_Instance.FlowOnlyPropsState_15544
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_FlowOnly_15544_Instance.given
import afpma.firecalc.engine.impl.common.instances.SectionDSL_15544_Instances.given
import afpma.firecalc.engine.impl.common.typeclasses.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import scala.annotation.targetName
import scala.reflect.*

import com.softwaremill.quicklens.*

import coulomb.policy.standard.given

trait FlowOnlyIncrementalBuilder_15544 extends IncrementalBuilderAlg:

    import AddFlowOnlyPipeElement_15544.*
    import SetFlowOnlyPipeProp_15544.*

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

    extension (addElement: AddElement) override def name: String = addElement.name

    override protected def isForbiddenAddElementAtStart(
        addElement: AddElement
    ): Boolean = addElement.isInstanceOf[AddDirectionChange]

    override protected def isForbiddenAddElementAtEnd(
        addElement: AddElement
    ): Boolean = addElement.isInstanceOf[AddDirectionChange]

    override type PT <: PipeType_EN15544

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    // ========== PropsState via Typeclass ==========

    override protected type PropsState = FlowOnlyPropsState_15544
    private val stateOps = summon[PropsStateOps[PropsState]]

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean   = stateOps.isValid(propsState)
        def nf              : NbOfFlows = stateOps.getNFlows(propsState)

    extension (piDescr: PipeIncrDescr) def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

    override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState =
        FlowOnlyPropsState_15544()

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    override protected def currentFrameFromPropsState(s: PropsState): Option[PipeFrame] =
        s.currentFrame

    override protected def applyExternalFrame(s: PropsState, frame: PipeFrame): PropsState =
        // Only apply if the pipe itself did not already define an initial direction
        if s.initialFrame.isDefined then s
        else s.copy(initialFrame = Some(frame), currentFrame = Some(frame))

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[Length] =
            convStep.nextOpIfAddElement
                .map(_._2)
                .flatMap:
                    case _ @AddSectionSlopped(_, l, _) => l.some
                    case _ @AddSectionHorizontal(_, l) => l.some
                    case _ @AddSectionVertical(_, l)   => l.some
                    case _: (AddSectionShapeChange | AddDirectionChange | AddFlowResistance | AddPressureDiff) => None

    override protected def mkFullElementsDescr(
        prevs   : PipeFullDescr,
        convStep: ConversionStep
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
            addElementOp match
                case op @ (_: AddSectionSlopped | _: AddSectionHorizontal | _: AddSectionVertical) =>
                    given FlowOnlyStraightSectionCtx_15544 =
                        FlowOnlyStraightSectionCtx_15544(
                            stateOps.getInnerShape(st),
                            stateOps.getRoughness (st),
                            pt
                        )
                    flowOnlyStraightSection15544.make(op).andThen { s =>
                        prevInnerGeomO match
                            case None => NonEmptyList.one((elIdx, None, s)).validNel
                            case Some(prevInnerGeom) if (prevInnerGeom == s.geometry) =>
                                NonEmptyList.one((elIdx, None, s)).validNel
                            case Some(prevInnerGeom) =>
                                val sectGeomCh = SectionGeometryChange(from = prevInnerGeom, to = s.geometry)
                                NonEmptyList(
                                    (elIdx, Some("sect° geom change"), sectGeomCh),
                                    (elIdx.incr(1), None, s                      ) :: Nil
                                ).validNel[IncrementalValidation_Error]
                    }

                case op: AddDirectionChange =>
                    given DirectionChangeCtx_15544 =
                        DirectionChangeCtx_15544(
                            stateOps.getInnerShape(st),
                            pt,
                            dirBeforePreviousDC = st.dirBeforePreviousDC,
                            currentFrame        = st.currentFrame
                        )
                    directionChange15544.make(op).asNonEmptyList

                case op: AddSectionShapeChange =>
                    given SectionGeometryChangeCtx_15544 =
                        SectionGeometryChangeCtx_15544(
                            stateOps.getInnerShape(st),
                            convStep.allSetPropsUntilNextAddElement.exists {
                                case (_, _: SetInnerShape) => true
                                case _ => false
                            },
                            pt
                        )
                    sectionGeometryChange15544.make(op).asNonEmptyList

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
            case None                                => propsState.validNel
            case Some(_ @AddSectionSlopped(_, _, _)) => propsState.validNel
            case Some(_ @AddSectionHorizontal(_, _)) => propsState.validNel
            case Some(_ @AddSectionVertical(_, _))   => propsState.validNel
            case Some(addDC: AddDirectionChange) =>
                addDC.roll match
                    case Some(rollAngle) =>
                        propsState.currentFrame match
                            case Some(frame) =>
                                val rollDeg  = rollAngle.toUnit[Degree].value
                                val deflDeg  = addDC.angle.toUnit[Degree].value
                                val newFrame = frame.applyBend(deflDeg, rollDeg)
                                propsState.copy(
                                    dirBeforePreviousDC = Some(frame.direction),
                                    currentFrame        = Some(newFrame)
                                ).validNel
                            case None => propsState.validNel
                    case None => propsState.validNel
            case Some(_ @AddFlowResistance(_, _, _)) => propsState.validNel
            case Some(_ @AddPressureDiff(_, _))      => propsState.validNel
            case Some(obj: AddSectionShapeChange)    => propsState.modify(_.geometry).setTo(obj.to_shape.some).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep  : ConversionStep
    ): ValidatedResult[PropsState] =
        convStep.allSetPropsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (_, setPropOp)) =>
                setPropOp match
                    case SetInnerShape(g)     =>
                        vState.map(_.modify(_.geometry).setTo(g.some))
                    case SetRoughness(r)      =>
                        vState.map(_.modify(_.roughness).setTo(r.some))
                    case SetMaterial(lm)      =>
                        vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                    case SetNumberOfFlows(nf) =>
                        vState.map(_.modify(_.nFlows).setTo(nf.some))
                    case SetInitialDirection(azimuth, inclination) =>
                        val dir   = Vec3.fromAzimuthElevation(
                            azimuth.toUnit[Degree].value,
                            inclination.toUnit[Degree].value
                        )
                        val frame = PipeFrame.initial(dir)
                        vState.map(_.copy(
                            initialFrame = Some(frame),
                            currentFrame = Some(frame)
                        ))
            }

    // Minimal ElementFactory object required by trait - delegates to typeclass instances
    object ElementFactory extends ElementFactoryModule

    // builder methods for Atomic modifiers

    def innerShape(shape: PipeShape) =
        SetInnerShape(shape)
    def roughness(r: Roughness)      =
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

    def addSharpAngle_0_to_180deg(name: String, angle: Angle, angleN2: Option[Angle] = None) =
        directionDSL.addSharpAngle_0_to_180deg(name, angle, angleN2)
    def addSharpAngle_30deg(name: String, angleN2: Option[Angle] = None)                     =
        directionDSL.addSharpAngle_30deg(name, angleN2)
    def addSharpAngle_45deg(name: String, angleN2: Option[Angle] = None)                     =
        directionDSL.addSharpAngle_45deg(name, angleN2)
    def addSharpAngle_60deg(name: String, angleN2: Option[Angle] = None)                     =
        directionDSL.addSharpAngle_60deg(name, angleN2)
    def addSharpAngle_90deg(name: String, angleN2: Option[Angle] = None)                     =
        directionDSL.addSharpAngle_90deg(name, angleN2)

    def addCircularArc60(name: String) =
        directionDSL.addCircularArc60(name)

    def addSectionShapeChange(
        name    : String,
        to_shape: PipeShape
    ) = AddSectionShapeChange(name, to_shape)

    // Delegate to SectionDSL typeclass
    given sectionDSL: SectionDSL[FlowOnlyPipeDescr_15544] =
        summon[SectionDSL[FlowOnlyPipeDescr_15544]]

    def addSectionSlopped(
        name          : String,
        length        : Length,
        elevation_gain: Length
    ) = sectionDSL.addSectionSlopped(name, length, elevation_gain)

    def addSectionHorizontal(
        name             : String,
        horizontal_length: Length
    ) = sectionDSL.addSectionHorizontal(name, horizontal_length)

    def addSectionVertical(
        name          : String,
        elevation_gain: Length
    ) = sectionDSL.addSectionVertical(name, elevation_gain)

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
        tt2  : TypeTest[FlowOnlyPipeDescr_15544, AddFlowOnlyPipeElement_15544]
    ): FlowOnlyIncrementalBuilder_15544 {
        type PT = PType
    } =
        new FlowOnlyIncrementalBuilder_15544:
            override given typeTestSetProp   : TypeTest[IncrDescr, SetProp]    = tt1
            override given typeTestAddElement: TypeTest[IncrDescr, AddElement] = tt2
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
