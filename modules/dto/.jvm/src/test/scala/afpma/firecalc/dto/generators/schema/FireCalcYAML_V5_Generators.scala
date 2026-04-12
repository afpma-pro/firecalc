/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import afpma.firecalc.dto.generators.common.CommonTypes_Generators
import afpma.firecalc.dto.generators.common.StoveParams_Generators
import afpma.firecalc.dto.generators.firebox.Firebox_V4_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_13384_V3_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_15544_V3_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetThermalPipeProp_13384_V3_Generators
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import org.scalacheck.Gen

/**
 * FireCalcYAML_V5_Generators
 *
 * Generates complete FireCalcYAML_V5 instances with:
 * - V4 fireboxes (new in V5, splits R1/R2/R3 for ecolabeled, adds Door15aFirebox_Catalog)
 * - FlowOnly pipes for air intake (13384 V3 - adds roll + SetInitialDirection)
 * - FlowOnly pipes for flue (15544 V3 - adds roll + SetInitialDirection)
 * - Thermal pipes for connector and chimney (13384 V3 - adds SetPropertiesInBatch)
 */
trait FireCalcYAML_V5_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V4_Generators
    with SetFlowOnlyPipeProp_13384_V3_Generators
    with SetFlowOnlyPipeProp_15544_V3_Generators
    with SetThermalPipeProp_13384_V3_Generators:

    /**
     * Generate a complete FireCalcYAML_V5 instance
     *
     * Composes all components with V5 changes:
     * - connector and chimney use ThermalPipeDescr_13384_V3 (adds SetPropertiesInBatch)
     * - air_intake uses FlowOnly 13384 V3 (adds roll + SetInitialDirection)
     * - flue_pipe uses FlowOnly 15544 V3 (adds roll + SetInitialDirection)
     * - firebox is V4 (splits R1/R2/R3, adds Door15aFirebox_Catalog)
     */
    def genFireCalcYAML_V5: Gen[FireCalcYAML_V5] =
        for
            locale          <- genLocale
            displayUnits    <- genDisplayUnits
            method          <- genStandardOrComputationMethod
            projectDescr    <- genProjectDescr
            localConditions <- genLocalConditions
            stoveParams     <- genStoveParams
            airIntake       <- genFlowOnlyPipeDescr_13384_V3_Seq
            firebox         <- genFirebox_V4
            fluePipe        <- genFlowOnlyPipeDescr_15544_V3_Seq
            connector       <- genThermalPipeDescr_13384_V3_Seq
            chimney         <- genThermalPipeDescr_13384_V3_Seq
        yield FireCalcYAML_V5                       (
            version                        = FireCalcYAML_V5.VERSION,
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

end FireCalcYAML_V5_Generators
