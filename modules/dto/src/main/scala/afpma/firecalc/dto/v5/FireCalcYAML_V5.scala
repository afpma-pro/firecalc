/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v5

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.instances.CommonInstances
import afpma.firecalc.dto.instances.V5Instances
import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v5.*

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Locale

/**
 * FireCalcYAML_V5 - Schema version 5
 *
 * Changes from V4:
 *   - bump to Firebox_V4 : R1,R2,R3,Y,Lr,Lt,Zt values for ecolabeled firebox
 */
final case class FireCalcYAML_V5(
    // FireCalc_Version.V[5] is a singleton literal type — prevents Chimney from auto-copying
    // the version field during migrations. See FIreCalc_Version.scala for details.
    version                       : FireCalc_Version.V[5] = FireCalcYAML_V5.VERSION,
    locale                        : Locale,
    display_units                 : DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description           : ProjectDescr,
    local_conditions              : LocalConditions,
    stove_params                  : StoveParams,
    air_intake_descr              : Seq[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3],
    firebox                       : Firebox_V4,
    flue_pipe_descr               : Seq[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3],
    connector_pipe_descr          : Seq[ThermalPipeDescr_13384_V3],
    chimney_pipe_descr            : Seq[ThermalPipeDescr_13384_V3]
) extends FireCalcYAML_Format

trait FireCalcYAML_V5_Module extends CustomYAMLEncoderDecoder[FireCalcYAML_V5]:

    type Version = FireCalc_Version.V[5]
    final val VERSION: FireCalc_Version.V[5] = FireCalc_Version.v[5]

    import CommonInstances.given
    import V5Instances.given

    override given decoder: Decoder[FireCalcYAML_V5] = semiauto.deriveDecoder[FireCalcYAML_V5]
    override given encoder: Encoder[FireCalcYAML_V5] = semiauto.deriveEncoder[FireCalcYAML_V5]

object FireCalcYAML_V5 extends FireCalcYAML_V5_Module
