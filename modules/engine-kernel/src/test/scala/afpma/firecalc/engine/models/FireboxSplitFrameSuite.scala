/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4
import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PostFireboxPipeSlot

import afpma.firecalc.domain.PipeShape
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

class FireboxSplitFrameSuite extends AnyFlatSpec with Matchers:

    // Default seed with Vec3.Rear direction so we can verify direction changes to Up
    val defaultSeed = PipeBuildSeed.fromFrame(Some(PipeFrame.initial(Vec3.Rear)))

    // Minimal split descriptors for FlueSlot and ThermalFlueSlot
    val flowOnlySplit =
        AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn         (
            name          = "split",
            newInnerShape = PipeShape.Circle(0.1.meters)
        )

    val thermalSplit =
        AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn         (
            name          = "split",
            newInnerShape = PipeShape.Circle(0.1.meters)
        )

    // Non-split descriptors
    val flowOnlySection =
        AddFlowOnlyPipeElement_15544_V4.AddSectionSlopped  (
            name   = "section",
            length = 1.0.meters
        )

    val thermalSection =
        AddThermalPipeElement_13384_V4.AddSectionSlopped  (
            name   = "section",
            length = 1.0.meters
        )

    "resolveInitialSeed" should "set seed direction to Up when FlueSlot starts with split" in {
        val slot   = PostFireboxPipeSlot.FlueSlot(Seq(flowOnlySplit))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result.frame.isDefined should be(true)
        result.frame.get.direction shouldBe Vec3.Up
        result.nFlows shouldBe NbOfFlows(2)
    }

    it should "set seed direction to Up when ThermalFlueSlot starts with split" in {
        val slot   = PostFireboxPipeSlot.ThermalFlueSlot(Seq(thermalSplit))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result.frame.isDefined should be(true)
        result.frame.get.direction shouldBe Vec3.Up
        result.nFlows shouldBe NbOfFlows(2)
    }

    it should "keep default seed unchanged when FlueSlot starts with non-split" in {
        val slot   = PostFireboxPipeSlot.FlueSlot(Seq(flowOnlySection))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result shouldBe defaultSeed
    }

    it should "keep default seed unchanged when ThermalFlueSlot starts with non-split" in {
        val slot   = PostFireboxPipeSlot.ThermalFlueSlot(Seq(thermalSection))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result shouldBe defaultSeed
    }

    it should "set seed direction to Up when ConnectorSlot starts with split" in {
        val slot   = PostFireboxPipeSlot.ConnectorSlot(Seq(thermalSplit))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result.frame.isDefined should be(true)
        result.frame.get.direction shouldBe Vec3.Up
        result.nFlows shouldBe NbOfFlows(2)
    }

    it should "set seed direction to Up when ChimneySlot starts with split" in {
        val slot   = PostFireboxPipeSlot.ChimneySlot(Seq(thermalSplit))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result.frame.isDefined should be(true)
        result.frame.get.direction shouldBe Vec3.Up
        result.nFlows shouldBe NbOfFlows(2)
    }

    it should "keep default seed unchanged when ConnectorSlot starts with non-split" in {
        val slot   = PostFireboxPipeSlot.ConnectorSlot(Seq(thermalSection))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result shouldBe defaultSeed
    }

    it should "keep default seed unchanged when ChimneySlot starts with non-split" in {
        val slot   = PostFireboxPipeSlot.ChimneySlot(Seq(thermalSection))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result shouldBe defaultSeed
    }
    it should "keep default seed unchanged when split is not first element" in {
        val slot   = PostFireboxPipeSlot.FlueSlot(Seq(flowOnlySection, flowOnlySplit))
        val result = FireboxSplitFrame.resolveInitialSeed(slot, defaultSeed)

        result shouldBe defaultSeed
    }

end FireboxSplitFrameSuite
