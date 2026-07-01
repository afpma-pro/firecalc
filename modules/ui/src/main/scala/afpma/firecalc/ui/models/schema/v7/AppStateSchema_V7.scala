/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v7

import afpma.firecalc.dto.v7.FireCalcYAML_V7

import afpma.firecalc.ui.models.schema.common.*
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

/**
 * Unified application state schema V7 (UI module).
 *
 * ## Changes from V6:
 * - engine_state upgraded from FireCalcYAML_V6 to FireCalcYAML_V7
 *   (V7 wraps post_firebox_pipes in FramedPostFireboxPipes with
 *   initialDirection and initialPosition at the wrapper level)
 *
 * ## Components:
 * - engine_state: FireCalcYAML_V7 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend API calls)
 */
final case class AppStateSchema_V7(
    version: AppStateSchema_Version = AppStateSchema_V7.VERSION,

    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V7,

    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,

    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V7:

    val VERSION = AppStateSchema_Version(7)

    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}

    given Encoder[AppStateSchema_V7] = semiauto.deriveEncoder[AppStateSchema_V7]
    given Decoder[AppStateSchema_V7] = semiauto.deriveDecoder[AppStateSchema_V7]
