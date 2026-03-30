/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v6

import afpma.firecalc.dto.v6.FireCalcYAML_V6

import afpma.firecalc.ui.models.schema.common.*
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

/**
 * Unified application state schema V6 (UI module).
 *
 * ## Changes from V5:
 * - engine_state upgraded from FireCalcYAML_V5 to FireCalcYAML_V6
 *   (V6 replaces separate flue/connector/chimney pipe fields with
 *   a single `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]`)
 *
 * ## Components:
 * - engine_state: FireCalcYAML_V6 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend)
 */
final case class AppStateSchema_V6(
    version: AppStateSchema_Version = AppStateSchema_V6.VERSION,

    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V6,

    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,

    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V6:

    val VERSION = AppStateSchema_Version(6)

    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}

    given Encoder[AppStateSchema_V6] = semiauto.deriveEncoder[AppStateSchema_V6]
    given Decoder[AppStateSchema_V6] = semiauto.deriveDecoder[AppStateSchema_V6]
