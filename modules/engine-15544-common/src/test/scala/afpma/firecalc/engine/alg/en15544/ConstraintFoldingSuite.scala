/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran&#231;aise du Po&#234;le Ma&#231;onn&#233; Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeShape

import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.api.WireConversions.toWireShape
import afpma.firecalc.engine.biblio.kov.firebox_emissions.Standing_Standard_Burning_Firebox
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

import cats.data.ValidatedNel
import cats.syntax.validated.*

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Characterisation tests for the `constraints` folding method on `FireboxEngine`.
 *
 * Covers spec &#167;9 Slice 2 AC-1 through AC-5.
 *
 * Uses a synthetic `FireboxEngine[TraditionalFirebox]` that delegates all
 * constraint methods to `FireboxConstraints_Strict` and provides no-op pipe
 * contributions.  `engine_15544_strict` is not a test dependency of
 * `engine_15544_common`, so the engine instance is constructed here.
 *
 * NOTE: the proprietary Austrian engine module lives in the private
 * repository and is not a test dependency; AC-3 tests only the
 * `fctx.airIntakePipeShape` path (the Strict engine's `firebox_custom_constraints`
 * returns `Nil`, so no shape mismatch errors are emitted).
 */
class ConstraintFoldingSuite extends AnyFreeSpec with Matchers:
    given io.taig.babel.Locale = Locales.en

    // ── Synthetic test engine ──────────────────────────────────────────────

    /**
     * Minimal `FoldsConstraints[TraditionalFirebox]` for testing `constraints`.
     *
     * Mixes in `FireboxConstraints_Strict` and hands itself back as
     * `constraintsAlg`, so the fold under test delegates to the real strict
     * per-term constraints.  Pipe contributions are stubbed because the
     * `constraints` method does not use them.
     */
    private object TestEngine
        extends FoldsConstraints[TraditionalFirebox]
        with FireboxConstraints_Strict[TraditionalFirebox]:

        override def constraintsAlg: FireboxConstraints[TraditionalFirebox] = this

        override type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
        override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr

        override def kind: FireboxKind = FireboxKind.Traditional

        override def fireboxContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

    /**
     * A `FoldsConstraints[TraditionalFirebox]` that always emits an
     * `AirIntakePipeShapeMismatch` from `firebox_custom_constraints`, so the
     * folding's §4.7 shape-check branch is exercised even for a Traditional
     * firebox (whose real `firebox_custom_constraints` returns `Nil`).
     */
    private object ShapeTestEngine
        extends FoldsConstraints[TraditionalFirebox]
        with FireboxConstraints_Strict[TraditionalFirebox]:

        override def constraintsAlg: FireboxConstraints[TraditionalFirebox] = this

        override type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
        override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr

        override def kind: FireboxKind = FireboxKind.Traditional

        override def fireboxContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        /** Forces a catalog shape-mismatch error so the folding's §4.7 branch fires. */
        override def firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            List(AirIntakePipeShapeMismatch(expected = "Circle 0.2 m", actual = "Circle 0.173 m"))

    /**
     * A `FoldsConstraints[TraditionalFirebox]` that declares exactly one advisory
     * ("Hinweis") finding via `advisory_firebox_custom_constraints` and no
     * blocking custom errors.  Everything else is identical to `TestEngine`, so
     * the only difference in the folded verdict list is the advisory verdict.
     */
    private object AdvisoryTestEngine
        extends FoldsConstraints[TraditionalFirebox]
        with FireboxConstraints_Strict[TraditionalFirebox]:

        override def constraintsAlg: FireboxConstraints[TraditionalFirebox] = this

        override type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
        override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr

        override def kind: FireboxKind = FireboxKind.Traditional

        override def fireboxContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        /** One "Hinweis"-class finding; shown verbatim by the `FireboxErrorCustom` case. */
        override def advisory_firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            List(new FireboxErrorCustom(advisoryReason))

    /**
     * The fold-twin of [[AdvisoryTestEngine]]: the same finding declared on the
     * blocking channel (`firebox_custom_constraints`).  Pinning both sides shows
     * the advisory verdict shares its code and message rendering with a blocking
     * custom verdict, and differs only in the `blocking` flag.
     */
    private object BlockingCustomTwinEngine
        extends FoldsConstraints[TraditionalFirebox]
        with FireboxConstraints_Strict[TraditionalFirebox]:

        override def constraintsAlg: FireboxConstraints[TraditionalFirebox] = this

        override type FireboxPipe_FullDescr       = FireboxPipe_Module_15544.FullDescr
        override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr

        override def kind: FireboxKind = FireboxKind.Traditional

        override def fireboxContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

        /** The same finding, declared blocking — the twin of the advisory override. */
        override def firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] =
            List(new FireboxErrorCustom(advisoryReason))

    /** Shared fixture finding; `FireboxErrorCustom` renders its reason verbatim. */
    private val advisoryReason = "cleaning hatch obstructed by the air column"

    // ── Fixture: clean TraditionalFirebox ──────────────────────────────────

    /**
     * A TraditionalFirebox that passes all standard constraints.
     *
     * Dimensions chosen so that:
     *   - base area 50x50 = 2500 cm² ∈ [1000, 4000]
     *   - depth/width ratio = 1.0 ∈ [0.5, 2.0]
     *   - width 50 cm ≥ 23 cm
     *   - height 80 cm ∈ [76, 84] (H_BR ± 5%)
     *   - glass area 30x40 = 1200 cm² ≤ area_O_BR/5 = 4200 cm² (the built
     *     50x50x80 surface is 21000 cm²; also under the nominal O_BR/5 = 1800)
     *   - ash pit height 10 cm ≥ 5 cm minimum
     */
    private val cleanFirebox: TraditionalFirebox =
        TraditionalFirebox                     (
            emissions_values                      = Standing_Standard_Burning_Firebox,
            firebox_depth_B                       = 50.cm,
            firebox_width_A                       = 50.cm,
            firebox_height_H                      = 80.cm,
            pressure_loss_coefficient_from_door   = 0.5.unitless,
            total_air_intake_surface_area_on_door = 0.1.squareMeters,
            glass_width                           = 30.cm,
            glass_height                          = 40.cm,
            ash_pit_height_AF                     = 10.cm
        )

    /**
     * A `TraditionalFirebox` that exposes accepted air-intake shapes via the
     * Seam R member (`expectedAirIntakePipeShapes`), mimicking a Door15a
     * catalog firebox. Lets the folding's `ShapeOneOf` path be exercised
     * without the proprietary Austrian engine module (private repository,
     * not on the `engine_15544_common` test classpath): the folding reads the shapes
     * through the same virtual dispatch a real Door15a firebox uses.
     */
    private val catalogShapesFirebox: TraditionalFirebox =
        new TraditionalFirebox                      (
            firebox_depth_B                       = 50.cm,
            firebox_width_A                       = 50.cm,
            firebox_height_H                      = 80.cm,
            pressure_loss_coefficient_from_door   = 0.5.unitless,
            total_air_intake_surface_area_on_door = 0.1.squareMeters,
            glass_width                           = 30.cm,
            glass_height                          = 40.cm,
            ash_pit_height_AF                     = 10.cm
        ):
            override def expectedAirIntakePipeShapes: Option[List[PipeShape]] =
                Some(List(PipeShape.Circle(0.2.meters)))

    // ── Fixture: ConstraintContext ────────────────────────────────────────

    /**
     * A ConstraintContext with m_B = 10 kg (the default minimum for Traditional).
     *
     * O_BR = 900 * m_B = 9000 cm&#178; (EN 15544 required surface, formula (3)).
     * area_O_BR = 21000 cm&#178;, the BUILT §3.7 interior surface of `cleanFirebox`
     * (2 · (50·50 + 80·50 + 80·50)) — the glass-area-cap denominator.
     * A_BR_min/max derived from standard formulas for m_B=10.
     * H_BR = firebox height (80 cm).
     */
    private val constraintContext: ConstraintContext =
        ConstraintContext                           (
            m_B                            = 10.kg,
            O_BR                           = 9000.cm2,
            area_O_BR                      = 21000.cm2,
            FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = 0.5,
            FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = 2.0,
            A_BR_min                       = Some(1000.cm2),
            A_BR_max                       = Some(4000.cm2),
            A_BR                           = 2500.cm2,
            H_BR_min                       = None,
            H_BR                           = 80.cm,
            n_min                          = 70.percent
        )

    // ── Fixture: FireboxConstraintContext (no air intake shape) ───────────

    private val fireboxCtxNoShape: FireboxConstraintContext =
        FireboxConstraintContext                (
            mB                 = 10.kg,
            flow_rate          = None,
            airIntakePipeShape = None
        )

    // ── AC-1: verdict count + no None ─────────────────────────────────────

    "AC-1: verdict count and no None outcomes" - {

        "returns non-empty list of verdicts with expected count" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            assert(verdicts.nonEmpty, "constraints must return a non-empty verdict list")

            // Expected verdicts for TraditionalFirebox with ctx=Some:
            //   1. m_B          (Range)
            //   2. m_B_min      (Range)
            //   3. glassArea    (Max)
            //   4. fireboxBase_area    (Range)
            //   5. fireboxBase_ratio   (Opaque)
            //   6. fireboxBase_minWidth (Opaque)
            //   7. h_BR_min     (Range)
            //   8. h_BR_band    (Range)
            //   9. lambda       (Range)
            //   10. heightOfLowestOpening (Min)
            //   11. glassSurfaceRatio    (Opaque)
            //   12. airIntakePipeShape_topology (Opaque, NotApplicable since shape=None)
            //   13. nominalLoadInRange    (Range)
            // Total = 13 (11 standard + 1 topology + 1 nominal)
            // Custom errors = 0 (TraditionalFirebox has no custom errors)
            val expectedCount = 13
            verdicts.length shouldBe expectedCount
        }

        "no verdict has null outcome" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            verdicts.foreach { v =>
                assert(v.outcome != null)
            }
        }

        "no verdict outcome is a None-equivalent" in {
            // The old constraint methods returned Option[TermConstraint],
            // where None meant "not applicable".  The new folding method
            // always emits a verdict — even for inapplicable terms, the
            // outcome is NotApplicable, not None.
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // Every verdict must have a non-null outcome; since
            // ConstraintOutcome is a sealed trait with no None case,
            // this is satisfied by construction.  We verify explicitly.
            verdicts.foreach { v =>
                // outcome is ConstraintOutcome (sealed trait), never null
                v.outcome match
                    case ConstraintOutcome.Satisfied         => // ok
                    case ConstraintOutcome.Violated(_, _, _) => // ok
                    case ConstraintOutcome.NotEvaluated(_)   => // ok
                    case ConstraintOutcome.NotApplicable     => // ok
            }
        }
    }

    // ── AC-2: clean firebox -> all Satisfied (or NotApplicable) ───────────

    "AC-2: clean firebox yields no Violated verdicts" - {

        "all verdicts are Satisfied, NotApplicable, or NotEvaluated" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            verdicts.foreach { v =>
                v.outcome match
                    case ConstraintOutcome.Satisfied         => // expected
                    case ConstraintOutcome.NotApplicable     => // acceptable
                    // Unreachable here: constraintContext sets A_BR_min/max (so
                    // FireboxBase does not emit NotEvaluated(MissingMB)), and the
                    // topology term no longer emits NotEvaluated.
                    case ConstraintOutcome.NotEvaluated(_)   =>
                    case ConstraintOutcome.Violated(_, _, _) =>
                        fail(
                            s"Unexpected Violated verdict for term=${v.term} " +
                                s"label=${v.label}: ${v.outcome}"
                        )
            }
        }
    }

    // ── AC-3: air-intake shape check ──────────────────────────────────────

    "AC-3: air-intake pipe shape topology verdict" - {

        "fctx.airIntakePipeShape=None yields NotApplicable" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            val topologyVerdicts = verdicts.filter(
                _.term == ConstraintTerm.AirIntakePipeShape
            )

            assert(topologyVerdicts.nonEmpty)

            // A missing pipe is a defined scenario (combustion air taken
            // from the room through the firebox door — the UI surfaces a
            // non-blocking warning for it), not a pending state.
            val hasNotApplicable = topologyVerdicts.exists { v =>
                v.outcome match
                    case ConstraintOutcome.NotApplicable => true
                    case _                               => false
            }
            hasNotApplicable shouldBe true
        }

        "fctx.airIntakePipeShape=Some yields Satisfied (no shape errors)" in {
            import afpma.firecalc.dto.common.PipeShape

            val fireboxCtxWithShape = FireboxConstraintContext(
                mB                 = 10.kg,
                flow_rate          = None,
                airIntakePipeShape = Some(PipeShape.Circle(15.cm))
            )

            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxWithShape
            )

            val topologyVerdicts = verdicts.filter(
                _.term == ConstraintTerm.AirIntakePipeShape
            )

            // With shape=Some and no shape errors (TraditionalFirebox
            // firebox_custom_constraints returns Nil), the topology verdict
            // should be Satisfied.
            assert(topologyVerdicts.nonEmpty)

            val hasNotEvaluated = topologyVerdicts.exists { v =>
                v.outcome match
                    case ConstraintOutcome.NotEvaluated(_) => true
                    case _                                 => false
            }
            // Should NOT be NotEvaluated when shape is provided
            hasNotEvaluated shouldBe false

            // Should be Satisfied (since no shape mismatch errors)
            val hasSatisfied = topologyVerdicts.exists { v =>
                v.outcome == ConstraintOutcome.Satisfied
            }
            hasSatisfied shouldBe true
        }
    }

    // ── §4.7 ShapeOneOf descriptor for the catalog shape check ──────────

    "§4.7 ShapeOneOf: catalog shape descriptor reflects the firebox's accepted shapes" - {

        "ShapeOneOf(expected) is emitted when expectedAirIntakePipeShapes is Some" in {
            val verdicts = ShapeTestEngine.constraints(
                catalogShapesFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // The catalog-mismatch verdict (check 1) must carry a declarative
            // ShapeOneOf with the firebox's accepted shapes, so the UI can
            // re-evaluate locally (§4.7, spec AC-3).
            val catalogVerdict = verdicts
                .find(v =>
                    v.term == ConstraintTerm.AirIntakePipeShape &&
                        v.label == "AirIntakePipeShapeMismatch"
                )
                .getOrElse(fail("expected an AirIntakePipeShapeMismatch verdict"))

            catalogVerdict.descriptor shouldBe ConstraintDescriptor.ShapeOneOf(
                List(toWireShape(PipeShape.Circle(0.2.meters)))
            )
        }

        "Opaque is emitted when expectedAirIntakePipeShapes is None (unchanged behaviour)" in {
            val verdicts = ShapeTestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // cleanFirebox is a TraditionalFirebox: expectedAirIntakePipeShapes
            // inherits the default None, so the descriptor stays Opaque — the
            // pre-Phase-4 behaviour is unchanged for kinds with no inlet
            // restriction.
            val catalogVerdict = verdicts
                .find(v =>
                    v.term == ConstraintTerm.AirIntakePipeShape &&
                        v.label == "AirIntakePipeShapeMismatch"
                )
                .getOrElse(fail("expected an AirIntakePipeShapeMismatch verdict"))

            catalogVerdict.descriptor shouldBe ConstraintDescriptor.Opaque
        }
    }

    // ── Advisory ("Hinweis") custom-constraint channel ───────────────────

    "advisory_firebox_custom_constraints folds to trailing non-blocking verdicts" - {

        "one advisory finding yields exactly one blocking = false Custom verdict" in {
            val baseline = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )
            val verdicts = AdvisoryTestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            verdicts.length shouldBe baseline.length + 1

            val advisories = verdicts.filter(_.term == ConstraintTerm.Custom)
            advisories.length shouldBe 1

            val advisory = advisories.head
            // Code = error class simple name, message = its localised show —
            // exactly the rendering `errorToCustomVerdict` uses on both channels.
            advisory.label shouldBe "FireboxErrorCustom"
            advisory.descriptor shouldBe ConstraintDescriptor.Opaque
            advisory.outcome shouldBe ConstraintOutcome.Violated(
                "FireboxErrorCustom",
                advisoryReason,
                blocking = false
            )
        }

        "every other verdict is unchanged versus the un-adorned fold" in {
            val baseline = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )
            val verdicts = AdvisoryTestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // The default `advisory_firebox_custom_constraints = Nil` keeps
            // existing kinds byte-identical; adding one finding adds exactly
            // that one verdict and touches nothing else.
            verdicts.filter(_.term != ConstraintTerm.Custom) shouldBe baseline
        }

        "the advisory verdict sits last inside the custom-verdict block" in {
            val verdicts = AdvisoryTestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            val advisoryIdx = verdicts.indexWhere(_.term == ConstraintTerm.Custom)
            assert(advisoryIdx >= 0, "expected an advisory Custom verdict")

            // Appended after the shape/topology verdicts of §10…
            advisoryIdx shouldBe verdicts.length - 2
            // …only the §11 nominal-load verdict follows it.
            verdicts.last.term shouldBe ConstraintTerm.NominalLoadInRange
        }

        "advisory verdict equals its blocking twin except for the severity flag" in {
            val advisories = AdvisoryTestEngine
                .constraints(
                    cleanFirebox,
                    Some(constraintContext),
                    fireboxCtxNoShape
                )
                .filter(_.term == ConstraintTerm.Custom)
            val twins      = BlockingCustomTwinEngine
                .constraints(
                    cleanFirebox,
                    Some(constraintContext),
                    fireboxCtxNoShape
                )
                .filter(_.term == ConstraintTerm.Custom)

            advisories.length shouldBe 1
            twins.length shouldBe 1

            val advisory = advisories.head
            val twin     = twins.head

            twin.label shouldBe "FireboxErrorCustom"
            twin.outcome shouldBe ConstraintOutcome.Violated(
                "FireboxErrorCustom",
                advisoryReason,
                blocking = true
            )

            // Same code, same message rendering, same descriptor: the two
            // channels differ only in `blocking`.
            advisory.label shouldBe twin.label
            advisory.descriptor shouldBe twin.descriptor
            val twinViolation = twin.outcome match
                case v: ConstraintOutcome.Violated => v
                case other => fail(s"expected a blocking Violated twin, got $other")
            advisory.outcome shouldBe twinViolation.copy(blocking = false)
        }
    }

    // ── AC-4: eta excluded ────────────────────────────────────────────────

    "AC-4: eta constraint is excluded from verdicts" - {

        "no verdict corresponds to eta" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // ConstraintTerm enum has no `eta` case (§4.6).
            // Verify by checking that no verdict label contains "eta".
            val hasEta = verdicts.exists { v =>
                v.term.toString.equalsIgnoreCase("eta") ||
                v.label.toLowerCase.contains("eta")
            }
            hasEta shouldBe false
        }

        "distinct terms are a subset of the 11 known ConstraintTerm values" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            val distinctTerms = verdicts.map(_.term).distinct

            // The 11 enum cases: MB, MBMin, GlassArea, FireboxBase, H_BR,
            // Lambda, HeightOfLowestOpening, GlassSurfaceRatio,
            // AirIntakePipeShape, NominalLoadInRange, Custom
            val knownTerms = Set[ConstraintTerm](
                ConstraintTerm.MB,
                ConstraintTerm.MBMin,
                ConstraintTerm.GlassArea,
                ConstraintTerm.FireboxBase,
                ConstraintTerm.H_BR,
                ConstraintTerm.Lambda,
                ConstraintTerm.HeightOfLowestOpening,
                ConstraintTerm.GlassSurfaceRatio,
                ConstraintTerm.AirIntakePipeShape,
                ConstraintTerm.NominalLoadInRange,
                ConstraintTerm.Custom
            )

            assert(distinctTerms.toSet.subsetOf(knownTerms))

            // eta is not among the known terms (and cannot be, since the
            // enum has no eta case)
            distinctTerms.foreach { term =>
                assert(term.toString != "eta")
            }
        }
    }

    // ── AC-5: ctx=None -> NotEvaluated(MissingMB) ─────────────────────────

    "AC-5: ctx=None yields NotEvaluated(MissingMB) for m_B-dependent terms" - {

        "m_B-dependent terms are NotEvaluated(MissingMB)" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            // Terms that depend on m_B (ctx):
            //   MB, MBMin, GlassArea, FireboxBase, H_BR, Lambda,
            //   HeightOfLowestOpening, Custom
            val mbDependentTerms = Set[ConstraintTerm](
                ConstraintTerm.MB,
                ConstraintTerm.MBMin,
                ConstraintTerm.GlassArea,
                ConstraintTerm.FireboxBase,
                ConstraintTerm.H_BR,
                ConstraintTerm.Lambda,
                ConstraintTerm.HeightOfLowestOpening,
                ConstraintTerm.Custom
            )

            val mbDependentVerdicts = verdicts.filter(v => mbDependentTerms.contains(v.term))

            mbDependentVerdicts.foreach { v =>
                v.outcome match
                    case ConstraintOutcome.NotEvaluated(
                            NotEvaluatedReason.MissingMB
                        ) => // expected
                    case other =>
                        fail(
                            s"Expected NotEvaluated(MissingMB) for " +
                                s"term=${v.term} label=${v.label}, " +
                                s"got ${other}"
                        )
            }
        }

        "non-m_B-dependent terms are evaluated normally" in {
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            // Terms that do NOT depend on m_B:
            //   GlassSurfaceRatio, NominalLoadInRange
            // (AirIntakePipeShape topology is not emitted at all when
            //  ctx=None — it is only folded in the Some branch)
            val independentTerms = Set[ConstraintTerm](
                ConstraintTerm.GlassSurfaceRatio,
                ConstraintTerm.NominalLoadInRange
            )

            val independentVerdicts = verdicts.filter(v => independentTerms.contains(v.term))

            independentVerdicts.foreach { v =>
                v.outcome match
                    case ConstraintOutcome.Satisfied         => // expected
                    case ConstraintOutcome.NotApplicable     => // acceptable
                    case ConstraintOutcome.Violated(_, _, _) => // acceptable
                    case ConstraintOutcome.NotEvaluated(
                            NotEvaluatedReason.MissingMB
                        ) =>
                        fail(
                            s"Term ${v.term} should not be " +
                                s"NotEvaluated(MissingMB) when ctx=None"
                        )
                    case other                               =>
                        fail(
                            s"Unexpected outcome for term=${v.term}: ${other}"
                        )
            }
        }

        "verdict count with ctx=None is 10" in {
            // With ctx=None:
            //   8 NotEvaluated(MissingMB) terms +
            //   1 GlassSurfaceRatio +
            //   1 NominalLoadInRange = 10
            // (AirIntakePipeShape topology is NOT included in the None branch —
            //  it's only in the Some branch)
            val verdicts = TestEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            verdicts.length shouldBe 10
        }
    }

    // ── Glass-area cap denominator: BUILT surface, not 900·m_B ─────────────
    //
    // Owner decision 2026-10-23 (tracker ticket 13): the 1/5 denominator of
    // the EN 15544:2023 clause-1 glass cap is the BUILT interior surface
    // (§3.7), NOT the §4.3.1.2 required surface (formula (3), 900·m_B)
    // carried by `ctx.O_BR`.  Both fixtures use m_B = 12 kg, so the
    // nominal O_BR is 10800 cm² and O_BR/5 is 2160 cm²; the glass area
    // sits strictly between the two readings in each direction.

    /**
     * 50×50×80 cm: built interior surface = 2·(2500 + 4000 + 4000) =
     * 21000 cm² > nominal 10800 cm².  Glass 50×50 = 2500 cm² exceeds the
     * nominal threshold 2160 cm² but stays under built/5 = 4200 cm²:
     * PASSES under the built-surface decision, would FAIL nominal-based.
     */
    private val builtAboveNominalFirebox: TraditionalFirebox =
        TraditionalFirebox                     (
            emissions_values                      = Standing_Standard_Burning_Firebox,
            firebox_depth_B                       = 50.cm,
            firebox_width_A                       = 50.cm,
            firebox_height_H                      = 80.cm,
            pressure_loss_coefficient_from_door   = 0.5.unitless,
            total_air_intake_surface_area_on_door = 0.1.squareMeters,
            glass_width                           = 50.cm,
            glass_height                          = 50.cm,
            ash_pit_height_AF                     = 10.cm
        )

    private val ctxBuiltAboveNominal: ConstraintContext =
        ConstraintContext                           (
            m_B                            = 12.kg,
            O_BR                           = 10800.cm2,
            area_O_BR                      = 21000.cm2,
            FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = 0.5,
            FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = 2.0,
            A_BR_min                       = Some(1200.cm2),
            A_BR_max                       = Some(4000.cm2),
            A_BR                           = 2500.cm2,
            H_BR_min                       = None,
            H_BR                           = 80.cm,
            n_min                          = 70.percent
        )

    /**
     * 40(w)×30(d)×50(h) cm: built interior surface = 2·(1200 + 1500 +
     * 2000) = 9400 cm² < nominal 10800 cm².  Glass 40×50 = 2000 cm² stays
     * under the nominal threshold 2160 cm² but exceeds built/5 = 1880 cm²:
     * FAILS under the built-surface decision, would PASS nominal-based.
     */
    private val builtBelowNominalFirebox: TraditionalFirebox =
        TraditionalFirebox                     (
            emissions_values                      = Standing_Standard_Burning_Firebox,
            firebox_depth_B                       = 30.cm,
            firebox_width_A                       = 40.cm,
            firebox_height_H                      = 50.cm,
            pressure_loss_coefficient_from_door   = 0.5.unitless,
            total_air_intake_surface_area_on_door = 0.1.squareMeters,
            glass_width                           = 40.cm,
            glass_height                          = 50.cm,
            ash_pit_height_AF                     = 10.cm
        )

    private val ctxBuiltBelowNominal: ConstraintContext =
        ConstraintContext                           (
            m_B                            = 12.kg,
            O_BR                           = 10800.cm2,
            area_O_BR                      = 9400.cm2,
            FLOOR_DEPTH_TO_WIDTH_MIN_RATIO = 0.5,
            FLOOR_DEPTH_TO_WIDTH_MAX_RATIO = 2.0,
            A_BR_min                       = Some(1200.cm2),
            A_BR_max                       = Some(4000.cm2),
            A_BR                           = 1200.cm2,
            H_BR_min                       = None,
            H_BR                           = 50.cm,
            n_min                          = 70.percent
        )

    "glass-area cap measures the BUILT surface, not 900·m_B (ticket 13)" - {

        "glass between O_BR/5 and built/5 PASSES when built > nominal" in {
            val constraints = TestEngine
                .glassArea_constraints(builtAboveNominalFirebox, ctxBuiltAboveNominal)
                .flatten
            constraints.length shouldBe 1
            constraints.head.validate(builtAboveNominalFirebox.glass_area).isValid shouldBe true
        }

        "glass between built/5 and O_BR/5 FAILS when built < nominal" in {
            val constraints = TestEngine
                .glassArea_constraints(builtBelowNominalFirebox, ctxBuiltBelowNominal)
                .flatten
            constraints.length shouldBe 1
            constraints.head.validate(builtBelowNominalFirebox.glass_area).isValid shouldBe false
        }

        "the folded Max descriptor reports built/5 = 0.42 m², not O_BR/5 = 0.216 m²" in {
            val verdicts     = TestEngine.constraints(
                builtAboveNominalFirebox,
                Some(ctxBuiltAboveNominal),
                fireboxCtxNoShape
            )
            val glassVerdict = verdicts
                .find(_.term == ConstraintTerm.GlassArea)
                .getOrElse(fail("expected a GlassArea verdict"))
            glassVerdict.descriptor match
                case ConstraintDescriptor.Max(Left(q)) =>
                    q.value.toDouble shouldBe 0.42 +- 1e-9
                case other                             =>
                    fail(s"expected Max(built/5), got $other")
        }

        "the folded Max descriptor reports built/5 = 0.188 m² on the failing side" in {
            val verdicts     = TestEngine.constraints(
                builtBelowNominalFirebox,
                Some(ctxBuiltBelowNominal),
                fireboxCtxNoShape
            )
            val glassVerdict = verdicts
                .find(_.term == ConstraintTerm.GlassArea)
                .getOrElse(fail("expected a GlassArea verdict"))
            glassVerdict.descriptor match
                case ConstraintDescriptor.Max(Left(q)) =>
                    q.value.toDouble shouldBe 0.188 +- 1e-9
                case other                             =>
                    fail(s"expected Max(built/5), got $other")
        }
    }

end ConstraintFoldingSuite
