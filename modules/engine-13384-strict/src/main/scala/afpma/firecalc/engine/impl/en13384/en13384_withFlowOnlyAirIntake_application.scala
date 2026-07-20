/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.alg.en13384.WithParams_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.SlotContext
import afpma.firecalc.engine.models.en13384.Inputs_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.ops.en13384 as ops_en13384

import coulomb.*
import coulomb.policy.standard.given

abstract class EN13384_WithFlowOnlyAirIntake_Application(
    override val formulas: EN13384_1_A1_2019_Formulas_Alg
) extends EN13384_1_A1_2019_Common_Application(formulas)
    with HasTypeMembers_13384_WithFlowOnlyAirIntake {
    import Params_13384.given

    override def airIntake_PipeResult_withVentilationOpenings(
        fd                : AirIntakePipe_Module.FullDescr
    ): HeatingAppliance.CtxOp4_EFPoM[WithParams_13384[PipeResultE]] =
        ops_en13384.FlowOnlyMecaFlu_13384.makePipeResult(
            fd                 = FlowOnlyAirIntakePipe_Module_13384.unwrap(fd),
            hafg               = HeatingAppliance.FlueGas.summon,
            hamf               = HeatingAppliance.MassFlows.summon,
            temp_start         = T_L,
            last_pipe_velocity = None,
            gas                = CombustionAir,
            sc                 = SlotContext.unslotted
        )

    override def airIntake_PipeResult =
        FlowOnlyAirIntakePipe_Module_13384.foldPipeCanBe(inputs.pipes.airIntake)(
            onNoVentilation = airIntake_PipeResult_withoutVentilationOpenings,
            onFullDescr     = fd => airIntake_PipeResult_withVentilationOpenings(fd)
        )
}

object EN13384_WithFlowOnlyAirIntake_Application:
    def make(
        f: EN13384_1_A1_2019_Formulas_Alg,
        i: Inputs_13384_WithFlowOnlyAirIntake
    ): EN13384_WithFlowOnlyAirIntake_Application =
        new EN13384_WithFlowOnlyAirIntake_Application(f) {
            override final lazy val inputs = i
        }
