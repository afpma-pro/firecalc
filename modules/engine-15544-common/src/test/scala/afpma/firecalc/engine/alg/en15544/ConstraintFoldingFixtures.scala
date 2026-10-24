/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.engine.biblio.kov.firebox_emissions.Standing_Standard_Burning_Firebox
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

import cats.data.ValidatedNel
import cats.syntax.validated.*

/**
 * Fixtures shared by the two constraint-folding suites:
 * [[ConstraintFoldingSuite]] (the fold and its verdict structure — spec §9
 * AC-1..AC-5, the catalog ShapeOneOf descriptor, the glass-cap denominator)
 * and [[SeverityFoldingSuite]] (severity and advisory behaviour — the custom
 * channels and the per-term evaluation switch).
 *
 * The one [[FoldingTestEngine]] below is what the old per-section host
 * objects (TestEngine, ShapeTestEngine, AdvisoryTestEngine,
 * BlockingCustomTwinEngine) collapse into: the same strict-delegating
 * engine everywhere, with the two custom-constraint channels as parameters.
 */
trait ConstraintFoldingFixtures:

    /**
     * Minimal `FoldsConstraints[TraditionalFirebox]` for testing `constraints`.
     *
     * Mixes in `FireboxConstraints_Strict` and hands itself back as
     * `constraintsAlg`, so the fold under test delegates to the real strict
     * per-term constraints.  Pipe contributions are stubbed because the
     * `constraints` method does not use them.  Empty custom channels are the
     * strict default (`firebox_custom_constraints` returns `Nil` for a
     * Traditional firebox); `customErrors` / `advisoryErrors` exist only to
     * force the fold's error branches with findings this kind would not
     * produce by itself.
     */
    protected class FoldingTestEngine(
        customErrors  : List[FireboxError] = Nil,
        advisoryErrors: List[FireboxError] = Nil
    ) extends FoldsConstraints[TraditionalFirebox]
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

        override def firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] = customErrors

        override def advisory_firebox_custom_constraints(
            firebox: TraditionalFirebox,
            ctx    : FireboxConstraintContext
        ): List[FireboxError] = advisoryErrors

    /** The plain strict host (the old `TestEngine`). */
    protected def plainEngine = new FoldingTestEngine

    /**
     * The old `ShapeTestEngine`: emits an `AirIntakePipeShapeMismatch` from
     * `firebox_custom_constraints`, so the folding's MB10-2022 / MB17 catalog
     * shape-check branch is exercised even for a Traditional firebox (whose
     * real `firebox_custom_constraints` returns `Nil`).  No EN 15544 clause;
     * see [[FoldsConstraints]].
     */
    protected def shapeMismatchEngine =
        new FoldingTestEngine(
            customErrors = List(AirIntakePipeShapeMismatch(expected = "Circle 0.2 m", actual = "Circle 0.173 m"))
        )

    /**
     * Declares exactly one advisory finding via
     * `advisory_firebox_custom_constraints` and no blocking custom errors
     * (the old `AdvisoryTestEngine`).  Everything else is the plain strict
     * host, so the only difference in the folded verdict list is the
     * advisory verdict.
     */
    protected def advisoryChannelEngine =
        new FoldingTestEngine(advisoryErrors = List(new FireboxErrorCustom(advisoryReason)))

    /**
     * The fold-twin of [[advisoryChannelEngine]] (the old
     * `BlockingCustomTwinEngine`): the same finding declared on the blocking
     * channel (`firebox_custom_constraints`).  Pinning both sides shows the
     * advisory verdict shares its code and message rendering with a blocking
     * custom verdict, and differs only in the `blocking` flag.
     */
    protected def blockingCustomTwinEngine =
        new FoldingTestEngine(customErrors = List(new FireboxErrorCustom(advisoryReason)))

    /** Shared fixture finding; `FireboxErrorCustom` renders its reason verbatim. */
    protected val advisoryReason = "cleaning hatch obstructed by the air column"

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
    protected val cleanFirebox: TraditionalFirebox =
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
    protected val catalogShapesFirebox: TraditionalFirebox =
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
     * O_BR = 900 * m_B = 9000 cm² (EN 15544 required surface, formula (3)).
     * area_O_BR = 21000 cm², the BUILT §3.7 interior surface of `cleanFirebox`
     * (2 · (50·50 + 80·50 + 80·50)) — the glass-area-cap denominator.
     * A_BR_min/max derived from standard formulas for m_B=10.
     * H_BR = firebox height (80 cm).
     */
    protected val constraintContext: ConstraintContext =
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

    protected val fireboxCtxNoShape: FireboxConstraintContext =
        FireboxConstraintContext                (
            mB                 = 10.kg,
            flow_rate          = None,
            airIntakePipeShape = None
        )

    // ── Glass-area cap denominator: BUILT surface, not 900·m_B ─────────────
    //
    // Owner decision 2026-10-23 (tracker ticket 13): the 1/5 denominator of
    // the EN 15544:2023 clause-1 glass cap is the BUILT interior surface
    // (§3.7), NOT the §4.3.1.2 required surface (formula (3), 900·m_B)
    // carried by `ctx.O_BR`.  Both fixtures use m_B = 12 kg, so the
    // nominal O_BR is 10800 cm² and O_BR/5 is 2160 cm²; the glass area
    // sits strictly between the two readings in each direction.
    //
    // The two directional tests live in [[ConstraintFoldingSuite]]; the
    // per-term evaluation switch in [[SeverityFoldingSuite]] pins its
    // marked-vs-unmarked difference on `builtBelowNominalFirebox`, whose
    // glass area strictly FAILS the built/5 cap.

    /**
     * 50×50×80 cm: built interior surface = 2·(2500 + 4000 + 4000) =
     * 21000 cm² > nominal 10800 cm².  Glass 50×50 = 2500 cm² exceeds the
     * nominal threshold 2160 cm² but stays under built/5 = 4200 cm²:
     * PASSES under the built-surface decision, would FAIL nominal-based.
     */
    protected val builtAboveNominalFirebox: TraditionalFirebox =
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

    protected val ctxBuiltAboveNominal: ConstraintContext =
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
    protected val builtBelowNominalFirebox: TraditionalFirebox =
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

    protected val ctxBuiltBelowNominal: ConstraintContext =
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

end ConstraintFoldingFixtures
