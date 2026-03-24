/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v5

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
 * FireCalcYAML_V5 - Schema version 5
 *
 * Changes from V4:
 * - Replaces separate `flue_pipe_descr`, `connector_pipe_descr`, `chimney_pipe_descr`
 *   fields with a single `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` that
 *   encodes the post-firebox pipe topology as a generic sequence of tagged pipe slots.
 * - Enables future N-pipe topologies (multiple flue segments, optional connector).
 * - Pipe descriptor types themselves are unchanged (FlowOnlyPipeDescr_15544_V3,
 *   ThermalPipeDescr_13384_V3).
 */
final case class FireCalcYAML_V5(
    version                       : FireCalc_Version = FireCalcYAML_V5.VERSION,
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[FlowOnlyPipeDescr_13384_V3],
    firebox                       : Firebox_V3,
    post_firebox_pipes            : Seq[PostFireboxPipeDescrSlot]
) extends FireCalcYAML_Format:

    /** Extract flue pipe descriptors (first FlueSlot, matching V4 single-flue behavior). */
    def flue_pipe_descr: Seq[FlowOnlyPipeDescr_15544_V3] =
        post_firebox_pipes.collectFirst { case PostFireboxPipeDescrSlot.FlueSlot(d) => d }.getOrElse(Seq.empty)

    /** Extract connector pipe descriptors (first ConnectorSlot). */
    def connector_pipe_descr: Seq[ThermalPipeDescr_13384_V3] =
        post_firebox_pipes.collectFirst { case PostFireboxPipeDescrSlot.ConnectorSlot(d) => d }.getOrElse(Seq.empty)

    /** Extract chimney pipe descriptors (first ChimneySlot). */
    def chimney_pipe_descr: Seq[ThermalPipeDescr_13384_V3] =
        post_firebox_pipes.collectFirst { case PostFireboxPipeDescrSlot.ChimneySlot(d) => d }.getOrElse(Seq.empty)

trait FireCalcYAML_V5_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V5]:

    type Version = FireCalc_Version
    final val VERSION = FireCalc_Version(5)

    import CommonInstances.given
    import V4Instances.given
    // PostFireboxPipeDescrSlot codecs are given in its companion object (v4 package)
    // Scala 3 implicit search finds them automatically via the type's companion

    override given decoder: Decoder[FireCalcYAML_V5] = semiauto.deriveDecoder[FireCalcYAML_V5]
    override given encoder: Encoder[FireCalcYAML_V5] = semiauto.deriveEncoder[FireCalcYAML_V5]

object FireCalcYAML_V5 extends FireCalcYAML_V5_Module
