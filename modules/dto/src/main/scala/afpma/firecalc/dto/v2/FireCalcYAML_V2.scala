/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v2

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V2 - Schema version 2
 * 
 * Changes from V1:
 * - Added `height_of_first_row_of_air_injectors` field to Traditional and EcoLabeled fireboxes
 *   (with default value 5.cm for backwards compatibility)
 */
final case class FireCalcYAML_V2(
    version: FireCalc_Version = FireCalc_Version(2),
    locale: Locale,
    display_units: DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description: ProjectDescr,
    local_conditions: LocalConditions,
    stove_params: StoveParams,
    air_intake_descr: Seq[FlowOnlyPipeDescr_13384_V1],
    firebox: Firebox_V2,
    flue_pipe_descr: Seq[FlowOnlyPipeDescr_15544_V1],
    connector_pipe_descr: Seq[ThermalPipeDescr_13384_V1],
    chimney_pipe_descr: Seq[ThermalPipeDescr_13384_V1],
) extends FireCalcYAML_Format

trait FireCalcYAML_V2_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V2]:
    
    import instances.given

    override given decoder: Decoder[FireCalcYAML_V2] = semiauto.deriveDecoder[FireCalcYAML_V2]
    override given encoder: Encoder[FireCalcYAML_V2] = semiauto.deriveEncoder[FireCalcYAML_V2]


object FireCalcYAML_V2 extends FireCalcYAML_V2_Module
