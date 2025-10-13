/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1

/**
 * Billing information for UI forms.
 * Type alias to the latest version (V1).
 *
 * This data transforms to CustomerInfo_V1 when calling payment APIs.
 */
type BillingInfo = BillingInfo_V1

object BillingInfo:
    export BillingInfo_V1.{given, *}

// Re-export types from schema.v1 for backward compatibility
export schema.v1.{BillingInfoWithLanguage, GivenName, FamilyName, CompanyName}