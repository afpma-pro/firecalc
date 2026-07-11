/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import cats.data.Validated.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.{
    SplitSingleFlowIntoTwoFlowsWith90DegTurn,
    MergeTwoFlowsIntoSingleWith90DegTurn
}
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.Position
import afpma.firecalc.engine.standard.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class Pipes_15544_IncrementalBuilder extends AnyFreeSpec with Matchers {

    import PipeShape.*

    val builder = FluePipe_Module_15544.incremental
    import builder.*

    "pipes.en15544" - {

        "modifiers api" - {

            "define()" - {

                "should be exposed" in {
                    builder.define(
                        innerShape(circle(100.0.mm))
                    )
                }
            }

            "toFullDescr()" - {

                "case 1 : straight section" - {

                    "returns proper pipe" in {
                        given NbOfFlows = 1.flow
                        val d0          = 100.mm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Rear,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define(
                                innerShape(square(d0)),
                                roughness           (2.mm             ),
                                addSectionHorizontal("first", 2.meters)
                            )

                        val vRepr = p.toFullDescr().map(_._2)

                        val expected = PipeFullDescr(
                            elements = Vector(
                                NamedPipeElDescr (
                                    idx  = PipeIdx(0),
                                    typ  = FluePipeT,
                                    name = "first",
                                    el   = StraightSection(
                                        length         = 2.meters,
                                        geometry       = PipeShape.Square(d0),
                                        roughness      = 2.mm,
                                        elevation_gain = 0.meters
                                    )
                                )
                            ),
                            pipeType = FluePipeT
                        )

                        vRepr.shouldBe(Valid(expected))
                    }
                }

                "case 2 : straight + angle + straight" - {

                    "returns proper pipe" in {
                        given NbOfFlows = 1.flow
                        val a           = 100.mm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Rear,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define(
                                innerShape(square(a)),
                                roughness                (2.mm              ),
                                addSectionHorizontal     ("first", 2.meters ),
                                addSharpAngle_0_to_180deg(
                                    "turn left",
                                    45.degrees,
                                    AbsoluteDirection(AzimuthDirection.RearRight, InclinationDirection.Horizontal)
                                ), // towards Right-ish
                                addSectionHorizontal     ("second", 1.meters)
                            )

                        val vRepr = p.toFullDescr().map(_._2)

                        // "second" has elevation_gain ≈ 0 (float noise from trig; expected exact 0
                        // for a horizontal pipe after a horizontal bend). Assert with tolerance.
                        val elems = vRepr.toOption.get.elems
                        elems.size.shouldBe                    (3                  )
                        elems(0).shouldBe                      (
                            NamedPipeElDescr (
                                idx  = PipeIdx(0),
                                typ  = FluePipeT,
                                name = "first",
                                el   = StraightSection(2.meters, PipeShape.Square(a), 2.mm, 0.meters)
                            )
                        )
                        elems(1).shouldBe                      (
                            NamedPipeElDescr (
                                idx  = PipeIdx(1),
                                typ  = FluePipeT,
                                name = "turn left",
                                el   = DirectionChange.AngleVifDe0A180(45.degrees, effectiveShape = PipeShape.Square(a))
                            )
                        )
                        val second = elems(2).el.asInstanceOf[StraightSection]
                        second.length.shouldEqual              (1.meters           )
                        second.geometry.shouldBe               (PipeShape.Square(a))
                        second.roughness.shouldBe              (2.mm               )
                        second.elevation_gain.value.shouldEqual(0.0 +- 1e-10       )
                    }
                }

                "case split + shape change + angle + straight" - {

                    "stores the split branch geometry on the direction change" in {
                        val width       = 18.cm
                        val height      = 9.cm
                        val branchShape = rectangle(width, height)
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (square(width)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2) ),
                                innerShape               (branchShape                 ),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val elems = p.toFullDescr().map(_._2).toOption.get.elems

                        elems.map(_.name) `shouldBe` Vector(
                            "descente",
                            "section geometry change",
                            "branch-start",
                            "vers section horizontale",
                            "section horizontale"
                        )
                        elems(3).el `shouldBe` DirectionChange.AngleVifDe0A180(
                            α              = 90.degrees,
                            angleN2        = None,
                            effectiveShape = branchShape
                        )
                        elems(3).el.innerShape(Some(square(width))).map(_(using Position.Middle)) `shouldBe` Some(
                            branchShape
                        )
                    }

                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "rejects split branch geometry that changes total cross-section" ignore {
                        val width = 18.cm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                                (
                                innerShape                                      (square(width)          ),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
                                innerShape                                      (rectangle(width, 10.cm)),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result = p.toFullDescr()

                        result.isValid `shouldBe` false
                        val Invalid(errors) = result: @unchecked
                        errors.toList.head shouldBe a[FlowTransitionChangesTotalCrossSection]
                    }

                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "rejects merge geometry that changes total cross-section" ignore {
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (rectangle(18.cm, 9.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)          ),
                                addSectionHorizontal     ("branch", 0.5.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(1)          ),
                                innerShape                                      (square(20.cm)         ),
                                addSectionHorizontal     ("apres merge", 1.meters     ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result = p.toFullDescr()

                        result.isValid `shouldBe` false
                        val Invalid(errors) = result: @unchecked
                        errors.toList.head shouldBe a[FlowTransitionChangesTotalCrossSection]
                    }

                    "split 20×10 cm → 2×10×10 cm: exact area conservation passes" in {
                        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×10 = 100 cm² × 2 = 200 cm²
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (rectangle(20.cm, 10.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
                                innerShape                                      (rectangle(10.cm, 10.cm)),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        p.toFullDescr().isValid `shouldBe` true
                    }

                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "split 20×10 cm → 2×10×9 cm: area too small fails with ExpectedDimRectangle" ignore {
                        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×9 = 90 cm² × 2 = 180 cm²  (20 cm² short)
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (rectangle(20.cm, 10.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
                                innerShape                                      (rectangle(10.cm, 9.cm) ),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result                                                      = p.toFullDescr()
                        result.isValid `shouldBe` false
                        val Invalid(errors)                                             = result: @unchecked
                        val err                                                         = errors.toList.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
                        err.expectedDimension shouldBe a[ExpectedDimRectangle]
                        val ExpectedDimRectangle(_, _, _, expectedHeight, expectedArea) =
                            err.expectedDimension: @unchecked
                        expectedHeight.to_cm.value shouldBe (10.0 +- 0.1 )
                        expectedArea.to_cm2.value shouldBe  (100.0 +- 0.1)
                    }

                    "split 20×10 cm → 2×10×9.99 cm: within 5 cm² tolerance passes" in {
                        // 20×10 = 200 cm² × 1 = 200 cm²  vs  10×9.99 = 99.9 cm² × 2 = 199.8 cm²  (0.2 cm² short — within tolerance)
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                        (
                                innerShape                                      (rectangle(20.cm, 10.cm)  ),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)             ),
                                innerShape                                      (rectangle(10.cm, 9.99.cm)),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        p.toFullDescr().isValid `shouldBe` true
                    }
                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "split 18 cm square → 2×9 cm square: area too small fails (ExpectedDimSquare)" ignore {
                        // 18×18 = 324 cm² × 1 = 324 cm²  vs  9×9 = 81 cm² × 2 = 162 cm²  (162 cm² short)
                        // Expected area per flow = 324 / 2 = 162 cm², expected side = sqrt(162) ≈ 12.73 cm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (square(18.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2) ),
                                innerShape                                      (square(9.cm) ),
                                addSectionHorizontal     ("branch-start", 0.1.meters  ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result                                   = p.toFullDescr()
                        result.isValid `shouldBe` false
                        val Invalid(errors)                          = result               : @unchecked
                        val err                                      = errors.toList.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
                        err.expectedDimension shouldBe a[ExpectedDimSquare]
                        val ExpectedDimSquare(_, _, expectedSide, _) = err.expectedDimension: @unchecked
                        expectedSide.to_cm.value shouldBe (12.73 +- 0.1)
                    }
                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "split D=18 cm circle → 2×D=9 cm circle: area too small fails (ExpectedDimCircle)" ignore {
                        // π×(18/2)² = 254.47 cm² × 1 = 254.47 cm²  vs  π×(9/2)² = 63.62 cm² × 2 = 127.23 cm²  (127.23 cm² short)
                        // Expected area per flow = 254.47 / 2 = 127.23 cm², expected diameter = sqrt(4×127.23/π) ≈ 12.73 cm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (circle(18.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2) ),
                                innerShape                                      (circle(9.cm) ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result                                       = p.toFullDescr()
                        result.isValid `shouldBe` false
                        val Invalid(errors)                              = result               : @unchecked
                        val err                                          = errors.toList.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
                        err.expectedDimension shouldBe a[ExpectedDimCircle]
                        val ExpectedDimCircle(_, _, expectedDiameter, _) = err.expectedDimension: @unchecked
                        expectedDiameter.to_cm.value shouldBe (12.73 +- 0.1)
                    }
                    // @ignore: flow area check deactivated — see FlowAreaConservation
                    "merge 2×10×10 cm → 1×20×9 cm: area too small fails (ExpectedDimRectangle)" ignore {
                        // 10×10 = 100 cm² × 2 = 200 cm²  vs  20×9 = 180 cm² × 1 = 180 cm²  (20 cm² short)
                        // Expected area = 200 cm², expected height for 20×H = 200/20 = 10 cm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (rectangle(10.cm, 10.cm)),
                                roughness                (3.mm                        ),
                                addSectionHorizontal     ("initial", 0.1.meters       ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)           ),
                                addSectionHorizontal     ("descente", 1.meters        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(1)           ),
                                innerShape                                      (rectangle(20.cm, 9.cm) ),
                                addSectionHorizontal     ("apres merge", 1.meters     ),
                                addSharpAngle_0_to_180deg(
                                    "vers section horizontale",
                                    90.degrees,
                                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                                ),
                                addSectionHorizontal     ("section horizontale", 50.cm)
                            )

                        val result                                                      = p.toFullDescr()
                        result.isValid `shouldBe` false
                        val Invalid(errors)                                             = result: @unchecked
                        val err                                                         = errors.toList.head.asInstanceOf[FlowTransitionChangesTotalCrossSection]
                        err.expectedDimension shouldBe a[ExpectedDimRectangle]
                        val ExpectedDimRectangle(_, _, _, expectedHeight, expectedArea) =
                            err.expectedDimension: @unchecked
                        expectedHeight.to_cm.value shouldBe (10.0 +- 0.1 )
                        expectedArea.to_cm2.value shouldBe  (200.0 +- 0.1)
                    }
                }

                "case direction-tracked : initial direction vertical up + addSectionSlopped" - {

                    "elevation_gain is auto-computed from direction (should be 2m for 2m vertical section)" in {
                        // given NbOfFlows = 1.flow
                        val d0 = 100.mm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Rear,
                                inclination = InclinationDirection.Up
                            )
                        )
                        val p =
                            builder.define(
                                innerShape(circle(d0)),
                                roughness        (2.mm          ),
                                addSectionSlopped("s1", 2.meters)
                            )

                        val vRepr = p.toFullDescr().map(_._2)

                        vRepr.isValid.shouldBe(true)

                        val section = vRepr.toOption.get.elems.head.el.asInstanceOf[StraightSection]
                        // direction.z = sin(90°) = 1.0, so finalElevGain = 2m * 1.0 = 2m
                        section.elevation_gain.shouldEqual(2.meters)
                        section.length.shouldEqual        (2.meters)
                    }
                }

                "case 3 : straight + angle + straight (short) + angle + straight" - {

                    "returns proper pipe" in {
                        given NbOfFlows = 1.flow
                        val diam        = 20.cm
                        builder.withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Rear,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define(
                                innerShape(circle(diam)),
                                roughness                (2.mm                     ),
                                addSectionHorizontal     ("straight-0", 50.cm      ),
                                addSharpAngle_0_to_180deg(
                                    "turn left",
                                    45.degrees,
                                    AbsoluteDirection(AzimuthDirection.RearLeft, InclinationDirection.Horizontal)
                                ), // Towards RearLeft
                                addSectionHorizontal     ("straight-1-short", 10.cm),
                                addSharpAngle_0_to_180deg(
                                    "turn left",
                                    45.degrees,
                                    AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
                                ), // Towards Left
                                addSectionHorizontal     ("straight-2", 50.cm      )
                            )

                        val vRepr = p.toFullDescr()

                        val expected = PipeFullDescr(
                            elements = Vector(
                                NamedPipeElDescr (
                                    idx  = PipeIdx(0),
                                    typ  = FluePipeT,
                                    name = "straight-0",
                                    el   = StraightSection(
                                        length         = 50.cm,
                                        geometry       = PipeShape.Circle(diam),
                                        roughness      = 2.mm,
                                        elevation_gain = 0.meters
                                    )
                                ),
                                NamedPipeElDescr (
                                    idx  = PipeIdx(1),
                                    typ  = FluePipeT,
                                    name = "turn left",
                                    el   = DirectionChange
                                        .AngleVifDe0A180(45.degrees, effectiveShape = PipeShape.Circle(diam))
                                ),
                                NamedPipeElDescr (
                                    idx  = PipeIdx(2),
                                    typ  = FluePipeT,
                                    name = "straight-1-short",
                                    el   = StraightSection(
                                        length         = 10.cm,
                                        geometry       = PipeShape.Circle(diam),
                                        roughness      = 2.mm,
                                        elevation_gain = 0.meters
                                    )
                                ),
                                NamedPipeElDescr (
                                    idx  = PipeIdx(3),
                                    typ  = FluePipeT,
                                    name = "turn left",
                                    el   = DirectionChange.AngleVifDe0A180(
                                        45.degrees,
                                        angleN2        = Some(90.degrees),
                                        effectiveShape = PipeShape.Circle(diam)
                                    )
                                ),
                                NamedPipeElDescr (
                                    idx  = PipeIdx(4),
                                    typ  = FluePipeT,
                                    name = "straight-2",
                                    el   = StraightSection(
                                        length         = 50.cm,
                                        geometry       = PipeShape.Circle(diam),
                                        roughness      = 2.mm,
                                        elevation_gain = 0.meters
                                    )
                                )
                            ),
                            pipeType = FluePipeT
                        )

                        vRepr.map(_._2).shouldBe(Valid(expected))
                        // then check for short section / pressures / etc...
                        val repr     = vRepr.toOption.get._2
                        val shortOpt = repr.elems.find(_.name == ("straight-1-short": PipeName))
                        shortOpt.isDefined.shouldBe(true)
                        shortOpt.get.el shouldBe a[StraightSection]
                        val short = shortOpt.get.el.asInstanceOf[StraightSection]
                        import afpma.firecalc.engine.models.en15544.shortsection.ShortOrRegularOps.given
                        short.isShort.shouldBe(true)
                    }
                }
            }

        }

        "materialization" - {

            "reject SetInnerShape right after a split (not materialized)" in {
                builder.withInitialDirection(
                    PipeInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    )
                )
                val p = builder.define(
                    innerShape(square(18.cm)          ),
                    roughness                               (3.mm                ),
                    addSectionHorizontal                    ("preSplit", 1.meters),
                    SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    innerShape(rectangle(20.cm, 10.cm)),
                    addSectionHorizontal                    ("dual", 1.meters    )
                )
                val result = p.toFullDescr()
                result.isValid `shouldBe` false
                val errors = result.toEither.left.toOption.get
                errors.head shouldBe a[ShapeNotMaterialized]
            }

            "reject SetInnerShape right after a merge (not materialized)" in {
                builder.withInitialDirection(
                    PipeInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    )
                )
                val p = builder.define(
                    innerShape(square(18.cm)          ),
                    roughness                               (3.mm                 ),
                    addSectionHorizontal                    ("preSplit", 1.meters ),
                    SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("dual", 1.meters     ),
                    MergeTwoFlowsIntoSingleWith90DegTurn    (
                        "merge",
                        newInnerShape = square(18.cm),
                        absDir        = None
                    ),
                    innerShape(rectangle(20.cm, 10.cm)),
                    addSectionHorizontal                    ("postMerge", 1.meters)
                )
                val result = p.toFullDescr()
                result.isValid `shouldBe` false
                val errors = result.toEither.left.toOption.get
                errors.head shouldBe a[ShapeNotMaterialized]
            }

            "accept SetInnerShape after split + length-bearing section" in {
                builder.withInitialDirection(
                    PipeInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    )
                )
                val p = builder.define(
                    innerShape(square(18.cm)          ),
                    roughness                               (3.mm                ),
                    addSectionHorizontal                    ("preSplit", 1.meters),
                    SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("dual", 1.meters    ),
                    innerShape(rectangle(10.cm, 10.cm)),
                    addSectionHorizontal                    ("dual2", 1.meters   )
                )
                val result = p.toFullDescr()
                result.isValid `shouldBe` true
            }

            "accept SetInnerShape after merge + length-bearing section" in {
                builder.withInitialDirection(
                    PipeInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    )
                )
                val p = builder.define(
                    innerShape(square(18.cm)          ),
                    roughness                               (3.mm                  ),
                    addSectionHorizontal                    ("preSplit", 1.meters  ),
                    SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("dual", 1.meters      ),
                    MergeTwoFlowsIntoSingleWith90DegTurn    (
                        "merge",
                        newInnerShape = square(18.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("postMerge", 1.meters ),
                    innerShape(rectangle(20.cm, 10.cm)),
                    addSectionHorizontal                    ("postMerge2", 1.meters)
                )
                val result = p.toFullDescr()
                result.isValid `shouldBe` true
            }

            "n_flows updates to 2 after split and back to 1 after merge" in {
                builder.withInitialDirection(
                    PipeInitialDirection    (
                        azimuth     = AzimuthDirection.Right,
                        inclination = InclinationDirection.Horizontal
                    )
                )
                // Descriptors:
                //  0: innerShape(square(18.cm))
                //  1: roughness(3.mm)
                //  2: addSectionHorizontal("preSplit", 1.meters)
                //  3: SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", ...)
                //  4: addSectionHorizontal("dual", 1.meters)
                //  5: MergeTwoFlowsIntoSingleWith90DegTurn("merge", ...)
                //  6: addSectionHorizontal("postMerge", 1.meters)
                val p = builder.define(
                    innerShape(square(18.cm)),
                    roughness                               (3.mm                 ),
                    addSectionHorizontal                    ("preSplit", 1.meters ),
                    SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("dual", 1.meters     ),
                    MergeTwoFlowsIntoSingleWith90DegTurn    (
                        "merge",
                        newInnerShape = square(18.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal                    ("postMerge", 1.meters)
                )

                // Before split (after preSplit section) — should be 1 flow
                p.nFlowsAtPrefix(3).getOrElse(1.flow) shouldBe 1.flow

                // After split — should be 2 flows
                p.nFlowsAtPrefix(4).getOrElse(1.flow) shouldBe 2.flows

                // After merge — should be 1 flow
                p.nFlowsAtPrefix(7).getOrElse(1.flow) shouldBe 1.flow
            }
        }
    }

}
