/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v6.*
import afpma.firecalc.dto.v7.*

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
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetInitialDirection(az, incl)

    private def flowOnlyInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetInitialPosition(x, y, z)

    private def flowOnlyFinalPos(
        x: Length,
        y: Length,
        z: Length
    ): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetFinalPosition(x, y, z)

    private def thermalInitialDir(
        az  : AzimuthDirection,
        incl: InclinationDirection
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetInitialDirection(az, incl)

    private def thermalInitialPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetInitialPosition(x, y, z)

    private def thermalFinalPos(
        x: Length,
        y: Length,
        z: Length
    ): ThermalPipeDescr_13384_V3 =
        SetThermalPipeProp_13384_V3.SetFinalPosition(x, y, z)

    private def roughness(): FlowOnlyPipeDescr_15544_V3 =
        SetFlowOnlyPipeProp_15544_V3.SetRoughness(3.mm)

    private def horizontalSection(name: String, length: Length): FlowOnlyPipeDescr_15544_V3 =
        AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal(name, length)

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
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            roughness        (             ),
                            horizontalSection("test", 50.cm)
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }

        "strips SetInitialDirection from FlueSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            flowOnlyInitialPos(5.cm, 5.cm, 5.cm                              ),
                            roughness         (                                              ),
                            horizontalSection ("test", 50.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded.isRight shouldBe true
            val result  = decoded.toOption.get
            result.initialDirection shouldBe wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal)
            result.initialPosition shouldBe wrapperPos(0.cm, 0.cm, 0.cm)
            result.slots.head match
                case PostFireboxPipeDescrSlot.FlueSlot(d) =>
                    d.size shouldBe 2
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V3.SetInitialDirection]) shouldBe false
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V3.SetInitialPosition]) shouldBe false
                case _                                    => fail("Expected FlueSlot")
        }

        "strips SetInitialPosition from FlueSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            flowOnlyInitialPos(5.cm, 5.cm, 5.cm),
                            roughness         (                )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot.FlueSlot(d) =>
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V3.SetInitialPosition]) shouldBe false
                case _                                    => fail("Expected FlueSlot")
        }

        "strips SetFinalPosition from FlueSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            flowOnlyFinalPos(100.cm, 200.cm, 300.cm),
                            roughness       (                      )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot.FlueSlot(d) =>
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V3.SetFinalPosition]) shouldBe false
                case _                                    => fail("Expected FlueSlot")
        }

        "strips thermal deprecated elements from ConnectorSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot     (Seq(roughness())),
                    PostFireboxPipeDescrSlot.ConnectorSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            thermalInitialPos(10.cm, 10.cm, 10.cm                           ),
                            thermalFinalPos  (50.cm, 50.cm, 50.cm                           )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ChimneySlot(Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots(1) match
                case PostFireboxPipeDescrSlot.ConnectorSlot(d) =>
                    d shouldBe empty
                case _                                         => fail("Expected ConnectorSlot")
        }

        "strips thermal deprecated elements from ChimneySlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot   (Seq(roughness())),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot(
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
                case PostFireboxPipeDescrSlot.ChimneySlot(d) =>
                    d shouldBe empty
                case _                                       => fail("Expected ChimneySlot")
        }

        "strips thermal deprecated elements from ThermalFlueSlot" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.ThermalFlueSlot(
                        Seq(
                            thermalInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            thermalInitialPos(10.cm, 10.cm, 10.cm                           ),
                            thermalFinalPos  (50.cm, 50.cm, 50.cm                           )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot.ThermalFlueSlot(d) =>
                    d shouldBe empty
                case _                                           => fail("Expected ThermalFlueSlot")
        }

        "preserves valid non-deprecated descriptor elements" in {
            val pipes   = PostFireboxPipes(
                initialDirection = wrapperDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                initialPosition  = wrapperPos(0.cm, 0.cm, 0.cm),
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Left, InclinationDirection.Up),
                            roughness         (                                              ),
                            horizontalSection ("test", 50.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            val result  = decoded.toOption.get
            result.slots.head match
                case PostFireboxPipeDescrSlot.FlueSlot(d) =>
                    d.size shouldBe 2
                    d.exists(_.isInstanceOf[SetFlowOnlyPipeProp_15544_V3.SetRoughness]) shouldBe true
                    d.exists(_.isInstanceOf[AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal]) shouldBe true
                case _                                    => fail("Expected FlueSlot")
        }

        "keeps wrapper fields unchanged" in {
            val dir     = wrapperDir(AzimuthDirection.Left, InclinationDirection.Up)
            val pos     = wrapperPos(10.cm, 20.cm, 30.cm)
            val pipes   = PostFireboxPipes(
                initialDirection = dir,
                initialPosition  = pos,
                slots            = Seq(
                    PostFireboxPipeDescrSlot.FlueSlot(
                        Seq(
                            flowOnlyInitialDir(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            flowOnlyInitialPos(999.cm, 999.cm, 999.cm                                 )
                        )
                    ),
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
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
                    PostFireboxPipeDescrSlot.NoFlueSlot,
                    PostFireboxPipeDescrSlot.ConnectorSlot(Seq.empty),
                    PostFireboxPipeDescrSlot.ChimneySlot  (Seq.empty)
                )
            )
            val json    = pipes.asJson
            val decoded = decode[PostFireboxPipes](json.noSpaces)
            decoded shouldBe Right(pipes)
        }
    }
end V7DecodeBoundarySuite
