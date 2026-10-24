/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.conversions.*
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.engine.api.WireConversions.qtyDToWireQty
import afpma.firecalc.engine.api.WireConversions.toWireShape
import afpma.firecalc.engine.models.TermConstraint
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.AirIntakePipeShapeMismatch
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.standard.given_ShowUsingLocale_FireboxError

import coulomb.*
import coulomb.policy.standard.given

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locale

/**
 * Shared trait that folds per-term constraint methods into a flat list of wire
 * verdicts, delegating each call to the [[FireboxConstraints]] typeclass
 * instance held in [[constraintsAlg]].
 *
 * Extracted from the default implementation that used to live on
 * [[FireboxEngine]] so the engine's abstract surface is reduced to the
 * essential members.  Both strict and MCE `ForwardsConstraints` traits
 * extend this to avoid duplicating the fold logic.
 *
 * `eta_constraints` is deliberately excluded — our own fold-scope choice,
 * not an EN matter.  This scaladoc is the single owner of that explanation:
 * the stale "§4.4"/"(§4.6)" markers once carried on the fold resembled
 * EN 15544:2009 numbering, which we do not hold (the 2023 §4.4 is the
 * burning-rate calculation), and the only EN 15544:2023 efficiency clause is
 * §4.10.3 « Calculation of the nominal output », printed p. 24 — outside
 * this fold either way.  Do not restate it at the call sites.
 *
 * `ctx = None` means the sizing context was not supplied — the HTTP
 * request path builds `ctx = None` exactly when `m_B`/`n_min` are
 * missing.  Every term that needs the sizing context then yields
 * `NotEvaluated(MissingMB)`: the reason names the missing request
 * parameter, not a data dependency on `m_B` itself (the glass-area cap
 * reads built geometry from the context, and the custom/advisory
 * channels need neither).  Precondition, accepted with ticket 23: on
 * that branch the blocking and advisory custom-constraint channels are
 * not evaluated at all — their `Custom` verdict is the same
 * placeholder — so advisory findings ride inside the 200 body only on
 * the `ctx = Some` branch.
 *
 * Every term defaults to described-only: the verdict reflects the
 * constraint's existence, not its validation (unchanged for every shipped
 * kind).  A kind opts a term into real evaluation by constructing its
 * `TermConstraint.Generic`/`GenericTyped` with `evaluated = Some(severity)`
 * (see [[TermConstraint.EvaluatedSeverity]]); marked slots additionally run
 * their validation here (the `slot`/`slotOutcome` pair below) and a failure
 * folds into the same `ConstraintOutcome.Violated(code, message, blocking)`
 * path the custom-constraint channels use.
 */
