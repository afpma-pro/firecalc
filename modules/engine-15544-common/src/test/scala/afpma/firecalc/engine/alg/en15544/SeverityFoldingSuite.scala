/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.TermConstraint
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ

import coulomb.*
import coulomb.policy.standard.given

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Severity behaviour of the `constraints` fold: the advisory custom-constraint
 * channel and the per-term evaluation switch — how a declared
 * finding or a marked standard constraint reaches `ConstraintOutcome.Violated`
 * with its `blocking` flag.
 *
 * The fold's structure (verdict counts, descriptors, the ctx = None
 * placeholder shape) is pinned by [[ConstraintFoldingSuite]]; engines and
 * fixtures are shared through [[ConstraintFoldingFixtures]].
 */
class SeverityFoldingSuite extends AnyFreeSpec with Matchers with ConstraintFoldingFixtures:
    given io.taig.babel.Locale = Locales.en

    // ── Advisory custom-constraint channel ───────────────────────────────

    "advisory_firebox_custom_constraints folds to trailing non-blocking verdicts" - {

        "one advisory finding yields exactly one blocking = false Custom verdict" in {
            val baseline = plainEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )
            val verdicts = advisoryChannelEngine.constraints(
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
            val baseline = plainEngine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )
            val verdicts = advisoryChannelEngine.constraints(
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
            val verdicts = advisoryChannelEngine.constraints(
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
            val advisories = advisoryChannelEngine
                .constraints(
                    cleanFirebox,
                    Some(constraintContext),
                    fireboxCtxNoShape
                )
                .filter(_.term == ConstraintTerm.Custom)
            val twins      = blockingCustomTwinEngine
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

        "ctx = None evaluates no custom or advisory channel at all" in {
            // Accepted precondition, stated on the fold's
            // scaladoc: without the sizing ctx the custom channels —
            // blocking and advisory alike — are never run, so a declared
            // advisory finding must NOT leak into the ctx = None verdict
            // list; `Custom` keeps its NotEvaluated(MissingMB) placeholder
            // exactly as on the plain engine.
            val verdicts = advisoryChannelEngine.constraints(
                cleanFirebox,
                None,
                fireboxCtxNoShape
            )

            val customs = verdicts.filter(_.term == ConstraintTerm.Custom)
            customs.length shouldBe 1
            customs.head.outcome shouldBe
                ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB)

            val carriesTheAdvisoryFinding = verdicts.exists(_.outcome match
                case ConstraintOutcome.Violated(_, message, _) => message == advisoryReason
                case _ => false)
            carriesTheAdvisoryFinding shouldBe false
        }
    }

    // ── Per-term evaluation switch ─────────────────────────────────────────
    //
    // The fold is described-only by default: a standard verdict reflects the
    // constraint's existence, not its validation.  A kind opts a term into
    // real evaluation by carrying `evaluated = Some(severity)` on its own
    // `TermConstraint.Generic` / `GenericTyped` construction; a marked
    // failure then folds through the same `Violated(code, message,
    // blocking)` path the custom channels use, and the severity mirrors the
    // blocking/advisory split of 3ab73d1b.
    //
    // The pinned mechanism is the real strict rule copied with the marker —
    // exactly what a kind does to opt in — on `builtBelowNominalFirebox`,
    // whose glass area strictly FAILS the built/5 cap (established by
    // [[ConstraintFoldingSuite]]'s glass-cap section), so marked-vs-unmarked
    // is the only variable.

    /**
     * `FoldingTestEngine` with the evaluation switch set on individual terms:
     * `markGlassArea` / `markGlassRatio` re-mark the strict rule for that
     * slot; `glassAreaOverride` / `lambdaOverrides` replace slots outright
     * (hand-built constraints go through the same fold path).
     */
    private class MarkedEngine(
        markGlassArea    : Option[TermConstraint.EvaluatedSeverity]               = None,
        markGlassRatio   : Option[TermConstraint.EvaluatedSeverity]               = None,
        glassAreaOverride: Option[ConstraintContext => TermConstraint[GlassArea]] = None,
        lambdaOverrides  : List[String]                                           = Nil
    ) extends FoldingTestEngine:

        override def glassArea_constraints(
            firebox: TraditionalFirebox,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[GlassArea]]] =
            glassAreaOverride match
                case Some(build) => Seq(Some(build(ctx)))
                case None        =>
                    markGlassArea match
                        case Some(severity) =>
                            super
                                .glassArea_constraints(firebox, ctx)
                                .map:
                                    case Some(gt: TermConstraint.GenericTyped[GlassArea, ?]) =>
                                        Some(gt.withEvaluated(severity))
                                    case other                                               => other
                        case None           => super.glassArea_constraints(firebox, ctx)

        override def firebox_glass_surface_ratio_constraint(
            firebox: TraditionalFirebox
        ): Option[TermConstraint[Unit]] =
            markGlassRatio match
                case Some(severity) =>
                    super.firebox_glass_surface_ratio_constraint(firebox).map {
                        case gt: TermConstraint.GenericTyped[Unit, ?] =>
                            gt.withEvaluated(severity)
                        case other => other
                    }
                case None           => super.firebox_glass_surface_ratio_constraint(firebox)

        /**
         * Replaces the strict λ slot with one marked failing `Generic` per
         * message, in order — two messages make the slot hold two marked
         * failing constraints at once.
         */
        override def lambda_constraints(
            firebox: TraditionalFirebox,
            ctx    : ConstraintContext
        ): Seq[Option[TermConstraint[λ]]] =
            if lambdaOverrides.isEmpty then super.lambda_constraints(firebox, ctx)
            else
                lambdaOverrides.map: msg =>
                    Some:
                        TermConstraint.Generic[λ]    (
                            value     = 2.5.unitless,
                            source    = "test: marked λ rule (first-wins probe)",
                            isValid   = _ => Left(msg),
                            evaluated = Some(TermConstraint.EvaluatedSeverity.Blocking)
                        )

    "per-term evaluation switch: described-only by default, evaluated when marked" - {

        def outcomeOf(
            verdicts: List[ConstraintVerdict],
            term    : ConstraintTerm,
            rule    : String
        ): ConstraintOutcome =
            verdicts
                .find(v => v.term == term && v.label == rule)
                .map(_.outcome)
                .getOrElse(fail(s"no verdict for term=$term label=$rule"))

        "an unmarked failing constraint keeps the existence-only Satisfied verdict" in {
            // `builtBelowNominalFirebox`'s glass area exceeds built/5 (see
            // [[ConstraintFoldingSuite]]'s glass-cap section: the strict
            // rule's validation FAILS), yet the folded verdict must stay
            // byte-identical to the pre-switch behaviour — marking is what
            // opts a term into evaluation.
            val verdicts = plainEngine.constraints(
                builtBelowNominalFirebox,
                Some(ctxBuiltBelowNominal),
                fireboxCtxNoShape
            )

            outcomeOf(verdicts, ConstraintTerm.GlassArea, "glassArea") shouldBe ConstraintOutcome.Satisfied
        }

        "a marked failing standard constraint folds to a blocking Violated with the slot code and the localized message" in {
            val engine   = MarkedEngine(markGlassArea = Some(TermConstraint.EvaluatedSeverity.Blocking))
            val verdicts = engine.constraints(
                builtBelowNominalFirebox,
                Some(ctxBuiltBelowNominal),
                fireboxCtxNoShape
            )

            val verdict = verdicts
                .find(_.term == ConstraintTerm.GlassArea)
                .getOrElse(fail("expected a GlassArea verdict"))

            // The descriptor stays exactly as built: a term keeps being
            // described whether or not it is evaluated.
            verdict.descriptor match
                case ConstraintDescriptor.Max(Left(q)) =>
                    q.value.toDouble shouldBe 0.188 +- 1e-9
                case other                             =>
                    fail(s"expected Max(built/5), got $other")

            verdict.outcome match
                case ConstraintOutcome.Violated(code, message, blocking) =>
                    code shouldBe "glassArea"
                    // Same localised text the full-application path shows for
                    // this failure (`GlassAreaTooLarge`), locale en as given
                    // at the top of the suite.
                    message should include("must be less than 1/5th of the firebox surface area")
                    blocking shouldBe true
                case other                                               =>
                    fail(s"expected a blocking Violated, got $other")
        }

        "a marked constraint that passes keeps the Satisfied outcome" in {
            val engine   = MarkedEngine(markGlassArea = Some(TermConstraint.EvaluatedSeverity.Blocking))
            val verdicts = engine.constraints(
                builtAboveNominalFirebox,
                Some(ctxBuiltAboveNominal),
                fireboxCtxNoShape
            )

            // glass 2500 cm² ≤ built/5 = 4200 cm²: valid → unchanged outcome.
            outcomeOf(verdicts, ConstraintTerm.GlassArea, "glassArea") shouldBe ConstraintOutcome.Satisfied
        }

        "an Advisory mark folds to a non-blocking violation, on the ctx = None path too" in {
            val engine   = MarkedEngine(markGlassRatio = Some(TermConstraint.EvaluatedSeverity.Advisory))
            val verdicts = engine.constraints(
                builtBelowNominalFirebox,
                None,
                fireboxCtxNoShape
            )

            // glass 2000 cm² over built 9400 cm² exceeds 1/5, so the strict
            // ratio rule is invalid — the mark routes it through the shared
            // violation path with `blocking = false`.
            verdicts
                .find(_.term == ConstraintTerm.GlassSurfaceRatio)
                .map(_.outcome)
                .getOrElse(fail("expected a GlassSurfaceRatio verdict")) match
                case ConstraintOutcome.Violated(code, message, blocking) =>
                    code shouldBe "glassSurfaceRatio"
                    message shouldBe "The glass surface must be less than 1/5th of the firebox surface area."
                    blocking shouldBe false
                case other                                               =>
                    fail(s"expected a non-blocking Violated, got $other")
        }

        "a hand-built `Generic` mark folds through the same path, message from its own text" in {
            val engine   = MarkedEngine(glassAreaOverride = Some { ctx =>
                TermConstraint.Generic[GlassArea]    (
                    value     = 0.6.squareMeters,
                    source    = "test: hand-built marked glassArea",
                    isValid   = area =>
                        if area <= ctx.area_O_BR / 5.0 then Right(area                                  )
                        else Left                                ("beyond the hand-built glass-area cap"),
                    evaluated = Some(TermConstraint.EvaluatedSeverity.Blocking)
                )
            })
            val verdicts = engine.constraints(
                // built > nominal fixture: the unmarked strict rule would
                // pass, but the hand-built rule's own value fails.
                builtAboveNominalFirebox,
                Some(ctxBuiltAboveNominal),
                fireboxCtxNoShape
            )

            val verdict = verdicts
                .find(_.term == ConstraintTerm.GlassArea)
                .getOrElse(fail("expected a GlassArea verdict"))

            verdict.outcome match
                case ConstraintOutcome.Violated(code, message, blocking) =>
                    code shouldBe "glassArea"
                    message should include("beyond the hand-built glass-area cap")
                    blocking shouldBe true
                case other                                               =>
                    fail(s"expected a blocking Violated, got $other")
        }

        "of several marked failing constraints in one slot, the first is the one reported" in {
            // The fold has no aggregation for evaluated terms: one rendering
            // per marked constraint, first failure wins (documented at
            // `TermConstraint.Evaluatable.evaluatedFailure`).  The λ slot is
            // replaced by two marked failing `Generic`s; the single lambda
            // verdict must carry the first one's text and the second must
            // appear nowhere in the verdict list.
            val engine   = MarkedEngine(
                lambdaOverrides = List("first marked λ failure", "second marked λ failure")
            )
            val verdicts = engine.constraints(
                cleanFirebox,
                Some(constraintContext),
                fireboxCtxNoShape
            )

            val lambdaVerdicts = verdicts.filter(_.term == ConstraintTerm.Lambda)
            lambdaVerdicts.length shouldBe 1

            val verdict = lambdaVerdicts.head
            verdict.label shouldBe "lambda"
            verdict.outcome match
                case ConstraintOutcome.Violated(code, message, blocking) =>
                    code shouldBe "lambda"
                    message should (
                        include("first marked λ failure") and
                            not include "second marked λ failure"
                    )
                    blocking shouldBe true
                case other                                               =>
                    fail(s"expected a blocking Violated, got $other")

            val carriesTheSecondFailure = verdicts.exists(_.outcome match
                case ConstraintOutcome.Violated(_, message, _) =>
                    message.contains("second marked λ failure")
                case _ => false)
            carriesTheSecondFailure shouldBe false
        }
    }

end SeverityFoldingSuite
