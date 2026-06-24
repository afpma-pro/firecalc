/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.domain.{IsDirectionChange, SetsInnerShape, SetsNumberOfFlows}
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import cats.data.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IncrementalBuilderSplitMergeSuite extends AnyFlatSpec with Matchers with IncrementalBuilderTestFixture:

    case class TestSetInnerShape(w: Length, h: Length)  extends TestSetProp with SetsInnerShape
    case class TestSetNumberOfFlows(n_flows: NbOfFlows) extends TestSetProp with SetsNumberOfFlows

    case class TestNeutralElement(name: String)  extends TestAddElement
    case class TestDirectionChange(name: String) extends TestAddElement with IsDirectionChange

    class TestBuilder(
        override val pt: FluePipeT
    ) extends TestBuilderBase(pt):

        override protected def updateStateForSetProp(
            vState : ValidatedResult[PropsState],
            setProp: SetProp
        ): ValidatedResult[PropsState] =
            setProp match
                case TestSetInnerShape(w, h) =>
                    vState.map(_.copy(geometry = Some(PipeShape.Rectangle(w, h))))
                case _: TestSetNumberOfFlows => vState

        override protected def mkFullElementForTest(
            prevs   : PipeFullDescr,
            convStep: ConversionStep
        )(id_addElementOp: (IdIncr, AddElement)): CtxValidatedResult[NonEmptyList[(IdIncr, NamedPipeElDescr)]] =
            id_addElementOp match
                case (idIncr, TestNeutralElement(n) ) =>
                    mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestNeutralElement"))
                case (idIncr, TestDirectionChange(n)) =>
                    mkNamedElement(prevs, idIncr, n, TestElZeroLength("TestDirectionChange"))
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
