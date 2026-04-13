/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import org.scalacheck.Gen
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.generators.common.{CommonTypes_Generators, StoveParams_Generators}
import afpma.firecalc.dto.generators.firebox.Firebox_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.{
    SetThermalPipeProp_13384_V1_Generators,
    SetFlowOnlyPipeProp_13384_V1_Generators,
    SetFlowOnlyPipeProp_15544_V1_Generators
}

/**
 * FireCalcYAML_V2_Generators
 *
 * Generates complete FireCalcYAML_V2 instances with:
 * - V2 fireboxes
 * - FlowOnly pipes for air intake (13384 V1)
 * - FlowOnly pipes for flue (15544 V1)
 * - Thermal pipes for connector and chimney (13384 V1)
 */
trait FireCalcYAML_V2_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V2_Generators
    with SetFlowOnlyPipeProp_13384_V1_Generators
    with SetFlowOnlyPipeProp_15544_V1_Generators
    with SetThermalPipeProp_13384_V1_Generators:

    /**
     * Generate a complete FireCalcYAML_V2 instance
     *
     * Composes all components with V2 changes:
     * - air_intake now uses FlowOnly (13384 V1) instead of Thermal
     * - firebox is now V2 (with height_of_first_row_of_air_injectors field)
     * - connector and chimney remain Thermal V1
     */
    def genFireCalcYAML_V2: Gen[FireCalcYAML_V2] =
        for
            locale          <- genLocale
            displayUnits    <- genDisplayUnits
            method          <- genStandardOrComputationMethod
            projectDescr    <- genProjectDescr
            localConditions <- genLocalConditions
            stoveParams     <- genStoveParams
            airIntake       <- genFlowOnlyPipeDescr_13384_V1_Seq
            firebox         <- genFirebox_V2
            fluePipe        <- genFlowOnlyPipeDescr_15544_V1_Seq
            connector       <- genThermalPipeDescr_13384_V1_Seq
            chimney         <- genThermalPipeDescr_13384_V1_Seq
        yield FireCalcYAML_V2                       (
            version                        = FireCalcYAML_V2.VERSION,
            locale                         = locale,
            display_units                  = displayUnits,
            standard_or_computation_method = method,
            project_description            = projectDescr,
            local_conditions               = localConditions,
            stove_params                   = stoveParams,
            air_intake_descr               = airIntake,
            firebox                        = firebox,
            flue_pipe_descr                = fluePipe,
            connector_pipe_descr           = connector,
            chimney_pipe_descr             = chimney
        )

end FireCalcYAML_V2_Generators
