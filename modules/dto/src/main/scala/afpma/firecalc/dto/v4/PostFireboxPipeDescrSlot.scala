/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v4

import io.circe.{Decoder, Encoder, Json}

/** A tagged pipe descriptor slot for the post-firebox topology.
  *
  * Each slot pairs a pipe type with its descriptor sequence. Used internally for
  * generic topology processing. Will become the serialized format in DTO V5.
  */
enum PostFireboxPipeDescrSlot:
    case FlueSlot(descr: Seq[FlowOnlyPipeDescr_15544_V3])
    case ConnectorSlot(descr: Seq[ThermalPipeDescr_13384_V3])
    case ChimneySlot(descr: Seq[ThermalPipeDescr_13384_V3])

object PostFireboxPipeDescrSlot:
    import afpma.firecalc.dto.instances.CommonInstances.given
    import afpma.firecalc.dto.instances.V4Instances.given

    given Encoder[PostFireboxPipeDescrSlot] = Encoder.instance {
        case PostFireboxPipeDescrSlot.FlueSlot(d)      =>
            Json.obj("FlueSlot" -> Encoder[Seq[FlowOnlyPipeDescr_15544_V3]].apply(d))
        case PostFireboxPipeDescrSlot.ConnectorSlot(d)  =>
            Json.obj("ConnectorSlot" -> Encoder[Seq[ThermalPipeDescr_13384_V3]].apply(d))
        case PostFireboxPipeDescrSlot.ChimneySlot(d)    =>
            Json.obj("ChimneySlot" -> Encoder[Seq[ThermalPipeDescr_13384_V3]].apply(d))
    }

    given Decoder[PostFireboxPipeDescrSlot] = Decoder.instance { c =>
        c.downField("FlueSlot").as[Seq[FlowOnlyPipeDescr_15544_V3]].map(PostFireboxPipeDescrSlot.FlueSlot(_))
            .orElse(c.downField("ConnectorSlot").as[Seq[ThermalPipeDescr_13384_V3]].map(PostFireboxPipeDescrSlot.ConnectorSlot(_)))
            .orElse(c.downField("ChimneySlot").as[Seq[ThermalPipeDescr_13384_V3]].map(PostFireboxPipeDescrSlot.ChimneySlot(_)))
    }
