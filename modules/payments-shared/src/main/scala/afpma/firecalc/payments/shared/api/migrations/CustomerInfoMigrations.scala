/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api.migrations

import io.scalaland.chimney.Transformer
import afpma.firecalc.payments.shared.api.v1.CustomerInfo_V1

/**
 * Migration transformers for CustomerInfo versions.
 * Handles evolution of the payment API customer data contract.
 */
object CustomerInfoMigrations:

    /**
     * Future: Transformer from V1 to V2
     * 
     * Example when V2 is created (e.g., adding VAT number):
     * 
     * given Transformer[CustomerInfo_V1, CustomerInfo_V2] = 
     *     Transformer.define[CustomerInfo_V1, CustomerInfo_V2]
     *         .withFieldConst(_.vatNumber, None)  // default for existing data
     *         .buildTransformer
     */

    /**
     * Migrate to latest version.
     * Currently returns V1 as-is since it's the latest.
     */
    def migrateToLatest(info: CustomerInfo_V1): CustomerInfo_V1 = info