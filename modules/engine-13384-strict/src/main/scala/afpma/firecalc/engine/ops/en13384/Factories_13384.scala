/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.engine.alg.en13384.{
    EN13384_1_A1_2019_Application_Alg,
    EN13384_1_A1_2019_Formulas_Alg,
    Params_13384
}
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.ops.{ExteriorAirOps, GasOps, PipeWithGasFlowOps}
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, UpstreamState}
import afpma.firecalc.engine.standard.{EN13384_FormulaError, MecaFlu_Error}

/**
 * EN 13384 factory extension methods for core ops companion objects.
 *
 * Extracted from the companion objects in `ops/` so that the core `ops`
 * package does not depend on `ops.en13384` or `ops.en15544`.
 * These extensions are automatically visible within the `ops.en13384` package,
 * and can be imported at external call sites via
 * `import afpma.firecalc.engine.ops.en13384.*`.
 */

// ── GasOps ──────────────────────────────────────────────────────────────

extension (obj: GasOps.type)
    def mkUsingEN13384(en13384: EN13384_1_A1_2019_Formulas_Alg): GasOps =
        new GasOps_13384(using en13384) {}

// ── ExteriorAirOps ──────────────────────────────────────────────────────

extension (obj: ExteriorAirOps.type)
    def mkUsingEN13384(en13384: EN13384_1_A1_2019_Formulas_Alg): ExteriorAirOps =
        new ExteriorAirOps_13384(using en13384) {}

// ── PipeWithGasFlowOps ──────────────────────────────────────────────────

extension (obj: PipeWithGasFlowOps.type)
    def mkforEN13384(
        en13384: EN13384_1_A1_2019_Formulas_Alg
    ): PipeWithGasFlowOps[EN13384_FormulaError] = new PipeWithGasFlowOps_13384(using en13384) {}

// ── CanComputePipeResult ────────────────────────────────────────────────

extension (obj: CanComputePipeResult.type)

    /** EN 13384 thermal (full heat-transfer) pipe result. */
    def forThermal13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg      : HeatingAppliance.FlueGas,
        hamf      : HeatingAppliance.MassFlows
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
                    fd,
                    hafg,
                    hamf,
                    upstream.temp_start,
                    upstream.last_pipe_density,
                    upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)

    /** EN 13384 flow-only (simplified air-intake) pipe result. */
    def forFlowOnly13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg      : HeatingAppliance.FlueGas,
        hamf      : HeatingAppliance.MassFlows
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
                    fd,
                    hafg,
                    hamf,
                    upstream.temp_start,
                    upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)
