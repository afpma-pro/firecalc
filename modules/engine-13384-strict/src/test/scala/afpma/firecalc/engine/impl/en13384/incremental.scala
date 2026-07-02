/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.{AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_13384.{
    SplitSingleFlowIntoTwoFlowsWith90DegTurn => SplitFlowOnly,
    MergeTwoFlowsIntoSingleWith90DegTurn => MergeFlowOnly
}
import afpma.firecalc.dto.all.AddThermalPipeElement_13384.{
    SplitSingleFlowIntoTwoFlowsWith90DegTurn => SplitThermal,
    MergeTwoFlowsIntoSingleWith90DegTurn => MergeThermal
}
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class Pipes_13384_IncrementalBuilder extends AnyFreeSpec with Matchers with IncrementalHelper_13384 {

    import afpma.firecalc.dto.all.*
    import afpma.firecalc.engine.models.*

    // import pipedescr.*
    val thermalBuilder  = ChimneyPipe_Module.incremental
    import thermalBuilder.*
    val flowOnlyBuilder = FlowOnlyAirIntakePipe_Module_13384.incremental

    "pipes.en13384" - {

        "modifiers api" - {

            "define()" - {

                "should be exposed" in {
                    thermalBuilder.define(
                        innerShape(circle(100.0.mm))
                    )
                }
            }

            "case 1 : straight section" - {

                "returns proper pipe" in {
                    given NbOfFlows = 1.flow
                    val d0          = 100.mm
                    val e0          = 2.mm
                    val λ0          = WattsPerMeterKelvin(1.2)

                    val p =
                        thermalBuilder
                            .withInitialDirection(
                                PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                            )
                            .define(
                                innerShape(square(d0)),
                                layer               (e0, λ0           ),
                                roughness           (2.mm             ),
                                pipeLocation        (HeatedArea       ),
                                addSectionHorizontal("first", 2.meters)
                            )

                    val vRepr = p.toFullDescr()

                    val expected = PipeFullDescr(
                        elements = Vector(
                            NamedPipeElDescr (
                                idx  = PipeIdx(0),
                                typ  = ChimneyPipeT,
                                name = "first",
                                el   = ThermalPipeDescr_13384.StraightSection(
                                    length           = 2.meters,
                                    innerShape       = PipeShape.Square(d0),
                                    outer_shape      = PipeShape.Square(104.mm),
                                    roughness        = 2.mm,
                                    layers           = List(AppendLayerDescr.FromLambdaUsingThickness(e0, λ0)),
                                    elevation_gain   = 0.meters,
                                    airSpaceDetailed = AirSpaceDetailed.WithoutAirSpace_V2,
                                    pipeLoc          = HeatedArea,
                                    ductType         = DuctType.NonConcentricDuctsHighThermalResistance
                                )
                            )
                        ),
                        pipeType = ChimneyPipeT
                    )

                    println(vRepr)

                    vRepr.isValid.shouldBe(true)

                    behave like equivalent_StraightSections(
                        vRepr.toOption.get._2.unwrap.elements.head.el,
                        expected.elementsUnwrap.head.el
                    )
                }
            }

        }

        "n_flows propagation (thermal builder)" - {

            import thermalBuilder.*

            "n_flows updates to 2 after split and back to 1 after merge" in {
                thermalBuilder.withInitialDirection(
                    PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                )
                // Descriptors:
                //  0: innerShape(square(18.cm))
                //  1: layer(2.mm, 1.2 W/mK)
                //  2: roughness(3.mm)
                //  3: pipeLocation(HeatedArea)
                //  4: addSectionHorizontal("preSplit", 1.meters)
                //  5: SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", ...)
                //  6: addSectionHorizontal("dual", 1.meters)
                //  7: MergeTwoFlowsIntoSingleWith90DegTurn("merge", ...)
                //  8: addSectionHorizontal("postMerge", 1.meters)
                val p = thermalBuilder.define(
                    innerShape(square(18.cm)),
                    layer               (2.mm, WattsPerMeterKelvin(1.2)),
                    roughness           (3.mm                          ),
                    pipeLocation        (HeatedArea                    ),
                    addSectionHorizontal("preSplit", 1.meters          ),
                    SplitThermal        (
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal("dual", 1.meters              ),
                    MergeThermal        (
                        "merge",
                        newInnerShape = square(18.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal("postMerge", 1.meters         )
                )

                // Before split (after preSplit section) — should be 1 flow
                p.nFlowsAtPrefix(5).getOrElse(1.flow) shouldBe 1.flow

                // After split — should be 2 flows
                p.nFlowsAtPrefix(6).getOrElse(1.flow) shouldBe 2.flows

                // After merge — should be 1 flow
                p.nFlowsAtPrefix(9).getOrElse(1.flow) shouldBe 1.flow
            }
        }

        "n_flows propagation (flow-only builder)" - {

            import flowOnlyBuilder.*

            "n_flows updates to 2 after split and back to 1 after merge" in {
                flowOnlyBuilder.withInitialDirection(
                    PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                )
                // Descriptors:
                //  0: innerShape(square(18.cm))
                //  1: roughness(3.mm)
                //  2: addSectionHorizontal("preSplit", 1.meters)
                //  3: SplitSingleFlowIntoTwoFlowsWith90DegTurn("split", ...)
                //  4: addSectionHorizontal("dual", 1.meters)
                //  5: MergeTwoFlowsIntoSingleWith90DegTurn("merge", ...)
                //  6: addSectionHorizontal("postMerge", 1.meters)
                val p = flowOnlyBuilder.define(
                    innerShape(square(18.cm)),
                    roughness           (3.mm                 ),
                    addSectionHorizontal("preSplit", 1.meters ),
                    SplitFlowOnly       (
                        "split",
                        newInnerShape = rectangle(9.cm, 9.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal("dual", 1.meters     ),
                    MergeFlowOnly       (
                        "merge",
                        newInnerShape = square(18.cm),
                        absDir        = None
                    ),
                    addSectionHorizontal("postMerge", 1.meters)
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
