/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.dto.common.{NbOfFlows, PipeShape}
import afpma.firecalc.dto.instances.V7Instances.given
import afpma.firecalc.dto.v7.{
    AddFlowOnlyPipeElement_13384_V4,
    AddFlowOnlyPipeElement_15544_V4,
    AddThermalPipeElement_13384_V4,
    FlowOnlyPipeDescr_13384_V4,
    FlowOnlyPipeDescr_15544_V4,
    ThermalPipeDescr_13384_V4
}

import afpma.firecalc.units.coulombutils.*

import io.circe.syntax.*
import io.circe.parser.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues.convertOptionToValuable

/**
 * Decode / JSON round-trip tests for the new Split/Merge DTO types.
 *
 * Verifies that circe codecs (derived via semiauto on the sealed traits)
 * correctly encode and decode both `SplitSingleFlowIntoTwoFlowsWith90DegTurn`
 * and `MergeTwoFlowsIntoSingleWith90DegTurn` across all three descriptor families.
 */
class V7SplitMergeDecodeSuite extends AnyFreeSpec with Matchers:

    private val shape = PipeShape.Circle(0.2.meters)
    private val absDir: AbsoluteDirection = AbsoluteDirection(
        afpma.firecalc.dto.v4.AzimuthDirection.Front,
        afpma.firecalc.dto.v4.InclinationDirection.Up
    )

    // ── EN 15544 FlowOnly ────────────────────────────────────────────────

    "EN 15544 FlowOnly Split round-trip" in {
        val orig: FlowOnlyPipeDescr_15544_V4 = AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "split-test",
            absDir        = Some(absDir),
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[FlowOnlyPipeDescr_15544_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 15544 FlowOnly Merge round-trip" in {
        val orig: FlowOnlyPipeDescr_15544_V4 = AddFlowOnlyPipeElement_15544_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "merge-test",
            absDir        = None,
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[FlowOnlyPipeDescr_15544_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 15544 Split n_flows == 2" in {
        val split = AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "s",
            newInnerShape = shape
        )
        split.n_flows `shouldBe` NbOfFlows(2)
    }

    "EN 15544 Merge n_flows == 1" in {
        val merge = AddFlowOnlyPipeElement_15544_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "m",
            newInnerShape = shape
        )
        merge.n_flows `shouldBe` NbOfFlows(1)
    }

    // ── EN 13384 FlowOnly ────────────────────────────────────────────────

    "EN 13384 FlowOnly Split round-trip" in {
        val orig: FlowOnlyPipeDescr_13384_V4 = AddFlowOnlyPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "split-test",
            absDir        = Some(absDir),
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[FlowOnlyPipeDescr_13384_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 13384 FlowOnly Merge round-trip" in {
        val orig: FlowOnlyPipeDescr_13384_V4 = AddFlowOnlyPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "merge-test",
            absDir        = None,
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[FlowOnlyPipeDescr_13384_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 13384 Split n_flows == 2" in {
        val split = AddFlowOnlyPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "s",
            newInnerShape = shape
        )
        split.n_flows `shouldBe` NbOfFlows(2)
    }

    "EN 13384 Merge n_flows == 1" in {
        val merge = AddFlowOnlyPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "m",
            newInnerShape = shape
        )
        merge.n_flows `shouldBe` NbOfFlows(1)
    }

    // ── EN 13384 Thermal ─────────────────────────────────────────────────

    "EN 13384 Thermal Split round-trip" in {
        val orig: ThermalPipeDescr_13384_V4 = AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "split-test",
            absDir        = Some(absDir),
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[ThermalPipeDescr_13384_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 13384 Thermal Merge round-trip" in {
        val orig: ThermalPipeDescr_13384_V4 = AddThermalPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "merge-test",
            absDir        = None,
            newInnerShape = shape
        )
        val json = orig.asJson.noSpaces
        val decoded = decode[ThermalPipeDescr_13384_V4](json)
        decoded `shouldBe` Right(orig)
    }

    "EN 13384 Thermal Split n_flows == 2" in {
        val split = AddThermalPipeElement_13384_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
            name          = "s",
            newInnerShape = shape
        )
        split.n_flows `shouldBe` NbOfFlows(2)
    }

    "EN 13384 Thermal Merge n_flows == 1" in {
        val merge = AddThermalPipeElement_13384_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "m",
            newInnerShape = shape
        )
        merge.n_flows `shouldBe` NbOfFlows(1)
    }

    // ── JSON discriminator keys ──────────────────────────────────────────

    "Split encodes with correct constructor key in EN 15544" in {
        val split: FlowOnlyPipeDescr_15544_V4 =
            AddFlowOnlyPipeElement_15544_V4.SplitSingleFlowIntoTwoFlowsWith90DegTurn         (
                name          = "s",
                newInnerShape = shape
            )
        val key = split.asJson.asObject.flatMap(_.keys.toList.headOption).value
        key `shouldBe` "SplitSingleFlowIntoTwoFlowsWith90DegTurn"
    }

    "Merge encodes with correct constructor key in EN 15544" in {
        val merge: FlowOnlyPipeDescr_15544_V4 = AddFlowOnlyPipeElement_15544_V4.MergeTwoFlowsIntoSingleWith90DegTurn(
            name          = "m",
            newInnerShape = shape
        )
        val key = merge.asJson.asObject.flatMap(_.keys.toList.headOption).value
        key `shouldBe` "MergeTwoFlowsIntoSingleWith90DegTurn"
    }

end V7SplitMergeDecodeSuite
