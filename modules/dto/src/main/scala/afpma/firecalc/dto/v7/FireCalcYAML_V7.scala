/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V7Instances

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V7 - Schema version 7
 *
 * Changes from V6:
 * - Replaces flat `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]` with
 *   `post_firebox_pipes: PostFireboxPipes` wrapper type.
 * - `PostFireboxPipes` enforces exactly one `initialDirection` and one
 *   `initialPosition` at the chain level, instead of embedding them as
 *   regular elements inside slot descriptors.
 * - Slot descriptor types upgraded to V4 (FlowOnlyPipeDescr_15544_V4,
 *   FlowOnlyPipeDescr_13384_V4, ThermalPipeDescr_13384_V4).
 * - `air_intake_descr` uses FlowOnlyPipeDescr_13384_V4.
 */
final case class FireCalcYAML_V7(
    version                       : FireCalc_Version.V[7] = FireCalcYAML_V7.VERSION,
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[FlowOnlyPipeDescr_13384_V4],
    firebox                       : afpma.firecalc.dto.v5.Firebox_V4,
    post_firebox_pipes            : PostFireboxPipes
) extends FireCalcYAML_Format

trait FireCalcYAML_V7_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V7]:

    type Version = FireCalc_Version.V[7]
    final val VERSION: FireCalc_Version.V[7] = FireCalc_Version.v[7]

    import CommonInstances.given
    import V7Instances.given

    override given decoder: Decoder[FireCalcYAML_V7] = semiauto.deriveDecoder[FireCalcYAML_V7]
    override given encoder: Encoder[FireCalcYAML_V7] = semiauto.deriveEncoder[FireCalcYAML_V7]

object FireCalcYAML_V7 extends FireCalcYAML_V7_Module
