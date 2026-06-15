/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v7.*
import afpma.firecalc.dto.common.*

import afpma.firecalc.units.coulombutils.*

import io.circe.parser.*
import io.circe.syntax.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class V7DecodeBoundarySuite extends AnyFreeSpec with Matchers:

    // ─── Helper factories ──────────────────────────────────────────

    private def roughness(): FlowOnlyPipeDescr_15544_V4 =
        SetFlowOnlyPipeProp_15544_V4.SetRoughness(3.mm)

    private def horizontalSection(name: String, length: Length): FlowOnlyPipeDescr_15544_V4 =
        AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal(name, length)

    private def wrapperDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): PipeInitialDirection =
        PipeInitialDirection(az, incl)

    private def wrapperPos(
        x: Length,
        y: Length,
        z: Length
    ): Position3D =
        Position3D(x, y, z)

    // ─── Decode boundary tests ─────────────────────────────────────

    "FramedPostFireboxPipes decoder" - {

        "decodes clean FramedPostFireboxPipes unchanged" in {
            val pipes   = FramedPostFireboxPipes.clean(
                wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                wrapperPos(10.cm, 20.cm, 30.cm                                    ),
                slots = Seq(
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
            val decoded = decode[FramedPostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }

        "handles NoFlueSlot unchanged" in {
            val pipes   = FramedPostFireboxPipes(
                PipeInitialFrame(
                    wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                    wrapperPos(0.cm, 0.cm, 0.cm                                       )
                ),
                slots = Seq(
                    PostFireboxPipeDescrSlot_V7.NoFlueSlot,
                    PostFireboxPipeDescrSlot_V7.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot_V7.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[FramedPostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }

        "decodes top-level SetNumberOfFlows as ChannelTopologyOp" in {
            val pipes   = FramedPostFireboxPipes(
                PipeInitialFrame(
                    wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                    wrapperPos(0.cm, 0.cm, 0.cm                                       )
                ),
                slots = Seq(
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
            val decoded = decode[FramedPostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }

        "cannot decode SetNumberOfFlows inside SetPropertiesInBatch.props" in {
            // SetNumberOfFlows is NOT a SetSingleProp in V7, so it cannot appear inside batch props.
            // Construct JSON manually with SetNumberOfFlows in a batch's props array.
            val malformedJson =
                """{"initialDirection":{"azimuth":"Front","inclination":"Up"},"initialPosition":{"x":0,"y":0,"z":0},"slots":[{"ThermalFlueSlot":{"descr":[{"type":"SetPropertiesInBatch","batch_name":"test-batch","props":[{"type":"SetNumberOfFlows","n_flows":2}]}]}},{"ConnectorSlot":{"descr":[]}},{"ChimneySlot":{"descr":[]}}]}"""
            val decoded       = decode[FramedPostFireboxPipes](malformedJson)
            decoded.isLeft shouldBe true
        }

        "cannot decode SetInitialDirection inside SetPropertiesInBatch.props" in {
            // SetInitialDirection is a PipeTrackingOp, NOT a SetSingleProp in V7.
            // It cannot appear inside batch props.
            val malformedJson =
                """{"initialDirection":{"azimuth":"Front","inclination":"Up"},"initialPosition":{"x":0,"y":0,"z":0},"slots":[{"ThermalFlueSlot":{"descr":[{"type":"SetPropertiesInBatch","batch_name":"test-batch","props":[{"type":"SetInitialDirection","azimuth":"Front","inclination":"Up"}]}]}},{"ConnectorSlot":{"descr":[]}},{"ChimneySlot":{"descr":[]}}]}"""
            val decoded       = decode[FramedPostFireboxPipes](malformedJson)
            decoded.isLeft shouldBe true
        }
    }
end V7DecodeBoundarySuite
