/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v1

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale


final case class FireCalcYAML_V1(
    version: FireCalc_Version = FireCalc_Version(1),
    locale: Locale,
    display_units: DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description: ProjectDescr,
    local_conditions: LocalConditions,
    stove_params: StoveParams,
    air_intake_descr: Seq[IncrDescr_13384],
    firebox: Firebox,
    flue_pipe_descr: Seq[IncrDescr_15544],
    connector_pipe_descr: Seq[IncrDescr_13384],
    chimney_pipe_descr: Seq[IncrDescr_13384],
) extends FireCalcYAML_Format

trait FireCalcYAML_V1_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V1]:
    
    import instances.given

    override given decoder: Decoder[FireCalcYAML_V1] = semiauto.deriveDecoder[FireCalcYAML_V1]
    override given encoder: Encoder[FireCalcYAML_V1] = semiauto.deriveEncoder[FireCalcYAML_V1]


object FireCalcYAML_V1 extends FireCalcYAML_V1_Module