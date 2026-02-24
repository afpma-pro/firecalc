/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.common.CommonTypes_Generators
import afpma.firecalc.dto.generators.common.StoveParams_Generators
import afpma.firecalc.dto.generators.firebox.Firebox_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_13384_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_15544_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetThermalPipeProp_13384_V3_Generators
import afpma.firecalc.dto.v4.FireCalcYAML_V4

import org.scalacheck.Gen

/**
 * FireCalcYAML_V4_Generators
 *
 * Generates complete FireCalcYAML_V4 instances with:
 * - V2 fireboxes (reused from V2/V3)
 * - FlowOnly pipes for air intake (13384 V2 - unchanged from V3)
 * - FlowOnly pipes for flue (15544 V2 - unchanged from V3)
 * - Thermal pipes for connector and chimney (13384 V3 - new in V4, adds SetPropertiesInBatch)
 */
trait FireCalcYAML_V4_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V2_Generators
    with SetFlowOnlyPipeProp_13384_V2_Generators
    with SetFlowOnlyPipeProp_15544_V2_Generators
    with SetThermalPipeProp_13384_V3_Generators:

    /**
     * Generate a complete FireCalcYAML_V4 instance
     *
     * Composes all components with V4 changes:
     * - connector and chimney now use ThermalPipeDescr_13384_V3 (adds SetPropertiesInBatch)
     * - air_intake uses FlowOnly (13384 V2) - unchanged from V3
     * - flue_pipe uses FlowOnly (15544 V2) - unchanged from V3
     * - firebox remains V2 (no changes)
     */
    def genFireCalcYAML_V4: Gen[FireCalcYAML_V4] =
        for
            locale        <- genLocale
            displayUnits  <- genDisplayUnits
            method        <- genStandardOrComputationMethod
            projectDescr  <- genProjectDescr
            localConditions <- genLocalConditions
            stoveParams   <- genStoveParams
            airIntake     <- genFlowOnlyPipeDescr_13384_V2_Seq
            firebox       <- genFirebox_V2
            fluePipe      <- genFlowOnlyPipeDescr_15544_V2_Seq
            connector     <- genThermalPipeDescr_13384_V3_Seq
            chimney       <- genThermalPipeDescr_13384_V3_Seq
        yield FireCalcYAML_V4(
            version                        = FireCalc_Version(4),
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

end FireCalcYAML_V4_Generators
