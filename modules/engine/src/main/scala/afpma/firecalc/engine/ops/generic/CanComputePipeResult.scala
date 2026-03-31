/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.MecaFlu_Error

/** Typeclass that knows how to compute a `PipeResult` for a specific `PipeDescrAlg`.
  *
  * The path-dependent type `descrAlg.PipeElDescr` flows through `mkSlot` and
  * `computePipeResult` without any casts — the compiler sees that the
  * `PipeFullDescrG` captured by the slot and the one consumed by
  * `computePipeResult` share the same stable path.
  */
trait CanComputePipeResult[D <: PipeDescrAlg]:
    val descrAlg: D

    def computePipeResult(
        fd      : PipeFullDescrG[descrAlg.PipeElDescr],
        gas     : Gas,
        upstream: UpstreamState,
        params  : Params_13384
    ): Either[MecaFlu_Error, PipeResult]

    /** Build a `PipeSlot` that closes over the full description and delegates
      * to `computePipeResult` at compute-time.  Because `capturedFd` and
      * `self.descrAlg.PipeElDescr` share the same stable path, zero casts are
      * needed.
      */
    final def mkSlot(
        _pipeType: PipeType,
        _label   : String,
        _gas     : Gas,
        fd       : PipeFullDescrG[descrAlg.PipeElDescr]
    ): PipeSlot =
        // Capture the computation as a closure *before* entering the anonymous class.
        // This avoids path-dependent type mismatch between `this.descrAlg` and `self.descrAlg`.
        val capturedElements = fd.elements.map(e => e: NamedPipeElDescrG[?])
        val capturedCompute  = (upstream: UpstreamState, params: Params_13384) =>
            this.computePipeResult(fd, _gas, upstream, params)
        new PipeSlot:
            val pipeType = _pipeType
            val label    = _label
            val gas      = _gas
            def elements = capturedElements
            def compute(upstream: UpstreamState, params: Params_13384) =
                capturedCompute(upstream, params)

object CanComputePipeResult