trait FoldsConstraints[-F <: Firebox_15544] extends FireboxEngine[F]:

    /**
     * The [[FireboxConstraints]] typeclass instance this engine delegates to.
     *
     * Declared here rather than on [[FireboxEngine]]: `FireboxConstraints` is
     * bounded by `Firebox_15544`, whereas `FireboxEngine` is deliberately
     * unbounded so black-box models need not extend the current hierarchy.
     * This trait carries the bound, so the algebra is well-typed on it.
     *
     * [[constraints]] below folds it into wire verdicts; the per-term
     * `TermConstraint`s reach the local reporting path through
     * [[FireboxEngineFor.constraintsAlg]], which republishes this member.
     */
    def constraintsAlg: FireboxConstraints[F]

    override def constraints(
        firebox: F,
        ctx    : Option[ConstraintContext],
        fctx   : FireboxConstraintContext
    )(using Locale): List[ConstraintVerdict] =

        def notEvaluatedMB(term: ConstraintTerm): ConstraintVerdict =
            ConstraintVerdict(
                term,
                term.toString,
                ConstraintDescriptor.Opaque,
                ConstraintOutcome.NotEvaluated(NotEvaluatedReason.MissingMB)
            )

        def errorToCustomVerdict(err: FireboxError, blocking: Boolean): ConstraintVerdict =
            val code    = err.getClass.getSimpleName
            val message = ShowUsingLocale[FireboxError].show(err)
            ConstraintVerdict(
                ConstraintTerm.Custom,
                code,
                ConstraintDescriptor.Opaque,
                ConstraintOutcome.Violated(code, message, blocking = blocking)
            )

        def errorToNominalVerdict(
            err       : FireboxError,
            label     : String,
            descriptor: ConstraintDescriptor
        ): ConstraintVerdict =
            val code    = err.getClass.getSimpleName
            val message = ShowUsingLocale[FireboxError].show(err)
            ConstraintVerdict(
                ConstraintTerm.NominalLoadInRange,
                label,
                descriptor,
                ConstraintOutcome.Violated(code, message, blocking = true)
            )

        /**
         * The evaluation switch behind every slot (KOV-2026-10 air-supply
         * map, ticket 23, option 1).
         *
         * Unmarked (the default for every `TermConstraint` any shipped kind
         * returns): returns `present` — the existence-derived outcome, i.e.
         * the pre-ticket behaviour unchanged.
         *
         * Marked (`TermConstraint.Evaluatable` constructed with
         * `evaluated = Some(severity)`): additionally runs the constraint's
         * validation against the value the kind embedded at its own
         * definition site, and a failure routes through the existing
         * `ConstraintOutcome.Violated(code, message, blocking)` path.  `code`
         * is the slot label — the stable per-rule identifier this verdict
         * already carries — and `message` is the same localised text the
         * full-application path shows for that failure
         * (`TermConstraintError.failMsg`).  The verdicts agree only for
         * `Blocking`: the full-application path (`checkFireboxConstraints`
         * in en15544 `typedefs.scala`) runs every cited constraint through
         * `foldToErrDeep` regardless of the marker, so an `Advisory`
         * finding still hard-fails there and the mark changes the outcome
         * on this fold alone until ticket 17 lands.  `blocking` comes from the
         * `EvaluatedSeverity` mark (`Blocking` / `Advisory`), mirroring the
         * `firebox_custom_constraints` / `advisory_firebox_custom_constraints`
         * split of 3ab73d1b; both severities already ride the wire shape and
         * the consumer's 422 filter unchanged.  Descriptors stay as built: a
         * term keeps being described whether or not it is evaluated.
         *
         * Only the value-carrying constructors extend `Evaluatable`, so the
         * type test below covers every constraint that can carry the marker;
         * the capability method is self-contained, hence no unchecked cast is
         * needed anywhere.  First marked failure wins when a slot holds
         * several marked failing constraints (one rendering per constraint,
         * no aggregation — pinned by the severity suite).
         */
        def slotOutcome[O](
            label  : String,
            tcs    : Iterable[TermConstraint[O]],
            present: ConstraintOutcome
        ): ConstraintOutcome =
            tcs.iterator
                .collect:
                    case e: TermConstraint.Evaluatable[t] => e.evaluatedFailure
                .flatten
                .nextOption() match
                case Some((severity, message)) =>
                    ConstraintOutcome.Violated    (
                        code     = label,
                        message  = message,
                        blocking = severity == TermConstraint.EvaluatedSeverity.Blocking
                    )
                case None                      => present

        /**
         * Fold one standard-constraint slot into its wire verdict; the single
         * entry point every term below uses, so a slot label is taken once
         * for both the verdict identity and a marked failure's `code`, and
         * the present/absent pair cannot drift apart.
         *
         * An empty slot yields `absent` (descriptor + outcome together); a
         * non-empty slot keeps `descriptor` and runs `tcs` through
         * [[slotOutcome]].  Generic in the slot's value type — the fold
         * always knows it at each slot — which keeps [[slotOutcome]]'s type
         * test sound without casts.
         */
        def slot[O](
            term      : ConstraintTerm,
            label     : String,
            descriptor: ConstraintDescriptor,
            tcs       : Iterable[TermConstraint[O]],
            absent    : (ConstraintDescriptor, ConstraintOutcome)
        ): ConstraintVerdict =
            if (tcs.isEmpty) ConstraintVerdict(term, label, absent._1, absent._2)
            else
                ConstraintVerdict(
                    term,
                    label,
                    descriptor,
                    slotOutcome(label, tcs, ConstraintOutcome.Satisfied)
                )

        def nominalLoadDescriptor: (ConstraintDescriptor, Boolean) =
            firebox match
                case fb: Firebox_15544 =>
                    (
                        ConstraintDescriptor.Range(
                            fb.min_load.value.map(m => Left(qtyDToWireQty(m))),
                            fb.max_load.map      (m => Left(qtyDToWireQty(m)))
                        ),
                        true
                    )

        /**
         * Descriptor for check 1 (MB10-2022 / MB17 "Door 15a" catalog
         * air-intake shape match — not an EN 15544 clause).
         *
         * Mirrors [[nominalLoadDescriptor]]: reads the accepted shapes off
         * the firebox via the Seam R virtual member
         * (`expectedAirIntakePipeShapes`) so this module names no downstream
         * kind (invariant 3). `PipeShape` is a shared domain type and
         * `toWireShape` converts to the wire `List[WirePipeShape]` that
         * `ShapeOneOf` carries. Kinds without an inlet restriction inherit
         * `None` and keep the legacy `Opaque` descriptor (unchanged
         * behaviour). Unlike [[nominalLoadDescriptor]] (which runs for
         * every firebox and needs a total fallback), this is only reached
         * when `firebox_custom_constraints` produced an
         * `AirIntakePipeShapeMismatch` — emitted solely by catalog
         * `Firebox_15544` engines — so the `Firebox_15544` arm covers every
         * reachable call without a catch-all (invariant 9).
         */
        def airIntakeShapeDescriptor: ConstraintDescriptor =
            firebox match
                case fb: Firebox_15544 =>
                    fb.expectedAirIntakePipeShapes match
                        case Some(shapes) => ConstraintDescriptor.ShapeOneOf(shapes.map(toWireShape))
                        case None         => ConstraintDescriptor.Opaque

        // Terms 9 and 11 need no sizing context; both ctx branches below
        // emit identical verdicts for them, so they are built once here.
        val glassSurfaceRatioVerdict = slot(
            ConstraintTerm.GlassSurfaceRatio,
            "glassSurfaceRatio",
            ConstraintDescriptor.Opaque,
            constraintsAlg.firebox_glass_surface_ratio_constraint(firebox).toList,
            (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
        )

        val nominalLoadLabel    = "nominalLoadInRange"
        val nominalLoadVerdicts =
            val (nomDesc, _) = nominalLoadDescriptor
            constraintsAlg.internal_firebox_custom_constraints(firebox) match
                case Nil    =>
                    List(
                        ConstraintVerdict(
                            ConstraintTerm.NominalLoadInRange,
                            nominalLoadLabel,
                            nomDesc,
                            ConstraintOutcome.Satisfied
                        )
                    )
                case errors =>
                    errors.map(errorToNominalVerdict(_, nominalLoadLabel, nomDesc)).toList

        ctx match

            // ── ctx = None: sizing context not supplied ─────────────────
            case None =>
                // Terms 1–6, 8 and the custom channels (10) are not
                // evaluated without the sizing context → the
                // NotEvaluated(MissingMB) placeholder.  The reason names
                // the request parameter whose absence built this branch
                // (`m_B`/`n_min` missing), not a data dependency on
                // `m_B`: e.g. the glass-area cap (3) reads built
                // geometry from the context, and the custom channels
                // would run on `fctx` alone — the no-advisory
                // without-context precondition, see the trait scaladoc.
                val notEvaluated = List(
                    notEvaluatedMB(ConstraintTerm.MB                   ),
                    notEvaluatedMB(ConstraintTerm.MBMin                ),
                    notEvaluatedMB(ConstraintTerm.GlassArea            ),
                    notEvaluatedMB(ConstraintTerm.FireboxBase          ),
                    notEvaluatedMB(ConstraintTerm.H_BR                 ),
                    notEvaluatedMB(ConstraintTerm.Lambda               ),
                    notEvaluatedMB(ConstraintTerm.HeightOfLowestOpening),
                    notEvaluatedMB(ConstraintTerm.Custom               )
                )

                notEvaluated ++ List(glassSurfaceRatioVerdict) ++ nominalLoadVerdicts

            // ── ctx = Some: m_B available, evaluate all ─────────────────
            case Some(c) =>

                // ── 1. m_B_constraints → Range(min, max) ────────────────
                val mBConstraints = constraintsAlg.m_B_constraints(firebox, c)
                val mBDescriptor  =
                    ConstraintDescriptor.Range(
                        mBConstraints.collectFirst { case Some(tc: TermConstraint.Min[?]) =>
                            Left(qtyDToWireQty(tc.min))
                        },
                        mBConstraints.collectFirst { case Some(tc: TermConstraint.Max[?]) =>
                            Left(qtyDToWireQty(tc.max))
                        }
                    )
                val mBVerdict     = slot(
                    ConstraintTerm.MB,
                    "m_B",
                    mBDescriptor,
                    mBConstraints.flatten,
                    (mBDescriptor, ConstraintOutcome.NotApplicable)
                )

                // ── 2. m_B_min_constraints → Range(min, None) ───────────
                val mBMinConstraints = constraintsAlg.m_B_min_constraints(firebox, c)
                val mBMinDescriptor  =
                    ConstraintDescriptor.Range(
                        mBMinConstraints.collectFirst { case Some(tc: TermConstraint.Min[?]) =>
                            Left(qtyDToWireQty(tc.min))
                        },
                        None
                    )
                val mBMinVerdict     = slot(
                    ConstraintTerm.MBMin,
                    "m_B_min",
                    mBMinDescriptor,
                    mBMinConstraints.flatten,
                    (mBMinDescriptor, ConstraintOutcome.NotApplicable)
                )

                // ── 3. glassArea_constraints → Max(built area / 5) ─────
                val glassAreaCap: GlassArea = c.area_O_BR / 5.0
                val glassAreaVerdicts = constraintsAlg
                    .glassArea_constraints(firebox, c)
                    .map: tcOpt =>
                        slot(
                            ConstraintTerm.GlassArea,
                            "glassArea",
                            ConstraintDescriptor.Max(Left(qtyDToWireQty(glassAreaCap))),
                            tcOpt.toList,
                            (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                        )

                // ── 4. fireboxDimensions_Base_constraints (3 verdicts) ──
                val baseConstraints     = constraintsAlg.fireboxDimensions_Base_constraints(firebox, c)
                val fireboxBaseVerdicts =
                    // Area check → Range(A_BR_min, A_BR_max)
                    val areaVerdict = (c.A_BR_min, c.A_BR_max) match
                        case (None, _) | (_, None) =>
                            notEvaluatedMB(ConstraintTerm.FireboxBase)
                        case (Some(aMin), Some(aMax)) =>
                            val descriptor = ConstraintDescriptor.Range(
                                Some(Left(qtyDToWireQty(aMin))),
                                Some(Left(qtyDToWireQty(aMax)))
                            )
                            slot(
                                ConstraintTerm.FireboxBase,
                                "fireboxBase_area",
                                descriptor,
                                baseConstraints.headOption.flatten.toList,
                                (descriptor, ConstraintOutcome.NotApplicable)
                            )

                    // Ratio check → Opaque
                    val ratioVerdict = slot(
                        ConstraintTerm.FireboxBase,
                        "fireboxBase_ratio",
                        ConstraintDescriptor.Opaque,
                        baseConstraints.lift(1).flatten.toList,
                        (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                    )

                    // MinWidth check → Opaque
                    val minWidthVerdict = slot(
                        ConstraintTerm.FireboxBase,
                        "fireboxBase_minWidth",
                        ConstraintDescriptor.Opaque,
                        baseConstraints.lift(2).flatten.toList,
                        (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                    )

                    List(areaVerdict, ratioVerdict, minWidthVerdict)

                // ── 5. h_br_constraints (2 verdicts) ────────────────────
                val hBrConstraints = constraintsAlg.h_br_constraints(firebox, c)
                val hBrVerdicts    =
                    // First: Min(H_BR_min) → Range(Some(min), None)
                    val firstHBr      = hBrConstraints.headOption.flatten
                    val minDescriptor = firstHBr match
                        case Some(tc: TermConstraint.Min[?]) =>
                            ConstraintDescriptor.Range(Some(Left(qtyDToWireQty(tc.min))), None)
                        case _                               => ConstraintDescriptor.Range(None, None)
                    val minVerdict    = slot(
                        ConstraintTerm.H_BR,
                        "h_BR_min",
                        minDescriptor,
                        firstHBr.toList,
                        (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                    )

                    // Second: ±5% band → Range(H_BR*0.95, H_BR*1.05)
                    val hBR = c.H_BR
                    val tol5p: H_BR = hBR * 5.percent / 100.percent
                    val bandVerdict = slot(
                        ConstraintTerm.H_BR,
                        "h_BR_band",
                        ConstraintDescriptor.Range(
                            Some(Left(qtyDToWireQty(hBR - tol5p))),
                            Some(Left(qtyDToWireQty(hBR + tol5p)))
                        ),
                        hBrConstraints.lift(1).flatten.toList,
                        (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                    )

                    List(minVerdict, bandVerdict)

                // ── 6. lambda_constraints → Range(1.95, 3.95) ───────────
                val lambdaDescriptor = ConstraintDescriptor.Range(Some(Right(1.95)), Some(Right(3.95)))
                val lambdaVerdict    = slot(
                    ConstraintTerm.Lambda,
                    "lambda",
                    lambdaDescriptor,
                    constraintsAlg.lambda_constraints(firebox, c).flatten,
                    (lambdaDescriptor, ConstraintOutcome.NotApplicable)
                )

                // ── 8. height_of_lowest_opening_constraints → Min(5cm) ──
                val heightLowestVerdicts =
                    constraintsAlg
                        .height_of_lowest_opening_constraints(firebox, c)
                        .map: tcOpt =>
                            val descriptor = tcOpt match
                                case Some(tc: TermConstraint.Min[?]) =>
                                    ConstraintDescriptor.Min(Left(qtyDToWireQty(tc.min)))
                                case _                               => ConstraintDescriptor.Opaque
                            slot(
                                ConstraintTerm.HeightOfLowestOpening,
                                "heightOfLowestOpening",
                                descriptor,
                                tcOpt.toList,
                                (ConstraintDescriptor.Opaque, ConstraintOutcome.NotApplicable)
                            )

                // ── 10. firebox_custom_constraints → split shape checks (MB10-2022 / MB17 catalog) ──
                // The old "(§4.7)" marker resembled EN 15544:2009 numbering,
                // which we do not hold; the 2023 §4.7 is « Dichten der
                // Verbrennungsluft und Abgase berechnen » (printed pp. 15–16),
                // a tightness calculation, not pipe shape.
                val allCustomErrors            = constraintsAlg.firebox_custom_constraints(firebox, fctx)
                val (shapeErrors, otherErrors) = allCustomErrors.partition {
                    case _: AirIntakePipeShapeMismatch         => true
                    case _: AirIntakePipeShapeTopologyMismatch => true
                    case _ => false
                }

                // Check 1 (MB10-2022 / MB17 "Door 15a" catalog shape match —
                // not an EN 15544 clause) is client-live: emit a
                // declarative `ShapeOneOf(expected)` carrying the firebox's
                // accepted shapes — sourced via the Seam R virtual member so
                // the UI can re-evaluate locally — falling back to `Opaque`
                // for kinds with no inlet restriction (unchanged behaviour).
                // Check 2 (topology, same catalog rules) stays `Opaque`: its
                // client-side re-evaluation is our own design choice, no EN
                // 15544:2023 clause governs it.
                val catalogShapeVerdicts  = shapeErrors.collect { case err: AirIntakePipeShapeMismatch =>
                    val code    = err.getClass.getSimpleName
                    val message = ShowUsingLocale[FireboxError].show(err)
                    ConstraintVerdict(
                        ConstraintTerm.AirIntakePipeShape,
                        code,
                        airIntakeShapeDescriptor,
                        ConstraintOutcome.Violated(code, message, blocking = true)
                    )
                }
                val topologyShapeVerdicts = shapeErrors.collect { case err: AirIntakePipeShapeTopologyMismatch =>
                    val code    = err.getClass.getSimpleName
                    val message = ShowUsingLocale[FireboxError].show(err)
                    ConstraintVerdict(
                        ConstraintTerm.AirIntakePipeShape,
                        code,
                        ConstraintDescriptor.Opaque,
                        ConstraintOutcome.Violated(code, message, blocking = true)
                    )
                }
                val shapeVerdicts         = catalogShapeVerdicts ++ topologyShapeVerdicts

                // No intake pipe is a defined scenario, not a pending
                // state: combustion air is taken from the room through
                // the firebox door (the UI surfaces a non-blocking
                // warning for it). The topology term is only evaluated
                // when a pipe is drawn.
                val topologyVerdict = fctx.airIntakePipeShape match
                    case Some(_) if shapeErrors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) =>
                        Nil // already emitted as Violated above
                    case drawn =>
                        val outcome =
                            if (drawn.isDefined) ConstraintOutcome.Satisfied
                            else ConstraintOutcome.NotApplicable
                        List(
                            ConstraintVerdict(
                                ConstraintTerm.AirIntakePipeShape,
                                "airIntakePipeShape_topology",
                                ConstraintDescriptor.Opaque,
                                outcome
                            )
                        )

                // Advisory findings fold to the same verdict shape with
                // `blocking = false`: on this branch — the sizing context was
                // supplied — they ride inside the 200 body's verdict list,
                // are excluded from the 422 detail list by the consumer's
                // `if blocking` filter, and never reach validation
                // (`FoldedVerdicts.violations` collects blocking violations
                // only).  Without the context this channel is not evaluated
                // at all: the `ctx = None` branch emits the `Custom`
                // placeholder instead (accepted precondition, trait scaladoc).
                val advisoryVerdicts =
                    constraintsAlg
                        .advisory_firebox_custom_constraints(firebox, fctx)
                        .map(errorToCustomVerdict(_, blocking = false))

                val customVerdicts = shapeVerdicts ++ topologyVerdict ++
                    otherErrors.map(errorToCustomVerdict(_, blocking = true)) ++
                    advisoryVerdicts

                List(mBVerdict, mBMinVerdict) ++ glassAreaVerdicts ++
                    fireboxBaseVerdicts ++ hBrVerdicts ++
                    List(lambdaVerdict) ++ heightLowestVerdicts ++
                    List(glassSurfaceRatioVerdict) ++ customVerdicts ++ nominalLoadVerdicts

end FoldsConstraints
