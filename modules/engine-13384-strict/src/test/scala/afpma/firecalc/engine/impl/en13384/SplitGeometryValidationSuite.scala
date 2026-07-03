/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.standard.SplitBranchesCollinear
import afpma.firecalc.engine.standard.SplitReflectedBranchAscends
import afpma.firecalc.units.coulombutils.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for split geometry validation in `updateStateAfterConversionStep`.
 *
 * This is the single point of truth for split validation (Path 2). It covers:
 *   - Splits placed directly without preceding `SetNumberOfFlows`
 *   - Splits with ascending reflected branch (forbidden)
 *   - Splits with horizontal/descending reflected branch (allowed)
 *   - Splits with no absDir (forbidden)
 *   - Merges (always allowed)
 */
class SplitGeometryValidationSuite extends AnyFlatSpec with Matchers:
    given FluePipeT = FluePipeT

    // ── Direction helpers ────────────────────────────────────────────────
    private val horizontalDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    private val ascendingDir  = PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Up)

    private val ascendingBranchDir                                    = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Custom(45.degrees))
    private val horizontalBranchDir                                   = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    // ── Fixture builders ───────────────────────────────────────────────
    private def makeFlowOnlyBuilder(initialDir: PipeInitialDirection) =
        FlowOnlyIncrementalBuilder_13384.makeFor[FluePipeT].withInitialDirection(initialDir)

    private def makeThermalBuilder(initialDir: PipeInitialDirection) =
        ThermalIncrementalBuilder_13384.makeFor[FluePipeT].withInitialDirection(initialDir)

    private val flowOnlySetup = Vector(
        SetFlowOnlyPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)),
        SetFlowOnlyPipeProp_13384.SetRoughness(1.mm)
    )

    private val thermalSetup = Vector(
        SetThermalPipeProp_13384.SetInnerShape(PipeShape.Circle(20.cm)                              ),
        SetThermalPipeProp_13384.SetRoughness   (1.mm                          ),
        SetThermalPipeProp_13384.SetMaterial  (afpma.firecalc.dto.v3.Material_13384_V2.WeldedSteel()),
        SetThermalPipeProp_13384.SetLayer       (2.mm, WattsPerMeterKelvin(1.2)),
        SetThermalPipeProp_13384.SetPipeLocation(PipeLocation.HeatedArea       )
    )
    // ── EN 13384 Flow-Only ──────────────────────────────────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn (EN 13384 flow-only)" should "reject split with ascending reflected branch" in {
        // Incoming = Up, branch1 = Up-Right \u2192 reflected = Up-Left \u2192 ascending \u2192 forbidden.
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(ascendingBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitReflectedBranchAscends]) shouldBe true
    }
    it should "allow split with horizontal reflected branch on horizontal pipe" in {
        // Incoming = Rear (horizontal), branch1 = Right \u2192 reflected = Left \u2192 horizontal \u2192 allowed.
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }
    it should "reject split without absDir (collinear)" in {
        // No branch direction \u2192 collinear with incoming \u2192 not a real split.
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = None
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesCollinear]) shouldBe true
    }
    it should "allow merge with absDir on ascending pipe" in {
        // Merge is always allowed regardless of geometry.
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                   ("preMerge", 1.meters ),
                AddFlowOnlyPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn(
                    "merge",
                    newInnerShape = PipeShape.Circle(20.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                   ("postMerge", 1.meters)
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }
    it should "allow split with horizontal branch on ascending pipe" in {
        // Incoming = Up, branch1 = Right (horizontal) \u2192 reflected = Left (horizontal) \u2192 allowed.
        // A 90\u00b0 turn on a vertical pipe always produces horizontal branches.
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }
    // ── EN 13384 Thermal ────────────────────────────────────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn (EN 13384 thermal)" should "reject split with ascending reflected branch" in {
        val builder = makeThermalBuilder(ascendingDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(ascendingBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitReflectedBranchAscends]) shouldBe true
    }
    it should "allow split with horizontal reflected branch on horizontal pipe" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }
    it should "reject split without absDir (collinear)" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = None
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesCollinear]) shouldBe true
    }
end SplitGeometryValidationSuite
