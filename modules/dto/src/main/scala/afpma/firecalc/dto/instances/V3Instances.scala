/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v3.Material_15544_V2
import afpma.firecalc.dto.v3.ThermalPipeDescr_13384_V2
import afpma.firecalc.dto.v3.FlowOnlyPipeDescr_13384_V2
import afpma.firecalc.dto.v3.FlowOnlyPipeDescr_15544_V2
import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

import coulomb.*
import coulomb.syntax.*

object V3Instances:

    import CommonInstances.given

    // Material_13384 V2

    given Decoder[Material_13384_V2] = semiauto.deriveDecoder[Material_13384_V2]
    given Encoder[Material_13384_V2] = semiauto.deriveEncoder[Material_13384_V2]

    // Material_15544 V2

    given Decoder[Material_15544_V2] = semiauto.deriveDecoder[Material_15544_V2]
    given Encoder[Material_15544_V2] = semiauto.deriveEncoder[Material_15544_V2]

    // ThermalPipeDescr_13384_V2

    given Decoder[ThermalPipeDescr_13384_V2] = semiauto.deriveDecoder[ThermalPipeDescr_13384_V2]
    given Encoder[ThermalPipeDescr_13384_V2] = semiauto.deriveEncoder[ThermalPipeDescr_13384_V2]

    // FlowOnlyPipeDescr_13384_V2

    given Decoder[FlowOnlyPipeDescr_13384_V2] = semiauto.deriveDecoder[FlowOnlyPipeDescr_13384_V2]
    given Encoder[FlowOnlyPipeDescr_13384_V2] = semiauto.deriveEncoder[FlowOnlyPipeDescr_13384_V2]

    // FlowOnlyPipeDescr_15544_V2

    given Decoder[FlowOnlyPipeDescr_15544_V2] = semiauto.deriveDecoder[FlowOnlyPipeDescr_15544_V2]
    given Encoder[FlowOnlyPipeDescr_15544_V2] = semiauto.deriveEncoder[FlowOnlyPipeDescr_15544_V2]
