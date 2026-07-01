/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v6.*
import afpma.firecalc.dto.v7.*
import afpma.firecalc.units.coulombutils.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class V6ToV7TransformerSuite extends AnyFlatSpec with Matchers:
    import transformers.*

    "normalizeToFramedAirIntakePipes" should "use default direction when missing" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            AddFlowOnlyPipeElement_13384_V3.AddSectionHorizontal("test", 10.cm)
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.initialDir shouldBe PipeInitialDirection.default
    }

    it should "use default position (FinalAuto) when missing" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            AddFlowOnlyPipeElement_13384_V3.AddSectionHorizontal("test", 10.cm)
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.position shouldBe AirIntakePosition.FinalAuto
    }

    it should "use last SetInitialDirection" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            SetFlowOnlyPipeProp_13384_V3.SetInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),
            SetFlowOnlyPipeProp_13384_V3.SetInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.initialDir shouldBe PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
    }

    it should "use InitialAuto when SetInitialPosition is present and no SetFinalPosition" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            SetFlowOnlyPipeProp_13384_V3.SetInitialPosition(10.cm, 20.cm, 30.cm),
            SetFlowOnlyPipeProp_13384_V3.SetInitialPosition(40.cm, 50.cm, 60.cm)
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.position shouldBe AirIntakePosition.InitialAuto
    }

    it should "use FinalAuto when SetFinalPosition is present" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            SetFlowOnlyPipeProp_13384_V3.SetInitialPosition(10.cm, 20.cm, 30.cm   ),
            SetFlowOnlyPipeProp_13384_V3.SetFinalPosition  (100.cm, 200.cm, 300.cm),
            SetFlowOnlyPipeProp_13384_V3.SetFinalPosition  (400.cm, 500.cm, 600.cm)
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.position shouldBe AirIntakePosition.FinalAuto
    }

    it should "strip migration-only properties from descriptors" in {
        val descr  = Seq[FlowOnlyPipeDescr_13384_V3](
            SetFlowOnlyPipeProp_13384_V3.SetInitialDirection    (AzimuthDirection.Right, InclinationDirection.Horizontal),
            SetFlowOnlyPipeProp_13384_V3.SetInitialPosition     (10.cm, 10.cm, 10.cm                                    ),
            SetFlowOnlyPipeProp_13384_V3.SetFinalPosition       (20.cm, 20.cm, 20.cm                                    ),
            AddFlowOnlyPipeElement_13384_V3.AddSectionHorizontal("test", 10.cm                                          )
        )
        val result = normalizeToFramedAirIntakePipes(descr)
        result.descr should have size 1
        result.descr.head shouldBe AddFlowOnlyPipeElement_13384_V4.AddSectionHorizontal("test", 10.cm)
    }

    it should "handle empty descriptor sequence" in {
        val result = normalizeToFramedAirIntakePipes(Seq.empty)
        result.initialDir shouldBe PipeInitialDirection.default
        result.position shouldBe AirIntakePosition.FinalAuto
        result.descr shouldBe empty
    }

    it should "strip tracking properties from post-firebox slots" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    SetFlowOnlyPipeProp_15544_V3
                        .SetInitialDirection                            (AzimuthDirection.Right, InclinationDirection.Horizontal),
                    AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("test", 10.cm                                          )
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.slots(0) match
            case PostFireboxPipeDescrSlot_V7.FlueSlot(d) =>
                d should have size 1
                d.head shouldBe AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal("test", 10.cm)
            case _                                       => fail("Slot should be a FlueSlot")
    }

    it should "use default direction when first non-NoFlueSlot is missing it" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("test", 10.cm)
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialDirection shouldBe PipeInitialDirection.default
    }

    it should "use Auto position when first non-NoFlueSlot is missing it" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("test", 10.cm)
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialPosition shouldBe PostFireboxStartPosition.Auto
    }

    it should "use last SetInitialDirection in the first non-NoFlueSlot" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    SetFlowOnlyPipeProp_15544_V3
                        .SetInitialDirection                        (AzimuthDirection.Right, InclinationDirection.Horizontal),
                    SetFlowOnlyPipeProp_15544_V3.SetInitialDirection(
                        AzimuthDirection.Front,
                        InclinationDirection.Horizontal
                    )
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialDirection shouldBe PipeInitialDirection(
            AzimuthDirection.Front,
            InclinationDirection.Horizontal
        )
    }

    it should "always use Auto position (V6 position values are discarded)" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    SetFlowOnlyPipeProp_15544_V3.SetInitialPosition(10.cm, 20.cm, 30.cm),
                    SetFlowOnlyPipeProp_15544_V3.SetInitialPosition(40.cm, 50.cm, 60.cm)
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialPosition shouldBe PostFireboxStartPosition.Auto
    }

    it should "skip NoFlueSlot when searching for initial frame" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.NoFlueSlot,
            PostFireboxPipeDescrSlot.FlueSlot(
                Seq(
                    SetFlowOnlyPipeProp_15544_V3.SetInitialDirection(
                        AzimuthDirection.Right,
                        InclinationDirection.Horizontal
                    )
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialDirection shouldBe PipeInitialDirection(
            AzimuthDirection.Right,
            InclinationDirection.Horizontal
        )
    }

    it should "use defaults when all slots are NoFlueSlot" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.NoFlueSlot,
            PostFireboxPipeDescrSlot.NoFlueSlot
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.initialDirection shouldBe PipeInitialDirection.default
        result.initialPosition shouldBe PostFireboxStartPosition.Auto
    }

    it should "handle empty slot sequence" in {
        val result = normalizeToFramedPostFireboxPipes(Seq.empty)
        result.initialDirection shouldBe PipeInitialDirection.default
        result.initialPosition shouldBe PostFireboxStartPosition.Auto
        result.slots shouldBe empty
    }

    it should "migrate slots correctly to V7" in {
        val slots  = Seq[PostFireboxPipeDescrSlot](
            PostFireboxPipeDescrSlot.FlueSlot     (
                Seq(
                    AddFlowOnlyPipeElement_15544_V3.AddSectionHorizontal("flue", 10.cm)
                )
            ),
            PostFireboxPipeDescrSlot.ConnectorSlot(
                Seq(
                    AddThermalPipeElement_13384_V3.AddSectionVertical("conn", 5.cm)
                )
            )
        )
        val result = normalizeToFramedPostFireboxPipes(slots)
        result.slots should have size 2
        result.slots(0) shouldBe PostFireboxPipeDescrSlot_V7.FlueSlot     (
            Seq(
                AddFlowOnlyPipeElement_15544_V4.AddSectionHorizontal("flue", 10.cm)
            )
        )
        result.slots(1) shouldBe PostFireboxPipeDescrSlot_V7.ConnectorSlot(
            Seq(
                AddThermalPipeElement_13384_V4.AddSectionVertical("conn", 5.cm)
            )
        )
    }
