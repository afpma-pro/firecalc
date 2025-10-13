/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import cats.data.Validated.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.units.coulombutils.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class Pipes_EN15544_IncrementalBuilder extends AnyFreeSpec with Matchers {

    import PipeShape.*

    val builder = FluePipe_Module_EN15544.incremental
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
                        val d0 = 100.mm
                        val p = 
                            builder.define(
                                innerShape(square(d0)),
                                roughness(2.mm),
                                addSectionHorizontal("first", 2.meters)
                            )
                        
                        val vRepr = p.toFullDescr()
    
                        val expected = PipeFullDescr(
                            elements = Vector(NamedPipeElDescr(
                                idx = PipeIdx(0),
                                typ = FluePipeT,
                                name = "first",
                                el = StraightSection(
                                    length = 2.meters,
                                    geometry = PipeShape.Square(d0),
                                    roughness = 2.mm,
                                    elevation_gain = 0.meters,
                                )
                            )),
                            pipeType = FluePipeT
                        )
    
                        vRepr.shouldBe(Valid(expected))
                    }
                }
    
                "case 2 : straight + angle + straight" - {
    
                    "returns proper pipe" in {
                        given NbOfFlows = 1.flow
                        val a = 100.mm
                        val p = 
                            builder.define(
                                innerShape(square(a)),
                                roughness(2.mm),
                                addSectionHorizontal("first", 2.meters),
                                addSharpAngle_0_to_180deg("turn left", 45.degrees),
                                addSectionHorizontal("second", 1.meters)
                            )
    
                        val vRepr = p.toFullDescr()
    
                        val expected = PipeFullDescr(
                            elements = Vector(
                                NamedPipeElDescr(
                                    idx = PipeIdx(0),
                                    typ = FluePipeT,
                                    name = "first",
                                    el = StraightSection(
                                        length = 2.meters,
                                        geometry = PipeShape.Square(a),
                                        roughness = 2.mm,
                                        elevation_gain = 0.meters,
                                    )
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(1),
                                    typ = FluePipeT,
                                    name = "turn left",
                                    el = DirectionChange.AngleVifDe0A180(45.degrees)
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(2),
                                    typ = FluePipeT,
                                    name = "second",
                                    el = StraightSection(
                                        length = 1.meters,
                                        geometry = PipeShape.Square(a),
                                        roughness = 2.mm,
                                        elevation_gain = 0.meters,
                                    )
                                )
                                
                            ),
                            pipeType = FluePipeT
                        )
    
                        vRepr.shouldBe(Valid(expected))
                    }
                }

                "case 3 : straight + angle + straight (short) + angle + straight" - {

                    "returns proper pipe" in {
                        given NbOfFlows = 1.flow
                        val diam = 20.cm
                        val p = 
                            builder.define(
                                innerShape(circle(diam)),
                                roughness(2.mm),
                                addSectionHorizontal("straight-0", 50.cm),
                                addSharpAngle_0_to_180deg("turn left", 45.degrees),
                                addSectionHorizontal("straight-1-short", 10.cm),
                                addSharpAngle_0_to_180deg("turn left", 45.degrees),
                                addSectionHorizontal("straight-2", 50.cm),
                            )

                        val vRepr = p.toFullDescr()

                        val expected = PipeFullDescr(
                            elements = Vector(
                                NamedPipeElDescr(
                                    idx = PipeIdx(0),
                                    typ = FluePipeT,
                                    name = "straight-0",
                                    el = StraightSection(
                                        length = 50.cm,
                                        geometry = PipeShape.Circle(diam),
                                        roughness = 2.mm,
                                        elevation_gain = 0.meters,
                                    )
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(1),
                                    typ = FluePipeT,
                                    name = "turn left",
                                    el = DirectionChange.AngleVifDe0A180(45.degrees)
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(2),
                                    typ = FluePipeT,
                                    name = "straight-1-short",
                                    el = StraightSection(
                                        length = 10.cm,
                                        geometry = PipeShape.Circle(diam),
                                        roughness = 2.mm,
                                        elevation_gain = 0.meters,
                                    )
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(3),
                                    typ = FluePipeT,
                                    name = "turn left",
                                    el = DirectionChange.AngleVifDe0A180(45.degrees)
                                ),
                                NamedPipeElDescr(
                                    idx = PipeIdx(4),
                                    typ = FluePipeT,
                                    name = "straight-2",
                                    el = StraightSection(
                                        length = 50.cm,
                                        geometry = PipeShape.Circle(diam),
                                        roughness = 2.mm,
                                        elevation_gain = 0.meters,
                                    )
                                ),

                            ),
                            pipeType = FluePipeT
                        )

                        vRepr.shouldBe(Valid(expected))
                        // then check for short section / pressures / etc...
                        val repr = vRepr.toOption.get._2
                        val shortOpt = repr.elems.find(_.name == ("straight-1-short": PipeName))
                        shortOpt.isDefined.shouldBe(true)
                        shortOpt.get.el shouldBe a [StraightSection]
                        val short = shortOpt.get.el.asInstanceOf[StraightSection]
                        import afpma.firecalc.engine.models.en15544.shortsection.ShortOrRegularOps.given
                        short.isShort.shouldBe(true)
                    }
                }
            }

            
        }
    }

}
