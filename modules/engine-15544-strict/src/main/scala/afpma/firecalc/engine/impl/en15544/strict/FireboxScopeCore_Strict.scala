/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.engine.alg.en15544.OpPoint15544
import afpma.firecalc.engine.models.OpPoint
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.ops.en15544 as ops_en15544
import afpma.firecalc.engine.standard.SlotContext
import afpma.firecalc.engine.standard.VNelMcalcErr

import cats.data.Validated
import cats.syntax.all.*

/**
 * The firebox-scope derivation core for the EN 15544 strict standard.
 *
 * Mixed into [[EN15544_Strict_Application]] so that the full application and
 * the standalone [[EN15544_FireboxCalculation_Strict_Application]] share one
 * derivation chain by construction. The physics exists once.
 *
 * '''Composition pattern:''' mixin with self-type. See the chunk summary for
 * the decision record (chosen + rejected alternatives + how each hard
 * criterion is met).
 *
 * The core provides:
 *   - [[fireboxScopeSolveAt]]: the per-operating-point pipe solve (firebox
 *     gas path + optional combustion-air path)
 *   - [[fireboxScopeGrid]]: the total grid over all operating points implied
 *     by the firebox
 *
 * The full application's `StrictOperatingPoint` delegates its `firebox_PipeResult`
 * and `combustionAir_PipeResult` to [[fireboxScopeSolveAt]], so no derivation
 * body is duplicated.
 */
trait FireboxScopeCore_Strict:
    self: EN15544_Strict_Application =>

    /**
     * Solve the firebox-scope pipe results at one operating point.
     *
     * Returns the firebox gas-path result (always present) and the
     * combustion-air result (`None` when the engine's contribution is
     * `Without`, mirroring `FireboxContribution.Without`).
     */
    def fireboxScopeSolveAt(
        op: OpPoint
    ): VNelMcalcErr[(PipeResult, Option[PipeResult])] =
        val dc = op.draftCondition
        val lq = op.loadQty
        (
            solveFireboxPipeAt      (dc, lq),
            solveCombustionAirPipeAt(dc, lq)
        ).mapN((fb, ca) => (fb, ca))

    /**
     * The total firebox-scope grid: every [[OpPoint]] implied by the firebox
     * (see [[OpPoint15544.axesFor]]) mapped to its solved pipe results.
     *
     * Totality contract: covers exactly the operating points the firebox
     * declares; a consumer indexing a covered point never misses.
     */
    lazy val fireboxScopeGrid: VNelMcalcErr[Map[OpPoint, (PipeResult, Option[PipeResult])]] =
        OpPoint15544
            .axesFor(firebox)
            .traverse(op => fireboxScopeSolveAt(op).map(r => op -> r))
            .map(_.toMap)

    // ── Per-path solves ──────────────────────────────────────────────────

    /**
     * The firebox gas-path result at one operating point.
     *
     * An engine that returned `FireboxContribution.AsResult` has dictated the
     * result outright — at THIS operating point, indexed from the contributed
     * grid; the solver is then not invoked.
     */
    private[strict] def solveFireboxPipeAt(
        dc: DraftCondition,
        lq: LoadQty
    ): VNelMcalcErr[PipeResult] =
        fireboxContributedResultOpt(dc, lq).andThen:
            case Some(r) => r.validNel
            case None    =>
                ops_en15544.FlowOnlyMecaFlu_15544
                    .makePipeResult                 (
                        fd                  = FireboxPipe_Module_15544.unwrap(inputs.pipes.firebox),
                        gas                 = FlueGas,
                        loadQty             = lq,
                        z_geodetical_height = z_geodetical_height,
                        params              = dc,
                        tempStartOverride   = None,
                        sc                  = SlotContext.unslotted
                    )(using self, self.ssalg(SlotContext.unslotted))
                    .toValidatedNel

    /**
     * The combustion-air path result at one operating point.
     *
     * `None` when the engine's contribution is `Without` (no combustion-air
     * pipe for this kind). An engine that returned `AsResult` has dictated the
     * result outright — at THIS operating point, indexed from the contributed
     * grid.
     */
    private[strict] def solveCombustionAirPipeAt(
        dc: DraftCondition,
        lq: LoadQty
    ): VNelMcalcErr[Option[PipeResult]] =
        combustionAirContributedResultOpt(dc, lq).andThen:
            case Some(r) => Validated.validNel(Some(r))
            case None    =>
                given OpPoint = OpPoint(dc, lq)
                CombustionAirPipe_Module_15544.foldPipeCanBe(inputs.pipes.combustionAir)  (
                    onWithout   = combustionAir_PipeResult_whenEmpty.map(Some(_)),
                    onFullDescr = fd => solveCombustionAirDescriptor(fd, dc, lq).map(Some(_))
                )

    /**
     * The combustion-air pipe solve for a full descriptor — the single
     * derivation body shared by the standalone grid ([[solveCombustionAirPipeAt]])
     * and the full application's `combustionAir_PipeResult_whenExists`.
     */
    private[strict] def solveCombustionAirDescriptor(
        fd: CombustionAirPipe_Module_15544.FullDescr,
        dc: DraftCondition,
        lq: LoadQty
    ): VNelMcalcErr[PipeResult] =
        ops_en15544.FlowOnlyMecaFlu_15544
            .makePipeResult                 (
                fd                  = CombustionAirPipe_Module_15544.unwrap(fd),
                gas                 = CombustionAir,
                loadQty             = lq,
                z_geodetical_height = z_geodetical_height,
                params              = dc,
                tempStartOverride   = None,
                sc                  = SlotContext.unslotted
            )(using self, self.ssalg(SlotContext.unslotted))
            .toValidatedNel

end FireboxScopeCore_Strict
