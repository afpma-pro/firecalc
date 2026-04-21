/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v6

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V6Instances
import afpma.firecalc.dto.v5.*

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V6 - Schema version 6
 *
 * Changes from V5:
 * - Replaces separate `flue_pipe_descr`, `connector_pipe_descr`, `chimney_pipe_descr`
 *   fields with a single `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` that
 *   encodes the post-firebox pipe topology as a generic sequence of tagged pipe slots.
 * - Enables future N-pipe topologies (multiple flue segments, optional connector).
 * - Pipe descriptor types themselves are unchanged (FlowOnlyPipeDescr_15544_V3,
 *   ThermalPipeDescr_13384_V3).
 * - Firebox type unchanged from V5 (Firebox_V4).
 */
final case class FireCalcYAML_V6(
    version                       : FireCalc_Version.V[6] = FireCalcYAML_V6.VERSION,
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3],
    firebox                       : Firebox_V4,
    post_firebox_pipes            : Seq[PostFireboxPipeDescrSlot]
) extends FireCalcYAML_Format

trait FireCalcYAML_V6_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V6]:

    type Version = FireCalc_Version.V[6]
    final val VERSION: FireCalc_Version.V[6] = FireCalc_Version.v[6]

    import CommonInstances.given
    import V6Instances.given

    override given decoder: Decoder[FireCalcYAML_V6] = semiauto.deriveDecoder[FireCalcYAML_V6]
    override given encoder: Encoder[FireCalcYAML_V6] = semiauto.deriveEncoder[FireCalcYAML_V6]

object FireCalcYAML_V6 extends FireCalcYAML_V6_Module
