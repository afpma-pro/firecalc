/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.dto.all.NbOfFlows
import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.engine.standard.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Shared ScalaTest behaviors for split geometry validation.
 *
 * Covers the validation rules enforced by `SplitGeometryValidator.validateSplitPositions`:
 *   - Branch direction must be perpendicular to incoming (90° turn)
 *   - Reflected branch must not ascend (z > 0)
 *   - Branch must not be collinear with incoming
 *   - Merge direction is always allowed
 *   - Split as first element (no preceding section) is accepted
 *   - Split without initial direction is rejected
 *
 * Concrete suites provide their own builder, setup props, and element types
 * via the `SplitGeometryFixture` parameter.
 */
trait SplitGeometryValidationBehaviors { this: AnyFlatSpec & Matchers =>

    import SplitGeometryValidationBehaviors.*

    /**
     * Behaviors for a flow-only builder with a preceding section before the split.
     * Covers: ascending reject, horizontal allow, collinear reject, non-perpendicular reject,
     * merge allowed, horizontal branch on ascending allowed.
     */
    def splitWithPrecedingSection[IncrDescr, FullDescr](
        name   : String,
        fixture: SplitGeometryFixture[IncrDescr, FullDescr]
    )(using org.scalactic.source.Position) = {
        name should "reject split with non-perpendicular branch on ascending pipe" in {
            val descr = fixture.buildWith(
                fixture.ascendingDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preSplit", 1.meters               ),
                    fixture.addSplit         ("split", fixture.ascendingBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                   )
                )
            )
            descr.left.toOption.get
                .exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
        }

        it should "allow split with horizontal reflected branch on horizontal pipe" in {
            val descr = fixture.buildWith(
                fixture.horizontalDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preSplit", 1.meters                ),
                    fixture.addSplit         ("split", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            descr.isRight shouldBe true
        }

        it should "reject split with collinear branch direction" in {
            val descr = fixture.buildWith(
                fixture.horizontalDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preSplit", 1.meters               ),
                    fixture.addSplit         ("split", fixture.collinearBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                   )
                )
            )
            descr.left.toOption.get
                .exists(_.isInstanceOf[SplitBranchesCollinear]) shouldBe true
        }

        it should "reject split with non-perpendicular branch direction" in {
            val descr = fixture.buildWith(
                fixture.horizontalDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preSplit", 1.meters                      ),
                    fixture.addSplit         ("split", fixture.nonPerpendicularBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                          )
                )
            )
            descr.left.toOption.get
                .exists(_.isInstanceOf[SplitBranchesNotOpposite]) shouldBe true
        }

        it should "allow merge with absDir on ascending pipe" in {
            val descr = fixture.buildWith(
                fixture.ascendingDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preMerge", 1.meters                ),
                    fixture.addMerge         ("merge", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            descr.isRight shouldBe true
        }

        it should "allow split with horizontal branch on ascending pipe" in {
            val descr = fixture.buildWith(
                fixture.ascendingDir,
                fixture.setup ++ Seq(
                    fixture.addSectionSlopped("preSplit", 1.meters                ),
                    fixture.addSplit         ("split", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            descr.isRight shouldBe true
        }
    }

    /** Behaviors for split as the first element (no preceding section). */
    def splitAsFirstElement[IncrDescr, FullDescr](
        name   : String,
        fixture: SplitGeometryFixture[IncrDescr, FullDescr]
    )(using org.scalactic.source.Position) = {
        name should "be accepted and produce SplitMerge90 with nFlows=2" in {
            val descr = fixture.buildWith(
                fixture.horizontalDir,
                fixture.setup ++ Seq(
                    fixture.addSplit         ("split", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            val pfd   = descr.toOption.get
            fixture.assertFirstElementIsSplitMerge90(pfd, NbOfFlows(2))
        }
    }

    /** Behaviors for split as first element on ascending pipe. */
    def splitAsFirstElementAscending[IncrDescr, FullDescr](
        name   : String,
        fixture: SplitGeometryFixture[IncrDescr, FullDescr]
    )(using org.scalactic.source.Position) = {
        name should "be accepted and produce SplitMerge90 with nFlows=2" in {
            val descr = fixture.buildWith(
                fixture.ascendingDir,
                fixture.setup ++ Seq(
                    fixture.addSplit         ("split", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            val pfd   = descr.toOption.get
            fixture.assertFirstElementIsSplitMerge90(pfd, NbOfFlows(2))
        }
    }

    /** Behaviors for split without initial direction — must be rejected. */
    def splitWithoutInitialDirection[IncrDescr, FullDescr](
        name   : String,
        fixture: SplitGeometryFixture[IncrDescr, FullDescr]
    )(using org.scalactic.source.Position) = {
        name should "be rejected with GeometryWithoutInitialDirection" in {
            val descr = fixture.buildWithoutInitialDirection(
                fixture.setup ++ Seq(
                    fixture.addSplit         ("split", fixture.horizontalBranchDir),
                    fixture.addSectionSlopped("dual", 1.meters                    )
                )
            )
            val errs  = descr.left.toOption.getOrElse(Vector.empty)
            errs.exists(_.isInstanceOf[GeometryWithoutInitialDirection]) shouldBe true
        }
    }
}

object SplitGeometryValidationBehaviors {
    import afpma.firecalc.units.coulombutils.*

    /**
     * Engine-specific fixture for shared split geometry validation tests.
     *
     * @tparam IncrDescr the incremental descriptor type (both props and elements)
     * @tparam FullDescr the full descriptor type returned by toFullDescr
     */
    /**
     * Engine-specific fixture for shared split geometry validation tests.
     *
     * @tparam IncrDescr the incremental descriptor type (both props and elements)
     * @tparam FullDescr the full descriptor type returned by build methods
     */
    trait SplitGeometryFixture[IncrDescr, FullDescr] {

        // ── Direction constants (shared across all engines) ─────────────
        val horizontalDir: PipeInitialDirection
        val ascendingDir : PipeInitialDirection

        val ascendingBranchDir       : AbsoluteDirection
        val horizontalBranchDir      : AbsoluteDirection
        val collinearBranchDir       : AbsoluteDirection
        val nonPerpendicularBranchDir: AbsoluteDirection

        // ── Setup props (inner shape, roughness, etc.) ─────────────────
        val setup: Vector[IncrDescr]

        // ── Builder + build (returns converted result) ─────────────────
        def buildWith                   (initialDir: PipeInitialDirection, descrs: Seq[IncrDescr]): Either[Vector[Any], FullDescr]
        def buildWithoutInitialDirection(descrs    : Seq[IncrDescr]                              ): Either[Vector[Any], FullDescr]

        // ── Element constructors ───────────────────────────────────────
        def addSectionSlopped(name: String, length: Length           ): IncrDescr
        def addSplit         (name: String, absDir: AbsoluteDirection): IncrDescr
        def addMerge         (name: String, absDir: AbsoluteDirection): IncrDescr

        // ── Result assertion (engine-specific SplitMerge90 type) ───────
        def assertFirstElementIsSplitMerge90(pfd: FullDescr, expectedNFlows: NbOfFlows): Unit
    }
}
