/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.alg.en13384.WithParams_13384
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models.en13384.std.Inputs

import afpma.firecalc.units.coulombutils.Density
import afpma.firecalc.units.coulombutils.FlowVelocity


abstract class EN13384_Strict_Application(
    override val formulas: EN13384_1_A1_2019_Formulas_Alg,
    override val inputs: Inputs,
)
    extends EN13384_1_A1_2019_Common_Application(
        formulas,
        inputs
    )
{
    override lazy val last_known_density_before_connector_pipe     : WithParams_13384[Option[Density]]         = None
    override lazy val last_known_velocity_before_connector_pipe    : WithParams_13384[Option[FlowVelocity]]    = None
}

object EN13384_Strict_Application:
    def make(f: EN13384_1_A1_2019_Formulas_Alg, i: Inputs): EN13384_Strict_Application = 
        new EN13384_Strict_Application(f, i) {
            final override lazy val computeAt = ComputeAt.Mean
            final override lazy val p_L_override = None
        }