/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.standard.SlotIndex

/**
 * A single slot in the post-firebox pipe chain.
 *
 * Existentially hides the description algebra — callers see only `PipeType`, `Gas`,
 * and the ability to `compute` a `PipeResult` given upstream state and params.
 *
 * Created ONLY via `CanComputePipeResult#mkSlot` (cast-free) or `PipeSlot.noop`.
 * There is intentionally no public constructor.
 */
trait PipeSlot:
    val pipeType: PipeType
    val label   : String
    val gas     : Gas

    /** Compute this pipe's result given upstream state and EN 13384 params. */
    def compute(upstream: UpstreamState, params: Params_13384, slotIndex: SlotIndex): Either[MecaFlu_Error, PipeResult]

    /** The pipe's element descriptors (type-erased). Used for validation access (e.g. inner shapes). */
    def elements: Vector[NamedPipeElDescrG[?]]

object PipeSlot:

    /**
     * No-op slot for absent optional pipes (e.g., connector = Without).
     * Passes temperature AND upstream density/velocity through unchanged,
     * so the next slot in the chain receives valid propagation values.
     */
    def noop(_pipeType: PipeType, _label: String): PipeSlot = new PipeSlot:
        val pipeType                                                                       = _pipeType
        val label                                                                          = _label
        val gas                                                                            = FlueGas
        def elements                                                                       = Vector.empty
        def compute(upstream: UpstreamState, _params: Params_13384, _slotIndex: SlotIndex) =
            Right(
                PipeResult.useless (
                    pipeType,
                    upstream.temp_start,
                    lastDensity  = upstream.last_pipe_density,
                    lastVelocity = upstream.last_pipe_velocity
                )
            )
