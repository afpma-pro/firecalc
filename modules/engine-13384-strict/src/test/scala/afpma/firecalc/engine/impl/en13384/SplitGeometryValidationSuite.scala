/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.standard.SplitBranchesCollinear
import afpma.firecalc.engine.standard.SplitBranchesNotOpposite

import afpma.firecalc.domain.AbsoluteDirection
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for split geometry validation in `updateStateAfterConversionStep`.
 *
 * This is the single point of truth for split validation (Path 2). It covers:
 *   - Splits placed directly without preceding `SetNumberOfFlows`
 *   - Splits with ascending reflected branch (forbidden)
 *   - Splits with horizontal/descending reflected branch (allowed)
 *   - Splits with collinear branch direction (forbidden)
 *   - Merges (direction always allowed; position validated in postBuildValidation)
 */
class SplitGeometryValidationSuite extends AnyFlatSpec with Matchers:
    given FluePipeT = FluePipeT

    // ── Direction helpers ────────────────────────────────────────────────
    private val horizontalDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    private val ascendingDir  = PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Up)

    private val ascendingBranchDir                                    = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Custom(45.degrees))
    private val horizontalBranchDir                                   = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    private val collinearBranchDir                                    = AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    // RearRight (45° azimuth) + Horizontal → dot with incoming (Rear) = 0.707 → not perpendicular
    private val nonPerpendicularBranchDir                             =
        AbsoluteDirection(AzimuthDirection.RearRight, InclinationDirection.Horizontal)
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
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn (EN 13384 flow-only)" should "reject split with non-perpendicular branch on ascending pipe" in {
        // Incoming = Up (0,0,1), branch = Up-Right (0.707,0,0.707) → dot=0.707 → not perpendicular → rejected.
        // Note: the ascending check is NOT dead code. For ascending incoming with perpendicular descending branch,
        // the reflected branch ascends (e.g., i=(0,0.6,0.8), o₁=(0,0.8,-0.6) → o₂=(0,-0.8,0.6) → z>0).
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                              ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn       (
                    "split",
                    newInnerShape        = PipeShape.Circle(9.cm),
                    absDir               = Some(ascendingBranchDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                              ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
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
    it should "reject split with collinear branch direction" in {
        // Branch direction same as incoming → collinear → not a real split.
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(collinearBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesCollinear]) shouldBe true
    }
    it should "reject split with non-perpendicular branch direction" in {
        // Incoming = Rear (horizontal), branch = RearRight (45° azimuth, horizontal) → dot = 0.707 → not perpendicular.
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(nonPerpendicularBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
    }
    it should "allow merge with absDir on ascending pipe" in {
        // Merge is always allowed regardless of geometry.
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                          ("preMerge", 1.meters ),
                AddFlowOnlyPipeElement_13384.MergeTwoFlowsIntoSingleWith90DegTurn       (
                    "merge",
                    newInnerShape        = PipeShape.Circle(20.cm),
                    absDir               = Some(horizontalBranchDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                          ("postMerge", 1.meters)
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
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                              ("preSplit", 1.meters),
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn       (
                    "split",
                    newInnerShape        = PipeShape.Circle(9.cm),
                    absDir               = Some(horizontalBranchDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                              ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().isValid shouldBe true
    }

    // ── EN 13384 Thermal ────────────────────────────────────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn (EN 13384 thermal)" should "reject split with non-perpendicular branch on ascending pipe" in {
        val builder = makeThermalBuilder(ascendingDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                              ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn       (
                    "split",
                    newInnerShape        = PipeShape.Circle(9.cm),
                    absDir               = Some(ascendingBranchDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                              ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
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
    it should "reject split with collinear branch direction" in {
        // Branch direction same as incoming → collinear → not a real split.
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(collinearBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesCollinear]) shouldBe true
    }
    it should "reject split with non-perpendicular branch direction" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.AddSectionSlopped                       ("preSplit", 1.meters),
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(nonPerpendicularBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters    )
            ))*
        )
        descr.toFullDescr().toEither.left.toOption.get.exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
    }
    // ── Split as first element (no preceding section) ──────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn as first element (flow-only)" should "be accepted and produce SplitMerge90 with nFlows=2" in {
        val builder = makeFlowOnlyBuilder(horizontalDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters)
            ))*
        )
        val (_, pfd) = descr.toFullDescr().toEither.toOption.get
        pfd.elems.head.el match
            case sm: afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.SplitMerge90 =>
                sm.nFlows shouldBe 2.flows
            case other => fail(s"Expected SplitMerge90 but got ${other.getClass.getSimpleName}")
    }

    "SplitSingleFlowIntoTwoFlowsWith90DegTurn as first element (thermal)" should "be accepted and produce SplitMerge90 with nFlows=2" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters)
            ))*
        )
        val (_, pfd) = descr.toFullDescr().toEither.toOption.get
        pfd.elems.head.el match
            case sm: afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SplitMerge90 =>
                sm.nFlows shouldBe 2.flows
            case other => fail(s"Expected SplitMerge90 but got ${other.getClass.getSimpleName}")
    }
    // ── Split as first element — ascending pipe (flow-only) ────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn as first element on ascending pipe (flow-only)" should "be accepted and produce SplitMerge90 with nFlows=2" in {
        val builder = makeFlowOnlyBuilder(ascendingDir)
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn       (
                    "split",
                    newInnerShape        = PipeShape.Circle(9.cm),
                    absDir               = Some(horizontalBranchDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Right)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                              ("dual", 1.meters)
            ))*
        )
        val (_, pfd) = descr.toFullDescr().toEither.toOption.get
        pfd.elems.head.el match
            case sm: afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384.SplitMerge90 =>
                sm.nFlows shouldBe 2.flows
            case other => fail(s"Expected SplitMerge90 but got ${other.getClass.getSimpleName}")
    }

    // ── Split as first element — horizontal pipe (thermal) ─────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn as first element on horizontal pipe (thermal)" should "be accepted and produce SplitMerge90 with nFlows=2" in {
        val builder = makeThermalBuilder(horizontalDir)
        val descr   = builder.define(
            (thermalSetup ++ Seq(
                AddThermalPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddThermalPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters)
            ))*
        )
        val (_, pfd) = descr.toFullDescr().toEither.toOption.get
        pfd.elems.head.el match
            case sm: afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SplitMerge90 =>
                sm.nFlows shouldBe 2.flows
            case other => fail(s"Expected SplitMerge90 but got ${other.getClass.getSimpleName}")
    }
    // ── Split without initial direction (no frame) ─────────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn without initial direction" should "be rejected with GeometryWithoutInitialDirection" in {
        // Builder without withInitialDirection → no frame → split cannot validate geometry.
        val builder = FlowOnlyIncrementalBuilder_13384.makeFor[FluePipeT]
        val descr   = builder.define(
            (flowOnlySetup ++ Seq(
                AddFlowOnlyPipeElement_13384.SplitSingleFlowIntoTwoFlowsWith90DegTurn(
                    "split",
                    newInnerShape = PipeShape.Circle(9.cm),
                    absDir        = Some(horizontalBranchDir)
                ),
                AddFlowOnlyPipeElement_13384.AddSectionSlopped                       ("dual", 1.meters)
            ))*
        )
        descr
            .toFullDescr()
            .toEither
            .left
            .toOption
            .get
            .exists(_.isInstanceOf[afpma.firecalc.engine.standard.GeometryWithoutInitialDirection]) shouldBe true
    }
end SplitGeometryValidationSuite
