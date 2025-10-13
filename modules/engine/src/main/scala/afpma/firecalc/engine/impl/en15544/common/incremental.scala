/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import scala.annotation.nowarn
import scala.reflect.*

import cats.Show
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.i18n.I

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.softwaremill.quicklens.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import magnolia1.Transl
import scala.annotation.targetName

import afpma.firecalc.engine.models.en15544.pipedescr.*

trait IncrementalBuilder extends IncrementalBuilderAlg:

    import AddElement_15544.*
    import SetProp_15544.*

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] = afpma.firecalc.engine.models.en15544.pipedescr.hasInnerShapeAtPos
    override given hasLength: HasLength[PipeElDescr] = afpma.firecalc.engine.models.en15544.pipedescr.hasLength
    override given hasVerticalElev: HasVerticalElev[PipeElDescr] = afpma.firecalc.engine.models.en15544.pipedescr.hasVerticalElev
    given showPipeElDescr: Show[PipeElDescr] = afpma.firecalc.engine.models.en15544.pipedescr.showPipeElDescr

    export afpma.firecalc.engine.models.en15544.pipedescr.StraightSection
    export afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange

    override type PipeElDescr = afpma.firecalc.engine.models.en15544.pipedescr.PipeElDescr

    override type IncrDescr = IncrDescr_15544
    override type SetProp = SetProp_15544
    override type AddElement = AddElement_15544

    extension (addElement: AddElement) 
        override def name: String = addElement.name

    override type PT <: PipeType_EN15544

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)
    
    override case class PropsState(
        geometry: Option[PipeShape] = None,
        roughness: Option[Roughness] = None,
        nFlows: Option[NbOfFlows] = Some(1.flow)
    )

    given nbOfFlowsFromPropsState: (ps: PropsState) => NbOfFlows = ps.nFlows.get

    extension (propsState: PropsState)
        override def isValid: Boolean = 
            val t = Tuple.fromProductTyped(propsState)
            t.toList.forall(_.isDefined)
        def nf: NbOfFlows = 
            propsState.nFlows.get
    
    extension (piDescr: PipeIncrDescr)
        def listIncrDescr(): Vector[Id_IncrDescr] = piDescr.idescrs

    override protected def mkInitPropsState(iPipeIncrDescr: PipeIncrDescr): PropsState = 
        PropsState()

    override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr = 
        PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

    extension (convStep: ConversionStep)
        def nextSectionLengthOpt: Option[Length] = 
            convStep.nextOpIfAddElement.map(_._2).flatMap:
                case _ @ AddSectionSlopped(_, l, _)        => l.some
                case _ @ AddSectionHorizontal(_, l) => l.some
                case _ @ AddSectionVertical(_, l)   => l.some
                case _: ( AddSectionShapeChange | AddDirectionChange | AddFlowResistance | AddPressureDiff ) => None
    
    override protected def mkFullElementsDescr(
        prevs: PipeFullDescr,
        convStep: ConversionStep,
    )(
        id_addElementOp: (IdIncr, AddElement)
    ): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
        val (idIncr, addElementOp) = id_addElementOp
        val elIdx = PipeIdx(prevs.elems.size)
        extension (vnelEl: VNelString[PipeElDescr]) def asNonEmptyList = vnelEl.map(el => NonEmptyList.one((elIdx, None, el)))
        val prevInnerGeomO = prevs.lastInnerGeom
        val vels: VNelString[NonEmptyList[(PipeIdx, Option[String], PipeElDescr)]] = addElementOp match
            case op: (AddSectionSlopped | AddSectionHorizontal | AddSectionVertical) =>                 
                ElementFactory.mkStraightSection2(op).andThen: s =>
                    prevInnerGeomO match
                        case None                                                 => NonEmptyList.one((elIdx, None, s)).validNel
                        case Some(prevInnerGeom) if (prevInnerGeom == s.geometry) => NonEmptyList.one((elIdx, None, s)).validNel
                        case Some(prevInnerGeom) => 
                            val sectGeomCh = SectionGeometryChange(from = prevInnerGeom, to = s.geometry)
                            NonEmptyList((elIdx, Some("sect° geom change"), sectGeomCh), (elIdx.incr(1), None, s) :: Nil).validNel[String]
            case op: AddDirectionChange => 
                ElementFactory.mkDirectionChange(convStep.nextSectionLengthOpt)(op).asNonEmptyList
            case op: AddSectionShapeChange =>
                val setPropsUntilNextGeom = convStep.allSetPropsUntilNextAddElement.toList
                ElementFactory.mkSectionGeometryChange(setPropsUntilNextGeom)(op).asNonEmptyList
            case op: AddFlowResistance =>
                ElementFactory.mkSingularFlowResistance(op).asNonEmptyList
            case op: AddPressureDiff =>
                ElementFactory.mkPressureDiff(op).asNonEmptyList
        vels.map(_.map: (idx, newNameO, el) =>
            (idIncr, el.named(idx, pt, newNameO.getOrElse(addElementOp.name))))
        
    override protected def updateStateAfterConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState] =
        convStep.findNextAddElement.map(_._2) match
            case None                                   => propsState.validNel
            case Some(_ @ AddSectionSlopped(_, _, _))           => propsState.validNel
            case Some(_ @ AddSectionHorizontal(_, _))    => propsState.validNel
            case Some(_ @ AddSectionVertical(_, _))      => propsState.validNel
            case Some(_: AddDirectionChange)            => propsState.validNel
            case Some(_ @ AddFlowResistance(_, _, _))   => propsState.validNel
            case Some(_ @ AddPressureDiff(_, _))        => propsState.validNel
            case Some(obj: AddSectionShapeChange)    => propsState.modify(_.geometry).setTo(obj.to_shape.some).validNel

    override protected def updateStateBeforeConversionStep(
        propsState: PropsState,
        convStep: ConversionStep
    ): ValidatedResult[PropsState] =
        convStep
            .allSetPropsUntilNextAddElement
            .foldLeft(propsState.validNel) { case (vState, (_, setPropOp)) =>
                setPropOp match
                    case SetInnerShape(g) =>
                        vState.map(_.modify(_.geometry).setTo(g.some))
                    case SetRoughness(r) =>
                        vState.map(_.modify(_.roughness).setTo(r.some))
                    case SetMaterial(lm) =>
                        vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                    case SetNumberOfFlows(nf) =>
                        vState.map(_.modify(_.nFlows).setTo(nf.some))
            }

    object ElementFactory extends ElementFactoryModule {

        val mkStraightSection2: MakeFor[AddSectionSlopped | AddSectionHorizontal | AddSectionVertical, StraightSection] = { op =>
            val vg = ctxState.getValidated(_.geometry, s"geometry should be set ('${op.name}')")
            val vr = ctxState.getValidated(_.roughness, s"roughness should be set ('${op.name}')")

            val (len, elev_gain) = op match
                case AddSectionSlopped(_, len, elev_gain)    => (len        , elev_gain)
                case AddSectionHorizontal(_, len) => (len        , 0.0.meters)
                case AddSectionVertical(_, elev_gain) => 
                    val len = if (elev_gain < 0.meters) -elev_gain else elev_gain
                    (len, elev_gain)

            (vg, vr).mapN { (g, r) =>
                StraightSection(
                    length = len,
                    geometry = g,
                    roughness = r,
                    elevation_gain = elev_gain,
                )
            }
        }

        def mkDirectionChange(
            @nowarn nextSectionLength: Option[Length]
        ): MakeFor[AddDirectionChange, DirectionChange] = 
            op =>
                ctxState.getValidated(_.geometry.map(_.dh), "section geometry should be defined before adding a change in direction")
                    .map: _ =>
                        op match
                            case AddSharpeAngle_0_to_180(_, angle, angleN2) => 
                                DirectionChange.AngleVifDe0A180(angle, angleN2)
                            case AddCircularArc_60(_) => 
                                DirectionChange.CircularArc60
            
        def mkSectionGeometryChange(
            setProps: List[(Int, SetProp)]
        ): MakeFor[AddSectionShapeChange, SectionGeometryChange] = 
            op =>
                // make sure we don't have some geometry change in atomic modifiers
                // otherwise it means we have two ways to change geometry that was specified by user
                val sectionGeometryChanged = setProps.exists {
                    case (_, _: SetInnerShape) => true
                    case (_, _) => false
                }

                if (sectionGeometryChanged)
                    "you can not 'set' a specific section geometry, right before defining a specific 'change' in geometry".invalid.toValidatedNel
                else
                    ctxState.getValidated(_.geometry, "section geometry should be defined")
                        .map: fromGeom =>
                            SectionGeometryChange(
                                from = fromGeom,
                                to = op.to_shape
                            )
                        
        val mkSingularFlowResistance: MakeFor[AddFlowResistance, SingularFlowResistance] = 
            op => op match
                case AddFlowResistance(name, zeta, NoneOfEither) =>
                    ctxState
                        .getValidated(_.geometry, 
                            s"geometry unknown: can not define singular flow resistance '${op.name}' unless cross section area is defined manually: use 'addFlowResistance(name, zeta, crossSectionArea)'")
                        .andThen: geom =>
                            SingularFlowResistance(zeta, crossSectionO = Some(geom.area)).validNel
                case AddFlowResistance(name, zeta, SomeLeft(area)) =>
                    SingularFlowResistance(zeta, Some(area.toUnit[Meter ^ 2])).validNel
                case AddFlowResistance(name, zeta, SomeRight(geom)) =>
                    SingularFlowResistance(zeta, Some(geom.area)).validNel

        val mkPressureDiff: MakeFor[AddPressureDiff, PressureDiff] = 
            op => PressureDiff(pa = op.pressure_difference).validNel
    }


    // builder methods for Atomic modifiers

    def innerShape(shape: PipeShape) =
        SetInnerShape(shape)
    def roughness(r: Roughness) = 
        SetRoughness(r)

    def material(material: Material_15544) =
        SetMaterial(material)

    def channelsSplit(n: Int) = 
        SetNumberOfFlows(n.flows)

    def channelsJoin() =
        SetNumberOfFlows(1.flow)

    // builder methods for obj modifiers

    def addSharpAngle_0_to_180deg(name: String, angle: Angle, angleN2: Option[Angle] = None) = AddSharpeAngle_0_to_180(name, angle, angleN2)
    def addSharpAngle_30deg(name: String, angleN2: Option[Angle] = None) = AddSharpeAngle_0_to_180(name, 30.degrees, angleN2)
    def addSharpAngle_45deg(name: String, angleN2: Option[Angle] = None) = AddSharpeAngle_0_to_180(name, 45.degrees, angleN2)
    def addSharpAngle_60deg(name: String, angleN2: Option[Angle] = None) = AddSharpeAngle_0_to_180(name, 60.degrees, angleN2)
    def addSharpAngle_90deg(name: String, angleN2: Option[Angle] = None) = AddSharpeAngle_0_to_180(name, 90.degrees, angleN2)

    def addCircularArc60(name: String)          = AddCircularArc_60(name)

    def addSectionShapeChange(
        name: String,
        to_shape: PipeShape
    ) = AddSectionShapeChange(name, to_shape)

    def addSectionSlopped(
        name: String,
        length: Length,
        elevation_gain: Length
    ) = AddSectionSlopped(name, length, elevation_gain)

    def addSectionHorizontal(
        name: String,
        horizontal_length: Length
    ) = AddSectionHorizontal(name, horizontal_length)

    def addSectionVertical(
        name: String,
        elevation_gain: Length
    ) = AddSectionVertical(name, elevation_gain)

    def addPressureDiff(name: String, pressure_difference: Pressure) =
        AddPressureDiff(name, pressure_difference)

    def addFlowResistance(name: String, zeta: ζ) =
        AddFlowResistance(name, zeta, cross_section = None)

    @targetName("addFlowResistance_crossSection")
    def addFlowResistance(name: String, zeta: ζ, cross_section: AreaInCm2) =
        AddFlowResistance(name, zeta, cross_section = cross_section.asLeft.some)

    @targetName("addFlowResistance_dh")
    def addFlowResistance(name: String, zeta: ζ, hydraulic_diameter: Length) =
        AddFlowResistance(name, zeta, PipeShape.circle(hydraulic_diameter).asRight.some)

object IncrementalBuilder:

    def makeFor[PType <: PipeType_EN15544](using
        ptype: PType,
        tt1: TypeTest[IncrDescr_15544, SetProp_15544], 
        tt2: TypeTest[IncrDescr_15544, AddElement_15544]
    ): IncrementalBuilder {
        type PT             = PType
    } = 
        new IncrementalBuilder:
            override given typeTestSetProp       : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement   : TypeTest[IncrDescr, AddElement]  = tt2
            type PT = PType
            given pt: PT = ptype

// usage: ```object FluePipe extends IncrementalPipeDef[FluePipeT, FluePipe]```
trait IncrementalPipeDefModule[PipeType <: PipeType_EN15544]
    extends IncrementalPipeDefModule_Common[PipeType]:
    
    type PipeElDescr0 = afpma.firecalc.engine.models.en15544.pipedescr.PipeElDescr

    type _IncrementalBuilder = IncrementalBuilder {
        type PipeElDescr = PipeElDescr0
        type PT = PipeType
    }
    
    type Params = DraftCondition

    override given hasLength: HasLength[PipeElDescr] = afpma.firecalc.engine.models.en15544.pipedescr.hasLength