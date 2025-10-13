/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.ui.models.{BillingInfo, ClientProjectData}

import io.circe.*
import io.circe.generic.semiauto

/* Instances for CustomYAMLEncoderDecoder[A] */
object custom_yaml:

    // BillingInfo
    
    val billingInfo = new CustomYAMLEncoderDecoder[BillingInfo]:
        override given decoder: Decoder[BillingInfo] = circe.given_Decoder_BillingInfo
        override given encoder: Encoder[BillingInfo] = circe.given_Encoder_BillingInfo

    // ClientProjectData
    
    val clientProjectData = new CustomYAMLEncoderDecoder[ClientProjectData]:
        override given decoder: Decoder[ClientProjectData] = circe.given_Decoder_ClientProjectData
        override given encoder: Encoder[ClientProjectData] = circe.given_Encoder_ClientProjectData
