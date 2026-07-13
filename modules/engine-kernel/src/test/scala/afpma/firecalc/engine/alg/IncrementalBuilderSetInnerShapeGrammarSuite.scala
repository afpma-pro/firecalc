/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.{
    IsDirectionChange,
    IsLengthBearingPipeElement,
    IsSectionGeometryChange,
    SetsInnerShape,
    SetsNumberOfFlows
}
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.ShapeNotMaterialized.Operation
import afpma.firecalc.units.coulombutils.*

import cats.Show
import cats.data.*
import cats.syntax.all.*

import scala.reflect.TypeTest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IncrementalBuilderSetInnerShapeGrammarSuite extends AnyFlatSpec with Matchers:

    // ---- Test State with shapeMaterialized ----
    case class GrammarTestState(geometry: Option[PipeShape], shapeMaterialized: Boolean)

    // ---- Test SetProp types ----
    case class GrammarSetInnerShape(shape: PipeShape)      extends TestSetProp with SetsInnerShape
    case class GrammarSetRoughness(roughness: Length)      extends TestSetProp
    case class GrammarSetMaterial(material: String)        extends TestSetProp
    case class GrammarSetNumberOfFlows(n_flows: NbOfFlows) extends TestSetProp with SetsNumberOfFlows

    // ---- Test AddElement types ----
    case class GrammarSectionSlopped(name: String, length: Length)
        extends TestAddElement
        with IsLengthBearingPipeElement
    case class GrammarDirectionChange(name: String) extends TestAddElement with IsDirectionChange
    case class GrammarFlowResistance(name: String)  extends TestAddElement
    case class GrammarPressureDiff(name: String)    extends TestAddElement

    // ---- Test PipeElDescr ----
    case class GrammarElStraight(length: Length, geometry: PipeShape) extends TestPipeElDescr
    case class GrammarElZeroLength(label: String)                     extends TestPipeElDescr
    case class GrammarSectionGeometryChange(from: PipeShape, to: PipeShape)
        extends TestPipeElDescr
        with IsSectionGeometryChange

    given Show[TestPipeElDescr] = Show.show:
        case GrammarElStraight(l, g)            => s"GrammarElStraight($l, $g)"
        case GrammarElZeroLength(lbl)           => lbl
        case GrammarSectionGeometryChange(f, t) => s"GrammarSectionGeometryChange($f -> $t)"

    given HasLength[TestPipeElDescr] = new HasLength[TestPipeElDescr]:
        extension (a: TestPipeElDescr)
            def length: Length = a match
                case GrammarElStraight(l, _)            => l
                case GrammarElZeroLength(_)             => 0.meters
                case GrammarSectionGeometryChange(_, _) => 0.meters

    given HasVerticalElev[TestPipeElDescr] = new HasVerticalElev[TestPipeElDescr]:
        extension (a: TestPipeElDescr) def verticalElev: Length = 0.meters

    given HasInnerShapeAtPos[TestPipeElDescr] = new HasInnerShapeAtPos[TestPipeElDescr]:
        extension (a: TestPipeElDescr)
            def innerShape(oPrevShape: Option[PipeShape]): Option[PositionOp[PipeShape]] =
                a match
                    case GrammarElStraight(_, geom)          =>
                        QtyDAtPosition.constant(geom).some.map(_.atPos)
                    case GrammarSectionGeometryChange(_, to) =>
                        QtyDAtPosition.constant(to).some.map(_.atPos)
                    case GrammarElZeroLength(_)              =>
                        oPrevShape.map(prev => QtyDAtPosition.constant(prev).atPos)

    // ---- Traits for type hierarchy ----
    trait TestIncrDescr
    trait TestSetProp    extends TestIncrDescr
    trait TestAddElement extends TestIncrDescr:
        def name: String
    trait TestPipeElDescr

    class GrammarTestBuilder(
        override val pt: FluePipeT
    ) extends IncrementalBuilderAlg:

        override type IncrDescr            = TestIncrDescr
        override type SetProp              = TestSetProp
        override type AddElement           = TestAddElement
        override type PT                   = FluePipeT
        override type PipeElDescr          = TestPipeElDescr
        override protected type PropsState = GrammarTestState

        override type PreElementOp      = TestSetProp
        override type ChannelTopologyOp = TestSetProp
        override type PipeTrackingOp    = TestSetProp

        override given typeTestSetProp: TypeTest[TestIncrDescr, TestSetProp] =
            new TypeTest[TestIncrDescr, TestSetProp]:
                def unapply(u: TestIncrDescr): Option[u.type & TestSetProp] =
                    if u.isInstanceOf[TestSetProp] then Some(u.asInstanceOf[u.type & TestSetProp]) else None

        override given typeTestAddElement: TypeTest[TestIncrDescr, AddElement] =
            new TypeTest[TestIncrDescr, AddElement]:
                def unapply(u: TestIncrDescr): Option[u.type & AddElement] =
                    if u.isInstanceOf[AddElement] then Some(u.asInstanceOf[u.type & AddElement]) else None

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
            GrammarTestState(None, false)

        override protected def mkInitPipeFullDescr(iPipeIncrDescr: PipeIncrDescr): PipeFullDescr =
            PipeFullDescr(elements = Vector.empty, iPipeIncrDescr.pipeType)

        override protected def updateStateBeforeConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            val nextElemName = convStep.findNextAddElement.map(_._2.name).getOrElse("?")
            convStep.allPreElementOpsUntilNextAddElement
                .foldLeft(propsState.validNel[IncrementalValidation_Error]) { case (vState, (idIncr, setPropOp)) =>
                    setPropOp match
                        case GrammarSetInnerShape(shape) =>
                            vState.andThen { st =>
                                if !st.shapeMaterialized && st.geometry.isDefined then
                                    ShapeNotMaterialized(pt, Operation.SetInnerShape, idIncr, nextElemName).invalidNel
                                else st.copy(geometry = Some(shape), shapeMaterialized = false).validNel
                            }
                        case GrammarSetRoughness(_)      =>
                            vState // shape-independent
                        case GrammarSetMaterial(_) =>
                            vState // shape-independent
                        case GrammarSetNumberOfFlows(_) =>
                            vState.andThen { st =>
                                if !st.shapeMaterialized && st.geometry.isDefined then
                                    ShapeNotMaterialized(
                                        pt,
                                        Operation.SetNumberOfFlows,
                                        idIncr,
                                        nextElemName
                                    ).invalidNel
                                else st.validNel
                            }
                        case _                          => vState
                }

        override protected def updateStateAfterConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            convStep.findNextAddElement.map(_._2) match
                case Some(_: GrammarSectionSlopped) =>
                    propsState.copy(shapeMaterialized = true).validNel
                case _                              =>
                    propsState.validNel

        override protected def mkFullElementsDescr(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            val st = summon[PropsState]
            val (idIncr, addElementOp) = id_addElementOp
            val elIdx       = PipeIdx(prevs.elems.size)
            given NbOfFlows = NbOfFlows(1)
            addElementOp match
                case GrammarSectionSlopped(n, l) =>
                    val currentGeom    = st.geometry.get
                    val prevInnerGeomO = prevs.lastInnerGeom
                    prevInnerGeomO match
                        case None                =>
                            NonEmptyList.one((idIncr, GrammarElStraight(l, currentGeom).named(elIdx, pt, n))).validNel
                        case Some(prevInnerGeom) =>
                            // Direct shape comparison (like 15544), equivalent circles only for SectionGeometryChange
                            if prevInnerGeom == currentGeom then
                                NonEmptyList
                                    .one((idIncr, GrammarElStraight(l, currentGeom).named(elIdx, pt, n)))
                                    .validNel
                            else
                                val sectGeomCh = GrammarSectionGeometryChange(
                                    from = PipeShape.Circle(prevInnerGeom.dh),
                                    to   = PipeShape.Circle(currentGeom.dh)
                                )
                                NonEmptyList       (
                                    (idIncr, sectGeomCh.named(elIdx, pt, "section geometry change")       ),
                                    (idIncr, GrammarElStraight(l, currentGeom).named(elIdx.incr(1), pt, n)) :: Nil
                                ).validNel
                case GrammarDirectionChange(n)   =>
                    st.geometry match
                        case None    => DirectionChangeRequiresSectionGeometry(pt).invalidNel
                        case Some(_) =>
                            (!st.shapeMaterialized && st.geometry.isDefined) match
                                case true  =>
                                    ShapeNotMaterialized(
                                        pt,
                                        Operation.AddDirectionChange,
                                        idIncr.unwrap,
                                        addElementOp.name
                                    ).invalidNel
                                case false =>
                                    NonEmptyList.one((idIncr, GrammarElZeroLength(n).named(elIdx, pt, n))).validNel
                case GrammarFlowResistance(n)    =>
                    (!st.shapeMaterialized && st.geometry.isDefined) match
                        case true  =>
                            ShapeNotMaterialized(
                                pt,
                                Operation.AddFlowResistance,
                                idIncr.unwrap,
                                addElementOp.name
                            ).invalidNel
                        case false => NonEmptyList.one((idIncr, GrammarElZeroLength(n).named(elIdx, pt, n))).validNel
                case GrammarPressureDiff(n)      =>
                    (!st.shapeMaterialized && st.geometry.isDefined) match
                        case true  =>
                            ShapeNotMaterialized(
                                pt,
                                Operation.AddPressureDiff,
                                idIncr.unwrap,
                                addElementOp.name
                            ).invalidNel
                        case false => NonEmptyList.one((idIncr, GrammarElZeroLength(n).named(elIdx, pt, n))).validNel
                case _                           =>
                    sys.error(s"Unexpected grammar test add-element: $addElementOp")

        object ElementFactory extends ElementFactoryModule

    def newBuilder: GrammarTestBuilder = GrammarTestBuilder(pt)

    given pt: FluePipeT = FluePipeT

    // ========================================================================
    // Grammar validation tests (14)
    // ========================================================================

    "IncrementalBuilderAlg SetInnerShape grammar" should "accept first SetInnerShape at pipe start" in {
        // Test 1: First SetInnerShape at pipe start — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    it should "reject two consecutive SetInnerShape operations" in {
        // Test 2: Two consecutive SetInnerShape — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject same shape set twice without materialization" in {
        // Test 3: Same shape twice — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "accept SetInnerShape separated by length-bearing element" in {
        // Test 4: SetInnerShape separated by length-bearing element — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    it should "accept SetInnerShape then SetRoughness then section" in {
        // Test 5: SetInnerShape → SetRoughness → AddSectionSlopped — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetRoughness  (2.mm          ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    it should "reject SetInnerShape then SetNumberOfFlows" in {
        // Test 6: SetInnerShape → SetNumberOfFlows — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape   (PipeShape.Circle(100.mm)),
            GrammarSetNumberOfFlows(NbOfFlows(2)            ),
            GrammarSetInnerShape   (PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject merge SetInnerShape then SetNumberOfFlows(1) without materialization" in {
        // Test 6b: SetInnerShape → SetNumberOfFlows(2) → SetNumberOfFlows(1) without materialization.
        // The merge to 1 flow is blocked by the materialized-shape guard.
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape   (PipeShape.Circle(100.mm)),
            GrammarSetNumberOfFlows(NbOfFlows(2)            ),
            GrammarSetNumberOfFlows(NbOfFlows(1)            ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape then AddDirectionChange" in {
        // Test 7: SetInnerShape → AddDirectionChange — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarDirectionChange("bend"        ),
            GrammarSectionSlopped ("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape then AddFlowResistance" in {
        // Test 8: SetInnerShape → AddFlowResistance — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarFlowResistance("resistance"  ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape then AddPressureDiff" in {
        // Test 9: SetInnerShape → AddPressureDiff — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarPressureDiff  ("pressure"    ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "accept correct split pattern with materialized shape before SetNumberOfFlows" in {
        // Test 9: SetInnerShape → AddSectionSlopped → SetNumberOfFlows → SetInnerShape — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape   (PipeShape.Circle(100.mm)),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetNumberOfFlows(NbOfFlows(2)            ),
            GrammarSetInnerShape   (PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    it should "reject three consecutive SetInnerShape on the second operation" in {
        // Test 10: Three consecutive SetInnerShape — rejected on second
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSetInnerShape(PipeShape.Circle(200.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape then AddDirectionChange then SetInnerShape" in {
        // Test 11: SetInnerShape → AddDirectionChange → SetInnerShape → AddSectionSlopped — rejected
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarDirectionChange("bend"        ),
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSectionSlopped ("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "accept SetInnerShape then SetMaterial then section" in {
        // Test 12: SetInnerShape → SetMaterial → AddSectionSlopped — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetMaterial   ("steel"       ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    it should "accept a sequence of properties before section" in {
        // Test 13: SetInnerShape → SetRoughness → SetMaterial → AddSectionSlopped — accepted
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetRoughness  (2.mm          ),
            GrammarSetMaterial   ("steel"       ),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid shouldBe true
    }

    // ========================================================================
    // Auto-insertion tests (4)
    // ========================================================================

    it should "auto-insert SectionGeometryChange with equivalent circles when shape changes between sections" in {
        // Test 14: Auto-insertion — SectionGeometryChange with equivalent circle from/to (13384 requirement)
        // Comparison uses direct shape equality (like 15544), SectionGeometryChange uses equivalent circles
        val shapeA    = PipeShape.Circle(100.mm)
        val shapeB    = PipeShape.Circle(150.mm)
        val builder   = newBuilder
        val descr     = builder.define(
            GrammarSetInnerShape (shapeA        ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (shapeB        ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result    = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr = result.toEither.toOption.get._2
        val elems     = fullDescr.elems.map(_.el)

        // Should contain: StraightSection(A), SectionGeometryChange(equiv(A)->equiv(B)), StraightSection(B)
        elems should have size 3
        elems(0) shouldBe a[GrammarElStraight]
        elems(0).asInstanceOf[GrammarElStraight].geometry shouldBe shapeA
        elems(1) shouldBe a[GrammarSectionGeometryChange]
        val geomChange = elems(1).asInstanceOf[GrammarSectionGeometryChange]
        // SectionGeometryChange uses equivalent circles (13384 requirement)
        geomChange.from shouldBe PipeShape.Circle(shapeA.dh)
        geomChange.to shouldBe PipeShape.Circle(shapeB.dh)
        elems(2) shouldBe a[GrammarElStraight]
        elems(2).asInstanceOf[GrammarElStraight].geometry shouldBe shapeB
    }

    it should "auto-insert SectionGeometryChange with equivalent circles for Circle shapes (13384-style)" in {
        // Test 15: Builder auto-insertion with Circle shapes — equivalent circles are the shapes themselves
        val shapeA    = PipeShape.Circle(200.mm)
        val shapeB    = PipeShape.Circle(150.mm)
        val builder   = newBuilder
        val descr     = builder.define(
            GrammarSetInnerShape (shapeA        ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (shapeB        ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result    = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr = result.toEither.toOption.get._2
        val elems     = fullDescr.elems.map(_.el)

        // Should contain: StraightSection(A), SectionGeometryChange(equiv(A)->equiv(B)), StraightSection(B)
        // For Circle shapes, dh = diameter, so equivalent circles are the shapes themselves
        elems should have size 3
        elems(1) shouldBe a[GrammarSectionGeometryChange]
        val geomChange = elems(1).asInstanceOf[GrammarSectionGeometryChange]

        // 13384-style: SectionGeometryChange uses equivalent circles
        geomChange.from shouldBe PipeShape.Circle(shapeA.dh)
        geomChange.to shouldBe PipeShape.Circle(shapeB.dh)

        // Shape decrease: from area > to area
        shapeA.area.value shouldBe >(shapeB.area.value)
    }

    it should "auto-insert SectionGeometryChange with equivalent circles for Square shapes (13384-style)" in {
        // Test 16: Builder auto-insertion with Square shapes — equivalent circle via dh
        val squareShape = PipeShape.Square(200.mm)
        val circleShape = PipeShape.Circle(150.mm)
        val builder     = newBuilder
        val descr       = builder.define(
            GrammarSetInnerShape (squareShape   ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (circleShape   ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr   = result.toEither.toOption.get._2
        val elems       = fullDescr.elems.map(_.el)

        // Should contain: StraightSection(Square), SectionGeometryChange(equiv(Square)->equiv(Circle)), StraightSection(Circle)
        elems should have size 3
        elems(0) shouldBe a[GrammarElStraight]
        elems(0).asInstanceOf[GrammarElStraight].geometry shouldBe squareShape
        elems(1) shouldBe a[GrammarSectionGeometryChange]
        val geomChange = elems(1).asInstanceOf[GrammarSectionGeometryChange]

        // 13384-style: SectionGeometryChange uses equivalent circles, NOT actual shapes
        // Square(200mm): dh = 4*A/P = 4*(0.2*0.2)/(4*0.2) = 0.2m = 200mm
        // So equivalent circle for Square(200mm) is Circle(200mm)
        geomChange.from shouldBe PipeShape.Circle(squareShape.dh) // Circle(200mm), NOT Square(200mm)
        geomChange.to shouldBe PipeShape.Circle(circleShape.dh) // Circle(150mm)

        // Verify shape decrease: Square(200mm) area = 0.04 m² > Circle(150mm) area ≈ 0.0177 m²
        squareShape.area.value shouldBe >(circleShape.area.value)
    }

    it should "auto-insert SectionGeometryChange with equivalent circles for Rectangle shapes" in {
        // Test 17: Builder auto-insertion with Rectangle shapes — equivalent circle via dh
        val rectShape   = PipeShape.Rectangle(180.mm, 90.mm)
        val circleShape = PipeShape.Circle(150.mm)
        val builder     = newBuilder
        val descr       = builder.define(
            GrammarSetInnerShape (rectShape     ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (circleShape   ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr   = result.toEither.toOption.get._2
        val elems       = fullDescr.elems.map(_.el)

        // Should contain: StraightSection(Rectangle), SectionGeometryChange(equiv(Rectangle)->equiv(Circle)), StraightSection(Circle)
        elems should have size 3
        elems(0) shouldBe a[GrammarElStraight]
        elems(0).asInstanceOf[GrammarElStraight].geometry shouldBe rectShape
        elems(1) shouldBe a[GrammarSectionGeometryChange]
        val geomChange = elems(1).asInstanceOf[GrammarSectionGeometryChange]

        // SectionGeometryChange uses equivalent circles (13384 requirement)
        // Rectangle(180x90mm): dh = 4*A/P = 4*(0.18*0.09)/(2*(0.18+0.09)) = 4*0.0162/0.54 = 0.12m = 120mm
        // So equivalent circle for Rectangle(180x90mm) is Circle(120mm)
        geomChange.from shouldBe PipeShape.Circle(rectShape.dh) // Circle(120mm), NOT Rectangle(180x90mm)
        geomChange.to shouldBe PipeShape.Circle(circleShape.dh) // Circle(150mm)

        // Verify shape increase: Rectangle(180x90mm) area = 0.0162 m² < Circle(150mm) area ≈ 0.0177 m²
        rectShape.area.value shouldBe <(circleShape.area.value)
    }

    it should "auto-insert SectionGeometryChange when same dh but different shape (H1 fix: direct shape comparison)" in {
        // Test 18: Same dh, different shape — MUST insert SectionGeometryChange
        // Square(200mm): dh = 200mm, Circle(200mm): dh = 200mm
        // Equivalent circle areas are the same, but actual shapes differ
        // Before H1 fix: equivalent-circle comparison would NOT insert (BUG)
        // After H1 fix: direct shape comparison DOES insert (CORRECT)
        val squareShape = PipeShape.Square(200.mm)
        val circleShape = PipeShape.Circle(200.mm)
        val builder     = newBuilder
        val descr       = builder.define(
            GrammarSetInnerShape (squareShape   ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (circleShape   ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result      = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr   = result.toEither.toOption.get._2
        val elems       = fullDescr.elems.map(_.el)

        // MUST contain SectionGeometryChange because shapes differ
        elems should have size 3
        elems(0) shouldBe a[GrammarElStraight]
        elems(0).asInstanceOf[GrammarElStraight].geometry shouldBe squareShape
        elems(1) shouldBe a[GrammarSectionGeometryChange]
        val geomChange = elems(1).asInstanceOf[GrammarSectionGeometryChange]
        // Both have dh=200mm, so equivalent circles are Circle(200mm)
        geomChange.from shouldBe PipeShape.Circle(squareShape.dh) // Circle(200mm)
        geomChange.to shouldBe PipeShape.Circle(circleShape.dh) // Circle(200mm)
        elems(2) shouldBe a[GrammarElStraight]
        elems(2).asInstanceOf[GrammarElStraight].geometry shouldBe circleShape

        // Verify: same dh (approximately) but different actual shapes
        Math.abs(squareShape.dh.value - circleShape.dh.value) shouldBe <(0.001)
        squareShape should not equal circleShape
    }

    it should "NOT auto-insert SectionGeometryChange when same shape (direct shape comparison)" in {
        // Test 19: Same shape — NO SectionGeometryChange
        val shape     = PipeShape.Circle(100.mm)
        val builder   = newBuilder
        val descr     = builder.define(
            GrammarSetInnerShape (shape         ),
            GrammarSectionSlopped("s1", 1.meters),
            GrammarSetInnerShape (shape         ),
            GrammarSectionSlopped("s2", 1.meters)
        )
        val result    = descr.toFullDescr()
        result.isValid shouldBe true
        val fullDescr = result.toEither.toOption.get._2
        val elems     = fullDescr.elems.map(_.el)

        // Only 2 straight sections, NO SectionGeometryChange
        elems should have size 2
        elems(0) shouldBe a[GrammarElStraight]
        elems(1) shouldBe a[GrammarElStraight]
    }

    // ========================================================================
    // Slot boundary semantics tests (4 tests)
    // ========================================================================

    it should "not carry shapeMaterialized across slot boundary — seed has no shapeMaterialized field" in {
        // Test 20: shapeMaterialized is NOT carried across slot boundaries.
        // PipeBuildSeed carries only frame and nFlows — no shapeMaterialized.
        // Each slot starts fresh with shapeMaterialized = false.
        val builder = newBuilder

        // Slot 1: materializes a shape
        val slot1Descr  = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        val slot1Result = slot1Descr.toFullDescrWithSeed(PipeBuildSeed(None, NbOfFlows(1), None))
        slot1Result.isValid shouldBe true
        val nextSeed    = slot1Result.toEither.toOption.get._3
        // Seed carries only frame and nFlows — no shapeMaterialized
        nextSeed.nFlows shouldBe NbOfFlows(1)
    }

    it should "accept first SetInnerShape in a new slot" in {
        // Test 22: First SetInnerShape in a new slot is always accepted because geometry=None.
        // The "First Shape" exception: no previous shape to materialize.
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        // Simulate slot 2: starts fresh, no shape set yet
        val seed    = PipeBuildSeed(None, NbOfFlows(1), None)
        val result  = descr.toFullDescrWithSeed(seed)
        // First SetInnerShape accepted because geometry=None ("First Shape" exception)
        result.isValid shouldBe true
    }

    it should "reject second SetInnerShape in same slot after first was accepted" in {
        // Test 23: After the first SetInnerShape, shapeMaterialized is reset to false.
        // A second SetInnerShape in the same slot should be rejected.
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSetInnerShape(PipeShape.Circle(150.mm)),
            GrammarSectionSlopped("s1", 1.meters)
        )
        // New slot: starts fresh
        val seed    = PipeBuildSeed(None, NbOfFlows(1), None)
        val result  = descr.toFullDescrWithSeed(seed)
        // First SetInnerShape accepted (geometry=None), second rejected (shapeMaterialized=false, geometry defined)
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "reject AddDirectionChange at slot boundary when no shape set in current slot" in {
        // Test 24: AddDirectionChange at a slot boundary with no SetInnerShape in the current slot.
        // The factory rejects with DirectionChangeRequiresSectionGeometry because geometry=None.
        val builder = newBuilder
        val descr   = builder.define(
            GrammarDirectionChange("bend"        ),
            GrammarSectionSlopped ("s1", 1.meters)
        )
        val seed    = PipeBuildSeed(None, NbOfFlows(1), None)
        val result  = descr.toFullDescrWithSeed(seed)
        // AddDirectionChange rejected because no geometry
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[DirectionChangeRequiresSectionGeometry]
    }

    it should "reject AddDirectionChange after SetInnerShape without length-bearing element" in {
        // Test 25: SetInnerShape → AddDirectionChange in the same slot — rejected by guard.
        // The shape was set but not materialized (no length-bearing element before the bend).
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarDirectionChange("bend"        ),
            GrammarSectionSlopped ("s1", 1.meters)
        )
        val seed    = PipeBuildSeed(None, NbOfFlows(1), None)
        val result  = descr.toFullDescrWithSeed(seed)
        // AddDirectionChange rejected because shape not materialized
        result.isValid shouldBe false
        val errors  = result.toEither.left.toOption.get
        errors.head shouldBe a[ShapeNotMaterialized]
    }

    it should "accept AddDirectionChange after SetInnerShape and length-bearing element" in {
        // Test 26: SetInnerShape → AddSectionSlopped → AddDirectionChange — accepted.
        // The shape was materialized by the length-bearing element before the bend.
        val builder = newBuilder
        val descr   = builder.define(
            GrammarSetInnerShape(PipeShape.Circle(100.mm)),
            GrammarSectionSlopped ("s1", 1.meters),
            GrammarDirectionChange("bend"        )
        )
        val seed    = PipeBuildSeed(None, NbOfFlows(1), None)
        val result  = descr.toFullDescrWithSeed(seed)
        // Accepted: shape was materialized before the bend
        result.isValid shouldBe true
    }
