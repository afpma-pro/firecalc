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
 * This ensures:
 * - Clear migration path when reading localStorage
 * - Compile-time guarantee that AppStateSchema_V5 contains FireCalcYAML_V5
 * - No ambiguity about which component versions are contained
 * 
 * ## Changes from V4:
 * - bump to Firebox_V4
 * - T
 * 
 * ## Changes from V3:
 * - add SetPropertiesInBatch subtype for ThermalPipeDescr_13384
 * - TODO
 *
 * ## Changes from V2:
 * - User can specify and override default roughtness when setting the propserty "SetMaterial" in EN13384 and EN15544 pipes
 * - Material_15544_V2 is to replace Material_15544
 *
 * ## Changes from V1:
 * - engine_state upgraded from FireCalcYAML_V1 to FireCalcYAML_V2
 *   (FireCalcYAML_V2 adds height_of_first_row_of_air_injectors field to Traditional firebox)
 *
 * ## Components:
 * - engine_state: FireCalcYAML_V4 (sent to backend for PDF generation)
 * - sensitive_data: ClientProjectData_V1 (NEVER sent to backend)
 * - billing_data: BillingInfo_V1 (stored client-side, transforms to CustomerInfo_V1 when sent to payment backend)
 *
 * @see docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md for full versioning documentation
 */
final case class AppStateSchema_V5(
    version: AppStateSchema_Version = AppStateSchema_V5.VERSION,

    /** Versioned engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V5,

    /** Versioned sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,

    /** Versioned billing data - stored client-side, transforms to CustomerInfo_V1 for payment backend API calls */
    billing_data: BillingInfo_V1
) extends AppStateSchema_Format

object AppStateSchema_V5:

    val VERSION = AppStateSchema_Version(5)

    import afpma.firecalc.ui.instances.circe.{given_Decoder_BillingInfo, given_Encoder_BillingInfo}

    given Encoder[AppStateSchema_V5] = semiauto.deriveEncoder[AppStateSchema_V5]
    given Decoder[AppStateSchema_V5] = semiauto.deriveDecoder[AppStateSchema_V5]
