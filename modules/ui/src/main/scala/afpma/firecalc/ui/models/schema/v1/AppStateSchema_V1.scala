/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v1

import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.ui.models.schema.common.*
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto

/**
 * Unified application state schema V1 (UI module).
 *
 * All three components are independently versioned and persisted atomically in localStorage:
 * - engine_state: FireCalcYAML_V1 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend)
 */
final case class AppStateSchema_V1(
    version: AppStateSchema_Version = AppStateSchema_Version(1),
    
    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V1,
    
    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,
    
    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V1:
    
    import afpma.firecalc.dto.instances.given
    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}
    
    given Encoder[AppStateSchema_V1] = semiauto.deriveEncoder[AppStateSchema_V1]
    given Decoder[AppStateSchema_V1] = semiauto.deriveDecoder[AppStateSchema_V1]