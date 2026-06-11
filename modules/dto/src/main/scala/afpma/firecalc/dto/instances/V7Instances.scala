/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.FlowOnlyPreElementOp_15544_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_15544_V4
import afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_15544_V4
import afpma.firecalc.dto.v7.FlowOnlyPipeTrackingOp_15544_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4

import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_13384_V4
import afpma.firecalc.dto.v7.FlowOnlyPreElementOp_13384_V4
import afpma.firecalc.dto.v7.SetFlowOnlyPipeProp_13384_V4
import afpma.firecalc.dto.v7.FlowOnlyChannelTopologyOp_13384_V4
import afpma.firecalc.dto.v7.FlowOnlyPipeTrackingOp_13384_V4
import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_13384_V4

import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.dto.v7.ThermalPreElementOp_13384_V4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4
import afpma.firecalc.dto.v7.ThermalChannelTopologyOp_13384_V4
import afpma.firecalc.dto.v7.ThermalPipeTrackingOp_13384_V4
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4

import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto

object V7Instances:

    import CommonInstances.given
    import V3Instances.given
    import V4Instances.given

    // Re-exports V6 codec instances for types unchanged between V6 and V7
    // (slot descriptors, thermal properties, FireCalcYAML_V6, etc.)
    export V6Instances.given

    // ── FlowOnlyPipeDescr_15544_V4 ────────────────────────────────────────

    // SetFlowOnlyPipeProp_15544_V4 — leaf case classes derived automatically
    // by the sealed-trait derivation below.

    given Decoder[SetFlowOnlyPipeProp_15544_V4] =
        semiauto.deriveDecoder[SetFlowOnlyPipeProp_15544_V4]
    given Encoder[SetFlowOnlyPipeProp_15544_V4] =
        semiauto.deriveEncoder[SetFlowOnlyPipeProp_15544_V4]

    given Decoder[FlowOnlyChannelTopologyOp_15544_V4] =
        semiauto.deriveDecoder[FlowOnlyChannelTopologyOp_15544_V4]
    given Encoder[FlowOnlyChannelTopologyOp_15544_V4] =
        semiauto.deriveEncoder[FlowOnlyChannelTopologyOp_15544_V4]

    given Decoder[FlowOnlyPipeTrackingOp_15544_V4] =
        semiauto.deriveDecoder[FlowOnlyPipeTrackingOp_15544_V4]
    given Encoder[FlowOnlyPipeTrackingOp_15544_V4] =
        semiauto.deriveEncoder[FlowOnlyPipeTrackingOp_15544_V4]

    given Decoder[FlowOnlyPreElementOp_15544_V4] =
        semiauto.deriveDecoder[FlowOnlyPreElementOp_15544_V4]
    given Encoder[FlowOnlyPreElementOp_15544_V4] =
        semiauto.deriveEncoder[FlowOnlyPreElementOp_15544_V4]

    given Decoder[AddFlowOnlyPipeElement_15544_V4] =
        semiauto.deriveDecoder[AddFlowOnlyPipeElement_15544_V4]
    given Encoder[AddFlowOnlyPipeElement_15544_V4] =
        semiauto.deriveEncoder[AddFlowOnlyPipeElement_15544_V4]

    given Decoder[FlowOnlyPipeDescr_15544_V4] =
        semiauto.deriveDecoder[FlowOnlyPipeDescr_15544_V4]
    given Encoder[FlowOnlyPipeDescr_15544_V4] =
        semiauto.deriveEncoder[FlowOnlyPipeDescr_15544_V4]

    // ── FlowOnlyPipeDescr_13384_V4 ───────────────────────────────────────

    given Decoder[SetFlowOnlyPipeProp_13384_V4] =
        semiauto.deriveDecoder[SetFlowOnlyPipeProp_13384_V4]
    given Encoder[SetFlowOnlyPipeProp_13384_V4] =
        semiauto.deriveEncoder[SetFlowOnlyPipeProp_13384_V4]

    given Decoder[FlowOnlyChannelTopologyOp_13384_V4] =
        semiauto.deriveDecoder[FlowOnlyChannelTopologyOp_13384_V4]
    given Encoder[FlowOnlyChannelTopologyOp_13384_V4] =
        semiauto.deriveEncoder[FlowOnlyChannelTopologyOp_13384_V4]

    given Decoder[FlowOnlyPipeTrackingOp_13384_V4] =
        semiauto.deriveDecoder[FlowOnlyPipeTrackingOp_13384_V4]
    given Encoder[FlowOnlyPipeTrackingOp_13384_V4] =
        semiauto.deriveEncoder[FlowOnlyPipeTrackingOp_13384_V4]

    given Decoder[FlowOnlyPreElementOp_13384_V4] =
        semiauto.deriveDecoder[FlowOnlyPreElementOp_13384_V4]
    given Encoder[FlowOnlyPreElementOp_13384_V4] =
        semiauto.deriveEncoder[FlowOnlyPreElementOp_13384_V4]

    given Decoder[AddFlowOnlyPipeElement_13384_V4] =
        semiauto.deriveDecoder[AddFlowOnlyPipeElement_13384_V4]
    given Encoder[AddFlowOnlyPipeElement_13384_V4] =
        semiauto.deriveEncoder[AddFlowOnlyPipeElement_13384_V4]

    given Decoder[FlowOnlyPipeDescr_13384_V4] =
        semiauto.deriveDecoder[FlowOnlyPipeDescr_13384_V4]
    given Encoder[FlowOnlyPipeDescr_13384_V4] =
        semiauto.deriveEncoder[FlowOnlyPipeDescr_13384_V4]

    // ── ThermalPipeDescr_13384_V4 ────────────────────────────────────────

    // Leaf types of SetThermalPipeProp_13384_V4 — derived first
    // Explicit names avoid collision with exported V3 givens of the same
    // simple name (e.g. SetSingleProp, SetPropertiesInBatch, LinedFlue).

    given decoder_SetSingleProp_V4: Decoder[SetThermalPipeProp_13384_V4.SetSingleProp] =
        semiauto.deriveDecoder[SetThermalPipeProp_13384_V4.SetSingleProp]
    given encoder_SetSingleProp_V4: Encoder[SetThermalPipeProp_13384_V4.SetSingleProp] =
        semiauto.deriveEncoder[SetThermalPipeProp_13384_V4.SetSingleProp]

    given decoder_SetPropertiesInBatch_V4: Decoder[SetThermalPipeProp_13384_V4.SetPropertiesInBatch] =
        semiauto.deriveDecoder[SetThermalPipeProp_13384_V4.SetPropertiesInBatch]
    given encoder_SetPropertiesInBatch_V4: Encoder[SetThermalPipeProp_13384_V4.SetPropertiesInBatch] =
        semiauto.deriveEncoder[SetThermalPipeProp_13384_V4.SetPropertiesInBatch]

    given decoder_LinedFlue_V4: Decoder[SetThermalPipeProp_13384_V4.LinedFlue] =
        semiauto.deriveDecoder[SetThermalPipeProp_13384_V4.LinedFlue]
    given encoder_LinedFlue_V4: Encoder[SetThermalPipeProp_13384_V4.LinedFlue] =
        semiauto.deriveEncoder[SetThermalPipeProp_13384_V4.LinedFlue]

    given Decoder[SetThermalPipeProp_13384_V4] =
        semiauto.deriveDecoder[SetThermalPipeProp_13384_V4]
    given Encoder[SetThermalPipeProp_13384_V4] =
        semiauto.deriveEncoder[SetThermalPipeProp_13384_V4]

    given Decoder[ThermalChannelTopologyOp_13384_V4] =
        semiauto.deriveDecoder[ThermalChannelTopologyOp_13384_V4]
    given Encoder[ThermalChannelTopologyOp_13384_V4] =
        semiauto.deriveEncoder[ThermalChannelTopologyOp_13384_V4]

    given Decoder[ThermalPipeTrackingOp_13384_V4] =
        semiauto.deriveDecoder[ThermalPipeTrackingOp_13384_V4]
    given Encoder[ThermalPipeTrackingOp_13384_V4] =
        semiauto.deriveEncoder[ThermalPipeTrackingOp_13384_V4]

    given Decoder[ThermalPreElementOp_13384_V4] =
        semiauto.deriveDecoder[ThermalPreElementOp_13384_V4]
    given Encoder[ThermalPreElementOp_13384_V4] =
        semiauto.deriveEncoder[ThermalPreElementOp_13384_V4]

    given Decoder[AddThermalPipeElement_13384_V4] =
        semiauto.deriveDecoder[AddThermalPipeElement_13384_V4]
    given Encoder[AddThermalPipeElement_13384_V4] =
        semiauto.deriveEncoder[AddThermalPipeElement_13384_V4]

    given Decoder[ThermalPipeDescr_13384_V4] =
        semiauto.deriveDecoder[ThermalPipeDescr_13384_V4]
    given Encoder[ThermalPipeDescr_13384_V4] =
        semiauto.deriveEncoder[ThermalPipeDescr_13384_V4]

    // ── PostFireboxPipeDescrSlot_V7 ──────────────────────────────────────
    // Hand-written encoder/decoder modelling the V6 PostFireboxPipeDescrSlot
    // companion-object pattern.

    given Encoder[PostFireboxPipeDescrSlot_V7] = Encoder.instance {
        case PostFireboxPipeDescrSlot_V7.FlueSlot(d)        =>
            Json.obj("FlueSlot" -> Encoder[Seq[FlowOnlyPipeDescr_15544_V4]].apply(d))
        case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(d) =>
            Json.obj("ThermalFlueSlot" -> Encoder[Seq[ThermalPipeDescr_13384_V4]].apply(d))
        case PostFireboxPipeDescrSlot_V7.ConnectorSlot(d)   =>
            Json.obj("ConnectorSlot" -> Encoder[Seq[ThermalPipeDescr_13384_V4]].apply(d))
        case PostFireboxPipeDescrSlot_V7.ChimneySlot(d)     =>
            Json.obj("ChimneySlot" -> Encoder[Seq[ThermalPipeDescr_13384_V4]].apply(d))
        case PostFireboxPipeDescrSlot_V7.NoFlueSlot         =>
            Json.obj("NoFlueSlot" -> Json.Null)
    }

    given Decoder[PostFireboxPipeDescrSlot_V7] = Decoder.instance { c =>
        c.downField("FlueSlot")
            .as[Seq[FlowOnlyPipeDescr_15544_V4]]
            .map(PostFireboxPipeDescrSlot_V7.FlueSlot(_))
            .orElse(
                c.downField("ThermalFlueSlot")
                    .as[Seq[ThermalPipeDescr_13384_V4]]
                    .map(PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(_))
            )
            .orElse(
                c.downField("ConnectorSlot")
                    .as[Seq[ThermalPipeDescr_13384_V4]]
                    .map(PostFireboxPipeDescrSlot_V7.ConnectorSlot(_))
            )
            .orElse(
                c.downField("ChimneySlot")
                    .as[Seq[ThermalPipeDescr_13384_V4]]
                    .map(PostFireboxPipeDescrSlot_V7.ChimneySlot(_))
            )
            .orElse(
                c.downField("NoFlueSlot").as[Unit].map(_ => PostFireboxPipeDescrSlot_V7.NoFlueSlot)
            )
    }

// PostFireboxPipes codecs are in its companion object
