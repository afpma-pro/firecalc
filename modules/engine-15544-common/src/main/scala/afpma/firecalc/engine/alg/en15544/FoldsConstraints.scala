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
 * `eta_constraints` is deliberately excluded (§4.6).
 * When `ctx` is `None` (m_B unavailable) every m_B-dependent term
 * yields `NotEvaluated(MissingMB)`.
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

        def errorToCustomVerdict(err: FireboxError): ConstraintVerdict =
            val code    = err.getClass.getSimpleName
            val message = ShowUsingLocale[FireboxError].show(err)
            ConstraintVerdict(
                ConstraintTerm.Custom,
                code,
                ConstraintDescriptor.Opaque,
                ConstraintOutcome.Violated(code, message, blocking = true)
            )

        def errorToNominalVerdict(
            err       : FireboxError,
            descriptor: ConstraintDescriptor
        ): ConstraintVerdict =
            val code    = err.getClass.getSimpleName
            val message = ShowUsingLocale[FireboxError].show(err)
            ConstraintVerdict(
                ConstraintTerm.NominalLoadInRange,
                "nominalLoadInRange",
                descriptor,
                ConstraintOutcome.Violated(code, message, blocking = true)
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
         * Descriptor for check 1 (catalog air-intake shape match, §4.7).
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

        ctx match

            // ── ctx = None: m_B unavailable ─────────────────────────────
            case None =>
                // Terms requiring ctx (1,2,3,4,5,6,8,10) → NotEvaluated(MissingMB)
                // Terms 9,11 don't need ctx → evaluate normally
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

                // Term 9: glass surface ratio (no ctx needed)
                val glassRatioVerdicts = constraintsAlg.firebox_glass_surface_ratio_constraint(firebox) match
                    case Some(_) =>
                        List(
                            ConstraintVerdict(
                                ConstraintTerm.GlassSurfaceRatio,
                                "glassSurfaceRatio",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.Satisfied
                            )
                        )
                    case None    =>
                        List(
                            ConstraintVerdict(
                                ConstraintTerm.GlassSurfaceRatio,
                                "glassSurfaceRatio",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )
                        )

                // Term 11: nominal load range (no ctx needed)
                val nominalLoadVerdicts = {
                    val (nomDesc, _) = nominalLoadDescriptor
                    constraintsAlg.internal_firebox_custom_constraints(firebox) match
                        case Nil    =>
                            List(
                                ConstraintVerdict(
                                    ConstraintTerm.NominalLoadInRange,
                                    "nominalLoadInRange",
                                    nomDesc,
                                    ConstraintOutcome.Satisfied
                                )
                            )
                        case errors =>
                            errors.map(errorToNominalVerdict(_, nomDesc)).toList
                }

                notEvaluated ++ glassRatioVerdicts ++ nominalLoadVerdicts

            // ── ctx = Some: m_B available, evaluate all ─────────────────
            case Some(c) =>

                // ── 1. m_B_constraints → Range(min, max) ────────────────
                val mBConstraints = constraintsAlg.m_B_constraints(firebox, c)
                val mBVerdict     =
                    val mins    = mBConstraints.collect { case Some(tc: TermConstraint.Min[?]) =>
                        Left(qtyDToWireQty(tc.min))
                    }
                    val maxes   = mBConstraints.collect { case Some(tc: TermConstraint.Max[?]) =>
                        Left(qtyDToWireQty(tc.max))
                    }
                    val outcome = if mBConstraints.flatten.nonEmpty then ConstraintOutcome.Satisfied
                    else ConstraintOutcome.NotApplicable
                    ConstraintVerdict(
                        ConstraintTerm.MB,
                        "m_B",
                        ConstraintDescriptor.Range(mins.headOption, maxes.headOption),
                        outcome
                    )

                // ── 2. m_B_min_constraints → Range(min, None) ───────────
                val mBMinConstraints = constraintsAlg.m_B_min_constraints(firebox, c)
                val mBMinVerdict     =
                    val mins    = mBMinConstraints.collect { case Some(tc: TermConstraint.Min[?]) =>
                        Left(qtyDToWireQty(tc.min))
                    }
                    val outcome = if mBMinConstraints.flatten.nonEmpty then ConstraintOutcome.Satisfied
                    else ConstraintOutcome.NotApplicable
                    ConstraintVerdict(
                        ConstraintTerm.MBMin,
                        "m_B_min",
                        ConstraintDescriptor.Range(mins.headOption, None),
                        outcome
                    )

                // ── 3. glassArea_constraints → Max(obrFifth) ────────────
                val glassAreaVerdicts = constraintsAlg
                    .glassArea_constraints(firebox, c)
                    .flatMap:
                        case Some(_) =>
                            val obrFifth: GlassArea = c.O_BR / 5.0
                            List(
                                ConstraintVerdict(
                                    ConstraintTerm.GlassArea,
                                    "glassArea",
                                    ConstraintDescriptor.Max(Left(qtyDToWireQty(obrFifth))),
                                    ConstraintOutcome.Satisfied
                                )
                            )
                        case None    =>
                            List(
                                ConstraintVerdict(
                                    ConstraintTerm.GlassArea,
                                    "glassArea",
                                    ConstraintDescriptor.Opaque,
                                    ConstraintOutcome.NotApplicable
                                )
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
                            val outcome    = baseConstraints.headOption.flatten match
                                case Some(_) => ConstraintOutcome.Satisfied
                                case None    => ConstraintOutcome.NotApplicable
                            ConstraintVerdict(ConstraintTerm.FireboxBase, "fireboxBase_area", descriptor, outcome)

                    // Ratio check → Opaque
                    val ratioVerdict = baseConstraints.lift(1).flatten match
                        case Some(_) =>
                            ConstraintVerdict(
                                ConstraintTerm.FireboxBase,
                                "fireboxBase_ratio",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.Satisfied
                            )
                        case None    =>
                            ConstraintVerdict(
                                ConstraintTerm.FireboxBase,
                                "fireboxBase_ratio",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )

                    // MinWidth check → Opaque
                    val minWidthVerdict = baseConstraints.lift(2).flatten match
                        case Some(_) =>
                            ConstraintVerdict(
                                ConstraintTerm.FireboxBase,
                                "fireboxBase_minWidth",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.Satisfied
                            )
                        case None    =>
                            ConstraintVerdict(
                                ConstraintTerm.FireboxBase,
                                "fireboxBase_minWidth",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )

                    List(areaVerdict, ratioVerdict, minWidthVerdict)

                // ── 5. h_br_constraints (2 verdicts) ────────────────────
                val hBrConstraints = constraintsAlg.h_br_constraints(firebox, c)
                val hBrVerdicts    =
                    // First: Min(H_BR_min) → Range(Some(min), None)
                    val minVerdict = hBrConstraints.headOption.flatten match
                        case Some(tc: TermConstraint.Min[?]) =>
                            ConstraintVerdict(
                                ConstraintTerm.H_BR,
                                "h_BR_min",
                                ConstraintDescriptor.Range(Some(Left(qtyDToWireQty(tc.min))), None),
                                ConstraintOutcome.Satisfied
                            )
                        case Some(_)                         =>
                            ConstraintVerdict(
                                ConstraintTerm.H_BR,
                                "h_BR_min",
                                ConstraintDescriptor.Range(None, None),
                                ConstraintOutcome.Satisfied
                            )
                        case None                            =>
                            ConstraintVerdict(
                                ConstraintTerm.H_BR,
                                "h_BR_min",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )

                    // Second: ±5% band → Range(H_BR*0.95, H_BR*1.05)
                    val bandVerdict = hBrConstraints.lift(1).flatten match
                        case Some(_) =>
                            val hBR = c.H_BR
                            val tol5p: H_BR = hBR * 5.percent / 100.percent
                            val minH = hBR - tol5p
                            val maxH = hBR + tol5p
                            ConstraintVerdict(
                                ConstraintTerm.H_BR,
                                "h_BR_band",
                                ConstraintDescriptor
                                    .Range(Some(Left(qtyDToWireQty(minH))), Some(Left(qtyDToWireQty(maxH)))),
                                ConstraintOutcome.Satisfied
                            )
                        case None    =>
                            ConstraintVerdict(
                                ConstraintTerm.H_BR,
                                "h_BR_band",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )

                    List(minVerdict, bandVerdict)

                // ── 6. lambda_constraints → Range(1.95, 3.95) ───────────
                val lambdaConstraints = constraintsAlg.lambda_constraints(firebox, c)
                val lambdaVerdict     =
                    val outcome = if lambdaConstraints.flatten.nonEmpty then ConstraintOutcome.Satisfied
                    else ConstraintOutcome.NotApplicable
                    ConstraintVerdict(
                        ConstraintTerm.Lambda,
                        "lambda",
                        ConstraintDescriptor.Range(Some(Right(1.95)), Some(Right(3.95))),
                        outcome
                    )

                // ── 8. height_of_lowest_opening_constraints → Min(5cm) ──
                val heightLowestVerdicts =
                    constraintsAlg
                        .height_of_lowest_opening_constraints(firebox, c)
                        .flatMap:
                            case Some(tc: TermConstraint.Min[?]) =>
                                List(
                                    ConstraintVerdict(
                                        ConstraintTerm.HeightOfLowestOpening,
                                        "heightOfLowestOpening",
                                        ConstraintDescriptor.Min(Left(qtyDToWireQty(tc.min))),
                                        ConstraintOutcome.Satisfied
                                    )
                                )
                            case Some(_)                         =>
                                List(
                                    ConstraintVerdict(
                                        ConstraintTerm.HeightOfLowestOpening,
                                        "heightOfLowestOpening",
                                        ConstraintDescriptor.Opaque,
                                        ConstraintOutcome.Satisfied
                                    )
                                )
                            case None                            =>
                                List(
                                    ConstraintVerdict(
                                        ConstraintTerm.HeightOfLowestOpening,
                                        "heightOfLowestOpening",
                                        ConstraintDescriptor.Opaque,
                                        ConstraintOutcome.NotApplicable
                                    )
                                )

                // ── 9. firebox_glass_surface_ratio_constraint → Opaque ──
                val glassRatioVerdict = constraintsAlg.firebox_glass_surface_ratio_constraint(firebox) match
                    case Some(_) =>
                        ConstraintVerdict(
                            ConstraintTerm.GlassSurfaceRatio,
                            "glassSurfaceRatio",
                            ConstraintDescriptor.Opaque,
                            ConstraintOutcome.Satisfied
                        )
                    case None    =>
                        ConstraintVerdict(
                            ConstraintTerm.GlassSurfaceRatio,
                            "glassSurfaceRatio",
                            ConstraintDescriptor.Opaque,
                            ConstraintOutcome.NotApplicable
                        )

                // ── 10. firebox_custom_constraints → split shape checks (§4.7) ──
                val allCustomErrors            = constraintsAlg.firebox_custom_constraints(firebox, fctx)
                val (shapeErrors, otherErrors) = allCustomErrors.partition {
                    case _: AirIntakePipeShapeMismatch         => true
                    case _: AirIntakePipeShapeTopologyMismatch => true
                    case _ => false
                }

                // Check 1 (catalog shape match, §4.7) is client-live: emit a
                // declarative `ShapeOneOf(expected)` carrying the firebox's
                // accepted shapes — sourced via the Seam R virtual member so
                // the UI can re-evaluate locally — falling back to `Opaque`
                // for kinds with no inlet restriction (unchanged behaviour).
                // Check 2 (topology) stays `Opaque`: §4.7 leaves its
                // client-side re-evaluation as a SHOULD, not required.
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
                    case None    =>
                        List(
                            ConstraintVerdict(
                                ConstraintTerm.AirIntakePipeShape,
                                "airIntakePipeShape_topology",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.NotApplicable
                            )
                        )
                    case Some(_)
                        if shapeErrors.collect { case _: AirIntakePipeShapeTopologyMismatch => true }.nonEmpty =>
                        Nil // already emitted as Violated above
                    case Some(_) =>
                        List(
                            ConstraintVerdict(
                                ConstraintTerm.AirIntakePipeShape,
                                "airIntakePipeShape_topology",
                                ConstraintDescriptor.Opaque,
                                ConstraintOutcome.Satisfied
                            )
                        )

                val customVerdicts = shapeVerdicts ++ topologyVerdict ++ otherErrors.map(errorToCustomVerdict)

                // ── 11. internal_firebox_custom_constraints → Range ─────
                val (nomDesc, _) = nominalLoadDescriptor
                val nominalLoadVerdicts =
                    constraintsAlg.internal_firebox_custom_constraints(firebox) match
                        case Nil    =>
                            List(
                                ConstraintVerdict(
                                    ConstraintTerm.NominalLoadInRange,
                                    "nominalLoadInRange",
                                    nomDesc,
                                    ConstraintOutcome.Satisfied
                                )
                            )
                        case errors =>
                            errors.map(errorToNominalVerdict(_, nomDesc)).toList

                List(mBVerdict, mBMinVerdict) ++ glassAreaVerdicts ++
                    fireboxBaseVerdicts ++ hBrVerdicts ++
                    List(lambdaVerdict) ++ heightLowestVerdicts ++
                    List(glassRatioVerdict) ++ customVerdicts ++ nominalLoadVerdicts

end FoldsConstraints
