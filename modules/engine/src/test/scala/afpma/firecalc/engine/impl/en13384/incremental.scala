/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.models.en13384.pipedescr.StraightSection

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class Pipes_EN13384_IncrementalBuilder extends AnyFreeSpec with Matchers with IncrementalHelper_EN13384 {

    import afpma.firecalc.dto.all.* 
    import afpma.firecalc.engine.models.*
    
    // import pipedescr.*
    val builder = AirIntakePipe_Module.incremental
    import builder.*

    val chimneyBuilder = ChimneyPipe_Module.incremental

    "pipes.en13384" - {

        "modifiers api" - {

            "define()" - {

                "should be exposed" in {
                    builder.define(
                        innerShape(circle(100.0.mm))
                    )
                }
            }

            "case 1 : straight section" - {

                "returns proper pipe" in {
                    given NbOfFlows = 1.flow
                    val d0 = 100.mm
                    val e0 = 2.mm
                    val λ0 = WattsPerMeterKelvin(1.2)

                    val p = 
                        builder.define(
                            innerShape(square(d0)),
                            roughness(2.mm),
                            layer(e0, λ0),
                            pipeLocation(PipeLocation.HeatedArea),
                            addSectionHorizontal("first", 2.meters)
                        )
                    
                    val vRepr = p.toFullDescr()

                    val expected = PipeFullDescr(
                        elements = Vector(NamedPipeElDescr(
                            idx = PipeIdx(0),
                            typ = AirIntakePipeT,
                            name = "first",
                            el = StraightSection(
                                length = 2.meters,
                                innerShape = PipeShape.Square(d0),
                                outer_shape = PipeShape.Square(104.mm),
                                roughness = 2.mm,
                                layers = List(AppendLayerDescr.FromLambdaUsingThickness(e0, λ0)),
                                elevation_gain = 0.meters,
                                airSpaceDetailed = AirSpaceDetailed.WithoutAirSpace,
                                pipeLoc = HeatedArea,
                                ductType = DuctType.NonConcentricDuctsHighThermalResistance,
                            )
                        )),
                        pipeType = AirIntakePipeT
                    )
                    
                    vRepr.isValid.shouldBe(true)
                    
                    behave like equivalent_StraightSections(
                        vRepr.toOption.get._2.unwrap.elements.head.el, 
                        expected.elementsUnwrap.head.el
                    )
                }
            }
            
        }
    }
}
