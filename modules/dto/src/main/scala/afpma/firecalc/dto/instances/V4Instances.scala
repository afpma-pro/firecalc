/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v3.FlowOnlyPipeDescr_13384_V2
import afpma.firecalc.dto.v3.FlowOnlyPipeDescr_15544_V2
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v3.Material_15544_V2
import afpma.firecalc.dto.v3.ThermalPipeDescr_13384_V2
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

object V4Instances:

    import CommonInstances.given
    import V3Instances.given

    // ThermalPipeDescr_13384_V3

    given Decoder[SetThermalPipeProp_13384_V3.SetSingleProp] = semiauto.deriveDecoder[SetThermalPipeProp_13384_V3.SetSingleProp]
    given Encoder[SetThermalPipeProp_13384_V3.SetSingleProp] = semiauto.deriveEncoder[SetThermalPipeProp_13384_V3.SetSingleProp]

    given Decoder[SetThermalPipeProp_13384_V3.SetPropertiesInBatch] = semiauto.deriveDecoder
    given Encoder[SetThermalPipeProp_13384_V3.SetPropertiesInBatch] = semiauto.deriveEncoder

    given Decoder[ThermalPipeDescr_13384_V3] = semiauto.deriveDecoder[ThermalPipeDescr_13384_V3]
    given Encoder[ThermalPipeDescr_13384_V3] = semiauto.deriveEncoder[ThermalPipeDescr_13384_V3]

    // FlowOnlyPipeDescr_13384_V3 (TODO)

    // given Decoder[FlowOnlyPipeDescr_13384_V2] = semiauto.deriveDecoder[FlowOnlyPipeDescr_13384_V2]
    // given Encoder[FlowOnlyPipeDescr_13384_V2] = semiauto.deriveEncoder[FlowOnlyPipeDescr_13384_V2]

    // FlowOnlyPipeDescr_15544_V3 (TODO)

    // given Decoder[FlowOnlyPipeDescr_15544_V2] = semiauto.deriveDecoder[FlowOnlyPipeDescr_15544_V2]
    // given Encoder[FlowOnlyPipeDescr_15544_V2] = semiauto.deriveEncoder[FlowOnlyPipeDescr_15544_V2]
