/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import cats.data.Validated.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.v7.PostFireboxInitialDirection
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
                            PostFireboxInitialDirection    (
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
                            PostFireboxInitialDirection    (
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
                            PostFireboxInitialDirection    (
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
                            "vers section horizontale",
                            "section horizontale"
                        )
                        elems(1).el `shouldBe` DirectionChange.AngleVifDe0A180(
                            α              = 90.degrees,
                            angleN2        = None,
                            effectiveShape = branchShape
                        )
                        elems(1).el.innerShape(Some(square(width))).map(_(using Position.Middle)) `shouldBe` Some(
                            branchShape
                        )
                    }

                    "rejects split branch geometry that changes total cross-section" in {
                        val width = 18.cm
                        builder.withInitialDirection(
                            PostFireboxInitialDirection    (
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

                    "rejects merge geometry that changes total cross-section" in {
                        builder.withInitialDirection(
                            PostFireboxInitialDirection    (
                                azimuth     = AzimuthDirection.Right,
                                inclination = InclinationDirection.Horizontal
                            )
                        )
                        val p =
                            builder.define                                      (
                                innerShape                                      (rectangle(18.cm, 9.cm)),
                                roughness                (3.mm                        ),
                                FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(NbOfFlows(2)          ),
                                addSectionHorizontal     ("descente", 1.meters        ),
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
                }

                "case direction-tracked : initial direction vertical up + addSectionSlopped" - {

                    "elevation_gain is auto-computed from direction (should be 2m for 2m vertical section)" in {
                        // given NbOfFlows = 1.flow
                        val d0 = 100.mm
                        builder.withInitialDirection(
                            PostFireboxInitialDirection    (
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
                            PostFireboxInitialDirection    (
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
    }

}
