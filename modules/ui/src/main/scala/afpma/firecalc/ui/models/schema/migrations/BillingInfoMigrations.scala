/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.migrations

import io.scalaland.chimney.Transformer
import afpma.firecalc.ui.models.schema.v1.BillingInfo_V1

/**
 * Migration transformers for BillingInfo versions.
 * Handles evolution of UI billing form data structure.
 */
object BillingInfoMigrations:

    /**
     * Future: Transformer from V1 to V2
     * 
     * Example when V2 is created (e.g., adding business registration number):
     * 
     * given Transformer[BillingInfo_V1, BillingInfo_V2] = 
     *     Transformer.define[BillingInfo_V1, BillingInfo_V2]
     *         .withFieldConst(_.business_registration, None)  // default for existing data
     *         .buildTransformer
     */

    /**
     * Migrate to latest version.
     * Currently returns V1 as-is since it's the latest.
     */
    def migrateToLatest(info: BillingInfo_V1): BillingInfo_V1 = info