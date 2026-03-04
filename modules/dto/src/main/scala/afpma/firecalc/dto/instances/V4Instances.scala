/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3
import afpma.firecalc.dto.v4.Firebox_V3
import afpma.firecalc.dto.v4.TypeOfAppliance

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto

object V4Instances:

    import CommonInstances.given
    import V3Instances.given

    given Decoder[Firebox_V3] = semiauto.deriveDecoder[Firebox_V3]
    given Encoder[Firebox_V3] = semiauto.deriveEncoder[Firebox_V3]


    // AirSpaceDetailed_V2
    // WithoutAirSpace_V2 is encoded as the plain string "WithoutAirSpace" so the
    // YAML printer emits a scalar — never the "TypeName: null" that semiauto would
    // produce for a no-field case object.
    given decoder_AirSpaceDetailed_V2: Decoder[AirSpaceDetailed_V2] =
        val derivedWithAirSpace = semiauto.deriveDecoder[AirSpaceDetailed_V2.WithAirSpace_V2]
        Decoder.instance { cursor =>
            cursor.as[String] match
                case Right("WithoutAirSpace") =>
                    Right(AirSpaceDetailed_V2.WithoutAirSpace_V2)
                case _ =>
                    // "WithAirSpace" key (no _V2 suffix) keeps the YAML clean
                    cursor.downField("WithAirSpace").as[AirSpaceDetailed_V2.WithAirSpace_V2](
                        using derivedWithAirSpace
                    )
        }

    given encoder_AirSpaceDetailed_V2: Encoder[AirSpaceDetailed_V2] = Encoder.instance {
        case AirSpaceDetailed_V2.WithoutAirSpace_V2 =>
            Json.fromString("WithoutAirSpace")
        case w: AirSpaceDetailed_V2.WithAirSpace_V2 =>
            // "WithAirSpace" key (no _V2 suffix) keeps the YAML clean
            Json.obj("WithAirSpace" -> semiauto.deriveEncoder[AirSpaceDetailed_V2.WithAirSpace_V2].apply(w))
    }

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

    // TypeOfAppliance: encoded as plain strings to avoid the YAML null bug
    // where `{}` (empty object) is emitted as `null` by the YAML printer.
    given Decoder[TypeOfAppliance] = Decoder.instance { cursor =>
        cursor.as[String] match
            case Right("WoodLogs") => Right(TypeOfAppliance.WoodLogs)
            case Right("Pellets")  => Right(TypeOfAppliance.Pellets)
            case other             =>
                Left(io.circe.DecodingFailure(s"Unknown TypeOfAppliance: $other", cursor.history))
    }

    given Encoder[TypeOfAppliance] = Encoder.instance {
        case TypeOfAppliance.WoodLogs => Json.fromString("WoodLogs")
        case TypeOfAppliance.Pellets  => Json.fromString("Pellets")
    }
