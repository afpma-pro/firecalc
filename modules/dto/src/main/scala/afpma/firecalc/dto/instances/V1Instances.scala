/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.utils.circe.{*, given}

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

import coulomb.*
import coulomb.syntax.*

object V1Instances:

    import CommonInstances.given

    // Material_13384 V1

    given Decoder[Material_13384_V1] = deriveDecoderForEnum[Material_13384_V1](Material_13384_V1.valueOf)
    given Encoder[Material_13384_V1] = deriveEncoderForEnum[Material_13384_V1]

    // Material_15544 V1

    given Decoder[Material_15544_V1] = deriveDecoderForEnum[Material_15544_V1](Material_15544_V1.valueOf)
    given Encoder[Material_15544_V1] = deriveEncoderForEnum[Material_15544_V1]

    // ThermalPipeDescr_13384_V1

    given Decoder[ThermalPipeDescr_13384_V1] = semiauto.deriveDecoder[ThermalPipeDescr_13384_V1]
    given Encoder[ThermalPipeDescr_13384_V1] = semiauto.deriveEncoder[ThermalPipeDescr_13384_V1]

    // FlowOnlyPipeDescr_13384_V1

    given Decoder[FlowOnlyPipeDescr_13384_V1] = semiauto.deriveDecoder[FlowOnlyPipeDescr_13384_V1]
    given Encoder[FlowOnlyPipeDescr_13384_V1] = semiauto.deriveEncoder[FlowOnlyPipeDescr_13384_V1]

    // FlowOnlyPipeDescr_15544_V1

    given Decoder[FlowOnlyPipeDescr_15544_V1] = semiauto.deriveDecoder[FlowOnlyPipeDescr_15544_V1]
    given Encoder[FlowOnlyPipeDescr_15544_V1] = semiauto.deriveEncoder[FlowOnlyPipeDescr_15544_V1]
