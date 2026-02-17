/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V3Instances
import afpma.firecalc.dto.instances.V4Instances
import afpma.firecalc.dto.v2.*
import afpma.firecalc.dto.v3.*
import afpma.firecalc.dto.v4.*

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V4 - Schema version 4
 *
 * Changes from V3:
 * - add SetPropertiesInBatch subtype for ThermalPipeDescr_13384
 * - TODO: SetPropertiesInBatch for FlowOnlyPipeDescr_13384
 * - TODO: SetPropertiesInBatch for ThermalPipeDescr_15544
 */
final case class FireCalcYAML_V4(
    version                       : FireCalc_Version = FireCalc_Version(4),
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[afpma.firecalc.dto.v3.FlowOnlyPipeDescr_13384_V2], // TODO
    firebox                       : Firebox_V2,
    flue_pipe_descr               : Seq[afpma.firecalc.dto.v3.FlowOnlyPipeDescr_15544_V2], // TODO
    connector_pipe_descr          : Seq[ThermalPipeDescr_13384_V3],
    chimney_pipe_descr            : Seq[ThermalPipeDescr_13384_V3]
) extends FireCalcYAML_Format

trait FireCalcYAML_V4_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V4]:

    import CommonInstances.given
    import V3Instances.given
    import V4Instances.given

    override given decoder: Decoder[FireCalcYAML_V4] = semiauto.deriveDecoder[FireCalcYAML_V4]
    override given encoder: Encoder[FireCalcYAML_V4] = semiauto.deriveEncoder[FireCalcYAML_V4]

object FireCalcYAML_V4 extends FireCalcYAML_V4_Module
