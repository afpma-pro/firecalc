/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.models.FluePipeT

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.engine.standard.{SlotContext, SlotIndex}
import afpma.firecalc.engine.alg.SplitGeometryValidationBehaviors
import afpma.firecalc.engine.alg.SplitGeometryValidationBehaviors.SplitGeometryFixture

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * EN 15544 tests for split geometry validation using shared behaviors.
 *
 * Mirrors the coverage in EN 13384 `SplitGeometryValidationSuite` but exercises
 * the EN 15544 flow-only builder path. Previously this engine had no test coverage
 * for ascending branch direction validation — all split tests used `absDir = None`.
 */
class SplitGeometryValidationSuite extends AnyFlatSpec with Matchers with SplitGeometryValidationBehaviors:

    given FluePipeT = FluePipeT

    // ── Builder (class-level so path-dependent types unify) ─────────────
    val builder = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
    import builder.*

    // ── Direction helpers ────────────────────────────────────────────────
    private val horizontalDir = PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    private val ascendingDir  = PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Up)

    private val ascendingBranchDir        = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Custom(45.degrees))
    private val horizontalBranchDir       = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    private val collinearBranchDir        = AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    private val nonPerpendicularBranchDir =
        AbsoluteDirection(AzimuthDirection.RearRight, InclinationDirection.Horizontal)

    // ── Setup props ──────────────────────────────────────────────────────
    private val setup = Vector[FlowOnlyPipeDescr_15544](
        SetFlowOnlyPipeProp_15544.SetInnerShape(PipeShape.Circle(20.cm)),
        SetFlowOnlyPipeProp_15544.SetRoughness(1.mm)
    )

    // ── Helper: build incremental descriptor and convert to full ────────
    private def buildAndConvert(
        initialDir: Option[PipeInitialDirection],
        descrs    : Seq[FlowOnlyPipeDescr_15544]
    )                          : Either[Vector[Any], PipeFullDescr]                           =
        // Use a fresh builder for None to avoid wrapperInitialDirection leaking from previous tests.
        // The class-level `builder` is reused for Some(dir) to keep path-dependent types unified.
        val useBuilder =
            initialDir match
                case None =>
                    afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
                case _    => builder
        import useBuilder.*
        val incrDescr  =
            initialDir match
                case Some(dir) => withInitialDirection(dir).define(descrs*)
                case None      => define(descrs*)
        incrDescr.toFullDescr(using SlotContext.forSlot(SlotIndex.unsafe(0))).toEither match
            case Right((_, pfd)) => Right(pfd.asInstanceOf[builder.PipeFullDescr])
            case Left(errs)      => Left(errs.toList.to(Vector))
    private val flowOnlyFixture: SplitGeometryFixture[FlowOnlyPipeDescr_15544, PipeFullDescr] =
        new SplitGeometryFixture[FlowOnlyPipeDescr_15544, PipeFullDescr]:

            override val horizontalDir: PipeInitialDirection = SplitGeometryValidationSuite.this.horizontalDir
            override val ascendingDir : PipeInitialDirection = SplitGeometryValidationSuite.this.ascendingDir

            override val ascendingBranchDir       : AbsoluteDirection = SplitGeometryValidationSuite.this.ascendingBranchDir
            override val horizontalBranchDir      : AbsoluteDirection = SplitGeometryValidationSuite.this.horizontalBranchDir
            override val collinearBranchDir       : AbsoluteDirection = SplitGeometryValidationSuite.this.collinearBranchDir
            override val nonPerpendicularBranchDir: AbsoluteDirection =
                SplitGeometryValidationSuite.this.nonPerpendicularBranchDir

            override val setup: Vector[FlowOnlyPipeDescr_15544] = SplitGeometryValidationSuite.this.setup

            override def buildWith(
                initialDir: PipeInitialDirection,
                descrs    : Seq[FlowOnlyPipeDescr_15544]
            ): Either[Vector[Any], PipeFullDescr] =
                buildAndConvert(Some(initialDir), descrs)

            override def buildWithoutInitialDirection(
                descrs: Seq[FlowOnlyPipeDescr_15544]
            ): Either[Vector[Any], PipeFullDescr] =
                buildAndConvert(None, descrs)

            override def addSectionSlopped(name: String, length: Length): FlowOnlyPipeDescr_15544 =
                AddFlowOnlyPipeElement_15544.AddSectionSlopped(name, length)

            override def addSplit(name: String, absDir: AbsoluteDirection): FlowOnlyPipeDescr_15544 =
                AddFlowOnlyPipeElement_15544.SplitSingleFlowIntoTwoFlowsWith90DegTurn       (
                    name,
                    newInnerShape        = PipeShape.Circle(9.cm),
                    absDir               = Some(absDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Front)
                )

            override def addMerge(name: String, absDir: AbsoluteDirection): FlowOnlyPipeDescr_15544 =
                AddFlowOnlyPipeElement_15544.MergeTwoFlowsIntoSingleWith90DegTurn       (
                    name,
                    newInnerShape        = PipeShape.Circle(15.cm),
                    absDir               = Some(absDir),
                    symmetryPlaneAzimuth = Some(AzimuthDirection.Front)
                )

            override def assertFirstElementIsSplitMerge90(pfd: PipeFullDescr, expectedNFlows: NbOfFlows): Unit =
                import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
                val first = pfd.elems.head
                first.el match
                    case sm: SplitMerge90 =>
                        sm.nFlows shouldBe expectedNFlows
                    case _ =>
                        fail(s"Expected SplitMerge90 but got ${first.el.getClass.getSimpleName}")

    // ── Shared behaviors ─────────────────────────────────────────────────
    "SplitSingleFlowIntoTwoFlowsWith90DegTurn (EN 15544 flow-only)" should behave like splitWithPrecedingSection(
        "reject invalid split geometry and allow valid splits",
        flowOnlyFixture
    )

    it should behave like splitAsFirstElement(
        "accept split as first element",
        flowOnlyFixture
    )

    it should behave like splitAsFirstElementAscending(
        "accept split as first element on ascending pipe",
        flowOnlyFixture
    )

    it should behave like splitWithoutInitialDirection(
        "reject split without initial direction",
        flowOnlyFixture
    )

end SplitGeometryValidationSuite
