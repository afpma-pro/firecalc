/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.dto.CustomYAMLEncoderDecoder

import afpma.firecalc.ui.models.BillingInfo
import afpma.firecalc.ui.models.ClientProjectData
import afpma.firecalc.ui.models.schema.common.BillingInfo_Version
import afpma.firecalc.ui.models.schema.common.ClientProjectData_Version

import io.circe.*

/* Instances for CustomYAMLEncoderDecoder[A] */
object custom_yaml:

    // BillingInfo

    val billingInfo = new CustomYAMLEncoderDecoder[BillingInfo]:
        type Version = BillingInfo_Version
        val VERSION = BillingInfo_Version(1)
        override given decoder: Decoder[BillingInfo] = circe.given_Decoder_BillingInfo
        override given encoder: Encoder[BillingInfo] = circe.given_Encoder_BillingInfo

    // ClientProjectData

    val clientProjectData = new CustomYAMLEncoderDecoder[ClientProjectData]:
        type Version = ClientProjectData_Version
        val VERSION = ClientProjectData_Version(1)
        override given decoder: Decoder[ClientProjectData] = circe.given_Decoder_ClientProjectData
        override given encoder: Encoder[ClientProjectData] = circe.given_Encoder_ClientProjectData
