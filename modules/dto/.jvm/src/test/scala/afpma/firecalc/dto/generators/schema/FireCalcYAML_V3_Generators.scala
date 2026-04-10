/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import afpma.firecalc.dto.generators.common.CommonTypes_Generators
import afpma.firecalc.dto.generators.common.StoveParams_Generators
import afpma.firecalc.dto.generators.firebox.Firebox_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_13384_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_15544_V2_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetThermalPipeProp_13384_V2_Generators
import afpma.firecalc.dto.v3.FireCalcYAML_V3

import org.scalacheck.Gen

/**
 * FireCalcYAML_V3_Generators
 *
 * Generates complete FireCalcYAML_V3 instances with:
 * - V2 fireboxes (reused from V2)
 * - FlowOnly pipes for air intake (13384 V2 - with Material_13384_V2)
 * - FlowOnly pipes for flue (15544 V2 - with Material_15544_V2)
 * - Thermal pipes for connector and chimney (13384 V2)
 */
trait FireCalcYAML_V3_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V2_Generators
    with SetFlowOnlyPipeProp_13384_V2_Generators
    with SetFlowOnlyPipeProp_15544_V2_Generators
    with SetThermalPipeProp_13384_V2_Generators:

    /**
     * Generate a complete FireCalcYAML_V3 instance
     *
     * Composes all components with V3 changes:
     * - all pipe descriptors now use V2 variants with updated material support
     * - air_intake uses FlowOnly (13384 V2) with Material_13384_V2
     * - flue_pipe uses FlowOnly (15544 V2) with Material_15544_V2
     * - connector and chimney use Thermal V2 with Material_13384_V2
     * - firebox remains V2 (no changes from V2)
     */
    def genFireCalcYAML_V3: Gen[FireCalcYAML_V3] =
        for
            locale <- genLocale
            displayUnits <- genDisplayUnits
            method <- genStandardOrComputationMethod
            projectDescr <- genProjectDescr
            localConditions <- genLocalConditions
            stoveParams <- genStoveParams
            airIntake <- genFlowOnlyPipeDescr_13384_V2_Seq
            firebox <- genFirebox_V2
            fluePipe <- genFlowOnlyPipeDescr_15544_V2_Seq
            connector <- genThermalPipeDescr_13384_V2_Seq
            chimney <- genThermalPipeDescr_13384_V2_Seq
        yield FireCalcYAML_V3(
            version = FireCalcYAML_V3.VERSION,
            locale = locale,
            display_units = displayUnits,
            standard_or_computation_method = method,
            project_description = projectDescr,
            local_conditions = localConditions,
            stove_params = stoveParams,
            air_intake_descr = airIntake,
            firebox = firebox,
            flue_pipe_descr = fluePipe,
            connector_pipe_descr = connector,
            chimney_pipe_descr = chimney
        )

end FireCalcYAML_V3_Generators
