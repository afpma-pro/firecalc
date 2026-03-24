/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v5

import afpma.firecalc.dto.v5.FireCalcYAML_V5

import afpma.firecalc.ui.models.schema.common.*
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

/**
 * Unified application state schema V5 (UI module).
 *
 * ## Versioning Rule (Option B - Composite Versioning)
 *
 * **The container schema version MUST be incremented when any component version changes.**
 *
 * ## Changes from V4:
 * - engine_state upgraded from FireCalcYAML_V4 to FireCalcYAML_V5
 *   (V5 replaces separate flue/connector/chimney pipe fields with
 *   a single `post_firebox_pipes: Seq[PostFireboxPipeDescrSlot]`)
 *
 * ## Components:
 * - engine_state: FireCalcYAML_V5 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend)
 *
 * @see docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md for full versioning documentation
 */
final case class AppStateSchema_V5(
    version: AppStateSchema_Version = AppStateSchema_Version(5),

    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V5,

    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,

    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V5:

    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}

    given Encoder[AppStateSchema_V5] = semiauto.deriveEncoder[AppStateSchema_V5]
    given Decoder[AppStateSchema_V5] = semiauto.deriveDecoder[AppStateSchema_V5]
