/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import scala.annotation.targetName
import scala.reflect.*

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*

import afpma.firecalc.i18n.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.softwaremill.quicklens.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import magnolia1.Transl

object models:

    final type Id_IncrDescr_13384 = (Int, IncrDescr_13384)

    type PipeIncrDescr = PipeIncrDescrG[Id_IncrDescr_13384]

import afpma.firecalc.engine.models.en13384.pipedescr.*

trait IncrementalBuilder extends IncrementalBuilderAlg:

    import AddElement_13384.*
    import SetProp_13384.*

    override given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] = afpma.firecalc.engine.models.en13384.pipedescr.hasInnerShapeAtPos
    override given hasLength: HasLength[PipeElDescr] = afpma.firecalc.engine.models.en13384.pipedescr.hasLength
    override given hasVerticalElev: HasVerticalElev[PipeElDescr] = afpma.firecalc.engine.models.en13384.pipedescr.hasVerticalElev
    // export afpma.firecalc.engine.models.en13384.pipedescr.showPipeElDescr

    export afpma.firecalc.engine.models.en13384.pipedescr.StraightSection

    override type PipeElDescr = afpma.firecalc.engine.models.en13384.pipedescr.PipeElDescr

    override type IncrDescr = IncrDescr_13384
    override type SetProp = SetProp_13384
    override type AddElement = AddElement_13384

    extension (addElement: AddElement) 
        override def name: String = addElement.name

    override type PT <: PipeType_EN13384

    override def define(iDescrs: IncrDescr*): PipeIncrDescr =
        val iiVec = iDescrs.toVector.mapWithIndex((x, i) => (IdIncr(i), x))
        PipeIncrDescrG[Id_IncrDescr](pt, iiVec)

    override protected case class PropsState(
        innerShape           : Option[PipeShape] = None,
        outer_shape          : Option[PipeShape] = None,
        roughness            : Option[Roughness] = None,
        layers               : Option[List[AppendLayerDescr]] = None,
        airSpace_afterLayers : Option[AirSpaceDetailed] = Some(AirSpaceDetailed.WithoutAirSpace),
        pipeLoc              : Option[PipeLocation] = None,
        ductType             : Option[DuctType] = None,
        nFlows               : Option[NbOfFlows] = Some(1.flow)
    )

    given nbOfFlowsFromPropsState: (ps: PropsState) => NbOfFlows = ps.nFlows.get

    extension (propsState: PropsState)
        override def isValid: Boolean = 
            val t = Tuple.fromProductTyped(propsState)
            t.toList.forall(_.isDefined)
        def nf: NbOfFlows = 
            propsState.nFlows.get

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
        PropsState(
            // by default, use non concentrict air ducts (see EN 13384 7.8.1)
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
        val (idIncr, addElementOp) = id_addElementOp
        val el = addElementOp match
            case op: (AddSectionSlopped | AddSectionHorizontal | AddSectionVertical) => 
                ElementFactory.mkStraightSection(op)
            case op: AddDirectionChange =>
                ElementFactory.mkDirectionChange(convStep.nextSectionLengthOpt)(op)
            case op: AddSectionChange =>
                val setPropsUntilNextGeom = convStep.allSetPropsUntilNextAddElement.toList
                ElementFactory.mkSectionGeometryChange(setPropsUntilNextGeom)(op)
            case op: AddFlowResistance =>
                ElementFactory.mkSingularFlowResistance(op)
            case op: AddPressureDiff =>
                ElementFactory.mkPressureDiff(op)
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
                                case None     => s"thickness can not be defined before inner geometry".invalidNel
                                case Some(ig) => v.modify(_.outer_shape).setTo(ig.expandGeomWithThickness(t).some).validNel
                    case SetRoughness(r) =>
                        vState.map(_.modify(_.roughness).setTo(r.some))
                    case SetMaterial(lm) =>
                        vState.map(_.modify(_.roughness).setTo(lm.roughness.some))
                    case SetLayer(e, lambda) =>
                        val vGeom = vState.andThen(_
                            .getValidated(
                                _.innerShape,
                                "before defining 'layer', you should set 'section geometry'"))
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
                                "before defining any 'layers', you should set 'innerShape'"))

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
    object ElementFactory extends ElementFactoryModule {

        val mkStraightSection: MakeFor[AddSectionSlopped | AddSectionHorizontal | AddSectionVertical, StraightSection] = { op =>
            val vig = ctxState.getValidated(_.innerShape, s"inner geometry should be set ('${op.name}')")
            val vog = ctxState.getValidated(_.outer_shape, s"outer geometry should be set ('${op.name}')")
            val vr = ctxState.getValidated(_.roughness, s"roughness should be set ('${op.name}')")
            val vlayers = ctxState.getValidated(_.layers, s"layers should be set ('${op.name}')")
            val vasp = ctxState.getValidated(_.airSpace_afterLayers, s"air space (after defined layers) should be set ('${op.name}')")
            val vpl = ctxState.getValidated(_.pipeLoc, s"pipe location should be set ('${op.name}')")
            val vduct = ctxState.getValidated(_.ductType, s"duct type should be set ('${op.name}')")

            val (len, elev_gain) = op match
                case AddSectionSlopped(_, len, elev_gain)    => (len, elev_gain)
                case AddSectionHorizontal(_, len) => (len, 0.0.m)
                case AddSectionVertical(_, elev_gain) => 
                    val len = if (elev_gain < 0.meters) -elev_gain else elev_gain
                    (len, elev_gain)

            (vig, vog, vr, vlayers, vasp, vpl, vduct)
                .mapN { (ig, og, r, layers, asp, pl, duct) =>
                    StraightSection(
                        length = len,
                        innerShape = ig,
                        outer_shape = og,
                        roughness = r,
                        layers = layers,
                        elevation_gain = elev_gain,
                        airSpaceDetailed = asp,
                        pipeLoc = pl,
                        ductType = duct
                    )
            }
        }

        def mkDirectionChange(nextSectionLength: Option[QtyD[Meter]]): MakeFor[AddDirectionChange, DirectionChange] = 
            op =>
                val vDh = ctxState.getValidated(_.innerShape.map(_.dh), "section geometry should be defined")
                val vLd = Validated.fromOption(nextSectionLength, ifNone = NonEmptyList.one("next section length should be defined"))
                (vDh, vLd).mapN: (dh, ld) =>
                    op match
                        case _ @ AddAngleAdjustable(name, angle, zeta)  => AngleSpecifique(angle, zeta)
                        case _ @ AddSharpeAngle_0_to_90(name, angle)         => AngleVifDe0A90(angle, Ld = ld, Dh = dh)
                        case _ @ AddSharpeAngle_0_to_90_Unsafe(name, angle)  => AngleVifDe0A90_Unsafe(angle, Ld = ld, Dh = dh)
                        case _ @ AddSmoothCurve_90(name, r)              => CoudeCourbe90(r, Ld = ld, Dh = dh)             
                        case _ @ AddSmoothCurve_90_Unsafe(name, r)       => CoudeCourbe90_Unsafe(r, Ld = ld, Dh = dh)             
                        case _ @ AddSmoothCurve_60(name, r)              => CoudeCourbe60(r, Ld = ld, Dh = dh)             
                        case _ @ AddSmoothCurve_60_Unsafe(name, r)       => CoudeCourbe60_Unsafe(r, Ld = ld, Dh = dh)             
                        case _ @ AddElbows_2x45(name, r)    => CoudeASegment90Avec2A45(r, Dh = dh)   
                        case _ @ AddElbows_3x30(name, r)    => CoudeASegment90Avec3A30(r, Dh = dh)   
                        case _ @ AddElbows_4x22p5(name, r)  => CoudeASegment90Avec4A22p5(r, Dh = dh) 

        def mkSectionGeometryChange(setProps: List[(Int, SetProp)]): MakeFor[AddSectionChange, SectionGeometryChange] = 
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
                    ctxState.getValidated(_.innerShape, "section geometry should be defined")
                        .andThen:
                            case fromCircleGeom: PipeShape.Circle => 
                                val sec = op match
                                    case AddSectionDecrease(_, diam) => 
                                        SectionDecrease.mkFromDiameters(fromD1 = fromCircleGeom.diameter, toD2 = diam)
                                    case AddSectionIncrease(_, diam) => 
                                        SectionIncrease.mkFromDiameters(fromD1 = fromCircleGeom.diameter, toD2 = diam)
                                    // case AddSectionDecreaseProgressive(_, diam, ɣ) => 
                                    //     SectionDecreaseProgressive(fromD1 = fromCircleGeom.diameter, toD2 = diam, ɣ = ɣ)
                                sec.validNel
                            case notACircleGeom: PipeShape => 
                                s"you can only change section geometry when current cross section is a circle (found $notACircleGeom)".invalid.toValidatedNel


        val mkSingularFlowResistance: MakeFor[AddFlowResistance, SingularFlowResistance] =
            op => op match
                case AddFlowResistance(name, zeta, NoneOfEither) =>
                    ctxState
                        .getValidated(_.innerShape, 
                            s"inner geometry unknown: can not define singular flow resistance '${op.name}' unless cross section area is defined manually: use 'addFlowResistance(name, zeta, crossSectionArea)'")
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

    def channelsSplit(n: Int) = 
        SetNumberOfFlows(n.flows)
    def channelsJoin() =
        SetNumberOfFlows(1.flow)

    // builder methods for obj modifiers

    def addAngleVifDe0A90(name: String, angle: QtyD[Degree]) = AddSharpeAngle_0_to_90(name, angle)
    def addSharpAngle_30deg(name: String) = addAngleVifDe0A90(name, 30.degrees)
    def addSharpAngle_45deg(name: String) = addAngleVifDe0A90(name, 45.degrees)
    def addSharpAngle_60deg(name: String) = addAngleVifDe0A90(name, 60.degrees)
    def addSharpAngle_90deg(name: String) = addAngleVifDe0A90(name, 90.degrees)

    // __INTERPRETATION__
    def addAngleVifDe0A90_unsafe(name: String, angle: QtyD[Degree]) = AddSharpeAngle_0_to_90_Unsafe(name, angle)
    def addSharpAngle_30deg_unsafe(name: String) = addAngleVifDe0A90_unsafe(name, 30.degrees)
    def addSharpAngle_45deg_unsafe(name: String) = addAngleVifDe0A90_unsafe(name, 45.degrees)
    def addSharpAngle_60deg_unsafe(name: String) = addAngleVifDe0A90_unsafe(name, 60.degrees)
    def addSharpAngle_90deg_unsafe(name: String) = addAngleVifDe0A90_unsafe(name, 90.degrees)


    def addCoudeCourbe90(name: String, R: QtyD[Meter])          = AddSmoothCurve_90(name, R)
    def addCoudeCourbe60(name: String, R: QtyD[Meter])          = AddSmoothCurve_60(name, R)

    // __INTERPRETATION__
    def addCoudeCourbe90_unsafe(name: String, R: QtyD[Meter])   = AddSmoothCurve_90_Unsafe(name, R)
    def addCoudeCourbe60_unsafe(name: String, R: QtyD[Meter])   = AddSmoothCurve_60_Unsafe(name, R)

    def addCoudeASegment90Avec2A45(name: String, R: QtyD[Meter])    = AddElbows_2x45(name, R)
    def addCoudeASegment90Avec3A30(name: String, R: QtyD[Meter])    = AddElbows_3x30(name, R)
    def addCoudeASegment90Avec4A22p5(name: String, R: QtyD[Meter])  =  AddElbows_4x22p5(name, R)

    def addAngleSpecifique(name: String, angle: Angle, zeta: Double)     = AddAngleAdjustable(name, angle, zeta.unitless)

    // def addSectionChange(
    //     name: String,
    //     to: PipeShape
    // ) = PipeUserModifier.Obj.AddSectionChange(name, to)
    def addSectionDecrease(name: String, toDiameter: QtyD[Meter]) = AddSectionDecrease(name, toDiameter)
    def addSectionIncrease(name: String, toDiameter: QtyD[Meter]) = AddSectionIncrease(name, toDiameter)
    // def addSectionDecreaseProgressive(name: String, toDiameter: QtyD[Meter], ɣ: QtyD[Degree]) = 
    //     AddSectionDecreaseProgressive(name, toDiameter, ɣ)


    def addSectionSlopped(
        name: String,
        length: QtyD[Meter],
        elevation_gain: QtyD[Meter]
    ) = AddSectionSlopped(name, length, elevation_gain)

    def addSectionHorizontal(
        name: String,
        horizontal_length: QtyD[Meter]
    ) = AddSectionHorizontal(name, horizontal_length)

    def addSectionVertical(
        name: String,
        elevation_gain: QtyD[Meter]
    ) = AddSectionVertical(name, elevation_gain)

    def addPressureDiff(name: String, pressure_difference: Pressure) =
        AddPressureDiff(name, pressure_difference)

    def addFlowResistance(name: String, zeta: QtyD[1]) =
        AddFlowResistance(name, zeta, cross_section = None)

    @targetName("addFlowResistance_crossSection")
    def addFlowResistance_crossSection(name: String, zeta: QtyD[1], cross_section: AreaInCm2) =
        AddFlowResistance(name, zeta, cross_section = cross_section.asLeft.some)
    
    @targetName("addFlowResistance_dh")
    def addFlowResistance(name: String, zeta: QtyD[1], hydraulic_diameter: Length) =
        AddFlowResistance(name, zeta, PipeShape.circle(hydraulic_diameter).asRight.some)

    /** add rain cap with H / Dh = 1.0 (meaning ζ = 1.0) according to Table B.8 */
    def addRainCapEN13384_withHeightEqualsDiameter(name: String) = 
        addFlowResistance(name, 1.0.unitless: ζ)

    /** add rain cap with H / Dh = 0.5 (meaning ζ = 1.5) according to Table B.8 */
    def addRainCapEN13384_withHeightEquals2Diameter(name: String) = 
        addFlowResistance(name, 1.5.unitless: ζ)

object IncrementalBuilder:

    def makeFor[PType <: PipeType_EN13384](using
        ptype: PType,
        tt1: TypeTest[IncrDescr_13384, SetProp_13384], 
        tt2: TypeTest[IncrDescr_13384, AddElement_13384]
    ): IncrementalBuilder {
        // type PipeElDescr    = PipeElDescr0
        type PT             = PType
    } = 
        new IncrementalBuilder:
            override given typeTestSetProp       : TypeTest[IncrDescr, SetProp]      = tt1
            override given typeTestAddElement   : TypeTest[IncrDescr, AddElement]  = tt2
            type PT = PType
            given pt: PT = ptype

// usage: ```object ConnectorPipe extends IncrementalPipeDef[ConnectorPipeT, ConnectorPipe]```
trait IncrementalPipeDefModule[PipeType /* <: PipeType_EN13384 */] 
    extends IncrementalPipeDefModule_Common[PipeType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.pipedescr.PipeElDescr

    type _IncrementalBuilder = IncrementalBuilder {
        type PipeElDescr = PipeElDescr0
        type PT = PipeType
    }
    
    type Params = DraftCondition
    
    override given hasLength: HasLength[PipeElDescr] = afpma.firecalc.engine.models.en13384.pipedescr.hasLength