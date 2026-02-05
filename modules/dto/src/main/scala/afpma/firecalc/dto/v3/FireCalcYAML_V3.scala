/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v3

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v2.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V2Instances
import afpma.firecalc.dto.instances.V3Instances
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V3 - Schema version 3
 * 
 * Changes from V2:
 * - User can specify and override default roughtness when setting the propserty "SetMaterial" in EN13384 and EN15544 pipes
 * - Material_15544_V2 is to replace Material_15544
 *   
 */
final case class FireCalcYAML_V3(
    version: FireCalc_Version = FireCalc_Version(3),
    locale: Locale,
    display_units: DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description: ProjectDescr,
    local_conditions: LocalConditions,
    stove_params: StoveParams,
    air_intake_descr: Seq[FlowOnlyPipeDescr_13384_V2],
    firebox: Firebox_V2,
    flue_pipe_descr: Seq[FlowOnlyPipeDescr_15544_V2],
    connector_pipe_descr: Seq[ThermalPipeDescr_13384_V2],
    chimney_pipe_descr: Seq[ThermalPipeDescr_13384_V2],
) extends FireCalcYAML_Format

trait FireCalcYAML_V3_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V3]:
    
    import CommonInstances.given
    import V2Instances.given
    import V3Instances.given

    override given decoder: Decoder[FireCalcYAML_V3] = semiauto.deriveDecoder[FireCalcYAML_V3]
    override given encoder: Encoder[FireCalcYAML_V3] = semiauto.deriveEncoder[FireCalcYAML_V3]


object FireCalcYAML_V3 extends FireCalcYAML_V3_Module
