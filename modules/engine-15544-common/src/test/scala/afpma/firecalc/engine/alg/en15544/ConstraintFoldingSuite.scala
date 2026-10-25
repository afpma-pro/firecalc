/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.api.WireConversions.toWireShape

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Characterisation tests for the `constraints` folding method on `FireboxEngine`:
 * the fold's structure — verdict count, per-term descriptors, the ctx = None
 * placeholder shape — and the glass-area-cap denominator decision.
 *
 * Covers spec §9 Slice 2 AC-1 through AC-3 and AC-5.  AC-4 is asserted
 * nowhere: it is structural, and the one explanation of that decision is
 * the scaladoc of [[FoldsConstraints]].  The severity side of the same
 * fold (the advisory custom channel, the per-term evaluation switch) lives in
 * [[SeverityFoldingSuite]]; engines and fixtures are shared through
 * [[ConstraintFoldingFixtures]].
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
class ConstraintFoldingSuite extends AnyFreeSpec with Matchers with ConstraintFoldingFixtures:
    given io.taig.babel.Locale = Locales.en

    // ── AC-1: verdict count + no None ─────────────────────────────────────

    "AC-1: verdict count and no None outcomes" - {

        "returns non-empty list of verdicts with expected count" in {
            val verdicts = plainEngine.constraints(
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

        "no verdict outcome is a None-equivalent" in {
            // The old constraint methods returned Option[TermConstraint],
            // where None meant "not applicable".  The new folding method
            // always emits a verdict — even for inapplicable terms, the
            // outcome is NotApplicable, not None.
            val verdicts = plainEngine.constraints(
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
            val verdicts = plainEngine.constraints(
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
            val verdicts = plainEngine.constraints(
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
            val fireboxCtxWithShape = FireboxConstraintContext(
                mB                 = 10.kg,
                flow_rate          = None,
                airIntakePipeShape = Some(PipeShape.Circle(15.cm))
            )

            val verdicts = plainEngine.constraints(
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

    // ── ShapeOneOf descriptor for the MB10-2022 / MB17 catalog shape check ──

    "ShapeOneOf: catalog shape descriptor reflects the firebox's accepted shapes" - {

        "ShapeOneOf(expected) is emitted when expectedAirIntakePipeShapes is Some" in {
            val verdicts = shapeMismatchEngine.constraints(
                catalogShapesFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            // The catalog-mismatch verdict (check 1) must carry a declarative
            // ShapeOneOf with the firebox's accepted shapes, so the UI can
            // re-evaluate locally (MB10-2022 / MB17 catalog rule, spec AC-3).
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
            val verdicts = shapeMismatchEngine.constraints(
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

    // ── AC-5: ctx=None -> NotEvaluated(MissingMB) ─────────────────────────

    "AC-5: ctx=None yields the NotEvaluated(MissingMB) placeholder" - {

        "the terms the fold cannot evaluate without the sizing ctx are NotEvaluated(MissingMB)" in {
            val verdicts = plainEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            // These terms' constraint slots are built from the sizing ctx,
            // which is absent here, so the fold emits the placeholder
            // instead of a slot outcome.  The reason names the missing
            // request parameter (`m_B`), not a data dependency on `m_B`
            // itself: the placeholder belongs to the ctx = None fold shape,
            // as documented once in the FoldsConstraints scaladoc.
            val nonEvaluatedTerms = Set[ConstraintTerm](
                ConstraintTerm.MB,
                ConstraintTerm.MBMin,
                ConstraintTerm.GlassArea,
                ConstraintTerm.FireboxBase,
                ConstraintTerm.H_BR,
                ConstraintTerm.Lambda,
                ConstraintTerm.HeightOfLowestOpening,
                ConstraintTerm.Custom
            )

            val nonEvaluatedVerdicts = verdicts.filter(v => nonEvaluatedTerms.contains(v.term))

            nonEvaluatedVerdicts.foreach { v =>
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

        "the terms that keep real outcomes with ctx=None are evaluated normally" in {
            val verdicts = plainEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            // These hooks read the firebox alone (built geometry, tested
            // fields), never the sizing ctx, so ctx = None changes nothing
            // for them.  The AirIntakePipeShape topology slot is folded
            // only on the ctx = Some branch, so it is not emitted at all.
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
            //   8 NotEvaluated(MissingMB) placeholder terms +
            //   1 GlassSurfaceRatio +
            //   1 NominalLoadInRange = 10
            // (AirIntakePipeShape topology is NOT included in the None branch —
            //  it's only in the Some branch)
            val verdicts = plainEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            verdicts.length shouldBe 10
        }
    }

    // ── Glass-area cap denominator: BUILT surface, not 900·m_B ─────────────
    //
    // Authority and the denominator decision: EN 15544:2023 clause 1, printed
    // p. 7, cap measured against the BUILT surface; the fixtures and their
    // arithmetic are in [[ConstraintFoldingFixtures]].
    // These tests pin which denominator the `GlassArea` hook and its folded
    // Max descriptor actually use.

    "glass-area cap measures the BUILT surface, not 900·m_B (ticket 13)" - {

        "glass between O_BR/5 and built/5 PASSES when built > nominal" in {
            val constraints = plainEngine
                .glassArea_constraints(builtAboveNominalFirebox, ctxBuiltAboveNominal)
                .flatten
            constraints.length shouldBe 1
            constraints.head.validate(builtAboveNominalFirebox.glass_area).isValid shouldBe true
        }

        "glass between built/5 and O_BR/5 FAILS when built < nominal" in {
            val constraints = plainEngine
                .glassArea_constraints(builtBelowNominalFirebox, ctxBuiltBelowNominal)
                .flatten
            constraints.length shouldBe 1
            constraints.head.validate(builtBelowNominalFirebox.glass_area).isValid shouldBe false
        }

        "the folded Max descriptor reports built/5 = 0.42 m², not O_BR/5 = 0.216 m²" in {
            val verdicts     = plainEngine.constraints(
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
            val verdicts     = plainEngine.constraints(
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
