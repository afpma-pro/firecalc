/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v2

import afpma.firecalc.dto.v2.FireCalcYAML_V2

import afpma.firecalc.ui.models.schema.common.*
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto

/**
 * Unified application state schema V2 (UI module).
 *
 * ## Versioning Rule (Option B - Composite Versioning)
 *
 * **The container schema version MUST be incremented when any component version changes.**
 *
 * This ensures:
 * - Clear migration path when reading localStorage
 * - Compile-time guarantee that AppStateSchema_V2 contains FireCalcYAML_V2
 * - No ambiguity about which component versions are contained
 *
 * ## Changes from V1:
 * - engine_state upgraded from FireCalcYAML_V1 to FireCalcYAML_V2
 *   (FireCalcYAML_V2 adds height_of_first_row_of_air_injectors field to Traditional firebox)
 *
 * ## Components:
 * - engine_state: FireCalcYAML_V2 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend)
 *
 * @see docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md for full versioning documentation
 */
final case class AppStateSchema_V2(
    version: AppStateSchema_Version = AppStateSchema_Version(2),

    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V2,

    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,

    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V2:

    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}

    given Encoder[AppStateSchema_V2] = semiauto.deriveEncoder[AppStateSchema_V2]
    given Decoder[AppStateSchema_V2] = semiauto.deriveDecoder[AppStateSchema_V2]
