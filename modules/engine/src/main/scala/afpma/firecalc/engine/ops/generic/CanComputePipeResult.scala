/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import afpma.firecalc.engine.alg.en13384.{EN13384_1_A1_2019_Application_Alg, Params_13384}
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height
import afpma.firecalc.engine.ops.en13384.{FlowOnlyMecaFlu_13384, ThermalMecaFlu_13384}
import afpma.firecalc.engine.ops.en15544.FlowOnlyMecaFlu_15544
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

object CanComputePipeResult:

    /** EN 15544 flow-only pipe result. */
    def forFlowOnly15544(
        en15544App: EN15544_Strict_Application,
        z_geo     : z_geodetical_height,
        ssa       : ShortSectionAlg
    ): CanComputePipeResult[FlowOnlyPipeDescr_15544.type] =
        new CanComputePipeResult[FlowOnlyPipeDescr_15544.type]:
            val descrAlg: FlowOnlyPipeDescr_15544.type = FlowOnlyPipeDescr_15544

            def computePipeResult(
                fd      : PipeFullDescrG[descrAlg.PipeElDescr],
                gas     : Gas,
                upstream: UpstreamState,
                params  : Params_13384
            ): Either[MecaFlu_Error, PipeResult] =
                // When upstream has a real temperature (not the firebox default t_W),
                // use it as the reference for the exponential decay instead of t_burnout.
                val tBurnout = en15544App.t_burnout
                val tempOverride =
                    if upstream.temp_start != tBurnout then Some(upstream.temp_start)
                    else None
                FlowOnlyMecaFlu_15544.makePipeResult(fd, gas, params._2, z_geo, params._1, tempOverride)(using en15544App, ssa)

    /** EN 13384 thermal (full heat-transfer) pipe result. */
    def forThermal13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg      : HeatingAppliance.FlueGas,
        hamf      : HeatingAppliance.MassFlows,
        hapwr     : HeatingAppliance.Powers,
        haeff     : HeatingAppliance.Efficiency
    ): CanComputePipeResult[ThermalPipeDescr_13384.type] =
        new CanComputePipeResult[ThermalPipeDescr_13384.type]:
            val descrAlg: ThermalPipeDescr_13384.type = ThermalPipeDescr_13384

            def computePipeResult(
                fd      : PipeFullDescrG[descrAlg.PipeElDescr],
                gas     : Gas,
                upstream: UpstreamState,
                params  : Params_13384
            ): Either[MecaFlu_Error, PipeResult] =
                ThermalMecaFlu_13384.makePipeResult(
                    fd, hafg, hamf, hapwr, haeff,
                    upstream.temp_start,
                    upstream.last_pipe_density,
                    upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)

    /** EN 13384 flow-only (simplified air-intake) pipe result. */
    def forFlowOnly13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg      : HeatingAppliance.FlueGas,
        hamf      : HeatingAppliance.MassFlows,
        hapwr     : HeatingAppliance.Powers,
        haeff     : HeatingAppliance.Efficiency
    ): CanComputePipeResult[FlowOnlyPipeDescr_13384.type] =
        new CanComputePipeResult[FlowOnlyPipeDescr_13384.type]:
            val descrAlg: FlowOnlyPipeDescr_13384.type = FlowOnlyPipeDescr_13384

            def computePipeResult(
                fd      : PipeFullDescrG[descrAlg.PipeElDescr],
                gas     : Gas,
                upstream: UpstreamState,
                params  : Params_13384
            ): Either[MecaFlu_Error, PipeResult] =
                FlowOnlyMecaFlu_13384.makePipeResult(
                    fd, hafg, hamf, hapwr, haeff,
                    upstream.temp_start,
                    upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)
