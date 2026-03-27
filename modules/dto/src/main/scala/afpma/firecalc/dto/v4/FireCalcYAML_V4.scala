/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V4Instances
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
 * - use AirSpaceDetailed_V2 (exported as AirSpaceDetailed) in all V4 pipe descriptors:
 *   - WithoutAirSpace is now encoded as the plain string "WithoutAirSpace"
 *     (instead of `{"WithoutAirSpace": {}}`) to avoid the YAML null round-trip
 *     bug where `{}` was emitted as `null` by the YAML printer
 *   - WithAirSpace is encoded as `{"WithAirSpace": {width, direction, ventil_openings}}`
 * - introduce MinLoad
 * - upgrade air_intake_descr to FlowOnlyPipeDescr_13384_V3 (adds roll + SetInitialDirection)
 * - upgrade flue_pipe_descr to FlowOnlyPipeDescr_15544_V3 (adds roll + SetInitialDirection)
 * - TODO: SetPropertiesInBatch for FlowOnlyPipeDescr_13384
 * - TODO: SetPropertiesInBatch for ThermalPipeDescr_15544
 */
final case class FireCalcYAML_V4(
    version                       : FireCalc_Version.V[4] = FireCalcYAML_V4.VERSION,
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3],
    firebox                       : Firebox_V3,
    flue_pipe_descr               : Seq[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3],
    connector_pipe_descr          : Seq[ThermalPipeDescr_13384_V3],
    chimney_pipe_descr            : Seq[ThermalPipeDescr_13384_V3]
) extends FireCalcYAML_Format

trait FireCalcYAML_V4_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V4]:

    type Version = FireCalc_Version.V[4]
    final val VERSION: FireCalc_Version.V[4] = FireCalc_Version.v[4]

    import CommonInstances.given
    import V4Instances.given

    override given decoder: Decoder[FireCalcYAML_V4] = semiauto.deriveDecoder[FireCalcYAML_V4]
    override given encoder: Encoder[FireCalcYAML_V4] = semiauto.deriveEncoder[FireCalcYAML_V4]

object FireCalcYAML_V4 extends FireCalcYAML_V4_Module
