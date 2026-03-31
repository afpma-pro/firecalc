/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, UpstreamState}
import afpma.firecalc.engine.standard.MecaFlu_Error

/** EN 15544 factory for [[CanComputePipeResult]].
  *
  * Extracted from the [[CanComputePipeResult]] companion object so that the
  * core `ops.generic` package does not depend on `ops.en15544`.
  */
extension (obj: CanComputePipeResult.type)

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
