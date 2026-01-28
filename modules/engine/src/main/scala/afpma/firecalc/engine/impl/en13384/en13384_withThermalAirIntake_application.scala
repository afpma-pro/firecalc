/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import algebra.instances.all.given
import afpma.firecalc.units.coulombutils.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.alg.en13384.WithParams_13384
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application.ComputeAt
import afpma.firecalc.engine.models.en13384.std.Inputs_13384_Alg
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.typedefs.*

import afpma.firecalc.dto.common.DuctType

import afpma.firecalc.units.coulombutils.Density
import afpma.firecalc.units.coulombutils.FlowVelocity


abstract class EN13384_WithThermalAirIntake_Application(
    override val formulas: EN13384_1_A1_2019_Formulas_Alg,
)
    extends EN13384_1_A1_2019_Common_Application(formulas)
    with HasTypeMembers_13384_WithThermalAirIntake
{
    import Params_13384.given

    override def airIntake_PipeResult_withVentilationOpenings(fd: AirIntakePipe_Module.FullDescr): HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]] =
        ops_en13384.ThermalMecaFlu_13384.makePipeResult(
            fd                          = ThermalAirIntakePipe_Module_13384.unwrap(fd),
            hafg                        = HeatingAppliance.FlueGas.summon,
            hamf                        = HeatingAppliance.MassFlows.summon,
            hapwr                       = HeatingAppliance.Powers.summon,
            haeff                       = HeatingAppliance.Efficiency.summon,
            temp_start                  = T_L,
            last_pipe_density           = None,
            last_pipe_velocity          = None,
            gas                         = CombustionAir,
        )

    override def airIntake_PipeResult =
        ThermalAirIntakePipe_Module_13384.foldPipeCanBe(inputs.pipes.airIntake)(
            onNoVentilation = airIntake_PipeResult_withoutVentilationOpenings,
            onFullDescr     = fd => airIntake_PipeResult_withVentilationOpenings(fd)
        )
}

object EN13384_WithThermalAirIntake_Application:
    def make(f: EN13384_1_A1_2019_Formulas_Alg, i: Inputs_13384_WithThermalAirIntake): EN13384_WithThermalAirIntake_Application = 
        new EN13384_WithThermalAirIntake_Application(f) {
            final override lazy val inputs = i
        }