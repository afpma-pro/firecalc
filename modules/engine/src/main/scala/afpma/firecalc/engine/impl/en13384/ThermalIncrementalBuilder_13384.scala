/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import scala.annotation.targetName
import scala.reflect.*

import cats.Show
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.impl.common.typeclasses.*
import afpma.firecalc.engine.impl.common.instances.SectionDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.DirectionChangeDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.FlowResistanceDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.ChannelsDSL_13384_Instances.given
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_Thermal_13384_Instance.ThermalPropsState_13384
import afpma.firecalc.engine.impl.common.instances.PropsStateOps_Thermal_13384_Instance.given
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.*
import afpma.firecalc.engine.impl.common.instances.ElementFactory_13384_Instances.given
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.softwaremill.quicklens.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import magnolia1.Transl

object models:

    final type Id_ThermalIncrDescr_13384 = (Int, ThermalPipeDescr_13384)
    final type Id_FlowOnlyIncrDescr_13384 = (Int, FlowOnlyPipeDescr_13384)

    type ThermalPipeIncrDescr = PipeIncrDescrG[Id_ThermalIncrDescr_13384]
    type FlowOnlyPipeIncrDescr = PipeIncrDescrG[Id_FlowOnlyIncrDescr_13384]

trait ThermalIncrementalBuilder_13384 extends IncrementalBuilderAlg:

    import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.*

    import AddThermalPipeElement_13384.*
    import SetThermalPipeProp_13384.*

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasInnerShapeAtPos
    override given hasLength: HasLength[PipeElDescr] = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength
    override given hasVerticalElev: HasVerticalElev[PipeElDescr] = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasVerticalElev
    // export afpma.firecalc.engine.models.en13384.pipedescr.showPipeElDescr

    export afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.StraightSection

    override type PipeElDescr = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    override type IncrDescr = ThermalPipeDescr_13384
    override type SetProp = SetThermalPipeProp_13384
    override type AddElement = AddThermalPipeElement_13384

    extension (addElement: AddElement) 
        override def name: String = addElement.name

    override type PT <: PipeType_EN13384

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    // ========== PropsState via Typeclass ==========
    
    override protected type PropsState = ThermalPropsState_13384
    private val stateOps = summon[ThermalPropsStateOps[PropsState]]

    given nbOfFlowsFromPropsState: Function1[PropsState, NbOfFlows] = stateOps.getNFlows

    extension (propsState: PropsState)
        override def isValid: Boolean = stateOps.isValid(propsState)
        def nf: NbOfFlows = stateOps.getNFlows(propsState)

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[QtyD[Meter]] = 
            val nextAddSectionsOps =
                convStep.allRemainingOps.map(_._2).find:
                    case _: SetProp                => false
                    case _: AddSectionSlopped      => true
                    case _: AddSectionHorizontal   => true
                    case _: AddSectionVertical     => true
                    case _: ( AddSectionChange | AddDirectionChange | AddFlowResistance | AddPressureDiff ) => false
                .map(_.asInstanceOf[AddElement])
            nextAddSectionsOps.headOption.flatMap:
                case _ @ AddSectionSlopped(_, l, _) => l.some
                case _ @ AddSectionHorizontal(_, l) => l.some
                case _ @ AddSectionVertical(_, l)   => l.some
                case _: ( AddSectionChange | AddDirectionChange | AddFlowResistance | AddPressureDiff ) => None

    extension (piDescr: PipeIncrDescr)
        override def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

    override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState = 
        ThermalPropsState_13384(
            // by default, use non concentric air ducts (see EN 13384 7.8.1)
            ductType = Some(DuctType.NonConcentricDuctsHighThermalResistance)
        )

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr = 
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    override protected def mkFullElementsDescr(
        prevs: PipeFullDescr,
        convStep: ConversionStep,
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
        given NbOfFlows = summon[PropsState].nf
        val (idIncr, addElementOp) = id_addElementOp
        val st = summon[PropsState]
        
        val el = addElementOp match
            case op @ (_: AddSectionSlopped | _: AddSectionHorizontal | _: AddSectionVertical) =>
                given ThermalStraightSectionCtx_13384 = 
                    ThermalStraightSectionCtx_13384(
                        stateOps.getInnerShape(st),
                        stateOps.getOuterShape(st),
                        stateOps.getRoughness(st),
                        stateOps.getLayers(st),
                        stateOps.getAirSpace(st),
                        stateOps.getPipeLoc(st),
                        stateOps.getDuctType(st),
                        pt
                    )
                thermalStraightSection13384.make(op)
                
            case op: AddDirectionChange =>
                given DirectionChangeCtx_13384 = DirectionChangeCtx_13384(
                    stateOps.getInnerShape(st),
                    convStep.nextSectionLengthOpt,
                    pt
                )
                thermalDirectionChange13384.make(op)
                
            case op: AddSectionChange =>
                given SectionGeometryChangeCtx_13384 = 
                    SectionGeometryChangeCtx_13384(
                        stateOps.getInnerShape(st),
                        convStep.allSetPropsUntilNextAddElement.exists {
                            case (_, _: SetInnerShape) => true
                            case _ => false
                        },
                        pt
                    )
                thermalSectionGeometryChange13384.make(op)
                
            case op: AddFlowResistance =>
                given FlowResistanceCtx_13384 = 
                    FlowResistanceCtx_13384(stateOps.getInnerShape(st), pt)
                thermalFlowResistance13384.make(op)
                
            case op: AddPressureDiff =>
                given Unit = ()
                thermalPressureDiff13384.make(op)
        
        val elIdx = PipeIdx(prevs.elems.size)
        el.map(el => NonEmptyList.one((idIncr, el.named(elIdx, pt, addElementOp.name))))

    override protected def updateStateAfterConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState] =
        convStep.findNextAddElement.map(_._2) match
            case None                                 => propsState.validNel
            case Some(_ @ AddSectionSlopped(_, _, _))         => propsState.validNel
            case Some(_ @ AddSectionHorizontal(_, _))  => propsState.validNel
            case Some(_ @ AddSectionVertical(_, _))    => propsState.validNel
            case Some(_: AddDirectionChange)          => propsState.validNel
            case Some(_ @ AddFlowResistance(_, _, _)) => propsState.validNel
            case Some(_ @ AddPressureDiff(_, _))      => propsState.validNel
            case Some(op: AddSectionChange) =>
                propsState.modify(_.innerShape).setTo(op.to_shape.some).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState] =
        convStep
            .allSetPropsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (_, atom)) =>
                atom match
                    case SetInnerShape(g) =>
                        vState.map(_.modify(_.innerShape).setTo(g.some))
                    case SetOuterShape(g) =>
                        vState.map(_.modify(_.outer_shape).setTo(g.some))
                    case SetThickness(t) =>
                        vState andThen: v =>
                            v.innerShape match
                                case None     => ThicknessRequiresInnerGeometry(pt).invalidNel
                                case Some(ig) => v.modify(_.outer_shape).setTo(ig.expandGeomWithThickness(t).some).validNel
                    case SetRoughness(r) =>
                        vState.map(_.modify(_.roughness).setTo(r.some))
                    case SetMaterial(lm) =>
                        vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                    case SetLayer(e, lambda) =>
                        val vGeom = vState.andThen(_
                            .getValidated(
                                _.innerShape,
                                LayerRequiresSectionGeometry(pt)))
                        vGeom.andThen: geom =>
                            vState.map(_
                                .modify(_.layers)
                                .setTo(List(AppendLayerDescr.FromLambdaUsingThickness(e, lambda)).some)
                                .modify(_.outer_shape) // also update outer geometry using thickness of layer
                                .setTo(geom.expandGeomWithThickness(e).some)
                            )
                    case SetLayers(ldescrs) => 
                        val vGeom = vState.andThen(_
                            .getValidated(
                                _.innerShape,
                                LayersRequireInnerShape(pt)))

                        vGeom andThen: geom =>
                            vState.map(_
                                .modify(_.layers)
                                .setTo(ldescrs.some)
                                .modify(_.outer_shape) // also update outer geometry using thickness of layer
                                .setTo(ldescrs.compute_outer_shape(geom).some)
                            )
                    case SetAirSpaceAfterLayers(asp) =>
                        vState.map(_.modify(_.airSpace_afterLayers).setTo(asp.some))
                    case SetPipeLocation(loc) =>
                        vState.map(_.modify(_.pipeLoc).setTo(loc.some))
                    case SetDuctType(duct) =>
                        vState.map(_.modify(_.ductType).setTo(duct.some))
                    case SetNumberOfFlows(nf) =>
                        vState.map(_.modify(_.nFlows).setTo(nf.some))
            }

    // Minimal ElementFactory object required by trait - delegates to typeclass instances
    object ElementFactory extends ElementFactoryModule

    // builder methods for Atomic modifiers

    def innerShape(shape: PipeShape) =
        SetInnerShape(shape)
    def outer_shape(shape: PipeShape) =
        SetOuterShape(shape)
    @deprecated def thickness(t: QtyD[Meter]) =
        SetThickness(t)
    def roughness(r: Roughness) = 
        SetRoughness(r)

    def material(lm: Material_13384) =
        SetMaterial(lm)

    def layer(e: Length, tr: SquareMeterKelvinPerWatt) =
        layers(AppendLayerDescr.FromThermalResistanceUsingThickness(e, tr))

    def layer(e: Length, λ: ThermalConductivity) =
        SetLayer(e, λ)
    def layers(ls: AppendLayerDescr*) =
        SetLayers(ls.toList)

    def airSpace_afterLayers(asp: AirSpaceDetailed) = 
        SetAirSpaceAfterLayers(asp)

    export PipeLocation.*
    def pipeLocation(ploc: PipeLocation) =
        SetPipeLocation(ploc)

    def ductType(duct: DuctType) = 
        SetDuctType(duct)

    // Delegate to ChannelsDSL typeclass
    def channelsSplit(n: Int) = 
        summon[ChannelsDSL[ThermalPipeDescr_13384]].channelsSplit(n)
    def channelsJoin() =
        summon[ChannelsDSL[ThermalPipeDescr_13384]].channelsJoin()

    // builder methods for obj modifiers - Delegate to DirectionChangeDSL_13384 typeclass

    given directionDSL: DirectionChangeDSL_13384[ThermalPipeDescr_13384] = 
        summon[DirectionChangeDSL_13384[ThermalPipeDescr_13384]]

    def addAngleVifDe0A90(name: String, angle: QtyD[Degree]) = 
        directionDSL.addAngleVifDe0A90(name, angle)
    def addSharpAngle_30deg(name: String) = 
        directionDSL.addSharpAngle_30deg(name)
    def addSharpAngle_45deg(name: String) = 
        directionDSL.addSharpAngle_45deg(name)
    def addSharpAngle_60deg(name: String) = 
        directionDSL.addSharpAngle_60deg(name)
    def addSharpAngle_90deg(name: String) = 
        directionDSL.addSharpAngle_90deg(name)

    // __INTERPRETATION__
    def addAngleVifDe0A90_unsafe(name: String, angle: QtyD[Degree]) = 
        directionDSL.addAngleVifDe0A90_unsafe(name, angle)
    def addSharpAngle_30deg_unsafe(name: String) = 
        directionDSL.addSharpAngle_30deg_unsafe(name)
    def addSharpAngle_45deg_unsafe(name: String) = 
        directionDSL.addSharpAngle_45deg_unsafe(name)
    def addSharpAngle_60deg_unsafe(name: String) = 
        directionDSL.addSharpAngle_60deg_unsafe(name)
    def addSharpAngle_90deg_unsafe(name: String) = 
        directionDSL.addSharpAngle_90deg_unsafe(name)

    def addCoudeCourbe90(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeCourbe90(name, R)
    def addCoudeCourbe60(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeCourbe60(name, R)

    // __INTERPRETATION__
    def addCoudeCourbe90_unsafe(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeCourbe90_unsafe(name, R)
    def addCoudeCourbe60_unsafe(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeCourbe60_unsafe(name, R)

    def addCoudeASegment90Avec2A45(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeASegment90Avec2A45(name, R)
    def addCoudeASegment90Avec3A30(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeASegment90Avec3A30(name, R)
    def addCoudeASegment90Avec4A22p5(name: String, R: QtyD[Meter]) = 
        directionDSL.addCoudeASegment90Avec4A22p5(name, R)

    def addAngleSpecifique(name: String, angle: Angle, zeta: Double) = 
        directionDSL.addAngleSpecifique(name, angle, zeta)

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
        name: String,
        length: QtyD[Meter],
        elevation_gain: QtyD[Meter]
    ) = sectionDSL.addSectionSlopped(name, length, elevation_gain)

    def addSectionHorizontal(
        name: String,
        horizontal_length: QtyD[Meter]
    ) = sectionDSL.addSectionHorizontal(name, horizontal_length)

    def addSectionVertical(
        name: String,
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
        tt1: TypeTest[ThermalPipeDescr_13384, SetThermalPipeProp_13384], 
        tt2: TypeTest[ThermalPipeDescr_13384, AddThermalPipeElement_13384]
    ): ThermalIncrementalBuilder_13384 {
        // type PipeElDescr    = PipeElDescr0
        type PT             = PType
    } = 
        new ThermalIncrementalBuilder_13384:
            override given typeTestSetProp       : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement   : TypeTest[IncrDescr, AddElement]  = tt2
            type PT = PType
            given pt: PT = ptype

trait ThermalIncrementalPipeDefModule_13384[PType <: PipeType_EN13384] 
    extends IncrementalPipeDefModule_Common[PType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PipeElDescr = PipeElDescr0
        type PT = PType
    }
    
    type Params = DraftCondition
    
    override given hasLength: HasLength[PipeElDescr0] = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength