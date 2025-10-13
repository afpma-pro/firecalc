/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.migrations

import io.scalaland.chimney.Transformer
import afpma.firecalc.ui.models.schema.v1.ClientProjectData_V1

/**
 * Migration transformers for ClientProjectData versions.
 * Handles evolution of sensitive customer data structure.
 */
object ClientProjectDataMigrations:

    /**
     * Future: Transformer from V1 to V2
     * 
     * Example when V2 is created (e.g., adding secondary contact):
     * 
     * given Transformer[ClientProjectData_V1, ClientProjectData_V2] = 
     *     Transformer.define[ClientProjectData_V1, ClientProjectData_V2]
     *         .withFieldConst(_.secondary_contact, None)  // default for existing data
     *         .buildTransformer
     */

    /**
     * Migrate to latest version.
     * Currently returns V1 as-is since it's the latest.
     */
    def migrateToLatest(data: ClientProjectData_V1): ClientProjectData_V1 = data