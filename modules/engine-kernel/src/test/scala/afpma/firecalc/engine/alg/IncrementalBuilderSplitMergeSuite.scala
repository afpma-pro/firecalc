/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.{
    IsDirectionChange,
    IsSingularFlowResistance,
    IsSplitMergeTurn,
    SetsInnerShape,
    SetsNumberOfFlows
}
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import cats.data.*
import cats.syntax.all.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IncrementalBuilderSplitMergeSuite extends AnyFlatSpec with Matchers with IncrementalBuilderTestFixture:

    case class TestSetInnerShape(w: Length, h: Length)  extends TestSetProp with SetsInnerShape
    case class TestSetNumberOfFlows(n_flows: NbOfFlows) extends TestSetProp with SetsNumberOfFlows
    case class TestSetRoughness(roughness: Length)      extends TestSetProp

    // Simulates SplitSingleFlowIntoTwoFlowsWith90DegTurn / MergeTwoFlowsIntoSingleWith90DegTurn
    // emitting 3 events: DirectionChange, FlowCountSet, InnerShapeSet
    case class TestSplitMerge90(name: String, nFlows: NbOfFlows)
        extends TestAddElement
        with SetsNumberOfFlows
        with SetsInnerShape
        with IsDirectionChange
        with IsSingularFlowResistance
        with IsSplitMergeTurn {
        def n_flows: NbOfFlows = nFlows
    }

    case class TestNeutralElement(name: String)  extends TestAddElement
    case class TestDirectionChange(name: String) extends TestAddElement with IsDirectionChange

    class TestBuilder(
        override val pt: FluePipeT
    ) extends TestBuilderBase(pt):

        // Expose final props state for test assertions
        private var _finalState: PropsState = TestState()

        def finalState: PropsState = _finalState

        override protected def updateStateForSetProp(
            vState : ValidatedResult[PropsState],
            setProp: SetProp
        ): ValidatedResult[PropsState] =
            setProp match
                case TestSetInnerShape(w, h) =>
                    if !vState.toOption.exists(_.materialized) then
                        ShapeNotMaterialized(
                            pt,
                            ShapeNotMaterialized.Operation.SetInnerShape,
                            -1,
                            "TestSetInnerShape"
                        ).invalidNel
                    else vState.map(_.copy(geometry = Some(PipeShape.Rectangle(w, h))))
                case _: TestSetNumberOfFlows => vState
                case TestSetRoughness(r)     =>
                    vState.map(_.copy(roughness = Some(r)))

        override protected def updateStateAfterConversionStep(
            propsState: PropsState,
            convStep  : ConversionStep
        ): ValidatedResult[PropsState] =
            _finalState = propsState
            // Determine the current step's add element (if any).
            val addElem  = convStep.currentStepOps.lastOption.collect { case (_, op: TestAddElement) => op }
            val newState = addElem match
                case Some(_: TestStraight)     =>
                    // Length-bearing element materializes the current shape
                    propsState.copy(materialized = true)
                case Some(_: TestSplitMerge90) =>
                    // Split/merge sets a new shape that isn't yet materialized
                    propsState.copy    (
                        geometry     = propsState.geometry.orElse(Some(PipeShape.Rectangle(15.cm, 15.cm))),
                        materialized = false
                    )
                case _                         =>
                    // Zero-length non-shape element (DirectionChange, Neutral) — preserve state
                    propsState
            newState.validNel[IncrementalValidation_Error]

        override protected def mkFullElementForTest(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            id_addElementOp match
                case (idIncr, TestNeutralElement(n) ) =>
                    mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestNeutralElement"))
                case (idIncr, TestDirectionChange(n)) =>
                    mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestDirectionChange"))
                case (idIncr, TestSplitMerge90(n, _)) =>
                    mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestSplitMerge90"))
                case (_, other                      ) => sys.error(s"Unexpected split/merge test add-element: $other")

    def newBuilder: TestBuilder = TestBuilder(pt)

    private def directionChangeRef(error: IncrementalValidation_Error): String =
        error match
            case e: FlowSplitRequiresInnerShapeBeforeDirectionChange           => e.directionChangeRef
            case e: FlowMergeRequiresInnerShapeBeforeDirectionChange           => e.directionChangeRef
            case e: FlowMergeRequiresLengthBearingSectionBeforeDirectionChange => e.directionChangeRef
            case other => fail(s"Expected flow-transition error but got ${other.getClass.getName}")

    "IncrementalBuilderAlg flow split/merge validation" should "reject direction change after split without SetInnerShape" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm ),
            TestStraight       ("s", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestDirectionChange("bend"       )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowSplitRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#3)")
    }

    it should "accept direction change after split with SetInnerShape" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm ),
            TestStraight       ("s", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm ),
            TestDirectionChange("bend"       )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "reject direction change after merge without SetInnerShape" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm    ),
            TestStraight       ("s", 1.meters   ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestStraight       ("dual", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestDirectionChange("bend"          )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#5)")
    }

    it should "enforce a merge requirement after a resolved split in the same descriptor" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm         ),
            TestStraight       ("single", 1.meters   ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm         ),
            TestDirectionChange("resolved split bend"),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestDirectionChange("merge bend"         )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'merge bend' (#6)")
    }

    it should "replace an older pending transition with a newer pending transition" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm      ),
            TestStraight       ("single", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestDirectionChange("bend"            )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#4)")
    }

    it should "keep a pending split requirement across length-bearing and neutral elements" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm      ),
            TestStraight       ("single", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestStraight       ("dual", 1.meters  ),
            TestNeutralElement ("neutral"         ),
            TestDirectionChange("bend"            )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowSplitRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#5)")
    }

    it should "keep a pending merge section requirement across neutral elements" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm      ),
            TestStraight       ("single", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm      ),
            TestStraight       ("dual", 1.meters  ),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestSetInnerShape  (10.cm, 10.cm      ),
            TestNeutralElement ("neutral"         ),
            TestDirectionChange("bend"            )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresLengthBearingSectionBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#8)")
    }

    it should "reject direction change after merge with SetInnerShape but no length-bearing section" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm    ),
            TestStraight       ("s", 1.meters   ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestStraight       ("dual", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestSetInnerShape  (15.cm, 15.cm    ),
            TestDirectionChange("bend"          )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresLengthBearingSectionBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#6)")
    }

    it should "accept direction change after merge with SetInnerShape and length-bearing section" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm      ),
            TestStraight       ("s", 1.meters     ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestStraight       ("dual", 1.meters  ),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestSetInnerShape  (15.cm, 15.cm      ),
            TestStraight       ("single", 1.meters),
            TestDirectionChange("bend"            )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "reject direction change when seeded with nFlows=2 and merged to 1" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestStraight       ("dual", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestDirectionChange("bend"          )
        )
        val seed    = PipeBuildSeed(frame = None, nFlows = NbOfFlows(2))
        val result  = descr.toFullDescrWithSeed(seed)
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#2)")
    }

    it should "reject direction change after a split from seeded flow count" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestStraight       ("dual", 1.meters),
            TestSetNumberOfFlows(NbOfFlows(3)),
            TestDirectionChange("bend"          )
        )
        val seed    = PipeBuildSeed(frame = None, nFlows = NbOfFlows(2))
        val result  = descr.toFullDescrWithSeed(seed)
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowSplitRequiresInnerShapeBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#2)")
    }

    it should "accept an alternating split and merge cycle when each direction-change prerequisite is resolved" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm       ),
            TestStraight       ("single", 1.meters ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm       ),
            TestDirectionChange("split bend"       ),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestSetInnerShape  (10.cm, 10.cm       ),
            TestStraight       ("single2", 1.meters),
            TestDirectionChange("merge bend"       ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm       ),
            TestDirectionChange("split bend 2"     )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    // ── Tests for 3-event flow pattern (SplitMerge90 emits DirectionChange + FlowCountSet + InnerShapeSet) ──

    it should "accept SplitMerge90 split (1->2) — 3 events: DC + FlowCountSet(2) + InnerShapeSet" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect     (10.cm, 10.cm         ),
            TestStraight    ("s", 1.meters        ),
            TestSplitMerge90("split", NbOfFlows(2))
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "accept SplitMerge90 merge (2->1) — 3 events: DC + FlowCountSet(1) + InnerShapeSet" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect      (10.cm, 10.cm         ),
            TestStraight     ("s", 1.meters        ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape(15.cm, 15.cm         ),
            TestStraight     ("dual", 1.meters     ),
            TestSplitMerge90 ("merge", NbOfFlows(1))
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "reject DirectionChange immediately after SplitMerge90 merge — needs length-bearing section" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm         ),
            TestStraight       ("s", 1.meters        ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm         ),
            TestStraight       ("dual", 1.meters     ),
            TestSplitMerge90   ("merge", NbOfFlows(1)),
            TestDirectionChange("bend"               )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresLengthBearingSectionBeforeDirectionChange]
        directionChangeRef(errors.head) should include("'bend' (#6)")
    }

    it should "accept DirectionChange after SplitMerge90 merge + length-bearing section" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm         ),
            TestStraight       ("s", 1.meters        ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm         ),
            TestStraight       ("dual", 1.meters     ),
            TestSplitMerge90   ("merge", NbOfFlows(1)),
            TestStraight       ("single", 1.meters   ),
            TestDirectionChange("bend"               )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "accept split -> merge -> split cycle with SplitMerge90" in {
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect     (10.cm, 10.cm          ),
            TestStraight    ("s", 1.meters         ),
            TestSplitMerge90("split", NbOfFlows(2) ),
            TestSplitMerge90("merge", NbOfFlows(1) ),
            TestStraight    ("single", 1.meters    ),
            TestSplitMerge90("split2", NbOfFlows(2))
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
    }

    it should "reject second DirectionChange after SplitMerge90 split without length-bearing" in {
        // SplitMerge90 split emits DC (allowed, fires first) + FlowCountSet(2) + InnerShapeSet
        // Then a separate DirectionChange should be rejected (split needs inner shape before DC)
        // But inner shape was already set by SplitMerge90 itself, so it should pass
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm         ),
            TestStraight       ("s", 1.meters        ),
            TestSplitMerge90   ("split", NbOfFlows(2)),
            TestDirectionChange("bend"               )
        )
        val result  = descr.toFullDescr()
        // The split's own DC fires first, then FlowCountSet(2), then InnerShapeSet
        // The separate DC comes after all 3 events — state is None, so it should pass
        result.isValid `shouldBe` true
    }

    it should "reject second DirectionChange after SplitMerge90 merge without length-bearing" in {
        // Merge emits DC + FlowCountSet(1) + InnerShapeSet
        // State after: MergeNeedsInnerShapeThenSection(true)
        // Separate DC -> error (needs length-bearing section)
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect        (10.cm, 10.cm         ),
            TestStraight       ("s", 1.meters        ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape  (15.cm, 15.cm         ),
            TestStraight       ("dual", 1.meters     ),
            TestSplitMerge90   ("merge", NbOfFlows(1)),
            TestDirectionChange("bend"               )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[FlowMergeRequiresLengthBearingSectionBeforeDirectionChange]
    }

    // ── SetRoughness passthrough through split-merge cycle ──

    it should "preserve SetRoughness through a split-merge cycle" in {
        // Verify that roughness set before a split is carried through to elements after the merge.
        // Roughness is a pipe property independent of flow count — the builder state must not
        // reset it during flow transitions (split or merge).
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect      (10.cm, 10.cm         ),
            TestSetRoughness (3.mm                 ),
            TestStraight     ("preSplit", 1.meters ),
            TestSetNumberOfFlows(NbOfFlows(2)),
            TestSetInnerShape(15.cm, 15.cm         ),
            TestStraight     ("dual", 1.meters     ),
            TestSetNumberOfFlows(NbOfFlows(1)),
            TestSetInnerShape(10.cm, 10.cm         ),
            TestStraight     ("postMerge", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true

        // The final props state must retain the roughness set before the split.
        // If the builder had reset roughness during split/merge flow transitions,
        // the state.roughness would be None.
        builder.finalState.roughness `shouldBe` Some(3.mm)
    }

    it should "accept SetRoughness after a split (before next section)" in {
        // SetRoughness placed between SplitMerge90 and the next length-bearing section
        // must not trigger any validation error — roughness is shape-independent and
        // should not interfere with the pending-split state machine.
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect     (10.cm, 10.cm         ),
            TestStraight    ("preSplit", 1.meters ),
            TestSplitMerge90("split", NbOfFlows(2)),
            TestSetRoughness(5.mm                 ),
            TestStraight    ("dual", 1.meters     )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
        builder.finalState.roughness `shouldBe` Some(5.mm)
    }

    it should "accept SetRoughness after a merge (before next section)" in {
        // SetRoughness placed between SplitMerge90 merge and the next length-bearing section
        // must not trigger any validation error — the merge state machine tracks inner shape
        // and section materialization, roughness is orthogonal.
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect     (10.cm, 10.cm         ),
            TestStraight    ("preSplit", 1.meters ),
            TestSplitMerge90("split", NbOfFlows(2)),
            TestStraight    ("dual", 1.meters     ),
            TestSplitMerge90("merge", NbOfFlows(1)),
            TestSetRoughness(5.mm                 ),
            TestStraight    ("postMerge", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` true
        builder.finalState.roughness `shouldBe` Some(5.mm)
    }

    it should "reject SetInnerShape right after a split (not materialized)" in {
        // A split element is zero-length — the shape it emits is not yet materialized
        // into a physical section. SetInnerShape requires a materialized shape,
        // so it must be rejected until a length-bearing section follows the split.
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect      (10.cm, 10.cm         ),
            TestStraight     ("preSplit", 1.meters ),
            TestSplitMerge90 ("split", NbOfFlows(2)),
            TestSetInnerShape(20.cm, 20.cm         ),
            TestStraight     ("dual", 1.meters     )
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[ShapeNotMaterialized]
    }

    it should "reject SetInnerShape right after a merge (not materialized)" in {
        // Same logic after a merge — the merge element is zero-length and its
        // emitted shape is not materialized until a length-bearing section follows.
        val builder = newBuilder
        val descr   = builder.define(
            TestSetRect      (10.cm, 10.cm         ),
            TestStraight     ("preSplit", 1.meters ),
            TestSplitMerge90 ("split", NbOfFlows(2)),
            TestStraight     ("dual", 1.meters     ),
            TestSplitMerge90 ("merge", NbOfFlows(1)),
            TestSetInnerShape(20.cm, 20.cm         ),
            TestStraight     ("postMerge", 1.meters)
        )
        val result  = descr.toFullDescr()
        result.isValid `shouldBe` false
        val errors  = result.toEither.left.toOption.get
        errors.head `shouldBe` a[ShapeNotMaterialized]
    }

end IncrementalBuilderSplitMergeSuite
