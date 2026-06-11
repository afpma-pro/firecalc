/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v7.*
import afpma.firecalc.dto.common.NbOfFlows

import afpma.firecalc.units.coulombutils.*

import io.circe.parser.*
import io.circe.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class V7DecodeBoundarySuite extends AnyFreeSpec with Matchers:

    // ─── Helper factories ──────────────────────────────────────────

    private def flowOnlyInitialDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): FlowOnlyPipeDescr_15544_V4 =
        FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection(az, incl)

    private def flowOnlyInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): FlowOnlyPipeDescr_15544_V4 =
        FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition(x, y, z)

    private def thermalInitialDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): ThermalPipeDescr_13384_V4 =
        ThermalPipeTrackingOp_13384_V4.SetInitialDirection(az, incl)

    private def thermalInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V4 =
        ThermalPipeTrackingOp_13384_V4.SetInitialPosition(x, y, z)

    private def thermalFinalPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V4 =
        ThermalPipeTrackingOp_13384_V4.SetFinalPosition(x, y, z)

    private def roughness(): FlowOnlyPipeDescr_15544_V4 =
        SetFlowOnlyPipeProp_15544_V4.SetRoughness(3.mm)

    private def horizontalSection(name: String, length: Length): FlowOnlyPipeDescr_15544_V4 =
        AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal(name, length)

    private def wrapperDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): PostFireboxInitialDirection =
        PostFireboxInitialDirection(az, incl)

    private def wrapperPos(
        x: Length,
        y: Length,
        z: Length
    ): PostFireboxInitialPosition =
        PostFireboxInitialPosition(x, y, z)

    // ─── Decode boundary tests ─────────────────────────────────────

    "PostFireboxPipes decoder" - {

        "decodes clean PostFireboxPipes unchanged" in {
            val pipes   = PostFireboxPipes.clean(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(10.cm, 20.cm, 30.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot(
                        Seq(
                            roughness        (             ),
                            horizontalSection("test", 50.cm)
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }

        "preserves SetInitialDirection and SetInitialPosition in FlueSlot" in {
            // V7 preserves tracking ops as PipeTrackingOp, does not strip them
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            flowOnlyInitialPos(5.cm, 5.cm, 5.cm                              ),
                            horizontalSection ("test", 50.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded.isRight shouldBe true
            val result  = decoded.toOption.get
            result.initialDirection shouldBe wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal)
            result.initialPosition shouldBe wrapperPos(0.cm, 0.cm, 0.cm)
            result.slots.head match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    // V7 preserves tracking ops: SetInitialDirection + SetInitialPosition + AddSectionHorizontal
                    d.size shouldBe 3
                    d.exists(_.isInstanceOf[FlowOnlyPipeTrackingOp_15544_V4.SetInitialDirection]) shouldBe true
                    d.exists(_.isInstanceOf[FlowOnlyPipeTrackingOp_15544_V4.SetInitialPosition]) shouldBe true
                case _                                       => fail("Expected FlueSlot")
        }

        "preserves thermal tracking elements from ConnectorSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot     (Seq(roughness())),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            thermalInitialPos(10.cm, 10.cm, 10.cm                           ),
                            thermalFinalPos  (50.cm, 50.cm, 50.cm                           )
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot(Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots(1) match
                case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d) =>
                    d.size > 0 shouldBe true // V7 preserves tracking ops
                case _ => fail("Expected ConnectorSlot")
        }

        "preserves thermal tracking elements from ChimneySlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot   (Seq(roughness())),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            thermalFinalPos  (50.cm, 50.cm, 50.cm                           )
                        )
                    )
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots(2) match
                case PostFireboxPipeDescrSlot_V7.ChimneySlot(d) =>
                    d.size > 0 shouldBe true // V7 preserves tracking ops
                case _ => fail("Expected ChimneySlot")
        }

        "preserves thermal tracking elements from ThermalFlueSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            thermalInitialPos(10.cm, 10.cm, 10.cm                           ),
                            thermalFinalPos  (50.cm, 50.cm, 50.cm                           )
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
                    d.size > 0 shouldBe true // V7 preserves tracking ops
                case _ => fail("Expected ThermalFlueSlot")
        }

        "preserves valid non-deprecated descriptor elements" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            roughness         (                                              ),
                            horizontalSection ("test", 50.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                    d.size shouldBe 3 // V7 preserves tracking ops: SetInitialDirection + SetRoughness + AddSectionHorizontal
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V4.SetRoughness]) shouldBe true
                    d.exists(_.isInstanceOf[AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal]) shouldBe true
                case _                                       => fail("Expected FlueSlot")
        }

        "keeps wrapper fields unchanged" in {
            val dir     = wrapperDir(AzimuthDirection.Left, InclinationDirection.Up)
            val pos     = wrapperPos(10.cm, 20.cm, 30.cm)
            val pipes   = PostFireboxPipes(
                initialDirection = dir,
                initialPosition  = pos,
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            flowOnlyInitialPos(999.cm, 999.cm, 999.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.initialDirection shouldBe dir
            result.initialPosition shouldBe pos
        }

        "handles NoFlueSlot unchanged" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.NoFlueSlot,
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }
        "decodes top-level SetNumberOfFlows as ChannelTopologyOp" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot_V7.FlueSlot(
                        Seq(
                            FlowOnlyChannelTopologyOp_15544_V4.SetNumberOfFlows(NbOfFlows(2)),
                            horizontalSection("test", 50.cm)
                        )
                    ),
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }
        "cannot decode SetNumberOfFlows inside SetPropertiesInBatch.props" in {
            // SetNumberOfFlows is NOT a SetSingleProp in V7, so it cannot appear inside batch props.
            // Construct JSON manually with SetNumberOfFlows in a batch's props array.
            val malformedJson =
                """{"initialDirection":{"azimuth":"Front","inclination":"Up"},"initialPosition":{"x":0,"y":0,"z":0},"slots":[{"ThermalFlueSlot":{"descr":[{"type":"SetPropertiesInBatch","batch_name":"test-batch","props":[{"type":"SetNumberOfFlows","n_flows":2}]}]}},{"ConnectorSlot":{"descr":[]}},{"ChimneySlot":{"descr":[]}}]}"""
            val decoded       = decode[PostFireboxPipes](malformedJson)
            decoded.isLeft shouldBe true
        }
        "cannot decode SetInitialDirection inside SetPropertiesInBatch.props" in {
            // SetInitialDirection is a PipeTrackingOp, NOT a SetSingleProp in V7.
            // It cannot appear inside batch props.
            val malformedJson =
                """{"initialDirection":{"azimuth":"Front","inclination":"Up"},"initialPosition":{"x":0,"y":0,"z":0},"slots":[{"ThermalFlueSlot":{"descr":[{"type":"SetPropertiesInBatch","batch_name":"test-batch","props":[{"type":"SetInitialDirection","azimuth":"Front","inclination":"Up"}]}]}},{"ConnectorSlot":{"descr":[]}},{"ChimneySlot":{"descr":[]}}]}"""
            val decoded       = decode[PostFireboxPipes](malformedJson)
            decoded.isLeft shouldBe true
        }
    }
end V7DecodeBoundarySuite
